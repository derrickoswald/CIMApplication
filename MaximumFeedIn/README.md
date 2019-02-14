Maximum Feed In
===============

Calculate the maximum feed-in power (e.g. capacity of a photovoltaic installation) at every node of an electric distribution network.

# Overview
This program reads in a [CIM file](https://cimug.ucaiug.org/) describing an electrical network
and evaluates the maximum feed-in power at each house without requiring network reinforcement.

A good overview presentation is [Network Analysis and Simulation using Apache Spark on Compute Clusters](https://derrickoswald.github.io/CIMSparkPresentation/index.html?audio), or [without the audio tracks](https://derrickoswald.github.io/CIMSparkPresentation).
 
# Use Case
As a network operator, I need to be able to inform a requesting customer,
who is seeking permission to attach a photo-voltaic (PV) system that would supply power to the network,
what the feed-in power limit is, without needing additional network infrastructure.

# Function
Basically the application runs two types of simulations, [trace and load-flow](#Processing).
For each house (CIM class EnergyConsumer) it determines at
what power level the injected current would raise the line voltage above 3% of nominal or would
cause the maximum rated current of any cable to be exceeded or would cause the power rating of the transformer
to be exceeded.

The output is a simple SQLite database providing, the maximum power in Watts for each house
(in CIM speak: the ConnectivityNode of each single terminal ConductingEquipment rdf:ID),
and a detailed reason for the limit.
This database can be utilized to augment a graphical display on a map or schematic or to provide reports
and other downstream analysis.

The program runs on [Spark](https://spark.apache.org), usually on a cluster such as
[Amazon Web Services (AWS)](https://aws.amazon.com), [Azure](https://docs.microsoft.com/en-us/azure) or
an in-house system.

# Input

The starting point for analysis is a CIM file of the network.
The files that have been tested are exported from [nisStrom](http://www.nis.ch).
Normally the entire network is exported, but the program can work on files that are just a subset of the network.
A typical file weighs in at about 10GB.
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
per Kilometer and is in service at 400 Volts with a maximum rated current of 170 Amps.
Not shown is that topologically, it is connected to a house and an intermediate junction.

# Preparation

HDFS
-----

The data file to be processed needs to be uploaded to HDFS. A command like this can be used:
```
hdfs dfs -mkdir /data
hdfs dfs -put NIS_CIM_Export_sias_current_20161220.rdf /data
```

For some HDFS configuration settings, the directory for simulation files may need to be created and given write access permissions for the user executing the program.
At the moment this is hard-coded as `/simulation`.
In the absence of specific user information, all write rights can be given:
```
hdfs dfs -mkdir /simulation
hdfs dfs -chmod a+w /simulation
```

Similarly, for some HDFS configuration settings, if you use checkpoints (described below), you will also need to make a checkpoint directory in a similar way:
```
hdfs dfs -mkdir /checkpoint
hdfs dfs -chmod a+w /checkpoint
```

_NOTE: checkpoint storage must be manually managed (it will not be erased automatically) and this can consume significant space.
It is recommended to periodically execute a recursive delete on the checkpoint directory (note the quotes around the asterisk wildcard):_
```
hdfs dfs -rm -R "/checkpoint/*"
```

# Operation

The program is submitted to the cluster with the [spark-submit command](https://spark.apache.org/docs/latest/submitting-applications.html#launching-applications-with-spark-submit).

Of the many arguments to spark-submit, the crucial one to execute the MaximumFeedIn program is the correct full path to the `.jar` file.
A bit of help text is available if you use the `--help` switch:
```
spark-submit --master spark://sandbox:7077 --conf spark.driver.memory=2g --conf spark.executor.memory=2g /opt/code/MaximumFeedIn-2.11-2.3.2-2.4.0-jar-with-dependencies.jar --help
MaximumFeedIn 2.11-2.3.2-2.4.0
Usage: MaximumFeedIn [otions] [<CIM> <CIM> ...]

Calculate maximum feed-in power without reinforcement or exceeding voltage, current or power constraints.

  --help                   prints this usage text
  --version                Scala: 2.11, Spark: 2.3.2, MaximumFeedIn: 2.4.0
  --quiet                  suppress informational messages [false]
  --master MASTER_URL      spark://host:port, mesos://host:port, yarn, or local[*]
  --opts k1=v1,k2=v2       Spark options [spark.graphx.pregel.checkpointInterval=8,spark.serializer=org.apache.spark.serializer.KryoSerializer,spark.ui.showConsoleProgress=false]
  --storage_level <value>  storage level for RDD serialization [MEMORY_AND_DISK_SER]
  --deduplicate            de-duplicate input (striped) files [false]
  --three                  use three phase computations [false]
  --precalculation         only calculates threshold and EEA existence for all HAS, assuming no EEA [false]
  --trafos <TRA file>      file of transformer names (one per line) to process []
  --export_only            generates glm files only - no solve or analyse operations [false]
  --all                    process all transformers (not just those with EEA) [false]
  --erase                  clean up (delete) simulation files [false]
  --logging <value>        log level, one of ALL,DEBUG,ERROR,FATAL,INFO,OFF,TRACE,WARN
  --checkpoint <dir>       checkpoint directory on HDFS, e.g. hdfs://...
  --simulation N           simulation number (precalc) to use for transformer list
  --reference N            simulation number (precalc) to use as reference for transformer list
  --delta D                delta power difference threshold for reference comparison [1.00000e-06]
  --precalcfactor D        factor to multiply precalculation results for gridlabd [2.50000]
  --cosphi D               power factor for new photo-voltaic installations [1.00000]
  --voltage_threshold D    the voltage threshold for the feeder of the house under test [3.00000%]
  --voltage_threshold2 D   the voltage threshold for neighboring feeders of the house under test [3.00000%]
  --ignore_other           ignore cable currents on neighboring feeders [false]
  --workdir <dir>          shared directory (HDFS or NFS share) with scheme (hdfs:// or file:/) for work files
  <CIM> <CIM> ...          CIM rdf files to process
```

_NOTE: It is important to understand that options before the jar file are consumed (and normally understood) by `spark-submit`,
while options after the jar file are passed to the `MaximumFeedIn` program.

The resulting SQLite database is created in the local directory `./simulation/results.db`,
i.e. the path relative to where the `spark-submit` command was executed.

## Options

### quiet
Together with the --logging option and log4j.properties configuration of Spark,
this controls if informational messages from the application are emitted.

### master
Allows specifying the Spark master (same meaning as spark-submit) when `MaximumFeedIn` is run in debug mode in a development environment.
Normally this should not be used by end users.
Within the program, the Spark session is accessed via `getOrCreate`,
so it should be benign to specify the same master as for `spark-submit`.

### opts
Allows specifying Spark options when `MaximumFeedIn` is run.
When specified, options are in the form of
[Spark Properties](https://spark.apache.org/docs/latest/configuration.html#viewing-spark-properties)
(just like the _Arbitrary Spark configuration property_ `--conf` option for `spark-submit`),
and not the form used by spark-submit.
For example spark-submit uses `--driver-memory 2g`, while `--opts` uses `spark.driver.memory=2g`.

### storage_level
The [object serialization](http://spark.apache.org/docs/latest/tuning.html#data-serialization) storage level
to use for the [`CIMReader`](https://derrickoswald.github.io/CIMReader/) and `MaximumFeedIn`.

### deduplicate
When using striped or tiled RDF files as input, this option will eliminate
duplicated elements contained in multiple files.
Only distinctly identified (rdf:ID) CIM elements are kept, duplicates are discarded.

### three
Changes from the default of single phase simulation to three phase simulation.
In general, the results should be the same between the two choices,
so the default is the less memory and disk intensive single phase.

### precalculation
Performs the [Pre-Calculation](#pre-calculation) phase only
that calculates the maximum feed-in power for houses
under the assumption of no other installed systems feeding in power.
This allows the processing to proceed in two phases separated by an arbitrary amount of time.
The simulation number (database primary key) is output on the screen at completion,
to be used as the `--simulation` option.

### trafos
Allows the specification of a file containing a list of transformers to process.
A typical use-case is to correct one or more problem areas
without processing the entire set of transformer areas.

### export_only
Creates the GridLAB-D .glm files for the transformer areas,
but doesn't use them to compute feed-in power.
A typical use case is to generate GridLAB-D model files for some other analysis.

### all
Performs load-flow simulation on all transformer areas in the input CIM file irregardless of
whether they have existing feed-in installations or not.
Normal processing would only perform the [Pre-Calculation](#pre-calculation) phase for
transformer areas without confounding feed-in sources.

### erase
Deletes generated simulation files from the local directory of each worker node in the cluster. The simulation files are copied locally from HDFS, in order for gridlabd to operate on them. Erasing is highly recommended. The files can be found in the `work` directory (standalone) or `userlogs` directory (yarn).

### logging
Specifies the logging level (verbosity) of log records. This overrides the logging level specified in log4j.properties.

### checkpointdir
Specifies the checkpoint directory and turns checkpointing on - if not specified no checkpointing occurs.
An RDD checkpoint is a _materialized_ RDD where the current state of the RDD is stored on disk
and the _recipe_ for how to re-create it is discarded.
The [`CIMReader`](https://derrickoswald.github.io/CIMReader/) and `MaximumFeedIn` application
have optional checkpoints coded in logic to save the state after _costly_ computations - 
which means we trade off time to re-create discarded RDD, for disk space and the time for extra disk/network I/O.

**At the moment it is unclear under which conditions checkpointing should be recommended.**

_NOTE: checkpoint storage must be manually managed (it will not be erased automatically) and this can consume significant space. It is recommended to periodically execute a recursive delete on the checkpoint directory (note the quotes around the asterisk wildcard):_
```
hdfs dfs -rm -R "/checkpoint/*"
```

### simulation
Performs the [Load Flow](#load-flow) phase only using the results
of a prior [Pre-Calculation](#pre-calculation) as specified by the simulation number.
This allows the processing to proceed in two phases separated by an arbitrary amount of time
or to re-execute a failed job.
A typical use-case is `--precalculation` followed by `--simulation` after some pause:
```
$ spark-submit /opt/code/GridLAB-D-2.11-2.2.1-2.4.0-jar-with-dependencies.jar --precalculation hdfs://...
**the simulation number is 42**
_some time later_
$ spark-submit /opt/code/GridLAB-D-2.11-2.2.1-2.4.0-jar-with-dependencies.jar --simulation 42 hdfs://...
```

### reference
Use this reference (simulation) number for a comparison to the current [Pre-Calculation](#pre-calculation)
to determine which transformers have new photo-voltaic installations or have changed the estimated
power sufficiently, and hence need to be simulated with [Load Flow](#load-flow).
This mode is used to avoid recomputing transformer circuits that have not changed.

### delta
The amount of power difference, relative to the reference value,
that will trigger a [Load Flow](#load-flow) simulation
when in `--reference` mode.

### precalcfactor
The scaling to be performed on [Pre-Calculation](#pre-calculation) feed-in value
to set the upper limit for [Load Flow](#load-flow) ramp up.

# Processing

The CIM file is read into Spark using a custom reader, [CIMReader](https://github.com/derrickoswald/CIMReader).
The reader produces a set of
[Resilient Distributed Dataset (RDD)](https://spark.apache.org/docs/latest/programming-guide.html#resilient-distributed-datasets-rdds);
one for [each CIM element class](https://derrickoswald.github.io/CIMReader/doc/scaladocs/#ch.ninecode.model.package).
It also executes a topological analysis to identify a reduced subset of nodes
(sets of electrically identical ConnectivityNode elements - 
connected with zero Ohm conductors such as bus bars or closed switches)
and topological islands isolated by transformers and open switches.
Usually, each power transformer (or set of ganged transformers) supplies one topological island.

## Pre-Calculation

A graph traversal using [GraphX](https://spark.apache.org/docs/latest/graphx-programming-guide.html)
yields the impedance between the transformer and each house
as well as the cable that can sustain the least maximum current.
From these values a best-case upper bound on maximum injected power can be computed for each node,
if the assumption is made that no other neighbors are also injecting power (the majority of cases).
This step also identifies the houses that already have a photo-voltaic or wind power installation
that is already feeding the network, which provides a list of topological islands that require `Load Flow` simulation.

## Load Flow

For topological islands with existing feed-in installations, a load-flow calculation is performed using
using [GridLAB-D](http://www.gridlabd.org/).
The program writes the island's equivalent circuit as
a [.glm](http://gridlab-d.sourceforge.net/wiki/index.php/Creating_GLM_Files) file
for GridLAB-D and also generates simulated power injection
[player](http://gridlab-d.sourceforge.net/wiki/index.php/Player) files for each house.
Each player is a staircase ramp of negative load values,
from zero to the maximum determined by the `Pre Calculation`
with each house (ConnectivityNode) being time-multiplexed with the other nodes in the transformer service area.
It then executes GridLAB-D to run load flow calculations.
The results of the load flow (voltages at each house and current through each conductor and the transformer)
are analyzed to find the maximum power at which voltages are still within tolerance and currents don't
exceed rated maximums.
A second simulation is performed to fine tune the result,
this time with the load step ramp for only the 10kW around the maximum determined in the first step
to provide a resolution of ±1kW.

## Database Output

After each simulation, final results are written to a SQLite database that is local to the spark master node.
This is the only step that runs only on the master node.
The schema of the database is two tables and a view:
```
$ sqlite3 simulation/results.db 
SQLite version 3.8.7.1 2014-10-29 13:59:56
Enter ".help" for usage hints.
sqlite> .schema
CREATE TABLE simulation
    -- table of simulation executions
(
    id integer primary key autoincrement, -- unique id for each simulation program execution
    description text,                     -- textual description of the simulation run
    time text                             -- the date and time at which the simulation was run
);
CREATE INDEX epoc on simulation (time);
CREATE TABLE results
    -- table of calculated maximum feed-in values
(
    id integer primary key autoincrement, -- unique id for each simulation result
    simulation integer,                   -- foreign key to corresponding simulation table program execution
    trafo text,                           -- mRID of the PowerTransformer (or ganged transformers) supplying the energy consumer
    house text,                           -- mRID of the EnergyConsumer at this feed-in node
    maximum double,                       -- maximum feed-in power (W)
    eea integer,                          -- the number of PV installations at this feed-in node
    reason text,                          -- the criteria dictating the maximum: "voltage limit", "current limit" or "transformer limit"
    details text                          -- details regarding the limiting criteria
);
CREATE INDEX house on results (house);
CREATE INDEX sim on results (simulation);
CREATE VIEW intermediate as
    -- staging view for unified (precalculated & GridLAB-D simulated) feed-in values
    select
        0 Priority,             -- low priority for precalculated values
        s.description Analysis, -- 'Threshold Precalculation'
        datetime(s.time/1000, 'unixepoch', 'localtime') Date, -- textual program execution time
        s.time When_Epoc,       -- numeric program execution time
        r.trafo Transformer,    -- mRID of the PowerTransformer (or ganged transformers) supplying the energy consumer
        r.house House,          -- mRID of the EnergyConsumer at this feed-in node
        r.maximum Maximum,      -- maximum feed-in power (W)
        r.reason Reason,        -- the criteria dictating the maximum: "voltage limit", "current limit" or "transformer limit"
        r.details Details       -- details regarding the limiting criteria
    from
        simulation s,           -- program executions
        results r               -- result values
    where
        s.description = 'Threshold Precalculation' and -- select only precalculated values
        s.id = r.simulation     -- join the program execution with the result value
union
    select
        1 Priority,             -- high priority for load-flow values
        s.description Analysis, -- 'Einspeiseleistung'
        datetime(s.time/1000, 'unixepoch', 'localtime') Date, -- textual program execution time
        s.time When_Epoc,       -- numeric program execution time
        r.trafo Transformer,    -- mRID of the PowerTransformer (or ganged transformers) supplying the energy consumer
        r.house House,          -- mRID of the EnergyConsumer at this feed-in node
        r.maximum Maximum,      -- maximum feed-in power (W)
        r.reason Reason,        -- the criteria dictating the maximum: "voltage limit", "current limit" or "transformer limit"
        r.details Details       -- details regarding the limiting criteria
    from
        simulation s,           -- program executions
        results r               -- result values
    where
        s.description = 'Einspeiseleistung' and -- select only load-flow values
        s.id = r.simulation     -- join the program execution with the result value;
CREATE VIEW feedin as
    -- view of the most recent best estimated value of maximum feed-in power
    select
        i.Analysis,             -- type of analysis, prefer 'Einspeiseleistung'
        i.Transformer,          -- mRID of the PowerTransformer (or ganged transformers) supplying the energy consumer
        i.House,                -- mRID of the EnergyConsumer at this feed-in node
        i.Maximum,              -- maximum feed-in power (W)
        i.Reason,               -- the criteria dictating the maximum: "voltage limit", "current limit" or "transformer limit"
        i.Details,              -- details regarding the limiting criteria
        max(i.When_Epoc) When_Epoc -- select only the most recent value
    from
        intermediate i          -- staging view
    where
        Priority = 1            -- select only the load-flow values (if any)
    group by
        House                   -- for each unique EnergyConsumer mRID
union
    select
        i.Analysis,             -- type of analysis, fall back to 'Threshold Precalculation'
        i.Transformer,          -- mRID of the PowerTransformer (or ganged transformers) supplying the energy consumer
        i.House,                -- mRID of the EnergyConsumer at this feed-in node
        i.Maximum,              -- maximum feed-in power (W)
        i.Reason,               -- the criteria dictating the maximum: "voltage limit", "current limit" or "transformer limit"
        i.Details,              -- details regarding the limiting criteria
        max(i.When_Epoc) When_Epoc -- select only the most recent value
    from
        intermediate i          -- staging view
    where
        House not in (select House from intermediate where Priority = 1 group By House) -- select precalculated values if no load-flow value is present
    group by
        House                   -- for each unique EnergyConsumer mRID;
```

The `feedin` view provides the _best and most current_ estimate of the maximum feed in power.
It prefers the load flow computed value where this exists, and falls back to the pre-calulated value where it doesn't.


# SWING Bus

The SWING bus (also known as a [slack bus](https://en.wikipedia.org/wiki/Slack_bus) or reference bus) is
a node in the system which supplies or absorbs active and reactive power to and from the system that is
external to the network under study, to maintain the phase angle at zero with a unity per unit voltage.

For our system there were three choices for the location of the bus
 
![SWING Bus Options](https://rawgit.com/derrickoswald/CIMApplication/master/MaximumFeedIn/img/swing%20bus%20choices.svg "SWING Bus Options")

1. transformer low voltage node (usually 400v)
2. transformer high voltage node (usually 16kV)
3. foreign power attachment node (usually 132kV)

The transformer low voltage node would eliminate the transformer from the evaluation, which was deemed inappropriate.
The foreign power attachment point requires an accurate model of the middle voltage system, which is not available
at all stations. Hence, the SWING busses are set as the high voltage pins of the distribution transformers.
