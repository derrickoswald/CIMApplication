package ch.ninecode.ingest

import ch.ninecode.ingest.Main.Formats
import ch.ninecode.ingest.Main.Formats.Formats
import ch.ninecode.ingest.Main.LogLevels
import ch.ninecode.ingest.Main.LogLevels.LogLevels

case class IngestOptions
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
     * Mapping CSV file name.
     */
    mapping: String = "",

    /**
     * Mapping CSV measurement identifier column name (column containing CH############################### values).
     */
    metercol: String = "Messpunktbezeichnung",

    /**
     * Mapping CSV mRID column name (column containing HAS##### values).
     */
    mridcol: String = "NISNr",

    /**
     * Time zone for the measurement files.
     */
    timezone: String = "Europe/Berlin",

    /**
     * Minimum time for ingestion timespan.
     * The default is the minimum representable time in the Linux epoc.
     */
    mintime: Long = 0L, // "1970-01-01 00:00:00.000+0000"

    /**
     * Maximum time for ingestion timespan.
     * The default is the maximum representable time in the Linux epoc.
     */
    maxtime: Long = Long.MaxValue, // "292278994-08-17 07:12:55.807+0000"

    /**
     * Type of data file, either Belvis or LPEx.
     */
    format: Formats = Formats.Belvis,

    /**
     * Source Belvis/LPEx files.
     */
    datafiles: Seq[String] = Seq (),

    /**
     * Cassandra keyspace
     */
    keyspace: String = "cimapplication",

    /**
     * Cassandra keyspace replication factor.
     */
    replication: Int = 2
)