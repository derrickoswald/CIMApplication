GridLAB-D
======

Distributed load-flow calculations using [GridLAB-D](http://www.gridlabd.org/).

# Overview
This Scala library is used within Spark programs to
- create GridLAB-D Model (.glm) files on HDFS from [CIM RDD](https://github.com/derrickoswald/CIMReader)
- run the [gridlabd](https://github.com/gridlab-d/gridlab-d) executable on worker nodes
- collect the contents of the generated recorder files into RDD

This library is used by other applications such as:
- [Short Circuit](https://github.com/derrickoswald/CIMApplication/tree/master/ShortCircuit)
- [Maximum Feed In](https://github.com/derrickoswald/CIMApplication/tree/master/MaximumFeedIn)
- [OneOfN](https://github.com/derrickoswald/CIMApplication/tree/master/OneOfN)
- [Simulation](https://github.com/derrickoswald/CIMApplication/tree/master/Simulation)

and has no user servicable parts.

# gridlabd
The gridlabd executable must be installed on each Spark worker node,
and normally also on the Spark master for debugging.
There are pre-built binaries for [redhat](https://sourceforge.net/projects/gridlab-d/)
based Linux distributions and [Windows](https://sourceforge.net/projects/gridlab-d/).
For debian based linux distributions, one can convert the .rpm with
[alien](https://help.ubuntu.com/community/RPM/AlienHowto),
or alternatively, use this [gridlabd fork](https://github.com/derrickoswald/gridlab-d),
in the `develop` branch to build package files directly from source with `make dist-deb`.

