package ch.ninecode.sim

import ch.ninecode.util.JSON
import ch.ninecode.util.JSONAble
import ch.ninecode.util.JSONCustomSerializer

/**
 * Simulation temperatures.
 *
 * @param cim_temperature        The temperature of the elements in the CIM file (°C).
 * @param simulation_temperature The temperature at which the simulation is to be run (°C).
 */
case class SimulationTemperatures (
    cim_temperature: Double = 20.0,
    simulation_temperature: Double = 20.0,
) extends JSONAble[SimulationTemperatures]
{
    /**
     * Output equivalent JSON options.
     */
    override def toJSON: String = SimulationTemperatures.toJSON(this)

    /**
     * Create one of these option objects from JSON.
     *
     * @param text the JSON text
     * @return either an error message in Left or the options instance in Right
     */
    override def fromJSON (text: String): Either[String, SimulationTemperatures] = SimulationTemperatures.fromJSON(text)
}
object SimulationTemperatures extends JSON[SimulationTemperatures]
{
    /**
     * The name of the resource containing the JSON schema for the class.
     *
     * @return a resource name string for use by ClassLoader.getResourceAsStream
     */
    override def schemaResourceName: String = "SimulationTemperaturesSchema.json"

    /**
     * The mapping from URI in the schema to local URI.
     *
     * @return The map from global URI to local URI
     */
    override def schemaUriMap: Map[String, String] = Map[String,String](
        "https://raw.githubusercontent.com/derrickoswald/CIMApplication/master/json-schema/SimulationTemperaturesSchema.json" -> "resource:SimulationTemperaturesSchema.json"
    )

    /**
     * The list of custom serializers for the options.
     */
    override def customSerializers: Seq[JSONCustomSerializer[_]] = ???
}