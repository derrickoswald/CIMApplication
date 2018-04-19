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
import ch.ninecode.sim.SimulationOptions

/**
 * Simulate a glm file and the associated input_data.
 *
 * @param options the directives and JSON text of the simulation to run
 */
case class EstimationFunction (options: SimulationOptions) extends CIMWebFunction
{
    val log: Logger = LoggerFactory.getLogger (getClass)

    jars = Array (jarForObject (this), jarForObject (options), jarForObject (Cluster.builder), jarForObject (Json.createObjectBuilder))

    override def getReturnType: Return = Return.JSON

    override def executeJSON (spark: SparkSession): JsonStructure =
    {
        val cassandra = spark.sparkContext.getConf.get ("spark.cassandra.connection.host", options.host)
        val new_options = options.copy (host = cassandra)
        val sim = Simulation (spark, new_options)
        sim.run ()
        log.info ("simulation succeeded")
        new RESTfulJSONResult (OK, "gridlab simulation ran").getJSON
    }

    override def toString: String =
    {
        val sb = new StringBuilder (super.toString)
        sb.append (" is EstimationFunction (simulation = %s)".format (options.simulation.mkString ("\n\n")))
        sb.toString
    }
}
