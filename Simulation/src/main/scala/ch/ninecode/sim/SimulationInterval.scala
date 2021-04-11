package ch.ninecode.sim

import java.util.Calendar

import ch.ninecode.util.JSON
import ch.ninecode.util.JSONAble
import ch.ninecode.util.JSONCustomSerializer

/**
 * Time period over which to simulate.
 *
 * @param start_time The starting date and time of the simulation.
 * @param end_time   The ending date and time of the simulation.
 * @param buffer     The number of milliseconds of buffer either side of the start=>end interval to read from measured data.
 */
case class SimulationInterval (
    start_time: Calendar,
    end_time: Calendar,
    buffer: Int
) extends JSONAble[SimulationInterval]
{
    /**
     * Output equivalent JSON options.
     */
    override def toJSON: String = SimulationInterval.toJSON(this)

    /**
     * Create one of these option objects from JSON.
     *
     * @param text the JSON text
     * @return either an error message in Left or the options instance in Right
     */
    override def fromJSON (text: String): Either[String, SimulationInterval] = SimulationInterval.fromJSON(text)
}
object SimulationInterval extends JSON[SimulationInterval]
{
    /**
     * The name of the resource containing the JSON schema for the options.
     *
     * @return a resource name string for use by ClassLoader.getResourceAsStream
     */
    override def schemaResourceName: String = "SimulationIntervalSchema.json"

    /**
     * The mapping from URI in the schema to local URI.
     *
     * @return The map from global URI to local URI
     */
    override def schemaUriMap: Map[String, String] = Map[String,String](
        "https://raw.githubusercontent.com/derrickoswald/CIMApplication/master/json-schema/SimulationIntervalSchema.json" -> "resource:SimulationIntervalSchema.json"
    )

    /**
     * The list of custom serializers for the options.
     */
    override def customSerializers: Seq[JSONCustomSerializer[_]] = Seq()
}