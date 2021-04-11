package ch.ninecode.sim

import ch.ninecode.util.JSON
import ch.ninecode.util.JSONAble
import ch.ninecode.util.JSONCustomSerializer

/**
 * Queries for extra property data.
 *
 * @param title The name for the extra query.
 *              This is used as part of the Cassandra primary key (in addition to the simulation id and the key field).
 * @param query The query to get key value pairs.
 *              Must return key and value.
 *              The simulation will be added automatically
 */
case class SimulationExtraQuery
(
    title: String,
    query: String
) extends JSONAble[SimulationExtraQuery]
{
    /**
     * Output equivalent JSON options.
     */
    override def toJSON: String = SimulationExtraQuery.toJSON(this)

    /**
     * Create one of these option objects from JSON.
     *
     * @param text the JSON text
     * @return either an error message in Left or the options instance in Right
     */
    override def fromJSON (text: String): Either[String, SimulationExtraQuery] = SimulationExtraQuery.fromJSON(text)
}

object SimulationExtraQuery extends JSON[SimulationExtraQuery]
{
    @SuppressWarnings(Array("org.wartremover.warts.Null"))
    def apply (title: String, queries: Seq[String]): SimulationExtraQuery =
        SimulationExtraQuery(title, queries.lastOption.orNull)

    /**
     * The name of the resource containing the JSON schema for the options.
     *
     * @return a resource name string for use by ClassLoader.getResourceAsStream
     */
    override def schemaResourceName: String = "SimulationExtraQuerySchema.json"

    /**
     * The mapping from URI in the schema to local URI.
     *
     * @return The map from global URI to local URI
     */
    override def schemaUriMap: Map[String, String] = Map[String,String](
        "https://raw.githubusercontent.com/derrickoswald/CIMApplication/master/json-schema/SimulationExtraQuerySchema.json" -> "resource:SimulationExtraQuerySchema.json"
    )

    /**
     * The list of custom serializers for the options.
     */
    override def customSerializers: Seq[JSONCustomSerializer[_]] = Seq()
}