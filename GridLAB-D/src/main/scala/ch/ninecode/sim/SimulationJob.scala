package ch.ninecode.sim

import java.io.StringReader

import javax.json.Json
import javax.json.JsonArray
import javax.json.JsonException
import javax.json.JsonObject

import scala.collection.JavaConverters._

import org.slf4j.Logger
import org.slf4j.LoggerFactory

case class SimulationJob
(
    json: String,
    name: String,
    description: String,
    cim: String,
    transformers: Seq[String],
    players: Seq[SimulationPlayer],
    recorders: Seq[SimulationRecorder]
)
{

}

object SimulationJob
{
    def readJSON (log: Logger, simulation: String): Option[(String, JsonObject)] =
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
                    case obj: JsonObject ⇒ Some ((file, obj))
                    case _ ⇒
                        log.error (""""%s" does not contain a JsonObject""".format (simulation))
                        None
                }
            catch
            {
                case je: JsonException ⇒
                    log.error (""""%s" could not be parsed as JSON (%s)""".format (simulation, je.getMessage))
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

    def parseTransformers (log: Logger, simulation: String, json: JsonObject): Seq[String] =
    {
        val transformers: JsonArray = json.getJsonArray ("transformers") // ToDo: more robust checking
        val ret = Array.ofDim[String](transformers.size)
        for (i <- 0 until transformers.size)
            ret(i) = transformers.getString (i)
        ret
    }

    def parsePlayer (log: Logger, simulation: String, recorder: JsonObject): List[SimulationPlayer] =
    {
        val title = recorder.getString ("title", "")
        var rdfquery = recorder.getString ("rdfquery", null)
        if (null == rdfquery)
        {
            log.error (""""%s" does not specify an RDF query for player "%s""".format (simulation, title))
            List()
        }
        else
        {
            var cassandraquery = recorder.getString ("cassandraquery", null)
            if (null == cassandraquery)
            {
                log.error (""""%s" does not specify a Cassandra query for player "%s"""".format (simulation, title))
                List()
            }
            else
                List (SimulationPlayer (title, rdfquery, cassandraquery))
        }
    }

    def parsePlayers (log: Logger, simulation: String, json: JsonObject): Seq[SimulationPlayer] =
    {
        val players: Seq[JsonObject] = json.getJsonArray ("players").getValuesAs (classOf [JsonObject]).asScala // ToDo: more robust checking
        players.flatMap (parsePlayer (log, simulation, _))
    }

    def parseRecorder (log: Logger, simulation: String, recorder: JsonObject): List[SimulationRecorder] =
    {
        val title = recorder.getString ("title", "")
        var query = recorder.getString ("query", null)
        if (null == query)
        {
            log.error (""""%s" does not specify a query for recorder "%s""".format (simulation, title))
            List()
        }
        else
            List (SimulationRecorder (title, query))
    }

    def parseRecorders (log: Logger, simulation: String, json: JsonObject): Seq[SimulationRecorder] =
    {
        val recorders: Seq[JsonObject] = json.getJsonArray ("recorders").getValuesAs (classOf [JsonObject]).asScala // ToDo: more robust checking
        recorders.flatMap (parseRecorder (log, simulation, _))
    }

    def parseJob (log: Logger, simulation: String, json: JsonObject): List[SimulationJob] =
    {

        var name = json.getString ("name", "")
        var description = json.getString ("description", "")
        var cim = json.getString ("cim", null)
        if (null == cim)
        {
            log.error (""""%s" does not specify a CIM file""".format (simulation))
            List ()
        }
        else
        {
            val transformers = parseTransformers (log, simulation, json)
            val players = parsePlayers (log, simulation, json)
            val recorders = parseRecorders (log, simulation, json)
            List (SimulationJob (simulation, name, description, cim, transformers, players, recorders))
        }
    }

    def getAll (options: SimulationOptions): Seq[SimulationJob] =
    {
        if (options.verbose)
            org.apache.log4j.LogManager.getLogger (getClass.getName).setLevel (org.apache.log4j.Level.INFO)
        val log: Logger = LoggerFactory.getLogger (getClass)

        var jsons = options.simulation.map (readJSON (log, _))
        if (!jsons.forall ({ case Some (_) ⇒ true case None ⇒ false }))
            log.info ("""not all simulations will be processed""")

        jsons.flatMap ({ case Some (pair) ⇒ parseJob (log, pair._1, pair._2) case None ⇒ List () })
    }
}