package ch.ninecode.util

import org.apache.log4j.Level

/**
 * Options for Spark context.
 *
 * @param master Spark master
 * @param options Spark options
 * @param log logging level
 * @param checkpoint checkpoint directory
 */
case class SparkOptions (
    master: String = "local[*]",
    options: Map[String, String] = Map (
        "spark.graphx.pregel.checkpointInterval" -> "8",
        "spark.serializer"                       -> "org.apache.spark.serializer.KryoSerializer",
        "spark.ui.showConsoleProgress"           -> "false",
        "spark.sql.debug.maxToStringFields"      -> "250"
    ),
    log: Level = Level.OFF,
    jars: Set[String] = Set (),
    checkpoint: String = ""
)
{
    /**
     * Convert log level to a string.
     *
     * @return a String that would generate log from Level.toLevel
     */
    def logAsString: String =
    {
        log match
        {
            case Level.OFF => "OFF"
            case Level.FATAL => "FATAL"
            case Level.ERROR => "ERROR"
            case Level.WARN => "WARN"
            case Level.INFO => "INFO"
            case Level.DEBUG => "DEBUG"
            case Level.TRACE => "TRACE"
            case Level.ALL => "ALL"
            case _ => ""
        }
    }
}