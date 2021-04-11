package ch.ninecode.sim

import ch.ninecode.util.JSON
import ch.ninecode.util.JSONAble
import ch.ninecode.util.JSONCustomSerializer

/**
 * Queries to determine which elements to add recorders to.
 *
 * @param title        The name for the recorder query.
 * @param query        The query to determine what nodes or edges are recorded.
 *                     Must return name, mrid, parent, type, property, unit and island.
 * @param interval     The recording interval in seconds.
 * @param aggregations The list of aggregations.
 */
case class SimulationRecorderQuery
(
    title: String,
    query: String,
    interval: Int,
    aggregations: List[SimulationAggregate]
) extends JSONAble[SimulationRecorderQuery]
{
    /**
     * Output equivalent JSON options.
     */
    override def toJSON: String = SimulationRecorderQuery.toJSON(this)

    /**
     * Create one of these option objects from JSON.
     *
     * @param text the JSON text
     * @return either an error message in Left or the options instance in Right
     */
    override def fromJSON (text: String): Either[String, SimulationRecorderQuery] = SimulationRecorderQuery.fromJSON(text)
}

object SimulationRecorderQuery extends JSON[SimulationRecorderQuery]
{
    @SuppressWarnings(Array("org.wartremover.warts.Null"))
    def apply (title: String, queries: Seq[String], interval: Int, aggregations: List[SimulationAggregate]): SimulationRecorderQuery =
        SimulationRecorderQuery(title, queries.lastOption.orNull, interval, aggregations)

    /**
     * The name of the resource containing the JSON schema for the options.
     *
     * @return a resource name string for use by ClassLoader.getResourceAsStream
     */
    override def schemaResourceName: String = "SimulationRecorderQuerySchema.json"

    /**
     * The mapping from URI in the schema to local URI.
     *
     * @return The map from global URI to local URI
     */
    override def schemaUriMap: Map[String, String] = Map[String,String](
        "https://raw.githubusercontent.com/derrickoswald/CIMApplication/master/json-schema/SimulationRecorderQuerySchema.json" -> "resource:SimulationRecorderQuerySchema.json"
    ) ++ SimulationAggregate.schemaUriMap

    /**
     * The list of custom serializers for the options.
     */
    override def customSerializers: Seq[JSONCustomSerializer[_]] = Seq()
}
