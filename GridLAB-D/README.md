GridLAB-D
======

Distributed load-flow calculations using [GridLAB-D](http://www.gridlabd.org/).

A good overview presentation is [Network Analysis and Simulation using Apache Spark on Compute Clusters](https://derrickoswald.github.io/CIMSparkPresentation/index.html?audio), or [without the audio tracks](https://derrickoswald.github.io/CIMSparkPresentation).

# Overview

This program reads in a [CIM file](http://cimug.ucaiug.org/default.aspx) describing an electrical network
and evaluates the maximum feed-in power at each house - to determine the upper size limit when a customer
requests permission to install a photo-voltaic (PV) system that would supply power to the network.

It's function is basically to run simulations for each house (CIM class EnergyConsumer) that determine at
what power level the injected current would raise the line voltage above 3% of nominal or would
cause the maximum rated current of any cable to be exceeded or would cause the power rating of the transformer
to be exceeded.

The output is a simple SQLite database providing, for each house id (CIM rdf:ID), the maximum power in Watts.
This database can be utilized to augment a graphical display on a map or schematic or to provide reports
and other downstream analysis.

The program runs on [Spark](https://spark.apache.org), usually on a cluster such as
[Amazon Web Services (AWS)](https://aws.amazon.com), [Azure](https://docs.microsoft.com/en-us/azure) or
an in-house system.

# Input

The starting point for analysis is a CIM export file.
The files that have been tested are exported from [nisStrom](http://www.nis.ch).
Normally the entire network is exported, but the program can work on files that are just a subset of the network. A typical file weighs in at about 10GB.
The input file includes all electrically salient features and their connections, but can include location and asset information which are not used by this program.

A CIM file is in RDF XML format. It includes electrical modeling parameters, such as switch status,
impedance, rated maximums, nominal power, etc.
For example, a typical cable element and its asset info record are shown below.

```XML
<cim:WireInfo rdf:ID="ART244">
        <cim:IdentifiedObject.name>TT-CLT 4x50</cim:IdentifiedObject.name>
        <cim:IdentifiedObject.aliasName>47:nis_el_cable_art</cim:IdentifiedObject.aliasName>
        <cim:IdentifiedObject.description>4x50</cim:IdentifiedObject.description>
        <cim:WireInfo.coreRadius>0.003989423000</cim:WireInfo.coreRadius>
        <cim:WireInfo.material rdf:resource="http://iec.ch/TC57/2013/CIM-schema-cim16#WireMaterialKind.copper"/>
        <cim:WireInfo.ratedCurrent>170</cim:WireInfo.ratedCurrent>
</cim:WireInfo>
<cim:ACLineSegment rdf:ID="KLE243310">
        <cim:IdentifiedObject.name>TT-CLT 4x50</cim:IdentifiedObject.name>
        <cim:IdentifiedObject.aliasName>204282032:nis_el_cable</cim:IdentifiedObject.aliasName>
        <cim:PowerSystemResource.Location rdf:resource="#_location_685171_1142578125_204282047"/>
        <cim:PowerSystemResource.PSRType rdf:resource="#PSRType_Underground"/>
        <cim:PowerSystemResource.AssetDatasheet rdf:resource="#ART244"/>
        <cim:Equipment.EquipmentContainer rdf:resource="#_line_tra6454"/>
        <cim:ConductingEquipment.BaseVoltage rdf:resource="#BaseVoltage_400"/>
        <cim:ConductingEquipment.SvStatus rdf:resource="#in_use"/>
        <cim:Conductor.length>47.48449322</cim:Conductor.length>
        <cim:ACLineSegment.b0ch>0</cim:ACLineSegment.b0ch>
        <cim:ACLineSegment.bch>257.6105976</cim:ACLineSegment.bch>
        <cim:ACLineSegment.g0ch>0</cim:ACLineSegment.g0ch>
        <cim:ACLineSegment.gch>0</cim:ACLineSegment.gch>
        <cim:ACLineSegment.r0>2.076</cim:ACLineSegment.r0>
        <cim:ACLineSegment.r>0.519</cim:ACLineSegment.r>
        <cim:ACLineSegment.shortCircuitEndTemperature>160</cim:ACLineSegment.shortCircuitEndTemperature>
        <cim:ACLineSegment.x0>0.336</cim:ACLineSegment.x0>
        <cim:ACLineSegment.x>0.084</cim:ACLineSegment.x>
</cim:ACLineSegment>
```

This 47.5m underground cable, of 4 conductors of 50 sq. mm. each, has a positive sequence impedance of 0.519+0.084jΩ
per Kilometer and is in service at 400 Volts with a maximum rated current of 170 Amps. Not shown is that topologically, it is connected to a house and
an intermediate junction.

# Preparation

HDFS
-----

The data file to be processed needs to be uploaded to HDFS. A command like this can be used:
```
hdfs dfs -mkdir /data
hdfs dfs -put NIS_CIM_Export_sias_current_20161220.rdf /data
```

The directory for GridLAB files needs to be created and given write access permissions for the user executing the program. At the moment this is hard-coded as `/simulation` In the absence of specific user information, all write rights can be given:
```
hdfs dfs -mkdir /simulation
hdfs dfs -chmod a+w /simulation
```

If you will be using checkpoints (described below), you will also need to make a checkpoint directory in a similar way:
```
hdfs dfs -mkdir /checkpoint
hdfs dfs -chmod a+w /checkpoint
```

_NOTE: checkpoint storage must be manually managed (it will not be erased automatically) and this can consume significant space. It is recommended to periodically execute a recursive delete on the checkpoint directory (note the quotes around the asterisk wildcard):_
```
hdfs dfs -rm -R "/checkpoint/*"
```

# Operation

The program is submitted to the cluster with the [spark-submit command](https://spark.apache.org/docs/latest/submitting-applications.html#launching-applications-with-spark-submit).

Of the many arguments to spark-submit, the crucial one to execute the GridLAB-D program is the correct full path to the `.jar` file. A bit of help text is available if you add a `--help` switch:
```
$ spark-submit /opt/code/GridLAB-D-2.2.0-jar-with-dependencies.jar --help
GridLAB-D 2.2.0
Usage: GridLAB-D [options] <CIM> <CIM> ...

  -m, --master MASTER_URL  spark://host:port, mesos://host:port, yarn, or local[*]
  -o, --opts k1=v1,k2=v2   other Spark options
  -g, --storage_level <value>
                           storage level for RDD serialization (default: MEMORY_AND_DISK_SER)
  -3, --three              use three phase computations
  -p, --precalculation     calculates threshold and EEA existence for all HAS, assuming no EEA
  -t, --trafos <TRA file>  file of transformer names (one per line) to process
  -x, --export_only        generates glm files only - no solve or analyse operations
  -a, --all                process all transformers (not just those with EEA)
  -e, --erase              clean up (delete) simulation HDFS files
  -l, --logging <value>    log level, one of ALL,DEBUG,ERROR,FATAL,INFO,OFF,TRACE,WARN
  -k, --checkpointdir <dir>
                           checkpoint directory on HDFS, e.g. hdfs://...
  -s, --simulation N       simulation number (precalc) to use for transformer list
  -r, --reference N        simulation number (precalc) to use as reference for transformer list
  -d, --delta D            delta power difference threshold for reference comparison
  -n, --number N           number of transformers to process
  -c, --csv <file>         short circuit power file
  -u, --deduplicate        de-duplicate input (striped) files
  --help                   prints this usage text
  <CIM> <CIM> ...          CIM rdf files to process
```

_NOTE: It is important to understand that options before the jar file are consumed (and normally understood) by `spark-submit`, while options after the jar file are passed to the `GridLAB-D` program. While `spark-submit` and `GridLAB-D` both have a `--master` option, in the following example `yarn` is passed to `spark-submit` while `local[*]` is passed to `GridLAB-D` (which overrides the default master set by `spark-submit`:_
```
$ spark-submit --master yarn /opt/code/GridLAB-D-2.0.0-jar-with-dependencies.jar --master local[*]
```

The resulting SQLite database is `./simulation/results.db`, path relative to where the `spark-submit` command was executed.

## Options

### master
Allows specifying the Spark master (same meaning as spark-submit) when `GridLAB-D` is run in debug mode in a development environment. Normally this should not be used by end users. Within the program, the Spark session is accessed via `getOrCreate`, so it should be benign to specify the same master as for `spark-submit`.

### opts
Allows specifying Spark options when `GridLAB-D` is run in debug mode in a development environment. Normally this should not be used by end users.
When specified, options are in the form of [Spark Properties](https://spark.apache.org/docs/latest/configuration.html#viewing-spark-properties) (just like the _Arbitrary Spark configuration property_ `--conf` option for `spark-submit`), and not the form used by spark-submit. For example spark-submit uses `--driver-memory 2g`, while `--opts` uses `spark.driver.memory=2g`.

### storage_level
The storage level to use for the CIMReader and GridLAB-D program
[object serialization](http://spark.apache.org/docs/latest/tuning.html#data-serialization).

### three
Changes from the default of single phase simulation to three phase simulation. In general, the results should be the same between the two choices, so the default is the less memory and disk intensive single phase.

### precalculation
Performs the pre-calculation phase only (see below) that calculates the maximum feed-in power for houses under the assumption of no other installed systems feeding in power. This allows the processing to proceed in two phases separated by an arbitrary amount of time. The simulation number (database primary key) is output on the screen at completion, and a list of transformers names (if any) which service houses with already existing photo-voltaic installations is written to `/simulation/trafos_with_eea/trafos.txt`.

### trafos
Allows the specification of a file containing a list of transformers to simulate, and calculate the maximum feed-in power for connected houses. A typical use-case is `--precalculation` followed by `--trafos` after editing the list of transformers:
```
$ spark-submit /opt/code/GridLAB-D-2.0.0-jar-with-dependencies.jar --precalculation hdfs://...
_edit the trafos.txt file_
$ spark-submit /opt/code/GridLAB-D-2.0.0-jar-with-dependencies.jar --trafos trafos.txt hdfs://...
```

### all
Performs simulation on all transformers in the input file.

### erase
Deletes generated simulation files from the local directory of each worker node in the cluster. The simulation files are copied locally from HDFS, in order for gridlabd to operate on them. Erasing is highly recommended. The files can be found in the `work` directory (standalone) or `userlogs` directory (yarn).

### logging
Specifies the logging level (verbosity) of log records. This overrides the logging level specified in log4j.properties.

### checkpointdir
Specifies the checkpoint directory and turns checkpointing on - if not specified no checkpointing occurs. A RDD checkpoint is a _materialized_ RDD where the current state of the RDD is stored on disk and the _recipe_ for how to re-create it is discarded. The GridLAB-D program has optional checkpoints coded in logic to save the state after _costly_ computations - which means we trade off disk space for time to re-create, and the cost of extra disk/network I/O.

**At the moment it is unclear under which conditions checkpointing should be recommended.**

_NOTE: checkpoint storage must be manually managed (it will not be erased automatically) and this can consume significant space. It is recommended to periodically execute a recursive delete on the checkpoint directory (note the quotes around the asterisk wildcard):_
```
hdfs dfs -rm -R "/checkpoint/*"
```

### simulation
Performs the simulation phase only (see below) using the results of a prior pre-calculation as specified by the simulation number. This allows the processing to proceed in two phases separated by an arbitrary amount of time or to re-execute a failed job. A typical use-case is `--precalculation` followed by `--simulation` after some pause:
```
$ spark-submit /opt/code/GridLAB-D-2.0.0-jar-with-dependencies.jar --precalculation hdfs://...
**the simulation number is 42**
_some time later_
$ spark-submit /opt/code/GridLAB-D-2.0.0-jar-with-dependencies.jar --simulation 42 hdfs://...
```

### reference
Use this reference (simulation) number for a comparison to the current pre-calculation simulation
to determine which transformers have new photo-voltaic installations or have changed the estimated
power sufficiently to need to be simulated. This mode is used to avoid recomputing transformer circuits
that have not changed.

### delta
The amount of power difference, relative to the reference value, that will trigger a simulation
in the reference mode documented above.

### csv
The name of the file with short circuit power availability at each substation.

### deduplicate
When using striped or tiled RDF files as input, this option will eliminate
duplicated elements contained in multiple files.


# Processing

The CIM file is read into Spark using a custom reader, [CIMReader](https://github.com/derrickoswald/CIMReader).
The reader produces a set of [Resilient Distributed Dataset (RDD)](https://spark.apache.org/docs/latest/programming-guide.html#resilient-distributed-datasets-rdds), one for each class of CIM element including their relations.
It also executes a topological analysis to identify a reduced subset of nodes
(sets of electrically identical ConnectivityNode elements - 
connected with zero Ohm conductors such as bus bars or closed switches)
and topological islands isolated by transformers and open switches.
Usually, each power transformer (or set of ganged transformers) supplies one topological island.

## Pre-Calculation

A graph traversal using [GraphX](https://spark.apache.org/docs/latest/graphx-programming-guide.html) yields the impedance between the transformer and each house as well as the cable that can sustain the least maximum current. From these values a best-case upper bound on maximum injected power can be computed, if the assumption is made that no other neighbors are also injecting power (the majority of cases). This step also identifies the houses that already have a photo-voltaic or wind power installation that is already feeding the network, which provides a list of topological islands that require simulation.

## Load Flow

For topological islands with existing feed-in installations, the program writes the island's equivalent circuit as
a [.glm](http://gridlab-d.sourceforge.net/wiki/index.php/Creating_GLM_Files) file
for GridLAB-D and also generates simulated power injection
[player](http://gridlab-d.sourceforge.net/wiki/index.php/Player) files for each house.
It then executes GridLAB-D to run load flow calculations.
The results of the load flow (voltages at each house and current through each conductor and the transformer)
are analyzed to find the maximum power at which voltages are still within tolerance and currents don't
exceed rated maximums.

## Database Output

After each simulation, final results are written to a SQLite database that is local to the spark master node.
This is the only step that runs only on the master node.
The schema of the database is two tables:
```
$ sqlite3 simulation/results.db 
SQLite version 3.8.7.1 2014-10-29 13:59:56
Enter ".help" for usage hints.
sqlite> .schema
CREATE TABLE simulation (id integer primary key autoincrement, description text, time text);
CREATE TABLE results (id integer primary key autoincrement, simulation integer, trafo text, house text, maximum double, eea integer, reason text, details text);
```

The SQL query or view to extract the most recent results for each house is TBD

# SWING Bus

The SWING bus (also known as a [slack bus](https://en.wikipedia.org/wiki/Slack_bus) or reference bus) is
a node in the system which supplies or absorbs active and reactive power to and from the system that is
external to the network under study, to maintain the phase angle at zero with a unity per unit voltage.

For our system there were three choices for the location of the bus
 
![SWING Bus Options](https://rawgit.com/derrickoswald/CIMApplication/master/GridLAB-D/img/swing%20bus%20choices.svg "SWING Bus Options")

1. transformer low voltage node (usually 400v)
2. transformer high voltage node (usually 16kV)
3. foreign power attachment node (usually 132kV)

The transformer low voltage node would eliminate the transformer from the evaluation, which was deemed inappropriate.
The foreign power attachment point requires an accurate model of the middle voltage system, which is not available
at all stations. Hence, the SWING busses are set as the high voltage pins of the distribution transformers.
