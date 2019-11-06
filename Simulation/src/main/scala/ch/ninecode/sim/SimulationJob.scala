package ch.ninecode.sim

import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

import javax.json.Json
import javax.json.JsonArray
import javax.json.JsonException
import javax.json.JsonNumber
import javax.json.JsonObject
import javax.json.JsonString
import javax.json.JsonValue

import scala.collection.JavaConverters._
import scala.collection.mutable

import org.apache.spark.sql.SparkSession
import org.apache.spark.rdd.RDD
import org.apache.spark.storage.StorageLevel
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import com.datastax.spark.connector._
import com.datastax.spark.connector.cql.CassandraConnector

/**
 * Simulation job details.
 * These values are parsed from the supplied JSON and drive all aspects of the simulation.
 *
 * @param id               The user specified ID or a generated GUID identifying this simulation uniquely.
 *                         If one is not supplied in the JSON it will be generated.
 * @param name             A user specified name for the simulation.
 *                         This would be used in chart titles or graph labels when comparing scenarios.
 * @param description      User specified description for the simulation.
 *                         This would be a reminder to the user about why they did the simulation, i.e. a project name.
 * @param cim              The CIM files used in the simulation.
 *                         If there is more than one file, e.g. an additional switch setting overlay file, then separate the file
 *                         names with commas. These are file system dependent names, e.g. hdfs://sandbox:8020/DemoData.rdf.
 * @param cimreaderoptions Options to pass to the CIMReader.
 *                         Unless there is a topology already in the CIM file, this should include ch.ninecode.cim.do_topo_islands=true.
 * @param input_keyspace   The Cassandra keyspace to read measured data from.
 *                         A table named measured_value with an appropriate schema (see simulation_schema.sql) is expected.
 * @param output_keyspace  The Cassandra keyspace to save results to.
 *                         A table named simulated_value with an appropriate schema (see simulation_schema.sql) and if summarization
 *                         operations are performed additional tables with appropriate schema are expected.
 * @param replication      The Cassandra keyspace replication.
 *                         The replication factor used in the <code>create keyspace if not exists</code> DDL, and hence
 *                         <em>used only if the <code>output_keyspace</code> is created.</em>
 * @param start_time       The starting date and time of the simulation.
 * @param end_time         The ending date and time of the simulation.
 * @param buffer           The number of milliseconds of buffer either side of the start⇒end interval to read from measured data.
 * @param transformers     The name of the transformers to simulate.
 *                         If this list is empty all transformers in the CIM file will be processed.
 *                         The names should reflect "ganged" transformers. For example, if TRA1234 and TRA1235 share a common
 *                         low voltage topological node, then the name would be "TRA1234_TRA1235".
 * @param players          Queries for driving data.
 *                         Queries against the Spark schema (https://github.com/derrickoswald/CIMReader/blob/master/Model.md)
 *                         yielding mrid, name, parent, type, property, unit and island for playing data.
 *                         An example for energy consumed by house connections:
 *
 *                         select
 *                             c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid,
 *                             'energy' type,
 *                             concat(c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_load') name,
 *                             t.TopologicalNode parent,
 *                             'constant_power' property,
 *                             'Watt' unit,
 *                             n.TopologicalIsland island
 *                         from
 *                             EnergyConsumer c,
 *                             Terminal t,
 *                             TopologicalNode n
 *                         where
 *                             c.ConductingEquipment.Equipment.PowerSystemResource.PSRType == 'PSRType_HouseService' and
 *                             c.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID = t.ConductingEquipment and
 *                             t.TopologicalNode = n.IdentifiedObject.mRID
 *
 * @param recorders        Queries for recording data and the recording interval and any aggregations.
 *                         Queries against the Spark schema (https://github.com/derrickoswald/CIMReader/blob/master/Model.md)
 *                         yielding mrid, name, parent, type, property, unit and island for recording data.
 *                         An example for recording transformer output power:
 *
 *                         select
 *                             concat (name_island.name, '_power_recorder') name,
 *                             name_island.name mrid,
 *                             name_island.name parent,
 *                             'power' type,
 *                             'power_out' property,
 *                             'VA' unit,
 *                             name_island.island
 *                         from
 *                         (
 *                             select
 *                                 concat_ws ('_', sort_array (collect_set (trafos.mrid))) name,
 *                                 first_value (trafos.island) island
 *                             from
 *                             (
 *                                 select distinct
 *                                     t.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid,
 *                                     t1.TopologicalNode node,
 *                                     n.TopologicalIsland island
 *                                 from
 *                                     PowerTransformer t,
 *                                     Terminal t1,
 *                                     Terminal t2,
 *                                     TopologicalNode n
 *                                 where
 *                                     t1.ConductingEquipment = t.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and
 *                                     t2.ConductingEquipment = t.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and
 *                                     t1.ACDCTerminal.sequenceNumber = 2 and
 *                                     t2.ACDCTerminal.sequenceNumber = 2 and
 *                                     t1.TopologicalNode = n.IdentifiedObject.mRID and
 *                                     t2.TopologicalNode = n.IdentifiedObject.mRID
 *                             ) trafos
 *                             group by
 *                                 node
 *                         ) name_island
 *
 * @param extras           Queries for data to attach to the GeoJSON point, line and polygon features.
 *                         Queries against the Spark schema (https://github.com/derrickoswald/CIMReader/blob/master/Model.md)
 *                         yielding key-value pairs. The key is the mRID of the object to attach the value to.
 *                         The query name is used as the key.
 *                         An example to attach the rated current to each conductor:
 *
 *                         select
 *                             l.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID key,
 *                             cast (w.ratedCurrent as string) value
 *                         from
 *                             ACLineSegment l,
 *                             WireInfo w
 *                         where
 *                             w.AssetInfo.IdentifiedObject.mRID = l.Conductor.ConductingEquipment.Equipment.PowerSystemResource.AssetDatasheet
 *
 *                         If this query were named ratedCurrent, the GeoJSON line objects representing cables would have a property, e.g.:
 *                             "ratedCurrent": "200.0"
 * @param postprocessors   Operations to be performed after the simulation is complete.
 *                         Each processor in the list handles one type of operation and each must parse its own JSON.
 */
case class SimulationJob
(
    id: String,
    name: String,
    description: String,
    cim: String,
    cimreaderoptions: Map[String, String],
    input_keyspace: String,
    output_keyspace: String,
    replication: Int,
    start_time: Calendar,
    end_time: Calendar,
    buffer: Int,
    transformers: Seq[String],
    swing: String = "hi",
    players: Seq[SimulationPlayerQuery],
    recorders: Seq[SimulationRecorderQuery],
    extras: Seq[SimulationExtraQuery],
    postprocessors: Seq[(SparkSession, SimulationOptions) ⇒ SimulationPostProcessor]
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
        // get the highest run number + 1
        val run = CassandraConnector (session.sparkContext.getConf).withSessionDo (
            session =>
            {
                val resultset = session.execute (s"select max(run) as hi from $keyspace.simulation where id='${id}'")
                val row = resultset.one
                if (row.isNull (0))
                    1
                else
                    row.getInt (0) + 1
            }
        )
        val current_time = Calendar.getInstance
        val json = session.sparkContext.parallelize (Seq (
            (
                id,
                run,
                name,
                description,
                cim,
                cimreaderoptions,
                current_time,
                start_time,
                end_time,
                input_keyspace,
                output_keyspace,
                transformers
            )
        ))

        json.saveToCassandra (keyspace, "simulation", SomeColumns ("id", "run", "name", "description", "cim", "cimreaderoptions", "run_time", "start_time", "end_time", "input_keyspace", "output_keyspace", "transformers"))

        if (run == 1)
        {
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

    def storage_level_tostring (level: StorageLevel): String =
    {
        level match
        {
            case StorageLevel.NONE ⇒ "NONE"
            case StorageLevel.DISK_ONLY ⇒ "DISK_ONLY"
            case StorageLevel.DISK_ONLY_2 ⇒ "DISK_ONLY_2"
            case StorageLevel.MEMORY_ONLY ⇒ "MEMORY_ONLY"
            case StorageLevel.MEMORY_ONLY_2 ⇒ "MEMORY_ONLY_2"
            case StorageLevel.MEMORY_ONLY_SER ⇒ "MEMORY_ONLY_SER"
            case StorageLevel.MEMORY_ONLY_SER_2 ⇒ "MEMORY_ONLY_SER_2"
            case StorageLevel.MEMORY_AND_DISK ⇒ "MEMORY_AND_DISK"
            case StorageLevel.MEMORY_AND_DISK_2 ⇒ "MEMORY_AND_DISK_2"
            case StorageLevel.MEMORY_AND_DISK_SER ⇒ "MEMORY_AND_DISK_SER"
            case StorageLevel.MEMORY_AND_DISK_SER_2 ⇒ "MEMORY_AND_DISK_SER_2"
            case StorageLevel.OFF_HEAP ⇒ "OFF_HEAP"
            case _ ⇒ ""
        }
    }

    def parseCIMReaderOptions (options: SimulationOptions, cim: String, json: JsonObject): Map[String, String] =
    {
        val MEMBERNAME = "cimreaderoptions"
        val map = Map[String,String](
            "path" → cim, // add path to support multiple files
            "StorageLevel" → storage_level_tostring (options.storage_level) // add storage option from command line
        )

        if (json.containsKey (MEMBERNAME))
        {
            val value = json.get (MEMBERNAME)
            if (value.getValueType == JsonValue.ValueType.OBJECT)
            {
                val cimreaderoptions: mutable.Map[String, JsonValue] = value.asInstanceOf[JsonObject].asScala
                val opt = cimreaderoptions.map (x ⇒ (x._1, x._2 match { case s: JsonString => s.getString case _ => x._2.toString }))
                map ++ opt
            }
            else
            {
                log.warn ("""JSON member "%s" is not a JSON object (type "%s")""".format (MEMBERNAME, value.getValueType.toString))
                map
            }
        }
        else
        {
            log.warn ("""JSON member "%s" not found""".format (MEMBERNAME))
            map
        }
    }

    def parseKeyspaces (json: JsonObject): (String, String, Int) =
    {
        val MEMBERNAME = "keyspaces"
        var input: String = "cimapplication"
        var output: String = "cimapplication"
        var replication = 2

        if (json.containsKey (MEMBERNAME))
        {
            val value = json.get (MEMBERNAME)
            if (value.getValueType == JsonValue.ValueType.OBJECT)
            {
                val keyspaces: mutable.Map[String, JsonValue] = value.asInstanceOf[JsonObject].asScala
                keyspaces.foreach
                {
                    case ("input", v: JsonString) ⇒ input = v.getString
                    case ("output", v: JsonString) ⇒ output = v.getString
                    case ("replication", v: JsonNumber) ⇒ replication = v.intValue
                    case (k: String, v: JsonValue) ⇒ log.warn ("""unexpected JSON member or type: %s["%s"] of type "%s"""".format (MEMBERNAME, k, v.getValueType.toString))
                }
            }
            else
                log.warn ("""JSON member "%s" is not a JSON object (type "%s")""".format (MEMBERNAME, value.getValueType.toString))
        }
        else
            log.warn ("""JSON member "%s" not found, using defaults""".format (MEMBERNAME))

        (input, output, replication)
    }

    def parseInterval (json: JsonObject): (Calendar, Calendar, Int) =
    {
        val MEMBERNAME = "interval"
        val calendar: Calendar = Calendar.getInstance ()
        calendar.setTimeZone (TimeZone.getTimeZone ("GMT"))
        calendar.setTimeInMillis (0L)
        var start: Calendar = calendar
        var end: Calendar = calendar
        var buffer: Int = 60 * 60 * 1000 // one hour buffer

        val iso_date_format: SimpleDateFormat = new SimpleDateFormat ("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
        iso_date_format.setCalendar (calendar)
        def iso_parse (s: String): Calendar =
        {
            val ret = Calendar.getInstance ()
            ret.setTime (iso_date_format.parse (s))
            ret
        }

        if (json.containsKey (MEMBERNAME))
        {
            val value = json.get (MEMBERNAME)
            if (value.getValueType == JsonValue.ValueType.OBJECT)
            {
                val interval: mutable.Map[String, JsonValue] = value.asInstanceOf[JsonObject].asScala
                interval.foreach
                {
                    case ("start", v: JsonString) ⇒ start = iso_parse (v.getString)
                    case ("end", v: JsonString) ⇒ end = iso_parse (v.getString)
                    case ("buffer", v: JsonNumber) ⇒ buffer = v.intValue
                    case (k: String, v: JsonValue) ⇒ log.warn ("""unexpected JSON member or type: %s["%s"] of type "%s"""".format (MEMBERNAME, k, v.getValueType.toString))
                }
            }
            else
                log.warn ("""JSON member "%s" is not a JSON object (type "%s")""".format (MEMBERNAME, value.getValueType.toString))
        }
        else
            log.warn ("""JSON member "%s" not found, using defaults""".format (MEMBERNAME))

        (start, end, buffer)
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

    def parsePlayers (name: String, json: JsonObject): Seq[SimulationPlayerQuery] =
    {
        val MEMBERNAME = "players"
        var ret: Seq[SimulationPlayerQuery] = Array[SimulationPlayerQuery] ()

        if (json.containsKey (MEMBERNAME))
        {
            val value = json.get (MEMBERNAME)
            if (value.getValueType == JsonValue.ValueType.ARRAY)
            {
                var title = "*unnamed player*"
                var query = null.asInstanceOf[String]
                var transform = null.asInstanceOf[String]
                for (player <- value.asInstanceOf[JsonArray].asScala)
                    if (player.getValueType == JsonValue.ValueType.OBJECT)
                    {
                        player.asInstanceOf[JsonObject].asScala.foreach
                        {
                            case ("title", v: JsonString) ⇒ title = v.getString
                            case ("query", v: JsonString) ⇒ query = v.getString
                            case ("transform", v: JsonString) ⇒
                                try
                                {
                                    val program = v.getString
                                    // try it
                                    val tx = MeasurementTransform.build (program)
                                    tx.transform (1.0, 2.0)
                                    tx.transform3 (1.0, 2.0, 3.0, 4.0, 5.0, 6.0)
                                    transform = program
                                }
                                catch
                                {
                                    case exception: Throwable ⇒ log.warn ("""reverting to identity MeasurementTransform because JSON member "%s" title: "%s" threw an exception: '%s'"""".format (MEMBERNAME, title, exception.getLocalizedMessage))
                                }
                            case (k: String, v: JsonValue) ⇒ log.warn ("""unexpected JSON member or type: %s["%s"] of type "%s"""".format (MEMBERNAME, k, v.getValueType.toString))
                        }
                        if (null == query)
                            log.error (""""%s" does not specify an RDF query for player "%s""".format (name, title))
                        else
                            ret = ret :+ SimulationPlayerQuery (title, query, transform)
                    }

            }
            else
                log.warn ("""JSON member "%s" is not a JSON array (type "%s")""".format (MEMBERNAME, value.getValueType.toString))
        }
        else
            log.warn ("""JSON member "%s" not found""".format (MEMBERNAME))

        ret
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

    def lookup (cls: String): JsonObject ⇒ (SparkSession, SimulationOptions) ⇒ SimulationPostProcessor =
    {
        cls match
        {
            case "event" ⇒ SimulationEvents.parser ()
            case "coincidence_factor" ⇒ SimulationCoincidenceFactor.parser ()
            case "load_factor" ⇒ SimulationLoadFactor.parser ()
            case "responsibility_factor" ⇒ SimulationResponsibilityFactor.parser ()
            case _ ⇒ throw new NotImplementedError ("""cls %s is not recognized as a post processor""".format (cls))
        }
    }

    def parsePostProcess (name: String, post: JsonObject): (SparkSession, SimulationOptions) ⇒ SimulationPostProcessor =
    {
        val cls = post.getString ("class", "")
        val class_parser = lookup (cls)
        class_parser (post)
    }

    def parsePostProcessing (name: String, json: JsonObject): Seq[(SparkSession, SimulationOptions) ⇒ SimulationPostProcessor] =
    {
        // ToDo: more robust checking
        if (json.containsKey ("postprocessing"))
        {
            val post_processes: Seq[JsonObject] = json.getJsonArray ("postprocessing").getValuesAs (classOf [JsonObject]).asScala
            post_processes.map (parsePostProcess (name, _))
        }
        else
            Seq()
    }


    def parseJob (options: SimulationOptions, json: JsonObject): List[SimulationJob] =
    {
        val id = json.getString ("id", java.util.UUID.randomUUID.toString)
        val name = json.getString ("name", "")
        val description = json.getString ("description", "")
        val cim = json.getString ("cim", null)
        val swing = json.getString ("swing", "hi")
        if (null == cim)
        {
            log.error (""""%s" does not specify a CIM file""".format (name))
            List ()
        }
        else
        {
            val cimreaderoptions = parseCIMReaderOptions (options, cim, json)
            val (read, write, replication) = parseKeyspaces (json)
            val (start, end, buffer) = parseInterval (json)
            val transformers = parseTransformers (json)
            val players = parsePlayers (name, json)
            val recorders = parseRecorders (name, json)
            val extras = parseExtras (name, json)
            val postprocessors = parsePostProcessing (name, json)
            List (SimulationJob (id, name, description, cim, cimreaderoptions, read, write, replication, start, end, buffer, transformers, swing, players, recorders, extras, postprocessors))
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
