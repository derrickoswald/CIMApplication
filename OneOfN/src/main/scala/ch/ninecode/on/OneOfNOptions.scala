package ch.ninecode.on

import ch.ninecode.util.CIMAble
import ch.ninecode.util.CIMReaderOptions
import ch.ninecode.util.JSON
import ch.ninecode.util.JSONAble
import ch.ninecode.util.JSONCustomSerializer
import ch.ninecode.util.MainOptions
import ch.ninecode.util.Mainable
import ch.ninecode.util.SparkOptions
import ch.ninecode.util.Sparkable

/**
 * Options for the One-Of-N program.
 *
 * @param verbose          flag to output progress and interesting values
 * @param three            If <code>true</code> generate three phase GridLAB-D .glm files, else single phase .glm files.
 * @param base_temperature Temperature of elements in the input CIM file (°C).
 * @param temperature      Temperature at which to generate the GridLAB-D .glm files (°C).
 * @param workdir          Shared directory (HDFS or NFS share) for intermediate results.
 */
case class OneOfNOptions
(
    var main_options: MainOptions = MainOptions(),
    var spark_options: SparkOptions = SparkOptions(),
    var cim_options: CIMReaderOptions = CIMReaderOptions(),
    verbose: Boolean = false,
    three: Boolean = false,
    base_temperature: Double = 20.0,
    temperature: Double = 60.0,
    workdir: String = ""
) extends Mainable with JSONAble[OneOfNOptions] with Sparkable with CIMAble
{
    /**
     * Output equivalent JSON options.
     */
    override def toJSON: String = OneOfNOptions.toJSON(this)

    /**
     * Create one of these option objects from JSON.
     *
     * @param text the JSON text
     * @return either an error message in Left or the options instance in Right
     */
    override def fromJSON (text: String): Either[String, OneOfNOptions] = OneOfNOptions.fromJSON(text)
}
object OneOfNOptions extends JSON[OneOfNOptions]
{
    /**
     * The name of the resource containing the JSON schema for the options.
     *
     * @return a resource name string for use by ClassLoader.getResourceAsStream
     */
    override def schemaResourceName: String = "OneOfNOptionsSchema.json"

    /**
     * The mapping from URI in the schema to local URI.
     *
     * @return The map from global URI to local URI
     */
    override def schemaUriMap: Map[String, String] = Map[String,String](
        "https://raw.githubusercontent.com/derrickoswald/CIMApplication/master/json-schema/OneOfNOptionsSchema.json" -> "resource:OneOfNOptionsSchema.json"
    ) ++ MainOptions.schemaUriMap ++ SparkOptions.schemaUriMap ++ CIMReaderOptions.schemaUriMap

    /**
     * The list of custom serializers for the options.
     */
    override def customSerializers: Seq[JSONCustomSerializer[_]] = List.concat(
        MainOptions.customSerializers,
        SparkOptions.customSerializers,
        CIMReaderOptions.customSerializers)
}
