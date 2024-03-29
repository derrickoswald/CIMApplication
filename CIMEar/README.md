CIMEar
======

This set of programs provides a demonstration of using Java EE Connector Architecture
[JCA](https://en.wikipedia.org/wiki/Java_EE_Connector_Architecture)
to connect to [Spark](https://spark.apache.org) and through the use of
[CIMReader](https://github.com/derrickoswald/CIMReader) being able to read
CIM files which are a standard interchange format based on IEC standards 61968 & 61970
(see [CIM users group](http://cimug.ucaiug.org/default.aspx) for additional details)
to access Spark Resilient Distributed Dataset (RDD) for each CIM class.

It thus provides a proof of principle for End-To-End access from a web browser to bespoke big-data electric distribution applications running on Hadoop.

# Provisioning a Cluster

To create a cluster on Amazon ECS you can use the provisioning tool:

[https://derrickoswald.github.io/CIMApplication](https://derrickoswald.github.io/CIMApplication)

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
- if necessary, change the files ownership, e.g chown --recursive /opt/apache-tomee-plus-8.0.0/
- to access the manager GUI (http://localhost:8080/manager/html), edit conf/tomcat-users.xml to
uncomment the section at the bottom of the file with rolename="tomee-admin" and username="tomee",
and then change the password as appropriate
- to enable TomEE, edit conf/tomee.xml and uncomment the &lt;Deployments dir="apps"/&gt; and make the apps directory:

    mkdir /opt/apache-tomee-plus-8.0.0/apps

- to deploy the application and reset, you could use these instructions (restart.sh marked executable):

    bin/shutdown.sh 2>/dev/null
    rm -rf apps/*
    rm logs/*
    cp ~/code/CIMApplication/CIMEar/target/CIMApplication.ear apps
    bin/startup.sh

# REST Services

The CIMApplication (HTML/Javascript) accessed at http://server:8080/cimweb is supported on the backend by
RESTful services provided by the CIMWeb .war file.

Endpoint | Verb | Parameters | Description|
------------ | ------------- | ------------- | -------------|
/cimweb/cim/ping | GET | optional debug | Simple response to check for proper deployment. The optional boolean debug matrix parameter will return the current set of environment variables on the server.|
/cimweb/cim/pong | GET | optional debug | Response to check for proper Spark connection. The optional boolean debug matrix parameter will return the current set of environment variables on the server and metadata from the connection.|
/cimweb/cim/file | GET | optional path | Returns the contents of the directory (if path ends with /) or the contents of the file from HDFS.|
/cimweb/cim/file | PUT | path zip | Stores the byte contents at the path on HDFS. The optional boolean zip matrix parameter unzips a single file from the zip and stores it's contents on HDFS at the given path|
/cimweb/cim/file | DELETE | path |Removes the file or directory at the path on HDFS.|
/cimweb/cim/load | GET | path | Reads the contents of the HDFS file (.rdf) into Spark via the CIMReader.|
/cimweb/cim/query | GET | sql cassandra table_name cassandra_table_name| Performs SQL query against the loaded CIM data, or against Cassandra if cassandra=true. Returns a JSON array of records retrieved. Optionally store results in supplied table name.|
/cimweb/cim/estimation | POST | simulation | Executes gridlabd for the model specified by the cim property in the given simulation file (JSON).|
/cimweb/cim/view | GET | about xmin ymin, xmax, ymax, reduceLines, maxLines, dougPeuk dougPeukFactor, resolution | Return (simplified) RDF for features within the bounding box from the CIM loaded into Spark.|
/cimweb/cim/short_circuit | GET | optional transformer | Returns the short circuit data for the house connections attached to the transformer, or all transfromers if none was specified.|
/cimweb/cim/spatial/nearest | GET | optional lat lon n | Finds the n nearest house connections to the given wgs84 lat,long.|
