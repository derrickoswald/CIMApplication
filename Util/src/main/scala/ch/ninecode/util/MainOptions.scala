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
    version: String = "2.12-3.0.1-3.0.5",
    valid: Boolean = true,
    unittest: Boolean = false,
)
{
    def toJSON: String = MainOptions.toJSON(this)
}
object MainOptions extends JSON[MainOptions]
{
    def schemaResourceName: String = "MainOptionsSchema.json"
}