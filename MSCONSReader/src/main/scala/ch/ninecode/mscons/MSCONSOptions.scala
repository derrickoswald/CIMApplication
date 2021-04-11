package ch.ninecode.mscons

import org.json4s.Formats
import org.json4s.JsonAST.JString

import ch.ninecode.mscons.LogLevels.LogLevels
import ch.ninecode.util.JSON
import ch.ninecode.util.JSONAble
import ch.ninecode.util.JSONCustomSerializer
import ch.ninecode.util.MainOptions
import ch.ninecode.util.Mainable

object LogLevels extends Enumeration
{
    type LogLevels = Value
    val ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN = Value
}

/**
 * Options for MSCONS processing.
 *
 * @param main_options main() program options
 * @param verbose      if <code>true</code>, emit progress messages
 * @param log_level    logging level
 * @param output_file  name of the output file to write
 * @param delimiter    the character to insert between record fields
 * @param mscons       MSCONS files
 */
case class MSCONSOptions
(
    var main_options: MainOptions = MainOptions(),
    verbose: Boolean = false,
    log_level: LogLevels.Value = LogLevels.OFF,
    output_file: String = "",
    delimiter: String = " ",
    mscons: Seq[String] = Seq()
) extends Mainable with JSONAble[MSCONSOptions]
{
    def toJSON: String = MSCONSOptions.toJSON(this)

    def fromJSON (text: String): Either[String, MSCONSOptions] = MSCONSOptions.fromJSON(text)
}
object MSCONSOptions extends JSON[MSCONSOptions]
{
    def schemaResourceName: String = "MSCONSOptionsSchema.json"
    def schemaUriMap: Map[String,String] = Map[String,String](
        "https://raw.githubusercontent.com/derrickoswald/CIMApplication/master/json-schema/MSCONSOptionsSchema.json" -> "resource:MSCONSOptionsSchema.json"
    ) ++ MainOptions.schemaUriMap

    class LogLevelsSerializer extends JSONCustomSerializer[LogLevels](
        (format: Formats) =>
            (
                {
                    case JString(s) => LogLevels.withName(s)
                },
                {
                    case x: LogLevels => JString(x.toString)
                }
            )
    )

    def customSerializers: Seq[JSONCustomSerializer[_]] =
        MainOptions.customSerializers :+ new LogLevelsSerializer
}
