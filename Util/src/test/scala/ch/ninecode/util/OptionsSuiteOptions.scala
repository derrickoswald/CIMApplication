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
) extends Mainable with Sparkable with CIMAble with JSONAble[OptionsSuiteOptions]
{
    def toJSON: String = OptionsSuiteOptions.toJSON(this)
    def fromJSON (text: String): Either[String, OptionsSuiteOptions] = OptionsSuiteOptions.fromJSON(text)
}
object OptionsSuiteOptions extends JSON[OptionsSuiteOptions]
{
    def schemaResourceName: String = "OptionsSuiteOptionsSchema.json"
    def schemaUriMap: Map[String,String] = Map[String,String](
        "https://raw.githubusercontent.com/derrickoswald/CIMApplication/master/json-schema/OptionsSuiteOptionsSchema.json" -> "resource:OptionsSuiteOptionsSchema.json"
    ) ++ MainOptions.schemaUriMap ++ SparkOptions.schemaUriMap ++ CIMReaderOptions.schemaUriMap
    def customSerializers: Seq[JSONCustomSerializer[_]] = Seq()
}