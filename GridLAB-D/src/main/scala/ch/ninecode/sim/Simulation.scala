package ch.ninecode.sim

import org.apache.spark.sql.SparkSession
import org.slf4j.Logger
import org.slf4j.LoggerFactory

case class Simulation (session: SparkSession, options: SimulationOptions)
{
    if (options.verbose)
        org.apache.log4j.LogManager.getLogger (getClass.getName).setLevel (org.apache.log4j.Level.INFO)
    val log: Logger = LoggerFactory.getLogger (getClass)

    def execute (simulation: String): Boolean =
    {
        log.info ("executing simulation %s".format (simulation))
        true
    }

    def run (): Unit =
    {
        if (options.simulation.forall (execute))
            log.info ("all simulations succeeded")
        else
            log.info ("not all simulations succeeded")
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

        )
    }
}
