package ch.ninecode.on

/**
 * Logging level enumeration.
 */
object LogLevels extends Enumeration
{
    type LogLevels = Value
    val ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN = Value
    def toLog4j (level: Value): org.apache.log4j.Level =
        level match {
            case ALL   => org.apache.log4j.Level.ALL
            case DEBUG => org.apache.log4j.Level.DEBUG
            case ERROR => org.apache.log4j.Level.ERROR
            case FATAL => org.apache.log4j.Level.FATAL
            case INFO  => org.apache.log4j.Level.INFO
            case OFF   => org.apache.log4j.Level.ALL
            case TRACE => org.apache.log4j.Level.ALL
            case WARN  => org.apache.log4j.Level.WARN
        }
}

/**
 * Options for the One-Of-N program.
 *
 * @param valid              False if either help or version requested (i.e. don't proceed with execution).
 * @param unittest           If <code>true</code>, don't call sys.exit().
 * @param verbose            flag to output progress and interesting values
 * @param master             Spark master.
 * @param options            Spark options.
 * @param cim_reader_options Options for the CIMReader.
 * @param dedup              Deduplicate input CIM files.
 * @param three              If <code>true</code> generate three phase GridLAB-D .glm files, else single phase .glm files.
 * @param base_temperature   Temperature of elements in the input CIM file (°C).
 * @param temperature        Temperature at which to generate the GridLAB-D .glm files (°C).
 * @param storage            Storage level for RDD serialization.
 * @param workdir            Shared directory (HDFS or NFS share) for intermediate results.
 * @param checkpoint_dir     Checkpoint directory on HDFS, e.g. hdfs://...
 * @param files              The list of files to process.
 */
case class OneOfNOptions
(
    var valid: Boolean = true,
    unittest: Boolean = false,
    verbose: Boolean = false,
    log_level: LogLevels.Value = LogLevels.OFF,
    master: String = "local[*]",
    options: Map[String, String] = Map (
        "spark.graphx.pregel.checkpointInterval" -> "8",
        "spark.serializer" -> "org.apache.spark.serializer.KryoSerializer",
        "spark.ui.showConsoleProgress" -> "false"
    ),
    cim_reader_options: Map[String, String] = Map (
    ),
    dedup: Boolean = false,
    three: Boolean = false,
    base_temperature: Double = 20.0,
    temperature: Double = 60.0,
    storage: String = "MEMORY_AND_DISK_SER",
    workdir: String = "",
    checkpoint_dir: String = "",
    files: Seq[String] = Seq ()
)
