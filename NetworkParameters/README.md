NetworkParameters
======

Spark based module to create CIM EquivalentInjection objects from spreadsheet data.

# Overview

Matches the transformers in a set of CIM files to the
available network short circuit power and angle in one or more .CSV files.
For each matched transformer, the program creates an
[EquivalentInjection](https://derrickoswald.github.io/CIMSpark/doc/scaladocs/ch/ninecode/model/EquivalentInjection.html)
object that represents the upstream network at the high voltage terminal,
of the transformer and writes these objects to a
CIM file of the same name as the CSV file.

# Customer1_NetworkParameters

Reads a .CSV file with the format:
```
StructType(StructField(id,StringType,true), StructField(Fehlerort,StringType,true), StructField(Un,DoubleType,true), StructField(Ikw...RST.,DoubleType,true), StructField(Sk..RST.,DoubleType,true), StructField(Beschreibung..SAP.Nr..,StringType,true), StructField(Abgang,StringType,true), StructField(NIS.ID,StringType,true), StructField(NIS.Name,StringType,true))
```
and matches the **Fehlerort** value to Substation CIM elements,
attaching an EquivalentInjection at each transformer contained in the station with the values:

- available network power: **Sk..RST.**
- network impedance angle: **Ikw...RST.**

# Customer2_NetworkParameters

Reads two .CSV files, one with the format:
```
StructType(StructField(nis_number,StringType,true), StructField(Name_Netzeinspeisung,StringType,true), StructField(Name_Station,StringType,true), StructField(Gemeinde_Station,StringType,true), StructField(article_id,StringType,true), StructField(Type,StringType,true), StructField(kf_neplan_typ,StringType,true), StructField(nominal_voltage,DoubleType,true), StructField(max_connections,IntegerType,true))
```
and the other as exported from NEPLAN, and matches the **Name_Netzeinspeisung** of the first
file with the **Typ** field of the second attaching an EquivalentInjection to each
**nis_number** transformer in the first file the values:

- worst case maximum network available power **Sk"max_MVA**
- worst case maximum network resistance to reactance ratio: **R(1)/X(1) max**
- worst case minimum network available power **Sk"min_MVA**
- worst case minimum network resistance to reactance ratio: **R(1)/X(1) min**

# Customer3_NetworkParameters

Reads a .CSV file with the format:
```
StructType(StructField(OBJEKT,StringType,true), StructField(KS-LEISTUNG MS-NETZ SK,DoubleType,true), StructField(R-X-VERHAELTNIS,DoubleType,true), StructField(SR_TYP_IN_KVA,DoubleType,true), StructField(UR1_NENNSPANNUNG1,DoubleType,true), StructField(UR2_NENNSPANNUNG1,DoubleType,true), StructField(URR1_KURZSCHLUSSVERLUSTE_IN_W,DoubleType,true), StructField(URR1_KURZSCHLUSSVERLUSTE_IN_PR,DoubleType,true), StructField(UKR1_KURZSCHLUSSSPANNUNG_IN_PR,DoubleType,true), StructField(EIGENTUEMER,StringType,true))
```

and matches OBJEKT with PowerTransformer CIM elements,
attaching an EquivalentInjection at each transformer contained in the station with the values:

- available network power: **KS-LEISTUNG MS-NETZ SK**
- network impedance ratio: **R-X-VERHAELTNIS**

# Execution

Use the
[spark-submit](http://spark.apache.org/docs/latest/submitting-applications.html#launching-applications-with-spark-submit)
command to launch the program. Using the --help switch:
```
$ spark-submit Customer2_NetworkParameters-*-jar-with-dependencies.jar --help
```
will print out a short summary of available parameters like so:
```
Customer2_NetworkParameters <version>
Usage: Customer2_NetworkParameters [options] [<CIM> <CIM> ...]

  --version                Scala: 2.12, Spark: 3.0.0, Customer2_NetworkParameters: <version>
  --master <master_url>    local[*], spark://host:port/, mesos://host:port or yarn [local[*]]
  --spark_options <map>    Spark options [spark.serializer=org.apache.spark.serializer.KryoSerializer,spark.sql.catalog.casscatalog=com.datastax.spark.connector.datasource.CassandraCatalog,spark.kryo.registrator=ch.ninecode.cim.CIMRegistrator,spark.graphx.pregel.checkpointInterval=8,spark.ui.showConsoleProgress=false,spark.sql.debug.maxToStringFields=250]
  --log <enum>             log level, one of OFF,FATAL,ERROR,WARN,INFO,DEBUG,TRACE,ALL [OFF]
  --jars <list>            names of jars to send to Spark [/home/derrick/code/CIMApplication/NetworkParameters/target/Customer2_NetworkParameters-<-jar-with-dependencies.jar]
  --checkpoint <dir>       checkpoint directory on HDFS, e.g. hdfs://... []
  --cim_options <map>      CIM options [ch.ninecode.cim.do_topo=true,ch.ninecode.cim.do_normalize=false,ch.ninecode.cim.do_join=false,ch.ninecode.cim.default_switch_open_state=false,ch.ninecode.cim.do_deduplication=true,path=,ch.ninecode.cim.debug=false,ch.ninecode.cim.split_maxsize=67108864,ch.ninecode.cim.force_retain_fuses=ForceTrue,ch.ninecode.cim.force_switch_separate_islands=Unforced,ch.ninecode.cim.do_topo_islands=true,ch.ninecode.cim.force_fuse_separate_islands=Unforced,ch.ninecode.cim.force_retain_switches=Unforced,ch.ninecode.cim.make_edges=false,StorageLevel=MEMORY_AND_DISK_SER,ch.ninecode.cim.cache=,ch.ninecode.cim.do_about=false]
  --topology               do topology processing (enables the following 6 options) [true]
  --identify_islands       perform island topological processing [true]
  --retain_switch <state>  attribute 'retain' for all switches except Fuse types, one of ForceTrue,ForceFalse,Unforced [Unforced]
  --retain_fuse <state>    attribute 'retain' for all fuses, one of ForceTrue,ForceFalse,Unforced [ForceTrue]
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
  --export <RDF file>      save processed RDF file as this name []
  --csv1 <CSV file>        csv file of available power at station data [Trafos_fuer_Analytiks.csv]
  --csv2 <CSV file>        csv file of mapping between station and transformer [Netzeinspeisungen.csv]
```
Most parameters are self explanatory, but the following may add more details:

## --csv1

The first "comma separated value" file, but it actually expects semicolon (;) delimited files.
For Customer1_NetworkParameters and Customer3_NetworkParameters there is only one .CSV
specified by **--csv**. For Customer1_NetworkParameters the CSV is comma delimited.

## --csv2

The second CSV file, again delimited by semicolons.
This format is the exported format for the **Netzeinspeisung** NEPLAN library.

## --export

If a filename is provided, the input CIM files will be exported as a single CIM
file after being deduplicated (if requested), topology processed and EquivalentInjections added.
That is, this export file can be used as the input to the ShortCircuit program.

 
