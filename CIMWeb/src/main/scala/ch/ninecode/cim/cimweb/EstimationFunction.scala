package ch.ninecode.cim.cimweb

import javax.json.Json
import javax.json.JsonStructure
import com.datastax.driver.core.Cluster
import org.apache.spark.sql.SparkSession
import org.slf4j.LoggerFactory

import ch.ninecode.cim.cimweb.RESTfulJSONResult.OK
import ch.ninecode.cim.connector.CIMFunction.Return
import ch.ninecode.gl.GLMGenerator
import ch.ninecode.net.LoadFlowNode
import ch.ninecode.sim.Simulation
import ch.ninecode.sim.SimulationOptions
import ch.ninecode.util.Complex

/**
 * Simulate a glm file and the associated input_data.
 *
 * @param options the directives and JSON text of the simulation to run
 */
case class EstimationFunction (options: SimulationOptions) extends CIMWebFunction
{
    jars = Array (
        jarForObject (this),
        jarForObject (options),                           // Simulate.jar
        jarForObject (new GLMGenerator ()),               // GridLabD.jar
        jarForObject (new LoadFlowNode ("", 0.0)), // Net.jar
        jarForObject (Complex (0.0, 0.0)),                // Util.jar
        jarForObject (Cluster.builder),                   // spark-cassandra-connector.jar
        jarForObject (new com.twitter.jsr166e.LongAdder ()), // some Spark garbage
        jarForObject (Json.createObjectBuilder))          // javaee-api <JSON implementation>.jar

    override def getReturnType: Return = Return.JSON

    /**
     * Executes the simulation specified by options.
     *
     * @param spark The Spark session to use.
     * @return the primary keys of the simulation(s) saved in Cassandra,
     *         using these keys, the simulation(s) can be queried from Cassandra with something like:
     *         select json * from cimapplication.simulation where run='cecd5518-c871-4cd3-8d04-af5df830d602';
     */
    override def executeJSON (spark: SparkSession): JsonStructure =
    {
        val cassandra = spark.sparkContext.getConf.get ("spark.cassandra.connection.host", options.host)
        val sim = Simulation (spark, options.copy (host = cassandra))
        val runs = sim.run ()
        def plural: String = if (runs.size > 1) "s" else ""
        LoggerFactory.getLogger (getClass).info (s"""simulation$plural ${runs.mkString (",")}""")
        val result = Json.createObjectBuilder
        val simulations = Json.createArrayBuilder
        for (run <- runs)
            simulations.add (run)
        result.add ("simulations", simulations)
        RESTfulJSONResult (OK, s"""GridLAB-D simulation$plural successful""", result.build).getJSON
    }

    override def toString: String =
    {
        s"${super.toString} is EstimationFunction (simulation = ${options.simulation.mkString ("\n\n")})"
    }
}
