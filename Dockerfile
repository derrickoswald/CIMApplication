# build:
#   $ cd CIMApplication; mvn -DskipTests install; docker build --tag derrickoswald/cimapplication .
# run:
#   $ docker run --rm --publish 9080:8080 --net spark_default --link="spark_master:sandbox" derrickoswald/cimapplication start-tomee sandbox
# access:
#   http://localhost:8080/cimweb/cim/ping
#   http://localhost:8080/cimweb/cim/list

# Most of this is directly copied from https://github.com/tomitribe/docker-tomee/blob/master/8-jre-7.0.4-plus/Dockerfile
# but based on a hadoop image from singularities, both of which have a common root at openjdk:8-jre,
# and just setting the fs.defaultFS in core-site.xml as is done in start-hadoop

FROM singularities/hadoop:2.7
LABEL maintainer = "Derrick.Oswald@9code.ch"

ENV PATH /usr/local/tomee/bin:$PATH
RUN mkdir -p /usr/local/tomee

WORKDIR /usr/local/tomee

# curl -fsSL 'https://www.apache.org/dist/tomee/KEYS' | awk -F ' = ' '$1 ~ /^ +Key fingerprint$/ { gsub(" ", "", $2); print $2 }' | sort -u
ENV GPG_KEYS \
    223D3A74B068ECA354DC385CE126833F9CF64915 \
    678F2D98F1FD9643811639FB622B8F2D043F71D8 \
    7A2744A8A9AAF063C23EB7868EBE7DBE8D050EEF \
    82D8419BA697F0E7FB85916EE91287822FDB81B1 \
    9056B710F1E332780DE7AF34CBAEBE39A46C4CA1 \
    A57DAF81C1B69921F4BA8723A8DE0A4DB863A7C1 \
    B7574789F5018690043E6DD9C212662E12F3E1DD \
    B8B301E6105DF628076BD92C5483E55897ABD9B9 \
    BDD0BBEB753192957EFC5F896A62FC8EF17D8FEF \
    C23A3F6F595EBD0F960270CC997C8F1A5BE6E4C1 \
    D11DF12CC2CA4894BDE638B967C1227A2678363C \
    DBCCD103B8B24F86FFAAB025C8BB472CD297D428 \
    F067B8140F5DD80E1D3B5D92318242FE9A0B1183 \
    FAA603D58B1BA4EDF65896D0ED340E0E6D545F97

RUN set -xe \
    && for key in $GPG_KEYS; do \
        gpg --keyserver ha.pool.sks-keyservers.net --recv-keys "$key"; \
    done

RUN set -x \
	&& curl -fSL https://repo.maven.apache.org/maven2/org/apache/tomee/apache-tomee/7.0.4/apache-tomee-7.0.4-plus.tar.gz.asc -o tomee.tar.gz.asc \
	&& curl -fSL https://repo.maven.apache.org/maven2/org/apache/tomee/apache-tomee/7.0.4/apache-tomee-7.0.4-plus.tar.gz -o tomee.tar.gz \
    && gpg --batch --verify tomee.tar.gz.asc tomee.tar.gz \
	&& tar -zxf tomee.tar.gz \
	&& mv apache-tomee-plus-7.0.4/* /usr/local/tomee \
	&& rm -Rf apache-tomee-plus-7.0.4 \
	&& rm bin/*.bat \
	&& rm tomee.tar.gz*

# a little more memory than 4049600512 bytes
ENV CATALINA_OPTS -Xmx8g

EXPOSE 8080

# install Cassandra
RUN echo "deb http://www.apache.org/dist/cassandra/debian 311x main" | tee -a /etc/apt/sources.list.d/cassandra.sources.list
RUN curl https://www.apache.org/dist/cassandra/KEYS | apt-key add -
RUN apt-get update \
  && apt-key adv --keyserver pool.sks-keyservers.net --recv-key A278B781FE4B2BDA \
  && DEBIAN_FRONTEND=noninteractive apt-get install \
    -yq --no-install-recommends \
       cassandra \
  && apt-get clean

# install tools
RUN apt-get update \
  && DEBIAN_FRONTEND=noninteractive apt-get install \
    -yq --no-install-recommends \
      python python3 vim sqlite3 r-base p7zip net-tools \
  && apt-get clean \
	&& rm -rf /var/lib/apt/lists/*

# Install GridLAB-D
COPY CIMEar/gridlabd_3.2.0-2_amd64.deb /opt/util/gridlabd_3.2.0-2_amd64.deb
RUN dpkg -i /opt/util/gridlabd_3.2.0-2_amd64.deb \
  && rm /opt/util/gridlabd_3.2.0-2_amd64.deb

# set up environment
RUN echo "alias ll='ls -alF'">> /etc/bash.bashrc

# layers added for CIMApplication (do this last to speed up Docker build)

# copy start script
COPY CIMEar/start-tomee /opt/util/bin/start-tomee
COPY CIMEar/schema.sql /opt/util/bin/schema.sql

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
  <!-- see http://tomcat.apache.org/tomcat-7.0-doc/config/filter.html#CORS_Filter -->\n\
    <filter>\n\
        <filter-name>CorsFilter</filter-name>\n\
        <filter-class>org.apache.catalina.filters.CorsFilter</filter-class>\n\
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
