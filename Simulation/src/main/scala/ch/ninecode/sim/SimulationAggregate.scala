package ch.ninecode.sim

import ch.ninecode.util.JSON
import ch.ninecode.util.JSONAble
import ch.ninecode.util.JSONCustomSerializer

case class SimulationAggregate
(
    /**
     * The number of periods to accumulate for the aggregation.
     *
     * For example, if the period is 15 minutes (900000 mSec) and the
     * aggregation is over one day, then intervals = 96 (60 * 60 * 24 / 15).
     *
     * The interval 1 (one) is special in that it sets the time-to-live for the baseline period data.
     * At a minimum there should be one aggregation with an interval of 1 (one).
     */
    intervals: Int,

    /**
     * The Cassandra time-to-live value.
     *
     * If non-zero the records saved for this aggregation will be assigned
     * this time-o-live (https://docs.datastax.com/en/dse/6.7/cql/cql/cql_using/useExpire.html).
     */
    ttl: Int
) extends JSONAble[SimulationAggregate]
{
    /**
     * Output equivalent JSON options.
     */
    override def toJSON: String = SimulationAggregate.toJSON(this)

    /**
     * Create one of these option objects from JSON.
     *
     * @param text the JSON text
     * @return either an error message in Left or the options instance in Right
     */
    override def fromJSON (text: String): Either[String, SimulationAggregate] = SimulationAggregate.fromJSON(text)
}
object SimulationAggregate extends JSON[SimulationAggregate]
{
    /**
     * The name of the resource containing the JSON schema for the options.
     *
     * @return a resource name string for use by ClassLoader.getResourceAsStream
     */
    override def schemaResourceName: String = "SimulationAggregateSchema.json"

    /**
     * The mapping from URI in the schema to local URI.
     *
     * @return The map from global URI to local URI
     */
    override def schemaUriMap: Map[String, String] = Map[String,String](
        "https://raw.githubusercontent.com/derrickoswald/CIMApplication/master/json-schema/SimulationAggregateSchema.json" -> "resource:SimulationAggregateSchema.json"
    )

    /**
     * The list of custom serializers for the options.
     */
    override def customSerializers: Seq[JSONCustomSerializer[_]] = Seq()
}
