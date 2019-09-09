package ch.ninecode.ingest

object LogLevels extends Enumeration
{
    type LogLevels = Value
    val ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN = Value
}

object Formats extends Enumeration
{
    type Formats = Value
    val Belvis, LPEx = Value
}

case class IngestOptions
(
    /**
     * False if either help or version requested (i.e. don't proceed with execution).
     */
    var valid: Boolean = true,

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
     * Cassandra connection port.
     */
    port: Int = 9042,

    /**
     * Storage level for RDD serialization.
     */
    storage: String = "MEMORY_AND_DISK_SER",

    /**
     * Logging level.
     */
    log_level: LogLevels.Value = LogLevels.OFF,

    /**
     * If <code>true</code>, use the file names provided directly, without unzipping or transferring them to HDFS.
     */
    nocopy: Boolean = false,

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
    format: Formats.Value = Formats.Belvis,

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
    replication: Int = 1
)