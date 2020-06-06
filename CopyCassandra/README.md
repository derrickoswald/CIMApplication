CopyCassandra
=============

Copies data between Cassandra instances.

Note: This does not work between keyspaces of the same instance - even though it should - due to ref counting/lock
issues with the underlying CassandraConnector.

Operation
---------

The program needs to be run using `spark-submit`:

```bash
spark-submit --master spark://sandbox:7077 CopyCassandra-*-jar-with-dependencies.jar --help

CopyCassandra 2.11-2.4.5-2.7.2
Usage: CopyCassandra [options]

  --log <value>            log level, one of ALL,DEBUG,ERROR,FATAL,INFO,OFF,TRACE,WARN [OFF]
  --master MASTER_URL      local[*], spark://host:port, mesos://host:port or yarn []
  --source_host Cassandra  Cassandra source connection host (listen_address or seed in cassandra.yaml) [localhost]
  --source_port <port>     Cassandra source connection port [9042]
  --source_keyspace <name> source Cassandra keyspace [cimapplication]
  --target_host Cassandra  Cassandra destination connection host (listen_address or seed in cassandra.yaml) [localhost]
  --target_port <port>     Cassandra destination connection port [9042]
  --target_keyspace <name> destination Cassandra keyspace [cimapplication]
  --target_replication <#> destination keyspace replication if the Cassandra keyspace needs creation [1]
  --version                Scala: 2.11, Spark: 2.4.5, CopyCassandra: 2.7.2

Copies data from one Cassandra instance or keyspace to another through Spark.
```

It first creates the target schema using the simulation_schema.sql in the resources folder.
It then scans system_schema.tables to make a list of table names to copy *from the source schema*.
For each table name it creates a `CassandraTableScanRDD[CassandraRow]` and copies it to the *target schema*.

No attempt is made to accommodate differences in the schema - it will just fail.

