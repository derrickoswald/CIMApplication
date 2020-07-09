package ch.ninecode.ingest

object LogLevels extends Enumeration
{
    type LogLevels = Value
    val ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN = Value
}

object Formats extends Enumeration
{
    type Formats = Value
    val Belvis, LPEx, MSCONS, Custom, Parquet = Value
}

object Modes extends Enumeration
{
    type Modes = Value
    val Overwrite, Append = Value
}

/**
 *
 * @param valid False if either help or version requested (i.e. don't proceed with execution).
 * @param unittest If <code>true</code>, don't call sys.exit().
 * @param verbose If <code>true</code>, emit progress messages.
 * @param master Spark master.
 * @param options Spark options.
 * @param host Cassandra connection host.
 * @param port Cassandra connection port.
 * @param storage Storage level for RDD serialization.
 * @param log_level Logging level.
 * @param nocopy If <code>true</code>, use the file names provided directly, without unzipping or transferring them to HDFS.
 * @param workdir Working directory for unzipping and copying if nocopy is <code>false</code>.
 * @param mapping Mapping CSV file or RDF file name.
 * @param metercol Mapping CSV measurement identifier column name (column containing CH############################### values).
 * @param mridcol Mapping CSV mRID column name (column containing HAS##### values).
 * @param timezone Time zone for the measurement files.
 * @param mintime Minimum time for ingestion timespan.
 *                The default is the minimum representable time in the Linux epoch.
 * @param maxtime Maximum time for ingestion timespan.
 *                The default is the maximum representable time in the Linux epoch.
 * @param format Type of data file, either Belvis, LPEx or MSCONS.
 * @param mode Ingest Mode: 'Overwrite' or 'Append'. Overwrite: overwrites existing values in database. Append: adds values to existing ones.
 * @param datafiles Source Belvis/LPEx/MSCONS files.
 * @param keyspace Cassandra keyspace.
 * @param replication Cassandra keyspace replication factor only if the keyspace is created.
 * @param aws_s3a_access_key The access key to log into AWS S3
 * @param aws_s3a_secret_key The secret key to log into AWS S3
 */
case class IngestOptions
(
    var valid: Boolean = true,
    unittest: Boolean = false,
    verbose: Boolean = false,
    master: String = "local[*]",
    options: Map[String, String] = Map (),
    host: String = "localhost",
    port: Int = 9042,
    storage: String = "MEMORY_AND_DISK_SER",
    log_level: LogLevels.Value = LogLevels.OFF,
    nocopy: Boolean = false,
    workdir: String = s"${IngestOptions.cwd}/work/",
    mapping: String = "",
    metercol: String = "Messpunktbezeichnung",
    mridcol: String = "NISNr",
    timezone: String = "Europe/Berlin",
    mintime: Long = 0L, // "1970-01-01 00:00:00.000+0000"
    maxtime: Long = Long.MaxValue, // "292278994-08-17 07:12:55.807+0000"
    format: Formats.Value = Formats.Belvis,
    mode: Modes.Value = Modes.Overwrite,
    datafiles: Seq[String] = Seq (),
    keyspace: String = "cimapplication",
    replication: Int = 1,
    aws_s3a_access_key: String = "",
    aws_s3a_secret_key: String = ""
)

object IngestOptions
{
    def cwd: String =
    {
        val pwd = new java.io.File(".").getCanonicalPath
        if (pwd.endsWith ("."))
            pwd.substring (0, pwd.length - 1)
        else
            pwd
    }
}
