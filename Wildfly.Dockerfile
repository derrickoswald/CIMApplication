# build:
#   $ cd CIMApplication; mvn -DskipTests install; docker build --file Wildfly.Dockerfile --tag derrickoswald/cimapplication .
# run:
#   $ docker run --rm --publish 9080:9080 --net spark_default --link="spark_master:sandbox" derrickoswald/cimapplication start-wildfly sandbox beach
# access:
#   http://localhost:8080/cimweb/cim/ping
#   http://localhost:8080/cimweb/cim/list

FROM derrickoswald/spark-docker:latest
LABEL maintainer = "Derrick.Oswald@9code.ch"

# Install Cassandra
RUN echo "deb http://www.apache.org/dist/cassandra/debian 311x main" | tee -a /etc/apt/sources.list.d/cassandra.sources.list
RUN curl https://downloads.apache.org/cassandra/KEYS | apt-key add -
RUN DEBIAN_FRONTEND=noninteractive apt-get install --yes --quiet apt-transport-https ca-certificates
RUN apt-get update \
  && apt-key adv --keyserver pool.sks-keyservers.net --recv-key A278B781FE4B2BDA
RUN DEBIAN_FRONTEND=noninteractive apt-get install --yes --quiet --no-install-recommends cassandra
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

ENV PATH /usr/local/wildfly/bin:$PATH
RUN mkdir -p /usr/local/wildfly

WORKDIR /usr/local/wildfly

RUN set -x \
	&& export WILDFLY_VERSION=19.0.0.Final \
	&& curl --fail --show-error --location https://download.jboss.org/wildfly/${WILDFLY_VERSION}/wildfly-${WILDFLY_VERSION}.zip --output wildfly.zip \
	&& unzip wildfly.zip \
	&& mv wildfly-${WILDFLY_VERSION}/* /usr/local/wildfly \
	&& rm --recursive --force wildfly-${WILDFLY_VERSION} \
	&& rm /usr/local/wildfly/bin/*.bat \
	&& rm wildfly.zip

# a little more memory than 536870912 bytes
RUN sed --in-place "s|-Xmx512m|-Xmx4g|g" /usr/local/wildfly/bin/standalone.conf

# Default web UI
RUN sed --in-place "s|<socket-binding name=\"http\" port=\"\${jboss.http.port:8080}\"/>|<socket-binding name=\"http\" port=\"\${jboss.http.port:9080}\"/>|g" /usr/local/wildfly/standalone/configuration/standalone.xml
RUN sed --in-place "s|<inet-address value=\"\${jboss.bind.address:127.0.0.1}\"/>|<any-address/>|g" /usr/local/wildfly/standalone/configuration/standalone.xml
RUN sed --in-place "s|<inet-address value=\"\${jboss.bind.address.management:127.0.0.1}\"/>|<any-address/>|g" /usr/local/wildfly/standalone/configuration/standalone.xml
EXPOSE 9080
EXPOSE 9990

# layers added for CIMApplication (do this last to speed up Docker build)

# copy start script
COPY CIMEar/start-wildfly /opt/util/bin/start-wildfly
COPY CIMEar/simulation_schema.sql /opt/util/bin/simulation_schema.sql

# set up wildfly manager
RUN /usr/local/wildfly/bin/add-user.sh wildfly Green1antern

# Notes:
# to change from logging INFO to TRACE:
# edit standalone/configuration/standalone.xml
#
# Wildfly tries to load every native library contained in a jar file - WTF? and this crashes:
#   SIGSEGV in ld-linux-x86-64.so
# to avoid the crash, remove native libraries for platforms other than x86_64-Linux from:
# - com.github.jnr.jffi
# - org.apache.commons.commons-crypto

# ToDo:
# - raise issue with Wildfly about loading native libraries
# - why are Net and Util jars sent to the cluster with package prefix names?
# - set up CIMApplication
# note, this doesn't work because because there is a conflict between JAX-RS providers (I think, call it a working hypothesis):
#    JBOSS (Wildfly) comes with RestEasy as a JAX-RS provider
#    Hadoop uses Jersey (version 1.9 ?) as a JAX-RS provider
# this leads to the following error in deployment:
#    ERROR [org.jboss.msc.service.fail] (MSC service thread 1-2) MSC000001: Failed to start service jboss.deployment.subunit."CIMApplication.ear"."CIMWeb.war".INSTALL: org.jboss.msc.service.StartException in service jboss.deployment.subunit."CIMApplication.ear"."CIMWeb.war".INSTALL: WFLYSRV0153: Failed to process phase INSTALL of subdeployment "CIMWeb.war" of deployment "CIMApplication.ear"
#    Caused by: java.lang.NoClassDefFoundError: Failed to link org/apache/hadoop/hdfs/web/resources/UserProvider (Module "deployment.CIMApplication.ear" from Service Module Loader): com/sun/jersey/server/impl/inject/AbstractHttpContextInjectable
# Tried various combinations of jboss-deployment-structure.xml in CIMEar META-INF with no success.
#
ADD CIMEar/target/CIMApplication.ear /usr/local/wildfly/standalone/deployments/
#RUN echo 'openejb.deployments.classpath.include = .*ninecode.*' >> /usr/local/tomee/conf/system.properties

# set up CORS
#RUN sed --in-place "s|</web-app>|\
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
