package ch.ninecode.sim

import ch.ninecode.cim.CIMTopologyOptions
import ch.ninecode.util.CIMAble
import ch.ninecode.util.CIMReaderOptions
import ch.ninecode.util.CassandraOptions
import ch.ninecode.util.Cassandraable
import ch.ninecode.util.JSON
import ch.ninecode.util.JSONAble
import ch.ninecode.util.JSONCustomSerializer
import ch.ninecode.util.MainOptions
import ch.ninecode.util.Mainable
import ch.ninecode.util.SparkOptions
import ch.ninecode.util.Sparkable

/**
 * Options for Simulation.
 *
 * @param main_options          main() program options
 * @param spark_options         Spark session options
 * @param cim_options           CIMReader options
 * @param cassandra_options     Cassandra options
 * @param verbose               If <code>true</code>, emit progress messages.
 * @param workdir               Working directory for executors.
 * @param three_phase           If <code>true</code>, simulate in three phase flag.
 * @param fake_three_phase      If <code>true</code>, convert single phase meter readings into three phase.
 * @param keep                  If <code>true</code>, keep glm and input/output files in workdir.
 * @param simulationonly        If <code>true</code>, only perform simulation, not postprocessing.
 * @param postprocessonly       If <code>true</code>, only perform postprocessing, not simulation.
 * @param cable_impedance_limit cables with a R1 value higher than this are not calculated with gridlab,
 *                              the reason is bad performance in gridlab with too high impedance values
 * @param simulation            Simulation jobs to process.
 */
case class SimulationOptions
(
    var main_options: MainOptions = MainOptions(),
    var spark_options: SparkOptions = SparkOptions(),
    var cim_options: CIMReaderOptions = CIMReaderOptions(
        topology = true,
        topology_options = CIMTopologyOptions(identify_islands = true)),
    var cassandra_options: CassandraOptions = CassandraOptions(),
    verbose: Boolean = false,
    workdir: String = "simulation/",
    three_phase: Boolean = false,
    fake_three_phase: Boolean = false,
    keep: Boolean = false,
    simulationonly: Boolean = false,
    postprocessonly: Boolean = false,
    cable_impedance_limit: Double = 5.0,
    simulation: Seq[SimulationJob] = Seq()
) extends Mainable with Sparkable with CIMAble with Cassandraable with JSONAble[SimulationOptions]
{
    /**
     * Output equivalent JSON options.
     */
    override def toJSON: String = SimulationOptions.toJSON(this)

    /**
     * Create one of these option objects from JSON.
     *
     * @param text the JSON text
     * @return either an error message in Left or the options instance in Right
     */
    override def fromJSON (text: String): Either[String, SimulationOptions] = SimulationOptions.fromJSON(text)
}
object SimulationOptions extends JSON[SimulationOptions]
{
    /**
     * The name of the resource containing the JSON schema for the options.
     *
     * @return a resource name string for use by ClassLoader.getResourceAsStream
     */
    override def schemaResourceName: String = "SimulationOptionsSchema.json"

    /**
     * The mapping from URI in the schema to local URI.
     *
     * @return The map from global URI to local URI
     */
    override def schemaUriMap: Map[String, String] = Map[String,String](
        "https://raw.githubusercontent.com/derrickoswald/CIMApplication/master/json-schema/SimulationOptionsSchema.json" -> "resource:SimulationOptionsSchema.json",
        "https://raw.githubusercontent.com/derrickoswald/CIMApplication/master/json-schema/SimulationJobSchema.json" -> "resource:SimulationJobSchema.json"
    ) ++ MainOptions.schemaUriMap ++ SparkOptions.schemaUriMap ++ CIMReaderOptions.schemaUriMap ++ CassandraOptions.schemaUriMap

    /**
     * The list of custom serializers for the options.
     */
    override def customSerializers: Seq[JSONCustomSerializer[_]] = List.concat(
        MainOptions.customSerializers,
        SparkOptions.customSerializers,
        CIMReaderOptions.customSerializers,
        CassandraOptions.customSerializers,
        SimulationJob.customSerializers)
}