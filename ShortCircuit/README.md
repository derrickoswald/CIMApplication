ShortCircuit
======

Spark based module to perform short-circuit and fuse specificity calculations.

# Overview

There are two ways to use this module:

- standalone command-line application
- from within the CIMApplication user interface

For both methods, the output, for each node of the network, is an estimate of:

- the network short-circuit power and impedance
- the maximum inrush current (starting current) for a motor
- the list of fuses encountered on the path from the source
- a Go/NoGo flag if the fuse is of the correct size

# Stand-alone Program

The stand-alone program is created using Maven:
```
cd ShortCircuit
mvn package
```

This will produce a jar file in the target directory with a name that indicates the Scala, Spark and program versions.
For example ShortCircuit-2.11-2.3.1-2.4.0-jar-with-dependencies.jar is built for Sala 2.11, Spark 2.3.1 and is version 2.4.0 of the program.

To execute the standalone program use the spark-submit command from within the Spark cluster and specify the CIM file(s) to process:
```
spark-submit --master spark://sandbox:7077 --conf spark.driver.memory=1g --conf spark.executor.memory=1g ShortCircuit-2.11-2.3.1-2.4.0-jar-with-dependencies.jar --logging WARN hdfs://sandbox:8020/cimfile.rdf
```

The --help option generates a description of the options available:

```
ShortCircuit 2.11-2.3.1-2.4.0
Usage: ShortCircuit [options] <CIM>,<CIM>...

  --quiet               suppress informational messages [false]
  --master MASTER_URL   spark://host:port, mesos://host:port, yarn, or local[*]
  --opts k1=v1,k2=v2    other Spark options
  --storage <value>     storage level for RDD serialization [MEMORY_AND_DISK_SER]
  --deduplicate         de-duplicate input (striped) files [false]
  --logging <value>     log level, one of ALL,DEBUG,ERROR,FATAL,INFO,OFF,TRACE,WARN [OFF]
  --checkpoint <dir>    checkpoint directory on HDFS, e.g. hdfs://...
  --description <text>  text describing this program execution for SQLite run table
  --netp_max <Sk_max>   maximum network power if not in CIM, VA [2.00000e+08]
  --netz_max <r + xj>   maximum network impedance if not in CIM, Ω [0.43778578-1.20280655j]
  --netp_min <Sk_min>   minimum network power if not in CIM, VA [1.00000e+08]
  --netz_min <r + xj>   minimum network impedance if not in CIM, Ω [0.43778578-1.20280655j]
  --tbase <value>       temperature assumed in CIM file (°C) [20.0000]
  --tlow <value>        low temperature for maximum fault (°C) [60.0000]
  --thigh <value>       high temperature for minimum fault (°C) [90.0000]
  --trafos <TRA file>   file of transformer names (one per line) to process
  --trafop <ratedS>     transformer power if not in CIM, VA [630000]
  --trafoz <r + xj>     transformer impedance if not in CIM, Ω [0.0059+0.03956248j]
  --cmax <value>        voltage factor for maximum fault level, used for rating equipment [1.00000]
  --cmin <value>        voltage factor for minimum fault level, used for protections settings [0.900000]
  --cosphi <value>      load power factor, used for maximum inrush current [worst case]
  --messagemax <value>  maximum number of warning and error messages per node [5]
  --batchsize <value>   size of result collections for driver database writes [10000]
  --workdir <dir>       shared directory (HDFS or NFS share) with scheme (hdfs:// or file:/) for work files
  --help                prints this usage text
  <CIM>,<CIM>...        CIM rdf files to process
```

# CIMApplication

Execution from CIMApplication is performed from the Analysis tab:

![CIMApplication Analysis Tab](https://rawgit.com/derrickoswald/CIMApplication/master/ShortCircuit/img/ShortCircuit.png "Analysis User Interface")

# Limitations

The program will *not* provide valid output values for the following cases:

## Three Winding Transformers

![Three Winding Transformer](https://rawgit.com/derrickoswald/CIMApplication/master/ShortCircuit/img/three_winding_transformer.svg "Three Winding Transformer")

Where a transformer has more than one winding, the program will not provide valid results for downstream nodes,
and will have a message like `INVALID: 3 transformer windings for edge TRA1234`.

## Voltage Regulators

![Voltage Regulator](https://rawgit.com/derrickoswald/CIMApplication/master/ShortCircuit/img/voltage_regulator.svg "Voltage Regulator")

Where a transformer has manual or automatic taps to change the voltage ratio, the program will not provide valid results for downstream nodes,
and will have a message like `INVALID: voltage (400V) regulator edge TRA1234`.

## Subtransmission

![Subtransmission](https://rawgit.com/derrickoswald/CIMApplication/master/ShortCircuit/img/subtransmission.svg "Subtransmission")

Where a set of transformers provides a step-up and step-down sequence, the program will not provide valid results for downstream nodes,
and will have a message like `INVALID: low voltage (400V:1000V) subtransmission edge TRA1234`.

