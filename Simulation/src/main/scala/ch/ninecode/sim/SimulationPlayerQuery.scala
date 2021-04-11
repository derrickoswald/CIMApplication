package ch.ninecode.sim

import ch.ninecode.util.JSON
import ch.ninecode.util.JSONAble
import ch.ninecode.util.JSONCustomSerializer

/**
 * Queries to determine which elements to add players to.
 *
 * @param title     The name for the player query.
 * @param query     The Spark query to determine what nodes or edges are played.
 *                  Must return mrid, name, parent, type, property, unit and island, and possibly synthesis if querying synthesized_value.
 * @param transform The transformation from meter data (from Cassandra) to the value to be applied during simulation.
 *                  Code that can be compiled to an instance of type MeasurementTransform.
 */
case class SimulationPlayerQuery
(
    title: String,
    query: String,
    transform: String
) extends JSONAble[SimulationPlayerQuery]
{
    /**
     * Output equivalent JSON options.
     */
    override def toJSON: String = SimulationPlayerQuery.toJSON(this)

    /**
     * Create one of these option objects from JSON.
     *
     * @param text the JSON text
     * @return either an error message in Left or the options instance in Right
     */
    override def fromJSON (text: String): Either[String, SimulationPlayerQuery] = SimulationPlayerQuery.fromJSON(text)
}

object SimulationPlayerQuery extends JSON[SimulationPlayerQuery]
{
    @SuppressWarnings(Array("org.wartremover.warts.Null"))
    def apply (title: String, queries: Seq[String], transform: Option[String]): SimulationPlayerQuery =
        SimulationPlayerQuery(title, queries.lastOption.orNull, transform.orNull)

    /**
     * The name of the resource containing the JSON schema for the options.
     *
     * @return a resource name string for use by ClassLoader.getResourceAsStream
     */
    override def schemaResourceName: String = "SimulationPlayerQuerySchema.json"

    /**
     * The mapping from URI in the schema to local URI.
     *
     * @return The map from global URI to local URI
     */
    override def schemaUriMap: Map[String, String] = Map[String,String](
        "https://raw.githubusercontent.com/derrickoswald/CIMApplication/master/json-schema/SimulationPlayerQuerySchema.json" -> "resource:SimulationPlayerQuerySchema.json"
    )

    /**
     * The list of custom serializers for the options.
     */
    override def customSerializers: Seq[JSONCustomSerializer[_]] = Seq()
}
