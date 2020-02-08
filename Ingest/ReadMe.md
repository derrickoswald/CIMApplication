Ingest
======

Import measurement data from Belvis/LPEx dump files.

![Ingest Overview](https://rawgit.com/derrickoswald/CIMApplication/master/Ingest/img/Ingest.svg "Overview diagram")

# Overview

This program reads in a mapping or correspondence file between the mRID that are present in a CIM file and
the CHxxxx meter id values. It then processes all the provided Belvis or LPEx files and stores the measurement data
as records in Cassandra.

The program runs on [Spark](https://spark.apache.org), usually on a cluster such as
[Amazon Web Services (AWS)](https://aws.amazon.com), [Azure](https://docs.microsoft.com/en-us/azure) or
an in-house system.

# Input

The mapping or correspondence file is a CSV format file (with a header and separator';') that has two columns of interest.
The measurement files are either [Belvis](http://www.kisters.com.au/belvis_modules.html)
or [LPEx](http://www.meks.org/produkte/meks-controlcenter/meks-zusatzmodule/meks-controlcenterlpex.html) files
similar to CSV files.

# Note

Because the program uses HDFS as a staging area for the input files, there must be a valid `HADOOP_CONF_DIR`
environment variable set, in order to locate the core-site.xml that contains the **fs.defaultFS** property.
A typical core-site.xml file looks like this:
```xml
<configuration>
  <property>
    <name>fs.defaultFS</name>
    <value>hdfs://sandbox:8020</value>
  </property>
</configuration>
```