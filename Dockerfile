# build:
#   $ cd CIMApplication; mvn -DskipTests install; docker build --tag derrickoswald/cimapplication .
# run:
#   $ docker run --rm --publish 8080:8080 --net resources_default --link="resources_sandbox_1:sandbox" derrickoswald/cimapplication
# access:
#   http://localhost:8080/cimweb/cim/ping
#   http://localhost:8080/cimweb/cim/list

FROM tomee:8-jre-7.0.2-plus
LABEL maintainer "Derrick.Oswald@9code.ch"

ADD CIMEar/target/CIMApplication.ear /usr/local/tomee/apps/

RUN mv /usr/local/tomee/conf/tomee.xml /usr/local/tomee/conf/tomee.xml.bak \
  && echo '<?xml version="1.0" encoding="UTF-8"?>' > /usr/local/tomee/conf/tomee.xml \
  && echo '<tomee>' >> /usr/local/tomee/conf/tomee.xml \
  && echo '  <!-- see http://tomee.apache.org/containers-and-resources.html -->' >> /usr/local/tomee/conf/tomee.xml \
  && echo '  <Deployments dir="apps" />' >> /usr/local/tomee/conf/tomee.xml \
  && echo '</tomee>' >> /usr/local/tomee/conf/tomee.xml

# Install tools
RUN apt-get update \
  && DEBIAN_FRONTEND=noninteractive apt-get install \
    -yq --no-install-recommends  \
      vim \
  && apt-get clean \
  && rm -rf /var/lib/apt/lists/*

# Set up environment
RUN echo "alias ll='ls -alF'">> /etc/bash.bashrc
