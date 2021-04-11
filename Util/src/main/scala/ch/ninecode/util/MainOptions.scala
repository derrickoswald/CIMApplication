package ch.ninecode.util

/**
 * Standard main program options.
 *
 * @param application the name of the program
 * @param version     the version of the program (format: <scala>-<spark>-<program>)
 * @param valid       <code>false</code> if either help or version requested (i.e. don't proceed with execution)
 * @param unittest    if <code>true</code>, don't call sys.exit()
 */
case class MainOptions
(
    application: String = "SparkApplication",
    version: String = "2.12-3.0.1-3.0.6",
    valid: Boolean = true,
    unittest: Boolean = false,
) extends JSONAble[MainOptions]
{
    def toJSON: String = MainOptions.toJSON(this)
    def fromJSON (text: String): Either[String, MainOptions] = MainOptions.fromJSON(text)
}
object MainOptions extends JSON[MainOptions]
{
    def schemaResourceName: String = "MainOptionsSchema.json"
    def schemaUriMap: Map[String,String] = Map[String,String](
        "https://raw.githubusercontent.com/derrickoswald/CIMApplication/master/json-schema/MainOptionsSchema.json" -> "resource:MainOptionsSchema.json"
    )
    def customSerializers: Seq[JSONCustomSerializer[_]] = Seq()
}