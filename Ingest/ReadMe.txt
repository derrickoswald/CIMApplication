Ingest
======

Import measurement data from Belvis dump files.

# Overview

This program reads in a mapping or correspondence file between the mRID that are present in a CIM file and
the CHxxxx meter id values. It then processes all the provided Belvis files and stores the measurement data
as records in Cassandra.

The program runs on [Spark](https://spark.apache.org), usually on a cluster such as
[Amazon Web Services (AWS)](https://aws.amazon.com), [Azure](https://docs.microsoft.com/en-us/azure) or
an in-house system.

# Input

The mapping or correspondenc file is a CSV format file (with a header and separator';') that has two columns of interest.
