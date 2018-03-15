package ch.ninecode.sim

import java.io.StringReader

import javax.json.Json
import javax.json.JsonException
import javax.json.JsonObject

import org.apache.spark.sql.SparkSession
import org.slf4j.Logger
import org.slf4j.LoggerFactory

case class Simulation (session: SparkSession, options: SimulationOptions)
{
    if (options.verbose)
        org.apache.log4j.LogManager.getLogger (getClass.getName).setLevel (org.apache.log4j.Level.INFO)
    val log: Logger = LoggerFactory.getLogger (getClass)

    def execute (job: SimulationJob): Unit =
    {
        log.info ("""executing simulation job "%s"""".format (job.name))
    }

    def run (): Unit =
    {
        var jobs: Seq[SimulationJob] = SimulationJob.getAll (options)
        jobs.foreach (execute)
    }
}

object Simulation
{
    /**
     * The list of classes that can be persisted in RDD.
     */
    lazy val classes: Array[Class[_]] =
    {
        Array (
            classOf[ch.ninecode.sim.Simulation],
            classOf[ch.ninecode.sim.SimulationOptions],
            classOf[ch.ninecode.sim.SimulationJob],
            classOf[ch.ninecode.sim.SimulationPlayer],
            classOf[ch.ninecode.sim.SimulationRecorder]
        )
    }
}
