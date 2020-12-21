package ch.ninecode.util

import java.io.IOException
import java.io.Serializable
import java.util

import scala.collection.convert.ImplicitConversions.`collection AsScalaIterable`
import scala.collection.convert.ImplicitConversions.`map AsScala`
import scala.io.Source

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.networknt.schema.SpecVersionDetector
import com.networknt.schema.ValidationMessage
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization.writePretty
import org.json4s.Formats
import org.json4s.NoTypeHints
import org.json4s.JValue
import org.json4s.jackson.JsonMethods.fromJsonNode

/**
 * Add JSON I/O to an options case class.
 *
 * @tparam T the options case class type
 */
trait JSON[T <: AnyRef with Product with Serializable] extends Using
{
    lazy val mapper: ObjectMapper = new ObjectMapper()

    @throws[IOException]
    def getJsonNodeFromStringContent (content: String): JsonNode =
        mapper.readTree(content)

    def getJsonSchemaFromClasspath (name: String): JsonSchema =
    {
        val factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V4)
        val is = Thread.currentThread.getContextClassLoader.getResourceAsStream(name)
        factory.getSchema(is)
    }

    // Automatically detect version for given JsonNode
    def getJsonSchemaFromJsonNodeAutomaticVersion (jsonNode: JsonNode): JsonSchema =
    {
        val factory = JsonSchemaFactory.getInstance(SpecVersionDetector.detect(jsonNode))
        factory.getSchema(jsonNode)
    }

    /**
     * The name of the resource containing the JSON schema for the options.
     *
     * @return a resource name string for use by ClassLoader.getResourceAsStream
     */
    def schemaResourceName: String

    /**
     * Return the text of the JSON schema for the options.
     *
     * @return a string that embodies the schema for these options
     */
    def schemaText: String = using (Source.fromResource(schemaResourceName)) { src => src.mkString }


    /**
     * Return the parsed schema for the options.
     *
     * @return the top level (object) node for the JSON schema of the options
     */
    def schema: JsonSchema = getJsonSchemaFromJsonNodeAutomaticVersion(getJsonNodeFromStringContent(schemaText))

    /**
     * Convert a validation error message into a string.
     *
     * @param error the validation error as produced by the Network New Technologies Inc. validator
     * @return a suitable string to post to the user
     */
    def errorAsString (error: ValidationMessage): String =
    {
        val details = if (null != error.getDetails) error.getDetails.map(p => s"${p._1},${p._2}") else ""
        s"${error.getType} ${error.getCode} ${error.getPath} ${error.getArguments.mkString("[", ",", "]")} $details ${error.getMessage}"
    }

    /**
     * Convert the given JSON text to an instance of the options.
     * @param json the string to parse into the object
     * @param serializers the custom serializers for the case class
     * @param m Scala voodoo to be able to extract the type
     * @return either an error message in Left or the options instance in Right
     */
    def fromJSON (json: String, serializers: Seq[JSONCustomSerializer[_]] = List())(implicit m: Manifest[T]): Either[String, T] =
    {
        val node: JsonNode = getJsonNodeFromStringContent(json)
        val errors: util.Set[ValidationMessage] = schema.validate(node)
        if (errors.isEmpty)
        {
            val json4s: JValue = fromJsonNode (node)
            val formats: Formats =  Serialization.formats(NoTypeHints) ++ serializers
            json4s.extractOpt[T](formats, m) match
            {
                case Some (options) => Right(options)
                case None =>
                    val error = serializers.foldLeft("")((error, ser) => error + ser.errors)
                    Left(if (error != "") error else s"${m.runtimeClass.getName} extract failed")
            }
        }
        else
            Left(errors.map(errorAsString).mkString ("\n"))
    }

    /**
     * Convert the given options instance into JSON.
     *
     * @param options the object to convert
     * @param serializers the custom serializers for the case class
     * @return the JSON corresponding to the object
     */
    def toJSON (options: T, serializers: Seq[JSONCustomSerializer[_]] = List()): String =
    {
        implicit val formats: AnyRef with Formats = Serialization.formats(NoTypeHints) ++ serializers
        writePretty (options)
    }
}
