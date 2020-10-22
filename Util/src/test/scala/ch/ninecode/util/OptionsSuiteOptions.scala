package ch.ninecode.util

/**
 * Sample options class for testing.
 *
 * @param main_options  main() program options
 * @param spark_options Spark session options
 * @param cim_options   CIMReader options
 * @param verbose       if <code>true</code> output informational messages
 */
case class OptionsSuiteOptions (
    var main_options: MainOptions = MainOptions(),
    var spark_options: SparkOptions = SparkOptions(),
    var cim_options: CIMReaderOptions = CIMReaderOptions(),
    verbose: Boolean = false
) extends Mainable with Sparkable with CIMAble
