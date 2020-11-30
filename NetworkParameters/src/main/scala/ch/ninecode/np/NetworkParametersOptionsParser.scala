package ch.ninecode.np

import ch.ninecode.util.CIMReaderOptionsParser
import ch.ninecode.util.MainOptionsParser
import ch.ninecode.util.SparkOptionsParser

/**
 * Generic parser for command line operation.
 */
@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
class NetworkParametersOptionsParser (options: NetworkParametersOptions)
    extends MainOptionsParser[NetworkParametersOptions](options)
    with SparkOptionsParser[NetworkParametersOptions]
    with CIMReaderOptionsParser[NetworkParametersOptions]
{
    opt[Unit]("verbose")
        .action((_, c) => c.copy(verbose = true))
        .text(s"emit progress messages [${options.verbose}]")

    opt[String]("export")
        .valueName("<RDF file>")
        .action((x, c) => c.copy(export = x))
        .text(s"save processed RDF file as this name [${options.export}]")

    note("Creates EquivalentInjection objects for the network that is supplying transformers using an external data file.\n")
}