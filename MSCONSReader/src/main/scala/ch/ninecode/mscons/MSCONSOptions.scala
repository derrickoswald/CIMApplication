package ch.ninecode.mscons

object LogLevels extends Enumeration
{
    type LogLevels = Value
    val ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN = Value
}

/**
 * Options for MSCONS processing.
 *
 * @param valid False if either help or version requested (i.e. don't proceed with execution).
 * @param unittest If <code>true</code>, don't call sys.exit().
 * @param verbose If <code>true</code>, emit progress messages.
 * @param log_level Logging level.
 * @param output_file The name of the output file to write.
 * @param delimiter The character to insert between record fields.
 * @param mscons MSCONS files.
 */
case class MSCONSOptions
(
    var valid: Boolean = true,
    unittest: Boolean = false,
    verbose: Boolean = false,
    log_level: LogLevels.Value = LogLevels.OFF,
    output_file: String = null,
    delimiter: String = " ",
    mscons: Seq[String] = Seq ()
)
