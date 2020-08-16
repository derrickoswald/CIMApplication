package ch.ninecode.on

import ch.ninecode.util.CIMAble
import ch.ninecode.util.CIMReaderOptions
import ch.ninecode.util.MainOptions
import ch.ninecode.util.Mainable
import ch.ninecode.util.SparkOptions
import ch.ninecode.util.Sparkable

/**
 * Logging level enumeration.
 */
object LogLevels extends Enumeration
{
    type LogLevels = Value
    val ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN = Value

    def toLog4j (level: Value): org.apache.log4j.Level =
        level match
        {
            case ALL => org.apache.log4j.Level.ALL
            case DEBUG => org.apache.log4j.Level.DEBUG
            case ERROR => org.apache.log4j.Level.ERROR
            case FATAL => org.apache.log4j.Level.FATAL
            case INFO => org.apache.log4j.Level.INFO
            case OFF => org.apache.log4j.Level.ALL
            case TRACE => org.apache.log4j.Level.ALL
            case WARN => org.apache.log4j.Level.WARN
        }
}

/**
 * Options for the One-Of-N program.
 *
 * @param verbose          flag to output progress and interesting values
 * @param three            If <code>true</code> generate three phase GridLAB-D .glm files, else single phase .glm files.
 * @param base_temperature Temperature of elements in the input CIM file (°C).
 * @param temperature      Temperature at which to generate the GridLAB-D .glm files (°C).
 * @param workdir          Shared directory (HDFS or NFS share) for intermediate results.
 */
case class OneOfNOptions
(
    var main_options: MainOptions = MainOptions (),
    var spark_options: SparkOptions = SparkOptions (),
    var cim_options: CIMReaderOptions = CIMReaderOptions (),
    verbose: Boolean = false,
    three: Boolean = false,
    base_temperature: Double = 20.0,
    temperature: Double = 60.0,
    workdir: String = ""
) extends Mainable with Sparkable with CIMAble

