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

# Video

An overview of the current release 2.3.4:

[![CIMApplication Overview](http://img.youtube.com/vi/uWfDuNiRp8M/0.jpg)](http://www.youtube.com/watch?v=uWfDuNiRp8M "CIMApplication Movie")


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
/cimweb/cim/ping | GET | optional debug | Simple response to check for proper deployment. The optional boolean debug matrix parameter will return the current set of environment variables on the server.|
/cimweb/cim/pong | GET | optional debug | Response to check for proper connection. The optional boolean debug matrix parameter will return the current set of environment variables on the server and metadata from the connection.|
/cimweb/cim/file | GET | optional path | Returns the contents of the directory (if path ends with /) or the contents of the file from HDFS.|
/cimweb/cim/file | PUT | path zip | Stores the byte contents at the path on HDFS. The optional boolean zip matrix parameter unzips a single file from the zip and stores it's contents on HDFS at the given path|
/cimweb/cim/file | DELETE | path |Removes the file or directory at the path on HDFS.|
/cimweb/cim/load | GET | path | Reads the contents of the HDFS file (.rdf) into Spark via the CIMReader.|
/cimweb/cim/export | GET | island | Returns the RDF of the given TopologicalIsland and related elements.|
/cimweb/cim/export | PUT | target | Stores the RDF of the TopologicalIsland specified by the contents (text island name) at the target path on HDFS.|
/cimweb/cim/query | GET | sql cassandra table_name cassandra_table_name| Performs SQL query against the loaded CIM data, or against Cassandra if cassandra=true. Returns a JSON array of records retrieved. Optionally store results in supplied table names.|
/cimweb/cim/gridlab | GET | simulation | Returns the GridLAB-D Model file (.glm) for the given simulation file (JSON).|
/cimweb/cim/gridlab | POST | simulation | Executes gridlabd for the model specified by the glm property in the given simulation file (JSON).|
/cimweb/cim/view | GET | about xmin ymin, xmax, ymax, reduceLines, maxLines, dougPeuk dougPeukFactor, resolution | Return (simplified) RDF for features within the bounding box from the CIM loaded into Spark.|
/cimweb/cim/short_circuit | GET | optional transformer | Returns the short circuit data for the house connections attached to the transformer, or all transfromers if none was specified.|
/cimweb/cim/spatial/nearest | GET | optional lat lon n | Finds the n nearest house connections to the given wgs84 lat,long.|
/cimweb/cim/timeseries | GET | | Sample to read SQLite database and return measurement data.|
