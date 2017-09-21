CIMApplication
======

J2EE access to Spark for Common Information Model (CIM) applications.

A good overview presentation is [Network Analysis and Simulation using Apache Spark on Compute Clusters](https://derrickoswald.github.io/CIMSparkPresentation/index.html?audio), or [without the audio tracks](https://derrickoswald.github.io/CIMSparkPresentation).

# Overview

This set of programs provides a demonstration of using Java EE Connector Architecture
[JCA](https://en.wikipedia.org/wiki/Java_EE_Connector_Architecture)
to connect to [Spark](https://spark.apache.org) and through the use of
[CIMReader](https://github.com/derrickoswald/CIMReader) being able to read
CIM files which are a standard interchange format based on IEC standards 61968 & 61970
(see [CIM users group](http://cimug.ucaiug.org/default.aspx) for additional details)
to access Spark Resilient Distributed Dataset (RDD) for each CIM class.

It thus provides a proof of principle for End-To-End access from a web browser to bespoke big-data applications running on Hadoop.

# Provisioning a Cluster

To create a cluster on Amazon ECS you can use the provisioning tool:

<a href="https://derrickoswald.github.io/CIMApplication">https://derrickoswald.github.io/CIMApplication</a>

# Architecture

![CIMApplication Overview](https://rawgit.com/derrickoswald/CIMApplication/master/img/Overview.svg "Overview diagram")

The use of a resource adapter within a J2EE container leverages the technologies that ease
deployment, security, transaction and user interface development that come with
the widely used enterprise platform. Code in Java or Scala runs in a virtual machine
within a containerized server-side platform and can send previously compiled long-running
jobs to the cluster of computers comprising the Hadoop infrastructure.

As such, it offers a bridge between legacy serial applications running on a single machine and
new parallel applications running on a cluster of machines.

# Caveats

- currently only works within [Tomee](https://tomee.apache.org/) the [openejb](https://en.wikipedia.org/wiki/Apache_OpenEJB) container hosted in [Tomcat](https://tomcat.apache.org/).

# TomEE Setup

- download and unzip/untar TomEE+ distribution in a suitable directory (for example in /opt)
- if necessary, change the files ownership, e.g chown --recursive /opt/apache-tomee-plus-7.0.1/
- to access the manager GUI (http://localhost:8080/manager/html), edit conf/tomcat-users.xml to
uncomment the section at the bottom of the file with rolename="tomee-admin" and username="tomee",
and then change the password as appropriate
- to enable TomEE, edit conf/tomee.xml and uncomment the &lt;Deployments dir="apps"/&gt; and make the apps directory:

    mkdir /opt/apache-tomee-plus-7.0.1/apps

- to deploy the application and reset, you could use these instructions (restart.sh marked executable):

    bin/shutdown.sh 2>/dev/null
    rm -rf apps/*
    rm logs/*
    cp ~/code/CIMApplication/CIMEar/target/CIMApplication.ear apps
    bin/startup.sh

- to enable SQLite access via JNDI, copy the SQLite jar file to the lib directory, and copy the SQLite data file too:

    cp .../org/xerial/sqlite-jdbc/3.14.2.1/sqlite-jdbc-3.14.2.1.jar /opt/apache-tomee-plus-7.0.1/lib
    mkdir /opt/apache-tomee-plus-7.0.1/database
    cp .../results.db /opt/apache-tomee-plus-7.0.1/database/timeseries.db

- then add a resource entry to the conf/tomee.xml, like so:

    <?xml version="1.0" encoding="UTF-8"?>
    <tomee>
      <!-- see http://tomee.apache.org/containers-and-resources.html -->
      <Deployments dir="apps" />
      <Resource id="SQLite Database" type="DataSource">
        JdbcDriver  org.sqlite.JDBC
        JdbcUrl jdbc:sqlite:/opt/apache-tomee-plus-7.0.1/database/timeseries.db
      </Resource>
    </tomee>

# REST Services

The CIMApplication (HTML/Javascript) accessed at http://server:8080/cimweb is supported on the backend by
RESTful services provided by the CIMWeb .war file.

Endpoint | Verb | Parameters | Description|
------------ | ------------- | ------------- | -------------|
/cimweb/cim/ping | GET | optional ;debug=true | Simple response EJB to check for proper deployment. The optional boolean debug matrix parameter will return the current set of environment variables on the server.|
/cimweb/cim/file | GET | optional path | Returns the contents of the directory or file.|
