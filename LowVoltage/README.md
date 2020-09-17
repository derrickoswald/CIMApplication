LowVoltage
======

Spark based module to create GridLAB-D model files (.glm) from CIM files.

![LowVoltage Overview](https://rawgit.com/derrickoswald/CIMApplication/master/LowVoltage/img/LowVoltage.svg "Overview diagram")

# Overview

This program is a standalone command-line application.
The input is one or more CIM files,
and the output is a GridLAB-D model file for each transformer service area. 

The stand-alone program is created using Maven:
```
cd LowVoltage
mvn package
```

This will produce a jar file in the `target/` directory with a name that indicates the Scala, Spark and program versions.
For example LowVoltage-2.12-3.0.0-3.0.1-jar-with-dependencies.jar is built for Scala 2.12, Spark 3.0.0 and is version 3.0.1 of the program.

To execute the standalone program use the
[spark-submit](https://spark.apache.org/docs/latest/submitting-applications.html#launching-applications-with-spark-submit)
command and specify the LowVoltage jar and the CIM file(s) to process:
```
spark-submit --master spark://sandbox:7077 --conf spark.driver.memory=1g --conf spark.executor.memory=1g LowVoltage-*-jar-with-dependencies.jar --log WARN hdfs://sandbox:8020/cimfile.rdf
```

The `--help` option generates a description of the options available:

```
LowVoltage 2.12-3.0.0-3.0.1
Usage: LowVoltage [options] [<CIM> <CIM> ...]

  --version                Scala: 2.12, Spark: 3.0.0, LowVoltage: 3.0.1
  --master <master_url>    local[*], spark://host:port/, mesos://host:port or yarn [local[*]]
  --spark_options <map>    Spark options [spark.serializer=org.apache.spark.serializer.KryoSerializer,spark.sql.catalog.casscatalog=com.datastax.spark.connector.datasource.CassandraCatalog,spark.kryo.registrator=ch.ninecode.cim.CIMRegistrator,spark.graphx.pregel.checkpointInterval=8,spark.ui.showConsoleProgress=false,spark.sql.debug.maxToStringFields=250]
  --log <enum>             log level, one of OFF,FATAL,ERROR,WARN,INFO,DEBUG,TRACE,ALL [OFF]
  --jars <list>            names of jars to send to Spark [./LowVoltage/target/LowVoltage-2.12-3.0.0-3.0.1-jar-with-dependencies.jar]
  --checkpoint <dir>       checkpoint directory on HDFS, e.g. hdfs://... []
  --cim_options <map>      CIM options [ch.ninecode.cim.do_topo=true,ch.ninecode.cim.do_normalize=false,ch.ninecode.cim.do_join=false,ch.ninecode.cim.default_switch_open_state=false,ch.ninecode.cim.do_deduplication=true,path=,ch.ninecode.cim.debug=false,ch.ninecode.cim.split_maxsize=67108864,ch.ninecode.cim.force_retain_fuses=Unforced,ch.ninecode.cim.force_switch_separate_islands=Unforced,ch.ninecode.cim.do_topo_islands=true,ch.ninecode.cim.force_fuse_separate_islands=Unforced,ch.ninecode.cim.force_retain_switches=Unforced,ch.ninecode.cim.make_edges=false,StorageLevel=MEMORY_AND_DISK_SER,ch.ninecode.cim.cache=,ch.ninecode.cim.do_about=false]
  --topology               do topology processing (enables the following 6 options) [true]
  --identify_islands       perform island topological processing [true]
  --retain_switch <state>  attribute 'retain' for all switches except Fuse types, one of ForceTrue,ForceFalse,Unforced [Unforced]
  --retain_fuse <state>    attribute 'retain' for all fuses, one of ForceTrue,ForceFalse,Unforced [Unforced]
  --switch_island <state>  switches (except Fuse) separate topological islands, one of ForceTrue,ForceFalse,Unforced [Unforced]
  --fuse_island <state>    fuses separate topological islands, one of ForceTrue,ForceFalse,Unforced [Unforced]
  --default_open           default switch open/normalOpen value if not specified [false]
  --about                  do about processing [false]
  --normalize              do normalization processing [false]
  --dedup                  do deduplication processing [true]
  --edges                  do edge processing [false]
  --join                   do asset join processing [false]
  --debug                  enable debug messages [false]
  --splitsize <value>      file read split size [67108864]
  --cache <dir>            CIM cache location []
  --storage <enum>         storage level for RDD serialization, one of NONE,DISK_ONLY,DISK_ONLY_2,MEMORY_ONLY,MEMORY_ONLY_2,MEMORY_ONLY_SER,MEMORY_ONLY_SER_2,MEMORY_AND_DISK,MEMORY_AND_DISK_2,MEMORY_AND_DISK_SER,MEMORY_AND_DISK_SER_2,OFF_HEAP [MEMORY_AND_DISK_SER]
  <CIM> <CIM> ...          CIM rdf files to process
  --verbose                emit progress messages [false]
  --three                  use three phase computations [false]
  --trafos <TRA file>      file of transformer names (one per line) to process []
  --workdir <value>        working directory for .glm data [/simulation/]

Generate GridLAB-D model files (.glm files) from CIM files.
```

## Options

Besides the standard options for CIMApplication command line programs,
there are the following additional options.

### verbose
Logs messages at the INFO level for LowVoltage classes
(as opposed to other Spark and CIMApplication classes which is handled by --log,
together with the log4j.properties configuration of Spark).

### three
Creates three phase .glm models instead of single phase models.
This changes the phase information from ```phases AN;``` to ```phases ABCN;```.
This is only useful if you will be doing unbalanced analysis,
since a single phase simulation (which takes less run time in ```gridlabd```)
can be used to model a balanced three phase system (with appropriate multipliers). 

### trafos
This option specifies the name of a text file containing transformer names to process.
While the entire CIM file may contain thousands of transformer service areas,
this option allows you to limit the output to a restricted set.
Transformer names are the ```mRID``` of ```PowerTransformer``` elements in the CIM file.
Where two transformers are ganged
(the primary and secondary terminals of two transformers are connected in parallel)
the combined name is the alphabetically sorted names of the ganged transformers,
separated by underscores.
A typical file, one transformer per line, might be:
```
TRA2290
TRA72846_TRA72846
TRA1672
```

### workdir
The common directory for working files (GridLAB-D models, players and recorders).
This directory should be available to all executors in the cluster so they can perform
load-flow analysis in parallel.

