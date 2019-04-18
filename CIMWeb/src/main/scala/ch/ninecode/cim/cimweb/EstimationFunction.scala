package ch.ninecode.cim.cimweb

import javax.json.Json
import javax.json.JsonStructure

import com.datastax.driver.core.Cluster
import org.apache.spark.sql.SparkSession
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.cim.cimweb.RESTfulJSONResult.OK
import ch.ninecode.cim.connector.CIMFunction.Return
import ch.ninecode.sim.Simulation
import ch.ninecode.sim.SimulationEvents
import ch.ninecode.sim.SimulationOptions
import ch.ninecode.sim.Summarize

/**
 * Simulate a glm file and the associated input_data.
 *
 * @param options the directives and JSON text of the simulation to run
 */
case class EstimationFunction (options: SimulationOptions) extends CIMWebFunction
{
    val log: Logger = LoggerFactory.getLogger (getClass)

    jars = Array (
        jarForObject (this),
        jarForObject (options),                           // Simulate.jar
        jarForObject (ch.ninecode.gl.Complex (0.0, 0.0)), // GridLabD.jar
        jarForObject (Cluster.builder),                   // spark-cassandra-connector.jar
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
        val new_options = options.copy (host = cassandra)
        val sim = Simulation (spark, new_options)
        val runs = sim.run ()
        log.info ("""simulation%s %s""".format (if (runs.size > 1) "s" else "", runs.mkString (",")))
        val result = Json.createObjectBuilder
        val simulations = Json.createArrayBuilder
        for (run ← runs)
            simulations.add (run)
        result.add ("simulations", simulations)
        if (options.summarize)
        {
            val sum = Summarize (spark, options)
            sum.run (runs)
            log.info ("""summarized %s""".format (runs.mkString (",")))
        }
        result.add ("summary", options.summarize)
        if (options.events)
        {
            val events = SimulationEvents (spark, options)
            events.run (runs)
            log.info ("""event detected %s""".format (runs.mkString (",")))
        }
        result.add ("events", options.events)
        RESTfulJSONResult (OK, """GridLAB-D simulation%s successful""".format (if (runs.size > 1) "s" else ""), result.build).getJSON
    }

    override def toString: String =
    {
        val sb = new StringBuilder (super.toString)
        sb.append (" is EstimationFunction (simulation = %s)".format (options.simulation.mkString ("\n\n")))
        sb.toString
    }
}
