package ch.ninecode.np

/**
 * Parser for Customer2_NetworkParameters command line operation.
 */
@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
class Customer2OptionsParser (options: NetworkParametersOptions) extends NetworkParametersOptionsParser(options)
{
    opt[String]("csv1")
        .valueName("<CSV file>")
        .action((x, c) => c.copy(available_power_csv = x))
        .text(s"csv file of available power at station data [${options.available_power_csv}]")

    opt[String]("csv2")
        .valueName("<CSV file>")
        .action((x, c) => c.copy(station_transformer_csv = x))
        .text(s"csv file of mapping between station and transformer [${options.station_transformer_csv}]")
}