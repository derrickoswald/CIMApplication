package ch.ninecode.sim

import java.io.StringReader

import javax.json.Json
import javax.json.JsonArray
import javax.json.JsonException
import javax.json.JsonObject
import javax.json.JsonString
import javax.json.JsonValue

import scala.collection.JavaConverters._
import scala.collection.mutable

import org.apache.spark.sql.SparkSession
import com.datastax.spark.connector._
import org.slf4j.Logger
import org.slf4j.LoggerFactory

case class SimulationJob
(
    name: String,
    description: String,
    cim: String,
    cimreaderoptions: Map[String, String],
    interval: Map[String, String],
    transformers: Seq[String],
    players: Seq[SimulationPlayerQuery],
    recorders: Seq[SimulationRecorderQuery],
    extras: Seq[SimulationExtraQuery]
)
{
    def optionString: String = cimreaderoptions.map (kv ⇒ kv._1 + "=" + kv._2).mkString (",")

    /**
     * Insert the simulation json into simulation table.
     *
     * @param session the Spark session to use
     * @param id the id to store this job under
     */
    def save (session: SparkSession, keyspace: String, id: String, task: SimulationTask): Unit =
    {
        val player_map =
            if (null != task)
                task.players.map (
                    player ⇒
                        Map (
                        "name" → player.name,
                        "mrid" → player.parent,
                        "typ" → player.typ,
                        "property" → player.property)
                        // "file" → player.file
                        // "sql" → player.sql
                        // "start" → iso_date_format.format (new Date (player.start))
                        // "end" → iso_date_format.format (new Date (player.end))
                ).toList
            else
                List()

        val recorder_map =
            if (null != task)
                task.recorders.map (
                    recorder ⇒
                        Map (
                            "name" → recorder.name,
                            "mrid" → recorder.mrid,
                            // "parent" → recorder.parent,
                            "typ" → recorder.typ,
                            "property" → recorder.property,
                            "unit" → recorder.unit,
                            // "file" → recorder.file,
                            "interval" → recorder.interval.toString)
                            // "aggregations" → recorder.aggregations.map (y ⇒ if (y.time_to_live == "") y.intervals.toString else y.intervals.toString + "@" + y.time_to_live.substring (y.time_to_live.lastIndexOf (" ") + 1)).mkString (",")
                ).toList
            else
                List()

        val json = session.sparkContext.parallelize (Seq (
            (
                id,
                cim,
                cimreaderoptions,
                description,
                interval,
                name,
                player_map,
                recorder_map,
                transformers
            )
        ))

        json.saveToCassandra (keyspace, "simulation")
    }
}

object SimulationJob
{
    val log: Logger = LoggerFactory.getLogger (getClass)

    def readJSON (json: String): Option[JsonObject] =
    {
        try
        {
            try
                Json.createReader (new StringReader (json)).readObject match
                {
                    case obj: JsonObject ⇒ Some (obj)
                    case _ ⇒
                        log.error ("""not a JsonObject""")
                        None
                }
            catch
            {
                case je: JsonException ⇒
                    log.error ("""unparseable as JSON (%s)""".format (je.getMessage))
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

    def parseCIMReaderOptions (options: SimulationOptions, cim: String, json: JsonObject): Map[String, String] =
    {
        val readeroptions: mutable.Map[String, JsonValue] = json.getJsonObject ("cimreaderoptions").asScala // ToDo: more robust checking
    val map = readeroptions.map (x ⇒ (x._1, x._2.toString))
        map ("path") = cim // add path to support multiple files
        map ("StorageLevel") = options.storage // add storage option from command line
        map.toMap
    }

    def parseInterval (json: JsonObject): Map[String, String] =
    {
        val interval: mutable.Map[String, JsonValue] = json.getJsonObject ("interval").asScala // ToDo: more robust checking
    val map: mutable.Map[String, String] = interval.map (x ⇒ (x._1, x._2.asInstanceOf [JsonString].getString))
        map.toMap
    }

    def parseTransformers (json: JsonObject): Seq[String] =
    {
        val transformers: JsonArray = json.getJsonArray ("transformers") // ToDo: more robust checking
    val ret = Array.ofDim [String](transformers.size)
        for (i <- 0 until transformers.size)
            ret (i) = transformers.getString (i)
        ret
    }

    def parsePlayer (name: String, player: JsonObject): List[SimulationPlayerQuery] =
    {
        val title = player.getString ("title", "")
        val query = player.getString ("query", null)
        if (null == query)
        {
            log.error (""""%s" does not specify an RDF query for player "%s""".format (name, title))
            List ()
        }
        else
        {
            val cassandraquery = player.getString ("cassandraquery", null)
            if (null == cassandraquery)
            {
                log.error (""""%s" does not specify a Cassandra query for player "%s"""".format (name, title))
                List ()
            }
            else
            {
                val binds = player.getJsonArray ("bind")
                val array = Array.ofDim [String](binds.size)
                for (i <- 0 until binds.size)
                    array (i) = binds.getJsonString (i).getString
                List (SimulationPlayerQuery (title, query, cassandraquery, array))
            }
        }
    }

    def parsePlayers (name: String, json: JsonObject): Seq[SimulationPlayerQuery] =
    {
        val players: Seq[JsonObject] = json.getJsonArray ("players").getValuesAs (classOf [JsonObject]).asScala // ToDo: more robust checking
        players.flatMap (parsePlayer (name, _))
    }

    def parseAggregation (ttl: JsonObject): List[SimulationAggregate] =
    {
        val intervals = ttl.getInt ("intervals")
        val time = if (ttl.isNull ("ttl"))
            ""
        else
            " using ttl " + ttl.getJsonNumber ("ttl").intValue
        List (SimulationAggregate (intervals, time))
    }

    def parseRecorder (name: String, recorder: JsonObject): List[SimulationRecorderQuery] =
    {
        val title = recorder.getString ("title", "")
        val query = recorder.getString ("query", null)
        val interval = recorder.getInt ("interval", 900)
        val array = recorder.getJsonArray ("aggregations").getValuesAs (classOf [JsonObject]).asScala
        val aggregations = array.flatMap (parseAggregation).toList
        if (null == query)
        {
            log.error (""""%s" does not specify a query for recorder "%s""".format (name, title))
            List ()
        }
        else
            List (SimulationRecorderQuery (title, query, interval, aggregations))
    }

    def parseRecorders (name: String, json: JsonObject): Seq[SimulationRecorderQuery] =
    {
        val recorders: Seq[JsonObject] = json.getJsonArray ("recorders").getValuesAs (classOf [JsonObject]).asScala // ToDo: more robust checking
        recorders.flatMap (parseRecorder (name, _))
    }

    def parseExtra (name: String, extra: JsonObject): List[SimulationExtraQuery] =
    {
        val title = extra.getString ("title", "")
        val query = extra.getString ("query", null)
        if (null == query)
        {
            log.error (""""%s" does not specify a query for extra "%s""".format (name, title))
            List ()
        }
        else
            List (SimulationExtraQuery (title, query))
    }

    def parseExtras (name: String, json: JsonObject): Seq[SimulationExtraQuery] =
    {
        val extras: Seq[JsonObject] = json.getJsonArray ("extras").getValuesAs (classOf [JsonObject]).asScala // ToDo: more robust checking
        extras.flatMap (parseExtra (name, _))
    }

    def parseJob (options: SimulationOptions, json: JsonObject): List[SimulationJob] =
    {

        val name = json.getString ("name", "")
        val description = json.getString ("description", "")
        val cim = json.getString ("cim", null)
        if (null == cim)
        {
            log.error (""""%s" does not specify a CIM file""".format (name))
            List ()
        }
        else
        {
            val cimreaderoptions = parseCIMReaderOptions (options, cim, json)
            val interval = parseInterval (json)
            val transformers = parseTransformers (json)
            val players = parsePlayers (name, json)
            val recorders = parseRecorders (name, json)
            val extras = parseExtras (name, json)
            List (SimulationJob (name, description, cim, cimreaderoptions, interval, transformers, players, recorders, extras))
        }
    }

    def getAll (options: SimulationOptions): Seq[SimulationJob] =
    {
        if (options.verbose)
            org.apache.log4j.LogManager.getLogger (getClass.getName).setLevel (org.apache.log4j.Level.INFO)
        val jsons = options.simulation.map (readJSON)
        if (!jsons.forall ({ case Some (_) ⇒ true case None ⇒ false }))
            log.warn ("""not all simulations will be processed""")

        jsons.flatMap ({ case Some (json) ⇒ parseJob (options, json) case None ⇒ List () })
    }
}
