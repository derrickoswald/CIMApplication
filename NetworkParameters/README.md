NetworkParameters
======

Spark based module to create CIM EquivalentInjection objects from spreadsheet data.

# Overview

Matches the transformers in a set of CIM files to the
available network short circuit power and angle in one or more .CSV files.
For each matched transformer, the program creates an
[EquivalentInjection](https://derrickoswald.github.io/CIMReader/doc/scaladocs/index.html#ch.ninecode.model.EquivalentInjection)
object that represents the upstream network at the high voltage terminal,
of the transformer and writes these objects to a
CIM file of the same name as the CSV file.

# Customer1_NetworkParameters

Reads a .CSV file with the format:
```
StructType(StructField(id,StringType,true), StructField(Fehlerort,StringType,true), StructField(Un,DoubleType,true), StructField(Ikw...RST.,DoubleType,true), StructField(Sk..RST.,DoubleType,true), StructField(Beschreibung..SAP.Nr..,StringType,true), StructField(Abgang,StringType,true), StructField(NIS.ID,StringType,true), StructField(NIS.Name,StringType,true))
```
and matches the **Fehlerort** value to Substation CIM elements,
attaching an EquivalentInjection at each transformer contained in the station the values:

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

# Execution

Use the
[spark-submit](http://spark.apache.org/docs/latest/submitting-applications.html#launching-applications-with-spark-submit)
command to launch the program. Using the --help switch:
```
$ spark-submit Customer2_NetworkParameters-2.11-2.3.1-2.4.0-jar-with-dependencies.jar --help
```
will print out a short summary of available parameters like so:
```
Customer2_NetworkParameters 2.11-2.3.1-2.4.0
Usage: Customer2_NetworkParameters [options] [<CIM>,<CIM>...]

Creates EquivalentInjection objects for the network supplying transformers from external data files.

  --help               prints this usage text
  --version            Scala: 2.11, Spark: 2.3.1, Customer2_NetworkParameters: 2.4.0
  --quiet              suppress informational messages [false]
  --master MASTER_URL  local[*], spark://host:port, mesos://host:port, yarn []
  --opts k1=v1,k2=v2   other Spark options []
  --storage <value>    storage level for RDD serialization [MEMORY_AND_DISK_SER]
  --deduplicate        de-duplicate input (striped) files [false]
  --logging <value>    log level, one of ALL,DEBUG,ERROR,FATAL,INFO,OFF,TRACE,WARN [OFF]
  --checkpoint <dir>   checkpoint directory on HDFS, e.g. hdfs://server:8020/... []
  --csv1 <file>        csv file of mapping between station and transformer [Trafos_fuer_Analytiks.csv]
  --csv2 <file>        csv file of available power at station data [Netzeinspeisungen.csv]
  --export <CIM>       name of deduped + topologically processed CIM file []
  <CIM>,<CIM>...       CIM rdf files to process
```
Most parameters are self explanatory, but the following may add more details:

## --csv1

The first "comma separated value" file, but it actually expects semicolon (;) delimited files.
For Customer1_NetworkParameters there is only one .CSV specified by **--csv**, which is comma delimited.

## --csv2

The second CSV file, again delimited by semicolons.
This format is the exported format for the **Netzeinspeisung** NEPLAN library.

## --export

If a filename is provided, the input CIM files will be exported as a single CIM
file after being deduplicated (if requested) and topology processed.
That is, this export file can be used as the input to the ShortCircuit program
to avoid re-executing the NetworkTopologyProcessor to determine the topological
nodes and islands.

 
