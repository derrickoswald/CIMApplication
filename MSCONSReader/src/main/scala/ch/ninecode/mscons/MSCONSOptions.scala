package ch.ninecode.mscons

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
) extends Mainable
