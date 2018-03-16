package ch.ninecode.sim

import java.io.StringReader
import java.io.StringWriter
import java.util
import javax.json.Json
import javax.json.JsonArray
import javax.json.JsonException
import javax.json.JsonObject
import javax.json.stream.JsonGenerator

import scala.collection.JavaConverters._

import org.apache.spark.sql.SparkSession
import org.slf4j.Logger
import org.slf4j.LoggerFactory

case class Simulation (session: SparkSession, options: SimulationOptions)
{
    if (options.verbose)
        org.apache.log4j.LogManager.getLogger (getClass.getName).setLevel (org.apache.log4j.Level.INFO)
    val log: Logger = LoggerFactory.getLogger (getClass)

    def read (rdf: String, reader_options: Map[String,String])
    {
        log.info ("""reading "%s"""".format (rdf))
        val start = System.nanoTime ()
        val elements = session.read.format ("ch.ninecode.cim").options (reader_options).load (rdf)
        log.info (elements.count () + " elements")
        val read = System.nanoTime ()
        log.info ("read: " + (read - start) / 1e9 + " seconds")
    }

    def dump (obj: JsonObject): Unit =
    {
        val o = obj.asScala
        val strings = o.map (x ⇒ x._1 + "=" + x._2.toString)
        log.info (strings.mkString (" "))
    }

    def stringify (resultset: Array[JsonObject]): String =
    {
        val array = Json.createArrayBuilder
        for (i ← resultset.indices)
            array.add (resultset(i))
        val string = new StringWriter
        val properties = new util.HashMap[String, AnyRef](1)
        properties.put (JsonGenerator.PRETTY_PRINTING, "true")
        val writer = Json.createWriterFactory (properties).createWriter (string)
        writer.write (array.build)
        writer.close ()
        string.toString
    }

    def destringify (string: String): Seq[JsonObject] =
    {
        try
            Json.createReader (new StringReader (string)).readArray match
            {
                case obj: JsonArray ⇒
                    obj.getValuesAs (classOf [JsonObject]).asScala
                case _ ⇒
                    log.error ("""not a JsonArray""")
                    Seq()
            }
        catch
        {
            case je: JsonException ⇒
                log.error (""" string could not be parsed as JSON (%s)""".format (je.getMessage))
                Seq()
        }
    }

    def input (player: SimulationPlayer): SimulationPlayer =
    {
        log.info ("""executing "%s" as %s""".format (player.title, player.rdfquery))
        val query = SimulationSparkQuery (session, player.rdfquery)
        val resultset = query.execute ()
        player.copy (jsons = stringify (resultset))
    }

    def output (recorder: SimulationRecorder): SimulationRecorder =
    {
        log.info ("""executing "%s" as %s""".format (recorder.title, recorder.query))
        val query = SimulationSparkQuery (session, recorder.query)
        val resultset = query.execute ()
        recorder.copy (jsons = stringify (resultset))
    }

    def query (job: SimulationJob): SimulationJob =
    {
        val players = job.players.map (input)
        val recorders = job.recorders.map (output)
        job.copy (players = players, recorders = recorders)
    }

    def execute (job: SimulationJob): Unit =
    {
        log.info ("""executing simulation job "%s"""".format (job.name))
        job.players.foreach (x ⇒ destringify (x.jsons).foreach (dump))
        job.recorders.foreach (x ⇒ destringify (x.jsons).foreach (dump))
    }

    def process (batch: Seq[SimulationJob]): Unit =
    {
        val ajob = batch.head // assumes that all jobs in a batch should have the same cluster state
        read (ajob.cim, ajob.cimreaderoptions)
        val newbatch = batch.map (query)
        val executors = Math.max (1, session.sparkContext.getExecutorMemoryStatus.keys.size - 1)
        val simulations = session.sparkContext.parallelize (newbatch, executors)
        simulations.foreach (execute)
    }

    def run (): Unit =
    {
        var jobs = SimulationJob.getAll (options)
        // organize by same RDF and same options
        var batches = jobs.groupBy (job ⇒ job.cim + job.optionString)
        batches.values.foreach (process)
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
            classOf[ch.ninecode.sim.SimulationJob],
            classOf[ch.ninecode.sim.SimulationOptions],
            classOf[ch.ninecode.sim.SimulationPlayer],
            classOf[ch.ninecode.sim.SimulationRecorder]
        )
    }
}
