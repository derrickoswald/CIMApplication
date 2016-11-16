CIMApplication
======

J2EE access to Spark for Common Information Model (CIM) applications.

#Overview

This set of programs provides a demonstration of using Java EE Connector Architecture
[JCA](https://en.wikipedia.org/wiki/Java_EE_Connector_Architecture)
to connect to [Spark](https://spark.apache.org) and through the use of
[CIMScala](https://github.com/derrickoswald/CIMScala) being able to read
CIM files which are a standard interchange format based on IEC standards 61968 & 61970
(see [CIM users group](http://cimug.ucaiug.org/default.aspx) for additional details)
to access Spark Resilient Distributed Dataset (RDD) for each CIM class.

![CIMApplication Overview](https://rawgit.com/derrickoswald/CIMApplication/master/img/Overview.svg "Overview diagram")

It thus provides a proof of principle for End-To-End access from a web browser to bespoke big-data applications running on Hadoop.

#Architecture

The use of a resource adapter within a J2EE container leverages the technologies that ease
deployment, security, transaction and user interface development that come with
the widely used enterprise platform. Code in Java or Scala runs in a virtual machine
within a containerized server-side platform and can send previously compiled long-running
jobs to the cluster of computers comprising the Hadoop infrastructure.

As such, it offers a bridge between legacy serial applications running on a single machine and
new parallel applications running on a cluster of machines.

#Caveats

- currently only works within [Tomee](https://tomee.apache.org/) the [openejb](https://en.wikipedia.org/wiki/Apache_OpenEJB) container hosted in [Tomcat](https://tomcat.apache.org/).
- currently only works with [Spark standalone](https://spark.apache.org/docs/1.6.0/spark-standalone.html) version 1.6.0

#TomEE Setup

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
