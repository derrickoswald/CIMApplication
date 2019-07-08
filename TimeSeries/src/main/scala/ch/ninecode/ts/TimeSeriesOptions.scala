package ch.ninecode.ts

import ch.ninecode.ts.Main.LogLevels
import ch.ninecode.ts.Main.LogLevels.LogLevels

case class TimeSeriesOptions
(
    /**
     * If <code>true</code>, don't call sys.exit().
     */
    unittest: Boolean = false,

    /**
     * If <code>true</code>, emit progress messages.
     */
    verbose: Boolean = false,

    /**
     * Spark master.
     */
    master: String = "",

    /**
     * Spark options.
     */
    options: Map[String, String] = Map (),

    /**
     * Cassandra connection host.
     */
    host: String = "localhost",

    /**
     * Storage level for RDD serialization.
     */
    storage: String = "MEMORY_AND_DISK_SER",

    /**
     * Logging level.
     */
    log_level: LogLevels = LogLevels.OFF,

    /**
     * Cassandra keyspace
     */
    keyspace: String = "cimapplication"
)