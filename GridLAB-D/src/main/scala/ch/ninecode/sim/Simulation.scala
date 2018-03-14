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

    def readJSON (simulation: String): Option[JsonObject] =
    {
        // read the file
        try
        {
            val sep = System.getProperty ("file.separator")
            val file = new java.io.File(".").getCanonicalPath + (if (simulation.startsWith (sep)) simulation else sep + simulation)
            val text = scala.io.Source.fromFile (file, "UTF-8").mkString
            try
                Json.createReader (new StringReader (text)).readObject match
                {
                    case obj: JsonObject ⇒ Some (obj)
                    case _ ⇒
                        log.error ("%s does not contain a JsonObject".format (simulation))
                        None
                }
            catch
            {
                case je: JsonException ⇒
                    log.error ("%s could not be parsed as JSON (%s)".format (simulation, je.getMessage))
                    None
            }
        }
        catch
        {
            case e: Exception =>
                log.error (e.getMessage)
                None
        }
    }

    def execute (simulation: String): Boolean =
    {
        log.info ("executing simulation %s".format (simulation))
        readJSON (simulation) match
        {
            case Some (json) ⇒ true
            case None ⇒ false
        }
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
