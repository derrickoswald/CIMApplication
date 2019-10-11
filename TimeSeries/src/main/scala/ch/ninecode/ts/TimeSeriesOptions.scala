package ch.ninecode.ts

import ch.ninecode.ts.LogLevels.LogLevels

/**
 * Options for time series processing.
 *
 * @param valid         <code>false</code> if either help or version requested (i.e. don't proceed with execution).
 * @param unittest      If <code>true</code>, don't call sys.exit().
 * @param master        Spark master.
 * @param spark_options Spark options.
 * @param storage       Storage level for RDD serialization.
 * @param log_level     Logging level.
 * @param host          Cassandra connection host.
 * @param port          Cassandra nativeTransportPort port.
 * @param keyspace      Cassandra keyspace.
 */
case class TimeSeriesOptions
(
    var valid: Boolean = true,
    unittest: Boolean = false,
    master: String = "",
    spark_options: Map[String, String] = Map (),
    storage: String = "MEMORY_AND_DISK_SER",
    log_level: LogLevels = LogLevels.OFF,
    host: String = "localhost",
    port: Int = 9042,
    keyspace: String = "cimapplication"
)