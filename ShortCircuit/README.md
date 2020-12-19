ShortCircuit
======

Spark based module to perform short-circuit and fuse specificity calculations.

![ShortCircuit Overview](https://rawgit.com/derrickoswald/CIMApplication/master/ShortCircuit/img/ShortCircuit.svg "Overview diagram")

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

This will produce a jar file in the `target/` directory with a name that indicates the Scala, Spark and program versions.

To execute the standalone program use the spark-submit command from within the Spark cluster and specify the CIM file(s) to process:
```
spark-submit --master spark://sandbox:7077 --conf spark.driver.memory=1g --conf spark.executor.memory=1g ShortCircuit-*-jar-with-dependencies.jar --log WARN hdfs://sandbox:8020/cimfile.rdf
```

The `--help` option generates a description of the options available:

```
ShortCircuit <version>
Usage: ShortCircuit [options] [<CIM> <CIM> ...]

  --version                Scala: 2.12, Spark: 3.0.1, ShortCircuit: <version>
  --master <master_url>    local[*], spark://host:port/, mesos://host:port or yarn [local[*]]
  --spark_options <map>    Spark options [spark.serializer=org.apache.spark.serializer.KryoSerializer,spark.sql.catalog.casscatalog=com.datastax.spark.connector.datasource.CassandraCatalog,spark.kryo.registrator=ch.ninecode.cim.CIMRegistrator,spark.graphx.pregel.checkpointInterval=8,spark.ui.showConsoleProgress=false,spark.sql.debug.maxToStringFields=250]
  --log <enum>             log level, one of OFF,FATAL,ERROR,WARN,INFO,DEBUG,TRACE,ALL [OFF]
  --jars <list>            names of jars to send to Spark [./ShortCircuit/target/ShortCircuit-<version>-jar-with-dependencies.jar]
  --checkpoint <dir>       checkpoint directory on HDFS, e.g. hdfs://... []
  --topology               do topology processing (enables the following 6 options) [true]
  --identify_islands       perform island topological processing [true]
  --retain_switch <state>  attribute 'retain' for all switches except Fuse types, one of ForceTrue,ForceFalse,Unforced [Unforced]
  --retain_fuse <state>    attribute 'retain' for all fuses, one of ForceTrue,ForceFalse,Unforced [ForceTrue]
  --switch_island <state>  switches (except Fuse) separate topological islands, one of ForceTrue,ForceFalse,Unforced [Unforced]
  --fuse_island <state>    fuses separate topological islands, one of ForceTrue,ForceFalse,Unforced [Unforced]
  --default_open           default switch open/normalOpen value if not specified [false]
  --about                  do about processing [false]
  --normalize              do normalization processing [false]
  --dedup                  do deduplication processing [false]
  --edges                  do edge processing [false]
  --join                   do asset join processing [false]
  --debug                  enable debug messages [false]
  --splitsize <value>      file read split size [67108864]
  --cache <dir>            CIM cache location []
  --storage <enum>         storage level for RDD serialization, one of NONE,DISK_ONLY,DISK_ONLY_2,MEMORY_ONLY,MEMORY_ONLY_2,MEMORY_ONLY_SER,MEMORY_ONLY_SER_2,MEMORY_AND_DISK,MEMORY_AND_DISK_2,MEMORY_AND_DISK_SER,MEMORY_AND_DISK_SER_2,OFF_HEAP [MEMORY_AND_DISK_SER]
  <CIM> <CIM> ...          CIM rdf files to process
  --verbose                log informational messages [false]
  --description <text>     text describing this program execution for SQLite run table
  --netp_max <Sk_max>      maximum network power if not in CIM, VA [2.0E8]
  --netz_max <r + xj>      network impedance at maximum power if not in CIM, Ω [0.43778578-1.20280655j]
  --neta_max <angle>       network power factor angle at maximum power if not in CIM, overrides impedance, ° [NaN]
  --netp_min <Sk_min>      minimum network power if not in CIM, VA [1.0E8]
  --netz_min <r + xj>      network impedance at minumum power if not in CIM, Ω [0.87557157-2.40561311j]
  --neta_min <angle>       network power factor angle at minimum power if not in CIM, overrides impedance, ° [NaN]
  --tbase <value>          temperature assumed in CIM file (°C) [20.0]
  --tlow <value>           low temperature for maximum fault (°C) [60.0]
  --thigh <value>          high temperature for minimum fault (°C) [90.0]
  --trafos <TRA file>      file of transformer names (one per line) to process
  --trafop <ratedS>        transformer power if not in CIM, VA [630000.0]
  --trafoz <r + xj>        transformer impedance if not in CIM, Ω [0.0059+0.03956248j]
  --cmax <value>           voltage factor for maximum fault level, used for rating equipment [1.0]
  --cmin <value>           voltage factor for minimum fault level, used for protections settings [0.9]
  --cosphi <value>         load power factor, used for maximum inrush current [worst case]
  --fuse_table <value>     recommended fuse sizing table, #1 from 65A⇒25 to 2400A⇒630, #2 from 28A⇒10 to 2500A⇒630, #3 DIN as #1, SEV 200A⇒60 to 1150A⇒400, #4 0⇒6,65A⇒25 to 2400A⇒500 [1]
  --messagemax <value>     maximum number of warning and error messages per node [5]
  --batchsize <value>      size of result collections for driver database writes [10000]
  --cable_impedance_limit <value>
                           cables with higher impedances for R1 will not be processed with gridlabd [5.0]
  --calculate_public_lighting <value>
                           calculate public lighting [false]
  --workdir <dir>          shared directory (HDFS or NFS share) with scheme (hdfs:// or file:/) for work files
```

## Options

### verbose
Logs messages at the INFO level for ShortCircuit classes
(as opposed to other Spark and CIMApplication classes which is handled by --log,
together with the log4j.properties configuration of Spark).

### master
Allows specifying the Spark master (same meaning as spark-submit) when `ShortCircuit` is run in debug mode in a development environment.
Normally this should not be used by end users.
Within the program, the Spark session is accessed via `getOrCreate`,
so it should be benign to specify the same master as for `spark-submit`.

### spark_options
Allows specifying Spark options when `ShortCircuit` is run.
When specified, options are in the form of
[Spark Properties](https://spark.apache.org/docs/latest/configuration.html#viewing-spark-properties)
(just like the _Arbitrary Spark configuration property_ `--conf` option for `spark-submit`),
and not the form used by spark-submit.
For example spark-submit uses `--driver-memory 2g`, while `--opts` uses `spark.driver.memory=2g`.

### storage
The [object serialization](http://spark.apache.org/docs/latest/tuning.html#data-serialization) storage level
to use for the [`CIMReader`](https://derrickoswald.github.io/CIMSpark/CIMReader/) and `ShortCircuit`.

### splitsize
Same as specifying `ch.ninecode.cim.split_maxsize=XXX` for the CIMReader.
A file larger than this threshold is split into multiple partitions when being read in.

### dedup
Same as specifying `ch.ninecode.cim.do_deduplication=???` for the CIMReader.
When using striped or tiled RDF files as input, this option will eliminate
duplicated elements contained in multiple files.
Only distinctly identified (rdf:ID) CIM elements are kept, duplicates are discarded.

### log
Specifies the logging level (verbosity) of log records.
This overrides the logging level specified in log4j.properties file.

### checkpointdir
Specifies the checkpoint directory and turns checkpointing on - if not specified no checkpointing occurs.
An RDD checkpoint is a _materialized_ RDD where the current state of the RDD is stored on disk
and the _recipe_ for how to re-create it is discarded.
The [`CIMReader`](https://derrickoswald.github.io/CIMSpark/CIMReader/) and `ShortCircuit` application
have optional checkpoints coded in logic to save the state after _costly_ computations -
which means we trade off time to re-create discarded RDD, for disk space and the time for extra disk/network I/O.

**At the moment it is unclear under which conditions checkpointing should be recommended.**

_NOTE: checkpoint storage must be manually managed (it will not be erased automatically) and this can consume significant space.
It is recommended to periodically execute a recursive delete on the checkpoint directory (note the quotes around the asterisk wildcard):_
```
hdfs dfs -rm -R "/checkpoint/*"
```

### description
User specified text to be included in the `description` column of the results database `shortcircuit_run` table.
Simply arbitrary text value describing this program execution.
Enclose the value in quotes to include spaces and funny characters.

### netp_max, netz_max, netp_min, netz_min
These four parameters specify the default short circuit characteristics of the medium voltage network.
In the absence of a EquivalentInjection element connected to the high voltage node of a transformer,
these values are used to set the maximum and minimum available power and upstream impedance.
The default values correspond to 200MVA max, 100MVA min at a power factor of -70°.

## tbase, tlow, thigh
These three temperature values control the temperature compensation performed by `ShortCircuit` for cables.
The `tbase` temperature defines what temperature was used to calculate the impedance values stored within the CIM file.
The `tlow` and `thigh` temperatures specify how the temperature compensation should be done for
maximum fault current and minimum fault current respectively. The former is used in fuse sizing and the latter is
used in fuse specificity.

### trafos
Allows the specification of a file containing a list of transformers to process.
A typical use-case is to analyze one or more problem areas
without processing the entire set of transformer service areas.

### trafop, trafoz
Set the defaults for transformer power rating and impedance when a transformer does not have these values in the CIM file.
The defaults are equivalent to a standard 630kVA old-style oil insulated transformer.

### cmax, cmin
These derating factors are used to approximate the actual voltage at the point of common coupling.
For maximum current (when rating equipment) the `cmax` value specifies the voltage factor for maximum fault level,
and is normally set to 1.0 so that the nominal voltage is used.
For minimum current (when checking protections settings) the `cmin` value specifies the voltage factor for minimum fault level,
and is normally set to 0.9 so that the simulated voltage is the worst case (lowest voltage).

### cosphi
Specifies the load power factor to be used for a "motor" causing the maximum inrush current.
For the worst case scenario this is normally left not specified so that the worst case power factor is used.
If a specific power factor is known, the power factor can be specified, overriding the worst case scenario. 

### fuse_table
Selects the fuse sizing table to be used for determining if a fuse is the correct size.
These tables take the form of a piecewise step approximation between the short circuit current and the fuse rating.
A value of I<sub>k</sub> selects the table entry larger than or equal to I<sub>k</sub> and retrieves the corresponding fuse rating.
This is compared to the existing fuse rating and if the fuse rating is greater than or equal to the table value it is deemed `'OK'`.
For example an I<sub>k</sub> of 345A would expect a fuse rating of 125A using table #1.
If the actual fuse is 100A then it is considered `'OK'`. 

For fuse table #1 the breakpoints are:

I<sub>k</sub>|Rating (A)
-------------|----------
65|25
105|40
140|50
180|63
240|80
320|100
380|125
500|160
650|200
800|250
1050|315
1300|400
1750|500
2400|630

For fuse table #2 the breakpoints are:

I<sub>k</sub>|Rating (A)
-------------|----------
28|10
40|16
55|20
70|25
93|32
120|40
160|50
190|63
230|80
305|100
380|125
490|160
690|200
820|250
1150|315
1350|400
1900|500
2500|630

For fuse table #3, the same fuse table as for #1 is used if the fuse name starts with "DIN".
However, if the fuse name starts with "SEV", then the following table is used:

I<sub>k</sub>|Rating (A)
-------------|----------
200|60
250|75
300|100
340|125
500|150
600|200
720|250
850|300
1150|400

For fuse table #4, the lowest value is a 6A fuse, otherwise the breakpoints are:

I<sub>k</sub>|Rating (A)
-------------|----------
65|25
105|35
140|50
180|50
240|63
320|100
380|100
500|160
650|160
800|200
1050|250
1300|400
1750|400
2400|500

### messagemax
Specifies the maximum number of warning and error messages per node.
Messages are encapsulated in a queue that is limited in size to this value.

### batchsize
When storing the results in SQLite, the large number of values must be throttled
to avoid database errors. This value sets the batch size - the number of values submitted at
one time.

### cable_impedance_limit
For cables with unknown impedance that have been given an arbitrarily large value,
this setting restricts the cable impedance - effectively ignoring (removing) cables
with a larger value.

### workdir
The common directory for working files (GridLAB-D models, players and recorders).
This directory should be available to all executors in the cluster so they can perform
load-flow analysis in parallel.

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

