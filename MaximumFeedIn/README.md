Maximum Feed In
===============

Calculate the maximum feed-in power (e.g. capacity of a photovoltaic installation) at every node of an electric distribution network.

![MaximumFeedIn Overview](https://rawgit.com/derrickoswald/CIMApplication/master/MaximumFeedIn/img/MaximumFeedIn.svg "Overview diagram")

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
For load-flow computations it uses [GridLAB-D](https://www.gridlabd.org/).

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
per kilometer and is in service at 400 Volts with a maximum rated current of 170 Amps.
Not shown is that topologically, it is connected to a house and an intermediate junction.

_NOTE: this is an error on our part, the impedances in the ACLineSegment are *not* supposed to be per kilometer._

# Preparation

HDFS
-----

The data file to be processed needs to be uploaded to HDFS. A command like this can be used:
```
hdfs dfs -mkdir /data
hdfs dfs -put cimfile.rdf /data
```

For some HDFS configuration settings, the directory for simulation files may need to be created and given write access permissions for the user executing the program.
This defaults to `/simulation` but can be altered with the `--outputfile` parameter.
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
spark-submit --master spark://sandbox:7077 --conf spark.driver.memory=2g --conf spark.executor.memory=2g /opt/code/MaximumFeedIn-*-jar-with-dependencies.jar --help
MaximumFeedIn 2.11-2.4.5-2.7.2
Usage: MaximumFeedIn [options] [<CIM> <CIM> ...]

  --master MASTER_URL      spark://host:port, mesos://host:port, yarn, or local[*]
  --sparkopts k1=v1,k2=v2  Spark options [spark.graphx.pregel.checkpointInterval=8,spark.serializer=org.apache.spark.serializer.KryoSerializer,spark.ui.showConsoleProgress=false]
  --storage_level <value>  storage level for RDD serialization [MEMORY_AND_DISK_SER]
  --cimopts k1=v1,k2=v2    CIMReader options [ch.ninecode.cim.do_topo=true,ch.ninecode.cim.default_switch_open_state=false,ch.ninecode.cim.force_retain_fuses=ForceTrue,ch.ninecode.cim.force_switch_separate_islands=Unforced,ch.ninecode.cim.do_topo_islands=true,ch.ninecode.cim.force_fuse_separate_islands=Unforced,ch.ninecode.cim.force_retain_switches=ForceTrue,StorageLevel=MEMORY_AND_DISK_SER]
  --deduplicate            de-duplicate input (striped) files [false]
  --logging <value>        log level, one of ALL,DEBUG,ERROR,FATAL,INFO,OFF,TRACE,WARN [OFF]
  --checkpoint <dir>       checkpoint directory on HDFS, e.g. hdfs://... []
  --verbose                turns on the INFO messages logging [false]
  --three                  use three phase computations [false]
  --precalculation         only calculates threshold and EEA existence for all HAS, assuming no EEA [false]
  --trafos <TRA file>      file of transformer names (one per line) to process []
  --export_only            generates glm files only - no solve or analyse operations [false]
  --all                    process all transformers (not just those with EEA) [false]
  --erase                  clean up (delete) simulation files [false]
  --simulation N           simulation number (precalc) to use for transformer list [-1]
  --reference N            simulation number (precalc) to use as reference for transformer list [-1]
  --delta D                delta power difference threshold for reference comparison [1.00000e-06]
  --precalcfactor D        factor to multiply precalculation results for GridLAB-D [2.50000]
  --cosphi D               power factor for photo-voltaic installations, positive leading, negative lagging [1.00000]
  --voltage_threshold D    the voltage threshold for the feeder of the house under test [3.00000%]
  --voltage_threshold2 D   the voltage threshold for neighboring feeders of the house under test [3.00000%]
  --ignore_other           ignore cable currents on neighboring feeders [false]
  --cable_impedance_limit D cables with higher impedances for R1 will not be processed with GridLAB-D [5.00000Ω]
  --workdir <dir>          shared directory (HDFS or NFS share) with scheme (hdfs:// or file:/) for work files []
  --outputfile <file>      name of the SQLite database results file [simulation/results.db]
  <CIM> <CIM> ...          CIM rdf files to process
  --version                Scala: 2.11, Spark: 2.4.5, MaximumFeedIn: 2.7.2

Calculate maximum feed-in power without reinforcement or exceeding voltage, current or power constraints.
```

_NOTE: It is important to understand that options before the jar file name are consumed (and normally understood) by `spark-submit`,
while options after the jar file are passed to the `MaximumFeedIn` program.

The resulting SQLite database is `simulation/results.db`,
i.e. the path relative to where the `spark-submit` command was executed.

## Options

### master
Allows specifying the Spark master (same meaning as spark-submit) when `MaximumFeedIn` is
run in debug mode in a development environment.
Normally this should not be used by end users.
Within the program, the Spark session is accessed via `getOrCreate`,
so it should be benign to specify the same master as for `spark-submit`.

### opts
Allows specifying Spark options when `MaximumFeedIn` is run.
When specified, options are in the form of
[Spark Properties](https://spark.apache.org/docs/latest/configuration.html#viewing-spark-properties)
(just like the _Arbitrary Spark configuration property_ `--conf` option for `spark-submit`),
and not the form used by spark-submit.
For example spark-submit uses `--executor-memory 2g`, while `--opts` uses `spark.executor.memory=2g`.

_Note: only some configuration properties can be set after the Spark context is created.
In general, options available via `spark-submit` options are **not** settable._

### storage_level
The [object serialization](http://spark.apache.org/docs/latest/tuning.html#data-serialization) storage level
to use for the [`CIMReader`](https://derrickoswald.github.io/CIMSpark/CIMReader/) and `MaximumFeedIn`.

### deduplicate
When using striped or tiled RDF files as input, this option will eliminate
duplicated elements contained in multiple files.
Only distinctly identified (rdf:ID) CIM elements are kept, duplicates are discarded.

### logging
Specifies the logging level (verbosity) of log records.
This overrides the logging level specified in log4j.properties.

### checkpointdir
Specifies the checkpoint directory and turns checkpointing on - if not specified no checkpointing occurs.
An RDD checkpoint is a _materialized_ RDD where the current state of the RDD is stored on disk
and the _recipe_ for how to re-create it is discarded.
The [`CIMReader`](https://derrickoswald.github.io/CIMSpark/CIMReader/) and `MaximumFeedIn` application
have optional checkpoints coded in logic to save the state after _costly_ computations - 
which means we trade off time to re-create discarded RDD, for disk space and the time for extra disk/network I/O.

**At the moment it is unclear under which conditions checkpointing should be recommended.**

_NOTE: checkpoint storage must be manually managed (it will not be erased automatically) and this can consume significant space. It is recommended to periodically execute a recursive delete on the checkpoint directory (note the quotes around the asterisk wildcard):_
```
hdfs dfs -rm -R "/checkpoint/*"
```
### verbose
Together with the --logging option and log4j.properties configuration of Spark,
this controls if informational messages from the application are emitted.
If verbose is specified, the `--logging` and log4j.properties settings of
some critical classes in MaximumFeedIn are overridden with the INFO level
to provide some feedback on processing stages. 

### three
Changes from the default of single phase simulation to three phase simulation.
In general, the results should be the same between the two choices,
so the default (`false`) is the less memory and disk intensive single phase calculations.

A single phase calculation could have been done by simply dropping two of three phases
and scaling the transformer and consumer power, but instead, the Line-to-Line voltage is used,
which leads to √3 × line current and hence full power on one phase for the transformer and consumer.

The following table summarizes the scaling in single phase calculations
to equal the results for three phase simulation:

| Property | 1-phase | 3-phase |
|----------|---------|---------|
|Voltage|Line-to-Line (√3 × L-N)| Line-to-Neutral|
|Current|√3 × line current|line current|
|Transformer Power|transformer power on one phase|three phase power|
|Transformer Current|transformer current on one phase|three phase current|
|EnergyConsumer Power|PV power on one phase|three phase PV power|

For limit checks, the line current is scaled by 1÷√3, since cables are specified as current per phase conductor.
The voltage limit is already relative (`--voltage_threshold`, default 3%) and the transformer power
and PV power are computed directly.

### precalculation
Performs the [Pre-Calculation](#pre-calculation) phase only.
This calculates the maximum feed-in power for houses
under the assumption that no other installed systems are feeding in power.
This allows the processing to proceed in two phases separated by an arbitrary amount of time.
The simulation number (database primary key) is output on the screen at completion,
to be used as the `--simulation` option.

### trafos
Allows the specification of a file containing a list of transformers to process.
A typical use-case is to correct one or more problem areas
without processing the entire set of transformer areas.
The file should consist of one transformer name per line.
For the case of ganged (parallel) transformers the names are alphabetically sorted
and concatenated by an underscore character.
An example file might look like this:
```
TRA12345
TRA7894_TRA7895
```

### export_only
Creates the GridLAB-D .glm files for the transformer areas,
but doesn't use them to compute feed-in power.
A typical use case is to generate GridLAB-D model files for some other analysis.

### all
Performs load-flow simulation on all transformer areas in the input CIM file irregardless of
whether they have existing feed-in installations or not.
Normal processing would only perform the [Pre-Calculation](#pre-calculation) phase for
transformer areas without confounding feed-in sources or non-radial networks.

### erase
Deletes generated simulation files from the local directory of each worker node in the cluster.
The simulation files are copied locally from HDFS, in order for gridlabd to operate on them.
Erasing is highly recommended.The files can be found in the `work` directory (standalone) or `userlogs` directory (yarn).

### simulation
Performs the [Load Flow](#load flow) phase only using the results
of a prior [Pre-Calculation](#pre-calculation) as specified by the simulation number.
This allows the processing to proceed in two phases separated by an arbitrary amount of time
or to re-execute a failed job.
A typical use-case is `--precalculation` followed by `--simulation` after some pause:
```
$ spark-submit /opt/code/MaximumFeedIn-*-jar-with-dependencies.jar --precalculation hdfs://...
**the simulation number is 42**
_some time later_
$ spark-submit /opt/code/MaximumFeedIn-*-jar-with-dependencies.jar --simulation 42 hdfs://...
```

### reference
Use this reference (simulation) number for a comparison to the current [Pre-Calculation](#pre-calculation)
to determine which transformers have new photo-voltaic installations or have changed the estimated
power sufficiently, and hence need to be simulated with [Load Flow](#load flow).
This mode is used to avoid recomputing transformer circuits that have not changed.

### delta
The amount of power difference, relative to the reference value,
that will trigger a [Load Flow](#load flow) simulation
when in `--reference` mode.

### precalcfactor
The scaling to be performed on [Pre-Calculation](#pre-calculation) feed-in value
to set the upper limit for [Load Flow](#load flow) ramp up.

### cosphi
The power factor which will be used for new photo-voltaic installations.
A positive CosPhi indicates a leading power factor for new PV, while a negative CosPhi indicates a lagging power factor.
See the discussion below under [Power Factor](#power-factor).

### voltage_threshold 
This specifies the threshold for voltage that will be used for each node on the same feeder (of the trafo station) as the house on which
the photo-voltaic is currently tested.

### voltage_threshold2 
This threshold for voltage will be used for each node NOT on the same feeder (of the trafo station) as the house on which
the photo-voltaic is currently tested.

### ignore_other
The current check for AC line segments can be ignored for lines which are NOT on the same feeder as the house which is 
under test.

### cable_impedance_limit
cables with a R1 value higher than this are not calculated with GridLAB-D.
The reason is bad performance, and sometimes non-convergence, with too high impedance values.

### workdir
This parameter specifies the directory for intermediate files.
For distributed processing (more than one Spark node) a common directory (HDFS or NFS share)
accessible by the driver and all worker nodes can be specified.
If it isn't specified the working directory is in the same location as the input files.

### outputfile
This parameter allows the placement of the output SQLite file.

### version
Outputs the version of Scala, Spark and MaximumFeedIn for the jar.
The Scala and Spark versions were used at compile time, so deviations from these values
when running MaximumFeedIn can cause failure is arcane and subtle ways.
If `--verbose` is specified or the log4j logging level is INFO,
the program reports the Spark version abtained from the session. 
  
# Processing

The CIM file is read into Spark using a custom reader, [CIMReader](https://github.com/derrickoswald/CIMReader).
The reader produces a set of
[Resilient Distributed Dataset (RDD)](https://spark.apache.org/docs/latest/programming-guide.html#resilient-distributed-datasets-rdds);
one for [each CIM element class](https://derrickoswald.github.io/CIMSpark/doc/scaladocs/#ch.ninecode.model.package).
It also executes a topological analysis to identify a reduced subset of nodes
(sets of electrically identical ConnectivityNode elements - 
connected with zero Ohm (0Ω) conductors such as bus bars or closed switches)
and topological islands isolated by transformers and open switches.
Usually, each power transformer (or set of ganged transformers) supplies one topological island.

## Pre-Calculation

A graph traversal using [GraphX](https://spark.apache.org/docs/latest/graphx-programming-guide.html)
yields the impedance between the transformer and each house
as well as the cable that can sustain the least maximum current.
From these values a best-case upper bound on maximum injected power can be computed for each node,
if the assumption is made that no other neighbors are also injecting power (the majority of cases).
This step also identifies the houses that already have a photo-voltaic or wind power installation
that is already feeding the network, or networks that have a non-radial topology.
This provides a list of topological islands that require `Load Flow` simulation.

## Load Flow

For topological islands with existing feed-in installations or non-radial topology,
a load-flow calculation is performed using [GridLAB-D](http://www.gridlabd.org/).
The program writes the island's equivalent circuit as
a [.glm](http://gridlab-d.sourceforge.net/wiki/index.php/Creating_GLM_Files) file
for GridLAB-D and also generates simulated power injection
[player](http://gridlab-d.sourceforge.net/wiki/index.php/Player) files for each house.
Each player is a staircase ramp of negative load values,
from zero to the maximum determined by the `Pre-Calculation`
with each house (ConnectivityNode) being time-multiplexed with the other nodes in the transformer service area.
It then executes GridLAB-D to run load flow calculations.
The results of the load flow (voltages at each house, current through each conductor and the transformer power)
are analyzed to find the maximum power at which voltages are still within tolerance, and currents and powers don't
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
SQLite version 3.30.1 2019-10-10 20:19:45
Enter ".help" for usage hints.
sqlite> .schema
CREATE TABLE simulation
    -- table of simulation executions
(
    id integer primary key autoincrement, -- unique id for each simulation program execution
    description text,                     -- textual description of the simulation run
    time text                             -- the date and time at which the simulation was run
);
CREATE TABLE sqlite_sequence(name,seq);
CREATE INDEX epoch on simulation (time);
CREATE TABLE results
    -- table of calculated maximum feed-in values
(
    id integer primary key autoincrement, -- unique id for each simulation result
    simulation integer,                   -- foreign key to corresponding simulation table program execution
    trafo text,                           -- mRID of the PowerTransformer (or ganged transformers) supplying the house
    feeder text,                          -- mRID of the Connector supplying the house from the substation
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
        s.time When_Epoch,      -- numeric program execution time
        r.trafo Transformer,    -- mRID of the PowerTransformer (or ganged transformers) supplying the house
        r.feeder Feeder,        -- mRID of the Connector supplying the house from the substation
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
        s.time When_Epoch,      -- numeric program execution time
        r.trafo Transformer,    -- mRID of the PowerTransformer (or ganged transformers) supplying the house
        r.feeder Feeder,        -- mRID of the Connector supplying the house from the substation
        r.house House,          -- mRID of the EnergyConsumer at this feed-in node
        r.maximum Maximum,      -- maximum feed-in power (W)
        r.reason Reason,        -- the criteria dictating the maximum: "voltage limit", "current limit" or "transformer limit"
        r.details Details       -- details regarding the limiting criteria
    from
        simulation s,           -- program executions
        results r               -- result values
    where
        s.description = 'Einspeiseleistung' and -- select only load-flow values
        s.id = r.simulation     -- join the program execution with the result value
/* intermediate(Priority,Analysis,Date,When_Epoch,Transformer,Feeder,House,Maximum,Reason,Details) */;
CREATE VIEW feedin as
    -- view of the most recent best estimated value of maximum feed-in power
    select
        i.Analysis,             -- type of analysis, prefer 'Einspeiseleistung'
        i.Transformer,          -- mRID of the PowerTransformer (or ganged transformers) supplying the house
        i.Feeder,               -- mRID of the Connector supplying the house from the substation
        i.House,                -- mRID of the EnergyConsumer at this feed-in node
        i.Maximum,              -- maximum feed-in power (W)
        i.Reason,               -- the criteria dictating the maximum: "voltage limit", "current limit" or "transformer limit"
        i.Details,              -- details regarding the limiting criteria
        max(i.When_Epoch) When_Epoch -- select only the most recent value
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
        i.Feeder,               -- mRID of the Connector supplying the house from the substation
        i.House,                -- mRID of the EnergyConsumer at this feed-in node
        i.Maximum,              -- maximum feed-in power (W)
        i.Reason,               -- the criteria dictating the maximum: "voltage limit", "current limit" or "transformer limit"
        i.Details,              -- details regarding the limiting criteria
        max(i.When_Epoch) When_Epoch -- select only the most recent value
    from
        intermediate i          -- staging view
    where
        House not in (select House from intermediate where Priority = 1 group By House) -- select precalculated values if no load-flow value is present
    group by
        House                   -- for each unique EnergyConsumer mRID
/* feedin(Analysis,Transformer,Feeder,House,Maximum,Reason,Details,When_Epoch) */;
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
2. transformer high voltage node (usually 20kV)
3. foreign power attachment node (usually 132kV)

The transformer low voltage node would eliminate the transformer from the evaluation, which was deemed inappropriate.
The foreign power attachment point requires an accurate model of the medium voltage network, which is not always
available at all stations.
Hence, the SWING busses are set as the high voltage pins of the distribution transformers.

# Power Factor

Most photovoltaics (PV) are only set up to inject power at unity power factor - they only produce active (real) power.
This has the effect of reducing the power factor, as the grid supplies less active power,
but the same amount of reactive power as before the PV was installed. This is undesirable.

The inverter of a PV with reactive power control can be configured to produce both active and
reactive power - a non-unity power factor - which ameliorates the problem.

The MaximumFeedIn application allows the specification of across-the-board power factor for proposed new PV,
and also accepts power factor values of existing PV as an attribute in the `SolarGeneratingUnit` object.

_Note: Currently (using CIM16) the attribute for existing PV power factor is `normalPF`,
which was originally intended to be `Generating unit economic participation factor` and
hence is not correct usage. 
With the switch to CIM100, the PowerElectronicsConnection class will be used instead._

## Leading and Lagging Loads

A brief overview of leading and lagging loads will illustrate the concepts.

Power is the product of the voltage and **complex conjugate** of the current.
As shown below a lagging power factor (inductive load) is produced by a
positive voltage and negative reactive portion of current:

![Lagging Power Factor](https://rawgit.com/derrickoswald/CIMApplication/master/MaximumFeedIn/img/lagging.svg "Lagging Power Factor")

In the rotation of the phasors, the current lags (is behind in time) the voltage that produces it.

Conversely, a leading power factor is produced by a
positive voltage and positive reactive portion of current:

![Leading Power Factor](https://rawgit.com/derrickoswald/CIMApplication/master/MaximumFeedIn/img/leading.svg "Leading Power Factor")

## Leading and Lagging Negative Loads

The model for a PV in GridLAB-D is a negative load.
In order to obtain a power in the second quadrant (II), the negative load is a
negative voltage and negative reactive portion of current:

![Negative Leading Load](https://rawgit.com/derrickoswald/CIMApplication/master/MaximumFeedIn/img/negative_leading.svg "Negative Leading Load")

Of less interest is power in the third quadrant (III), where the negative load is a
negative voltage and positive reactive portion of current:

![Negative Lagging Load](https://rawgit.com/derrickoswald/CIMApplication/master/MaximumFeedIn/img/negative_lagging.svg "Negative Lagging Load")

## cosphi parameter

The choice of cosine is unfortunate because it is an [even function](https://en.wikipedia.org/wiki/Even_and_odd_functions#Even_functions).
Both a positive and a negative reactive component yield a positive cosine.
So, normally an additional bit of information is provided to indicate leading or lagging.
Normally in electric grid references a lagging power factor is assumed.
For the purposes of the MaximumFeedIn program a minus sign (-) on the cosphi parameter indicates
a lagging negative load.
So, without a negative sign, a positive cosphi parameter (which is the normal grid reference)
specifies a negative leading load,
putting the resulting apparent power in the second quadrant (II).

## Smax

For the Precalculation phase, a non-unity PV cosphi complicates the normal direct
calculation of maximum PV power as described in
[Deriving the Smax Algorithm for Non-unity Power Factors](doc/Deriving%20the%20Smax%20Algorithm%20for%20Non-unity%20Power%20Factors%204.pdf).
