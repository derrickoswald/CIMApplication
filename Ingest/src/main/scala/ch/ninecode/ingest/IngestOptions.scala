package ch.ninecode.ingest

object LogLevels extends Enumeration
{
    type LogLevels = Value
    val ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN = Value
}

/**
 * Ingest meter readings options.
 *
 * @param valid     False if either help or version requested (i.e. don't proceed with execution).
 * @param unittest  If <code>true</code>, don't call sys.exit().
 * @param verbose   If <code>true</code>, emit progress messages.
 * @param master    Spark master.
 * @param options   Spark options.
 * @param host      Cassandra connection host.
 * @param port      Cassandra connection port.
 * @param storage   Storage level for RDD serialization.
 * @param log_level Logging level.
 * @param workdir   Working directory for unzipping and copying if nocopy is <code>false</code>.
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
    workdir: String = s"${IngestOptions.cwd}/work/",
    ingestions: Seq[String] = Seq ()
)

object IngestOptions
{
    def cwd: String =
    {
        val pwd = new java.io.File (".").getCanonicalPath
        if (pwd.endsWith ("."))
            pwd.substring (0, pwd.length - 1)
        else
            pwd
    }
}
