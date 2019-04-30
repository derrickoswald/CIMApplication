package ch.ninecode.sim

import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

import javax.json.Json
import javax.json.JsonArray
import javax.json.JsonException
import javax.json.JsonObject
import javax.json.JsonString
import javax.json.JsonValue

import scala.collection.JavaConverters._
import scala.collection.mutable

import com.datastax.spark.connector._
import org.apache.spark.sql.SparkSession
import org.apache.spark.rdd.RDD
import org.slf4j.Logger
import org.slf4j.LoggerFactory

case class SimulationJob
(
    /**
     * A user specified name for the simulation.
     *
     * This would be used in chart titles or graph labels when comparing scenarios.
     */
    name: String,

    /**
     * User specified description for the simulation.
     *
     * This would be a reminder to the user about why they did the simulation, i.e. a project name.
     */
    description: String,

    /**
     * The CIM files used in the simulation.
     *
     * If there is more than one file, e.g. an additional switch setting overlay file, then separate the file
     * names with commas. These are file system dependent names, e.g. hdfs://sandbox:8020/DemoData.rdf.
     */
    cim: String,

    /**
     * Options to pass to the CIMReader.
     *
     * Unless there is a topology already in the CIM file, this should include ch.ninecode.cim.do_topo_islands=true.
     */
    cimreaderoptions: Map[String, String],

    /**
     * The Cassandra keyspace to read measured data from.
     *
     * A table named measured_value with an appropriate schema (see simulation_schema.sql) is expected.
     */
    input_keyspace: String,

    /**
     * The Cassandra keyspace to save results to.
     *
     * A table named simulated_value with an appropriate schema (see simulation_schema.sql) and if summarization
     * operations are performed additional tables with appropriate schema are expected.
     */
    output_keyspace: String,

    /**
     * The starting time of the simulation.
     */
    start_time: Calendar,

    /**
     * The ending time of the simulation.
     */
    end_time: Calendar,

    /**
     * The name of the transformers to simulate.
     *
     * If this list is empty all transformers in the CIM file will be processed.
     * The names should reflect "ganged" transformers. For example, if TRA1234 and TRA1235 share a common
     * low voltage topological node, then the name would be "TRA1234_TRA1235".
     */
    transformers: Seq[String],

    /**
     * Queries for driving data.
     *
     * Queries against the Spark schema (https://github.com/derrickoswald/CIMReader/blob/master/Model.md)
     * yielding mrid, name, parent, type, property, unit and island for playing data.
     * An example for energy consumed by house connections:
     *
     * select
     *    c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid,
     *    'energy' type,
     *    concat(c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_load') name,
     *    t.TopologicalNode parent,
     *    'constant_power' property,
     *    'Watt' unit,
     *     n.TopologicalIsland island
     * from
     *     EnergyConsumer c,
     *     Terminal t,
     *     TopologicalNode n
     * where
     *     c.ConductingEquipment.Equipment.PowerSystemResource.PSRType == 'PSRType_HouseService' and
     *     c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID = t.ConductingEquipment and
     *     t.TopologicalNode = n.IdentifiedObject.mRID
     */
    players: Seq[SimulationPlayerQuery],

    /**
     * Queries for recording data and the recording interval and any aggregations.
     *
     * Queries against the Spark schema (https://github.com/derrickoswald/CIMReader/blob/master/Model.md)
     * yielding mrid, name, parent, type, property, unit and island for recording data.
     * An example for recording transformer losses:
     *
     * select
     *     concat (p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_losses_recorder') name,
     *     p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid,
     *     p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID parent,
     *     'losses' type,
     *     'power_losses' property,
     *     'VA' unit,
     *     n.TopologicalIsland island
     * from
     *     PowerTransformer p,
     *     Terminal t,
     *     TopologicalNode n
     * where
     *     t.ConductingEquipment = p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and
     *     t.ACDCTerminal.sequenceNumber > 1 and
     *     t.TopologicalNode = n.IdentifiedObject.mRID
     */
    recorders: Seq[SimulationRecorderQuery],

    /**
     * Queries for data to attach to the GeoJSON point, line and polygon features.
     *
     * Queries against the Spark schema (https://github.com/derrickoswald/CIMReader/blob/master/Model.md)
     * yielding key-value pairs. The key is the mRID of the object to attach the value to.
     * The query name is used as the key.
     * An example to attach the rated current to each conductor:
     *
     * select
     *     l.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID key,
     *     cast (w.ratedCurrent as string) value
     * from
     *     ACLineSegment l,
     *     WireInfo w
     * where
     *     w.AssetInfo.IdentifiedObject.mRID = l.Conductor.ConductingEquipment.Equipment.PowerSystemResource.AssetDatasheet
     *
     * If this query were named ratedCurrent, the GeoJSON line objects representing cables would have a property, e.g.:
     *     "ratedCurrent": "200.0"
     */
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
    def save (session: SparkSession, keyspace: String, id: String, tasks: RDD[SimulationTask]): Unit =
    {
        val json = session.sparkContext.parallelize (Seq (
            (
                id,
                name,
                description,
                cim,
                cimreaderoptions,
                start_time,
                end_time,
                input_keyspace,
                output_keyspace,
                transformers
            )
        ))
        json.saveToCassandra (keyspace, "simulation", SomeColumns ("id", "name", "description", "cim", "cimreaderoptions", "start_time", "end_time", "input_keyspace", "output_keyspace", "transformers"))

        val players =
            tasks.flatMap (
                task ⇒ task.players.map (player ⇒ (id, task.transformer, player.name, player.mrid, player.`type`, player.property)))
        players.saveToCassandra (keyspace, "simulation_player", SomeColumns ("simulation", "transformer", "name", "mrid", "type", "property"))

        val recorders =
            tasks.flatMap (
                task ⇒ task.recorders.map (recorder ⇒ (id, task.transformer, recorder.name, recorder.mrid, recorder.`type`, recorder.property, recorder.unit, recorder.interval, recorder.aggregationsMap)))
        recorders.saveToCassandra (keyspace, "simulation_recorder", SomeColumns ("simulation", "transformer", "name", "mrid", "type", "property", "unit", "interval", "aggregations"))
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
        // ToDo: more robust checking
        val readeroptions: mutable.Map[String, JsonValue] = json.getJsonObject ("cimreaderoptions").asScala
        val map = readeroptions.map (x ⇒ (x._1, x._2.toString))
        map ("path") = cim // add path to support multiple files
        map ("StorageLevel") = options.storage // add storage option from command line
        map.toMap
    }

    def parseKeyspaces (json: JsonObject): (String, String) =
    {
        // ToDo: more robust checking
        val keyspaces: mutable.Map[String, JsonValue] = json.getJsonObject ("keyspaces").asScala
        var input: String = "cimapplication"
        var output: String = "cimapplication"
        keyspaces.foreach (
            x ⇒
            {
                val s = x._2.asInstanceOf [JsonString].getString
                if ("input" == x._1)
                    input = s
                else if ("output" == x._1)
                    output = s
            }
        )
        (input, output)
    }

    def parseInterval (json: JsonObject): (Calendar, Calendar) =
    {
        val calendar: Calendar = Calendar.getInstance ()
        calendar.setTimeZone (TimeZone.getTimeZone ("GMT"))
        calendar.setTimeInMillis (0L)
        val iso_date_format: SimpleDateFormat = new SimpleDateFormat ("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
        iso_date_format.setCalendar (calendar)
        def iso_parse (s: String): Calendar =
        {
            val ret = Calendar.getInstance ()
            ret.setTime (iso_date_format.parse (s))
            ret
        }

        // ToDo: more robust checking
        val interval: mutable.Map[String, JsonValue] = json.getJsonObject ("interval").asScala
        var start: Calendar = calendar
        var end: Calendar = calendar
        interval.foreach (
            x ⇒
            {
                val d = iso_parse (x._2.asInstanceOf [JsonString].getString)
                if ("start" == x._1)
                    start = d
                else if ("end" == x._1)
                    end = d
            }
        )
        (start, end)
    }

    def parseTransformers (json: JsonObject): Seq[String] =
    {
        // ToDo: more robust checking
        val transformers: JsonArray = json.getJsonArray ("transformers")
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
            List (SimulationPlayerQuery (title, query))
    }

    def parsePlayers (name: String, json: JsonObject): Seq[SimulationPlayerQuery] =
    {
        // ToDo: more robust checking
        val players: Seq[JsonObject] = json.getJsonArray ("players").getValuesAs (classOf [JsonObject]).asScala
        players.flatMap (parsePlayer (name, _))
    }

    def parseAggregation (aggregations: JsonObject): List[SimulationAggregate] =
    {
        val intervals = aggregations.getInt ("intervals")
        val time = if (aggregations.isNull ("ttl"))
            0
        else
            aggregations.getJsonNumber ("ttl").intValue
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
        // ToDo: more robust checking
        val recorders: Seq[JsonObject] = json.getJsonArray ("recorders").getValuesAs (classOf [JsonObject]).asScala
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
        // ToDo: more robust checking
        val extras: Seq[JsonObject] = json.getJsonArray ("extras").getValuesAs (classOf [JsonObject]).asScala
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
            val (read, write) = parseKeyspaces (json)
            val (start, end) = parseInterval (json)
            val transformers = parseTransformers (json)
            val players = parsePlayers (name, json)
            val recorders = parseRecorders (name, json)
            val extras = parseExtras (name, json)
            List (SimulationJob (name, description, cim, cimreaderoptions, read, write, start, end, transformers, players, recorders, extras))
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
