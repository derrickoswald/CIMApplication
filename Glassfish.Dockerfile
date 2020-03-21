# build:
#   $ cd CIMApplication; mvn -DskipTests install; docker build --file Glassfish.Dockerfile --tag derrickoswald/cimapplication .
# run:
#   $ docker run --rm --publish 9080:9080 --net spark_default --link="spark_master:sandbox" derrickoswald/cimapplication start-tomee sandbox beach
# access:
#   http://localhost:8080/cimweb/cim/ping
#   http://localhost:8080/cimweb/cim/list

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

WORKDIR /usr/local/glassfish
#	&& curl --fail --show-error --location http://download.oracle.com/glassfish/${GLASS_VERSION}/release/glassfish-${GLASS_VERSION}.zip --output glassfish.zip \

RUN set -x \
	&& export GLASS_MAJOR_VERSION=5 \
	&& export GLASS_VERSION=5.0.1 \
	&& curl --fail --show-error --location http://download.oracle.com/glassfish/${GLASS_VERSION}/nightly/glassfish-${GLASS_VERSION}-b05-01_23_2019.zip --output glassfish.zip \
	&& unzip glassfish.zip \
	&& mv glassfish${GLASS_MAJOR_VERSION}/* /usr/local/glassfish \
	&& rm --recursive --force glassfish${GLASS_MAJOR_VERSION} \
	&& rm /usr/local/glassfish/bin/*.bat \
	&& rm /usr/local/glassfish/glassfish/bin/*.bat \
	&& rm /usr/local/glassfish/javadb/bin/*.bat \
	&& rm glassfish.zip*

# a little more memory than 536870912 bytes
RUN sed --in-place "s|<jvm-options>-Xmx512m</jvm-options>|<jvm-options>-Xmx4g</jvm-options>|g" /usr/local/glassfish/glassfish/domains/domain1/config/domain.xml

# Default web UI
RUN sed --in-place "s|<network-listener port=\"8080\" protocol=\"http-listener-1\" transport=\"tcp\" name=\"http-listener-1\" thread-pool=\"http-thread-pool\"></network-listener>|<network-listener port=\"9080\" protocol=\"http-listener-1\" transport=\"tcp\" name=\"http-listener-1\" thread-pool=\"http-thread-pool\"></network-listener>|g" /usr/local/glassfish/glassfish/domains/domain1/config/domain.xml
EXPOSE 9080
EXPOSE 4848

# layers added for CIMApplication (do this last to speed up Docker build)

# copy start script
COPY CIMEar/start-glassfish /opt/util/bin/start-glassfish
COPY CIMEar/simulation_schema.sql /opt/util/bin/simulation_schema.sql

# set up CIMApplication
# this doesn't work because of a bug loading the joda time jar:
#    Exception while loading the app : Type org.joda.time.ReadableInstant not present
# see https://stackoverflow.com/questions/46604406/when-jodatime-v-2-5-library-included-into-war-file-deployment-fails
# see also: https://github.com/javaee/glassfish/blob/562677552f8b9325435101d9eda12a8ee3a8f457/appserver/connectors/connectors-runtime/src/main/java/com/sun/enterprise/connectors/module/ConnectorDeployer.java
ADD CIMEar/target/CIMApplication.ear /usr/local/glassfish/glassfish/domains/domain1/autodeploy
# this fails with messages about CDI:
#[#|2020-01-30T16:00:15.479+0000|SEVERE|glassfish 5.0|javax.enterprise.system.core|_ThreadID=65;_ThreadName=AutoDeployer;_TimeMillis=1580400015479;_LevelValue=1000;|
#  Exception while loading the app : CDI deployment failure:null
#java.lang.reflect.MalformedParameterizedTypeException

# To set up the admin, you need to use the asadmin utility.
# I don't know a way to drive the asadmin utility, other than with a redirect to stdin,
# this is what need to be done:

#root@glassfish:/usr/local/glassfish# bin/asadmin
#Use "exit" to exit and "help" for online help.
#asadmin> change-admin-password
#Enter admin user name [default: admin]>      yes, it's admin
#Enter the admin password>       note that it is initially no passowrd, i.e. blank, so just press return
#Enter the new admin password>        note it silently doesn't echo Green1antern
#Enter the new admin password again>       note here too Green1antern
#Command change-admin-password executed successfully.
#
#asadmin> enable-secure-admin
#Enter admin user name>  admin
#Enter admin password for user "admin">       note that it's the Green1antern entered above
#You must restart all running servers for the change in secure admin to take effect.
#Command enable-secure-admin executed successfully.
#
#asadmin> stop-domain
#asadmin> start-domain
# But this fails with messages about class not found exception in the security (HTTPS) layer.

## set up CORS
#RUN sed -i.bak "s|</web-app>|\
#  <!-- ==================== CORS support ==================== -->\n\
#  <!-- see http://tomcat.apache.org/tomcat-8.0-doc/config/filter.html#CORS_Filter -->\n\
#    <filter>\n\
#        <filter-name>CorsFilter</filter-name>\n\
#        <filter-class>org.apache.catalina.filters.CorsFilter</filter-class>\n\
#        <init-param>\n\
#          <param-name>cors.allowed.origins</param-name>\n\
#          <param-value>*</param-value>\n\
#        </init-param>\n\
#        <init-param>\n\
#            <param-name>cors.allowed.methods</param-name>\n\
#            <param-value>GET,POST,HEAD,OPTIONS,PUT,DELETE</param-value>\n\
#        </init-param>\n\
#    </filter>\n\
#    <filter-mapping>\n\
#        <filter-name>CorsFilter</filter-name>\n\
#        <url-pattern>/*</url-pattern>\n\
#    </filter-mapping>\n\n\
#</web-app>|g" /usr/local/tomee/conf/web.xml
