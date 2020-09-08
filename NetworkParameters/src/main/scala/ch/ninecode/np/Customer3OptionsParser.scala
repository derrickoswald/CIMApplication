package ch.ninecode.np

/**
 * Parser for Customer3_NetworkParameters command line operation.
 */
@SuppressWarnings (Array ("org.wartremover.warts.NonUnitStatements"))
class Customer3OptionsParser (options: NetworkParametersOptions) extends NetworkParametersOptionsParser (options)
{
    opt[String]("csv")
        .valueName ("<CSV file>")
        .action ((x, c) => c.copy (available_power_csv = x))
        .text (s"csv file of available power at station data [${options.available_power_csv}]")
}