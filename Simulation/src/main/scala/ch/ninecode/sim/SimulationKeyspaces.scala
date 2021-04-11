package ch.ninecode.sim

import ch.ninecode.util.JSON
import ch.ninecode.util.JSONAble
import ch.ninecode.util.JSONCustomSerializer

/**
 * Cassandra keyspace definitions.
 *
 * @param input_keyspace         The Cassandra keyspace to read measured data from.
 *                               A table named measured_value with an appropriate schema (see simulation_schema.sql) is expected.
 * @param output_keyspace        The Cassandra keyspace to save results to.
 *                               A table named simulated_value with an appropriate schema (see simulation_schema.sql) and if summarization
 *                               operations are performed additional tables with appropriate schema are expected.
 * @param replication            The Cassandra keyspace replication.
 *                               The replication factor used in the <code>create keyspace if not exists</code> DDL, and hence
 *                               <em>used only if the <code>output_keyspace</code> is created.</em>
 */
case class SimulationKeyspaces (
    input_keyspace: String = "cimapplication",
    output_keyspace: String = "cimapplication",
    replication: Int = 1
) extends JSONAble[SimulationKeyspaces]
{
    /**
     * Output equivalent JSON options.
     */
    override def toJSON: String = SimulationKeyspaces.toJSON(this)

    /**
     * Create one of these option objects from JSON.
     *
     * @param text the JSON text
     * @return either an error message in Left or the options instance in Right
     */
    override def fromJSON (text: String): Either[String, SimulationKeyspaces] = SimulationKeyspaces.fromJSON(text)
}
object SimulationKeyspaces extends JSON[SimulationKeyspaces]
{
    /**
     * The name of the resource containing the JSON schema for the options.
     *
     * @return a resource name string for use by ClassLoader.getResourceAsStream
     */
    override def schemaResourceName: String = "SimulationKeyspaces.json"

    /**
     * The mapping from URI in the schema to local URI.
     *
     * @return The map from global URI to local URI
     */
    override def schemaUriMap: Map[String, String] = Map[String,String](
        "https://raw.githubusercontent.com/derrickoswald/CIMApplication/master/json-schema/SimulationKeyspacesSchema.json" -> "resource:SimulationKeyspacesSchema.json"
    )

    /**
     * The list of custom serializers for the options.
     */
    override def customSerializers: Seq[JSONCustomSerializer[_]] = Seq()
}