# build:
#   $ cd CIMApplication; mvn -DskipTests install; docker build --file OpenLiberty.Dockerfile --tag derrickoswald/cimapplication .
# run:
#   $ docker run --rm --publish 9080:9080 --net spark_default --link="spark_master:sandbox" derrickoswald/cimapplication start-openliberty sandbox beach
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

ENV PATH /usr/local/openliberty/bin:$PATH
RUN mkdir -p /usr/local/openliberty

WORKDIR /usr/local/openliberty

RUN DEBIAN_FRONTEND=noninteractive apt-get install --yes --quiet execstack
RUN set -x \
	&& export OPENLIBERTY_VERSION=20.0.0.3 \
	&& export OPENLIBERTY_RELEASE=2020-03-05_1433 \
	&& curl --fail --show-error --location https://public.dhe.ibm.com/ibmdl/export/pub/software/openliberty/runtime/release/${OPENLIBERTY_RELEASE}/openliberty-javaee8-${OPENLIBERTY_VERSION}.zip --output openliberty.zip \
	&& unzip openliberty.zip \
	&& mv wlp/* /usr/local/openliberty \
	&& rm --recursive --force wlp \
	&& rm openliberty.zip

# Default web UI
EXPOSE 9080
EXPOSE 9443

# layers added for CIMApplication (do this last to speed up Docker build)

# copy start script
COPY CIMEar/start-openliberty /opt/util/bin/start-openliberty

# set up CIMApplication
RUN bin/server create cimapplication
# copy server configuration doesn't work
# COPY CIMEar/server.xml /usr/local/openliberty/usr/servers/cimapplication/server.xml
# so we edit the file:
RUN sed --in-place "s|<server description=\"new server\">|<server description=\"cimapplication\">\n\n    <variable name=\"spark_host\" value=\"\${NAMENODE}\" defaultValue=\"sandbox\" />\n    <variable name=\"cassandra_host\" value=\"\${CASSANDRANODE}\" defaultValue=\"beach\" />|g" /usr/local/openliberty/usr/servers/cimapplication/server.xml
RUN sed --in-place "s|<httpEndpoint|<httpEndpoint host=\"*\"|g" /usr/local/openliberty/usr/servers/cimapplication/server.xml
RUN sed --in-place "s|<\!-- <user name=\"yourUserName\" password=\"\" />  -->|<user name=\"admin\" password=\"Green1antern\" />|g" /usr/local/openliberty/usr/servers/cimapplication/server.xml
RUN sed --in-place "s|</server>|    <connectionFactory jndiName=\"eis/SparkConnectionFactory\">\n        <interface-name>ch.ninecode.cim.connector.CIMConnectionFactory</interface-name>\n        <resource-adapter>#CIMConnector</resource-adapter>\n        <properties.CIMApplication.CIMConnector ConnectionURL=\"spark://\${spark_host}:7077\"/>\n        <properties.CIMApplication.CIMConnector ServerName=\"\${cassandra_host}\"/>\n    </connectionFactory>\n\n</server>|g" /usr/local/openliberty/usr/servers/cimapplication/server.xml
RUN sed --in-place "s|</server>|    <cors\n        domain=\"/cimweb\"\n        allowedOrigins=\"*\"\n        allowedMethods=\"GET, POST, HEAD, OPTIONS, PUT, DELETE\"\n        allowCredentials=\"true\"/>\n\n</server>|g" /usr/local/openliberty/usr/servers/cimapplication/server.xml
RUN sed --in-place "s|</server>|    <transaction totalTranLifetimeTimeout=\"10m\"/>\n\n</server>|g" /usr/local/openliberty/usr/servers/cimapplication/server.xml

# copy the .ear to dropins to install it
ADD CIMEar/target/CIMApplication.ear /usr/local/openliberty/usr/servers/cimapplication/dropins/
