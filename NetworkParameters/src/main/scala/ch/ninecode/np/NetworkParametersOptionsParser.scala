package ch.ninecode.np

import ch.ninecode.util.CIMReaderOptionsParser

/**
 * Generic parser for command line operation.
 */
@SuppressWarnings (Array ("org.wartremover.warts.NonUnitStatements"))
class NetworkParametersOptionsParser (options: NetworkParametersOptions) extends CIMReaderOptionsParser[NetworkParametersOptions](options)
{
    opt[Unit]("verbose")
        .action ((_, c) => c.copy (verbose = true))
        .text (s"emit progress messages [${options.verbose}]")

    opt[String]("export")
        .valueName ("<RDF file>")
        .action ((x, c) => c.copy (export = x))
        .text (s"save processed RDF file as this name [${options.export}]")
}