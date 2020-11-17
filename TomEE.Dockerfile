# build:
#   $ cd CIMApplication; mvn -DskipTests install; docker build --file TomEE.Dockerfile --tag derrickoswald/cimapplication .
# run:
#   $ docker run --rm --publish 9080:9080 --net spark_default --link="spark_master:sandbox" derrickoswald/cimapplication start-tomee sandbox beach
# access:
#   http://localhost:8080/cimweb/cim/ping
#   http://localhost:8080/cimweb/cim/list

# Most of this is directly copied from https://github.com/tomitribe/docker-tomee/blob/master/8-jre-7.1.0-plus/Dockerfile
# but based on a hadoop image from spark-docker, both of which have a common root at openjdk:8-jre.

FROM derrickoswald/spark-docker:latest
LABEL maintainer = "Derrick.Oswald@9code.ch"

# Install Cassandra
RUN echo "deb http://www.apache.org/dist/cassandra/debian 311x main" | tee -a /etc/apt/sources.list.d/cassandra.sources.list
RUN curl https://downloads.apache.org/cassandra/KEYS | apt-key add -
RUN DEBIAN_FRONTEND=noninteractive apt-get install -yq apt-transport-https ca-certificates
RUN apt-get update \
  && apt-key adv --keyserver pool.sks-keyservers.net --recv-key A278B781FE4B2BDA
RUN DEBIAN_FRONTEND=noninteractive apt-get install -yq --no-install-recommends cassandra
RUN apt-get clean
RUN  sed --in-place 's/enable_user_defined_functions: false/enable_user_defined_functions: true/g' /etc/cassandra/cassandra.yaml \
  && sed --in-place 's/enable_scripted_user_defined_functions: false/enable_scripted_user_defined_functions: true/g' /etc/cassandra/cassandra.yaml \
  && sed --in-place 's/read_request_timeout_in_ms: 5000/read_request_timeout_in_ms: 100000/g' /etc/cassandra/cassandra.yaml \
  && sed --in-place 's/range_request_timeout_in_ms: 10000/range_request_timeout_in_ms: 100000/g' /etc/cassandra/cassandra.yaml \
  && sed --in-place 's/write_request_timeout_in_ms: 2000/write_request_timeout_in_ms: 100000/g' /etc/cassandra/cassandra.yaml \
  && sed --in-place 's/INFO/WARN/g' /etc/cassandra/logback.xml \
  && sed --in-place 's/level="DEBUG"/level="WARN"/g' /etc/cassandra/logback.xml \
  && sed --in-place 's/level="ERROR"/level="WARN"/g' /etc/cassandra/logback.xml

# Cassandra ports
# Cassandra storage_port
EXPOSE 7000
# Cassandra ssl_storage_port
EXPOSE 7001
# Cassandra JMX monitoring port
# EXPOSE 7199
# Cassandra native_transport_port
EXPOSE 9042
# Cassandra rpc_port
EXPOSE 9160

ENV PATH /usr/local/tomee/bin:$PATH
RUN mkdir -p /usr/local/tomee

WORKDIR /usr/local/tomee

# for now skip verification step:
# see https://checker.apache.org/projs/tomee.html :
#     expired signiture cf6fc99c2cc77782	David Blevins <dblevins@tomitribe.com>

#RUN set -xe \
#	&& export GPG_KEYS="`curl -fsSL 'https://www.apache.org/dist/tomee/KEYS' | awk -F ' = ' '$1 ~ /^ +Key fingerprint$/ { gsub(" ", "", $2); print $2 }' | sort --unique`" \
#	&& for key in $GPG_KEYS; do \
#		gpg --keyserver ha.pool.sks-keyservers.net --recv-keys "$key"; \
#	done

#	&& curl -fSL https://repo.maven.apache.org/maven2/org/apache/tomee/apache-tomee/${TOMEE_VERSION}/apache-tomee-${TOMEE_VERSION}-plus.tar.gz.asc -o tomee.tar.gz.asc \

#	&& gpg --batch --verify tomee.tar.gz.asc tomee.tar.gz \

ENV TOMEE_VERSION 8.0.0
ENV CATALINA_BASE /usr/local/tomee
# a little more memory than 4049600512 bytes
ENV CATALINA_OPTS -Xmx8g

RUN set -x \
	&& curl --fail --show-error --location https://repo.maven.apache.org/maven2/org/apache/tomee/apache-tomee/${TOMEE_VERSION}/apache-tomee-${TOMEE_VERSION}-plus.tar.gz --output tomee.tar.gz \
	&& tar --gunzip --extract --file=tomee.tar.gz \
	&& mv apache-tomee-plus-${TOMEE_VERSION}/* /usr/local/tomee \
	&& rm --recursive --force apache-tomee-plus-${TOMEE_VERSION} \
	&& rm /usr/local/tomee/bin/*.bat \
	&& rm tomee.tar.gz*

# increase the cache size
RUN sed -i.bak "s|</Context>|    <Resources cacheMaxSize=\"51200\" />\r\n</Context>|g" ${CATALINA_BASE}/conf/context.xml

# Tomcat/TomEE+ web UI
RUN sed -i.bak "s|Connector port=\"8080\" protocol=\"HTTP/1.1\"|Connector port=\"9080\" protocol=\"HTTP/1.1\"|g" ${CATALINA_BASE}/conf/server.xml
EXPOSE 9080

# remove jul-to-slf4j, jul-to-slf4j is used by Spark and Hadoop and both can't exist together: see https://www.slf4j.org/legacy.html#jul-to-slf4j
# RUN rm /usr/local/tomee/lib/slf4j-jdk14-1.7.21.jar doesn't work (org.glassfish.jersey.server.ContainerException: java.lang.NoClassDefFoundError: org/slf4j/impl/StaticLoggerBinder)
# RUN rm /usr/local/spark-2.2.0/jars/jul-to-slf4j-1.7.16.jar doesn't work (java.lang.NoClassDefFoundError: org/slf4j/bridge/SLF4JBridgeHandler)

# deleting both together doesn't work either
# RUN rm /usr/local/tomee/lib/slf4j-jdk14-1.7.21.jar
# RUN rm /usr/local/tomee/lib/slf4j-api-1.7.21.jar

# naively removing AsyncConsoleHandler references doesn't work either
# RUN mv /usr/local/tomee/conf/logging.properties /usr/local/tomee/conf/logging.properties.original
# COPY CIMEar/logging.properties /usr/local/tomee/conf/logging.properties

# configuring Tomcat to use log4j rather than java.util.logging doesn't work either, see https://tomcat.apache.org/tomcat-8.0-doc/logging.html#Using_Log4j
# the only juli jars are for an earlier version and support was apparently dropped in 8.5.20 (TomEE 7.1.0)
# RUN mv /usr/local/tomee/conf/logging.properties /usr/local/tomee/conf/logging.properties.original
# COPY CIMEar/log4j.properties /usr/local/tomee/lib/log4j.properties
# COPY CIMEar/apache-log4j-1.2.17/log4j-1.2.17.jar /usr/local/tomee/liblog4j-1.2.17.jar
# COPY CIMEar/tomcat-juli-adapters.jar /usr/local/tomee/lib/tomcat-juli-adapters.jar
# RUN rm /usr/local/tomee/bin/tomcat-juli.jar
# COPY CIMEar/tomcat-juli.jar /usr/local/tomee/bin/tomcat-juli.jar

# naive remove slf4j doesn't work (catalina complains bitterly)
# RUN rm /usr/local/tomee/lib/slf4j-api-1.7.21.jar
# RUN rm /usr/local/tomee/lib/slf4j-jdk14-1.7.21.jar

# try no logging - still StackOverflowError
# COPY CIMEar/no_logging.properties /usr/local/tomee/conf/logging.properties

# removing slf4j from Spark doesn't work (org.apache.spark.sql.execution.datasources.parquet.ParquetFileFormat could not be instantiated)
# RUN rm /usr/local/spark-2.2.0/jars/jul-to-slf4j-1.7.16.jar
# RUN rm /usr/local/spark-2.2.0/jars/slf4j-api-1.7.16.jar

# layers added for CIMApplication (do this last to speed up Docker build)

# copy start script
COPY CIMEar/start-tomee /opt/util/bin/start-tomee
COPY CIMEar/simulation_schema.sql /opt/util/bin/simulation_schema.sql

# set up apps directory
RUN mv /usr/local/tomee/conf/tomee.xml /usr/local/tomee/conf/tomee.xml.bak
COPY CIMEar/tomee.xml /usr/local/tomee/conf/tomee.xml

# set up tomee user and manager
RUN mv /usr/local/tomee/conf/tomcat-users.xml /usr/local/tomee/conf/tomcat-users.bak
COPY CIMEar/tomcat-users.xml /usr/local/tomee/conf/tomcat-users.xml
RUN mkdir --parents /usr/local/tomee/conf/Catalina/localhost/
COPY CIMEar/manager.xml /usr/local/tomee/conf/Catalina/localhost/manager.xml

# set up CIMApplication
ADD CIMEar/target/CIMApplication.ear /usr/local/tomee/apps/
RUN echo 'openejb.deployments.classpath.include = .*ninecode.*' >> /usr/local/tomee/conf/system.properties

# set up CORS
RUN sed -i.bak "s|</web-app>|\
  <!-- ==================== CORS support ==================== -->\n\
  <!-- see http://tomcat.apache.org/tomcat-8.0-doc/config/filter.html#CORS_Filter -->\n\
    <filter>\n\
        <filter-name>CorsFilter</filter-name>\n\
        <filter-class>org.apache.catalina.filters.CorsFilter</filter-class>\n\
        <init-param>\n\
          <param-name>cors.allowed.origins</param-name>\n\
          <param-value>*</param-value>\n\
        </init-param>\n\
        <init-param>\n\
            <param-name>cors.allowed.methods</param-name>\n\
            <param-value>GET,POST,HEAD,OPTIONS,PUT,DELETE</param-value>\n\
        </init-param>\n\
    </filter>\n\
    <filter-mapping>\n\
        <filter-name>CorsFilter</filter-name>\n\
        <url-pattern>/*</url-pattern>\n\
    </filter-mapping>\n\n\
</web-app>|g" /usr/local/tomee/conf/web.xml
