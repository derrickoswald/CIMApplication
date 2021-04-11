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

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.mapAsScalaMapConverter

import com.datastax.spark.connector._
import com.datastax.spark.connector.cql.CassandraConnector
import org.apache.spark.sql.SparkSession
import org.apache.spark.rdd.RDD
import org.apache.spark.storage.StorageLevel
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.json4s.Formats
import org.json4s.JsonAST.JObject
import org.json4s.JsonAST.JString
import org.json4s.JArray
import org.json4s.JDouble
import org.json4s.JInt
import org.json4s.JValue


import ch.ninecode.util.JSON
import ch.ninecode.util.JSONAble
import ch.ninecode.util.JSONCustomSerializer

/**
 * Simulation job details.
 * These values are parsed from the supplied JSON and drive all aspects of the simulation.
 *
 * @param id                     The user specified ID or a generated GUID identifying this simulation uniquely.
 *                               If one is not supplied in the JSON it will be generated.
 * @param name                   A user specified name for the simulation.
 *                               This would be used in chart titles or graph labels when comparing scenarios.
 * @param description            User specified description for the simulation.
 *                               This would be a reminder to the user about why they did the simulation, i.e. a project name.
 * @param cim                    The CIM files used in the simulation.
 *                               If there is more than one file, e.g. an additional switch setting overlay file, then separate the file
 *                               names with commas. These are file system dependent names, e.g. hdfs://sandbox:8020/DemoData.rdf.
 * @param cimreaderoptions       Options to pass to the CIMReader.
 *                               Unless there is a topology already in the CIM file, this should include ch.ninecode.cim.do_topo_islands=true.
 * @param keyspaces              The Cassandra keyspaces to use.
 * @param interval               The time period over which to simulate.
 * @param temperatures           The simulation temperature and CIM file temperature.
 * @param swing_voltage_factor   The factor to apply to the nominal slack voltage, e.g. 1.03 = 103% of nominal.
 * @param transformers           The name of the transformers to simulate.
 *                               If this list is empty all transformers in the CIM file will be processed.
 *                               The names should reflect "ganged" transformers. For example, if TRA1234 and TRA1235 share a common
 *                               low voltage topological node, then the name would be "TRA1234_TRA1235".
 * @param swing                  Either "hi" or "lo" to set the swing bus (slack bus) on the high or low voltage winding of the transformer.
 * @param players                Queries for driving data.
 *                               Queries against the Spark schema (https://github.com/derrickoswald/CIMReader/blob/master/Model.md)
 *                               yielding mrid, name, parent, type, property, unit and island for playing data.
 *                               An example for energy consumed by house connections:
 *
 *                               select
 *                               c.EnergyConnection.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid,
 *                               'energy' type,
 *                               concat(c.EnergyConnection.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, '_load') name,
 *                               t.TopologicalNode parent,
 *                               'constant_power' property,
 *                               'Watt' unit,
 *                               n.TopologicalIsland island
 *                               from
 *                               EnergyConsumer c,
 *                               Terminal t,
 *                               TopologicalNode n
 *                               where
 *                               c.EnergyConnection.ConductingEquipment.Equipment.PowerSystemResource.PSRType == 'PSRType_HouseService' and
 *                               c.EnergyConnection.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID = t.ConductingEquipment and
 *                               t.TopologicalNode = n.IdentifiedObject.mRID
 * @param recorders              Queries for recording data and the recording interval and any aggregations.
 *                               Queries against the Spark schema (https://github.com/derrickoswald/CIMReader/blob/master/Model.md)
 *                               yielding mrid, name, parent, type, property, unit and island for recording data.
 *                               An example for recording transformer output power:
 *
 *                               select
 *                               concat (name_island.name, '_power_recorder') name,
 *                               name_island.name mrid,
 *                               name_island.name parent,
 *                               'power' type,
 *                               'power_out' property,
 *                               'VA' unit,
 *                               name_island.island
 *                               from
 *                               (
 *                               select
 *                               concat_ws ('_', sort_array (collect_set (trafos.mrid))) name,
 *                               first_value (trafos.island) island
 *                               from
 *                               (
 *                               select distinct
 *                               t.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID mrid,
 *                               t1.TopologicalNode node,
 *                               n.TopologicalIsland island
 *                               from
 *                               PowerTransformer t,
 *                               Terminal t1,
 *                               Terminal t2,
 *                               TopologicalNode n
 *                               where
 *                               t1.ConductingEquipment = t.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and
 *                               t2.ConductingEquipment = t.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID and
 *                               t1.ACDCTerminal.sequenceNumber = 2 and
 *                               t2.ACDCTerminal.sequenceNumber = 2 and
 *                               t1.TopologicalNode = n.IdentifiedObject.mRID and
 *                               t2.TopologicalNode = n.IdentifiedObject.mRID
 *                               ) trafos
 *                               group by
 *                               node
 *                               ) name_island
 * @param extras                 Queries for data to attach to the GeoJSON point, line and polygon features.
 *                               Queries against the Spark schema (https://github.com/derrickoswald/CIMReader/blob/master/Model.md)
 *                               yielding key-value pairs. The key is the mRID of the object to attach the value to.
 *                               The query name is used as the key.
 *                               An example to attach the rated current to each conductor:
 *
 *                               select
 *                               l.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID key,
 *                               cast (w.ratedCurrent as string) value
 *                               from
 *                               ACLineSegment l,
 *                               WireInfo w
 *                               where
 *                               w.AssetInfo.IdentifiedObject.mRID = l.Conductor.ConductingEquipment.Equipment.PowerSystemResource.AssetDatasheet
 *
 *                               If this query were named ratedCurrent, the GeoJSON line objects representing cables would have a property, e.g.:
 *                               "ratedCurrent": "200.0"
 * @param postprocessors         Operations to be performed after the simulation is complete.
 *                               Each processor in the list handles one type of operation and each must parse its own JSON.
 */
case class SimulationJob
(
    id: String,
    name: String,
    description: String,
    cim: String,
    cimreaderoptions: Map[String, String],
    keyspaces: SimulationKeyspaces,
    interval: SimulationInterval,
    temperatures: SimulationTemperatures,
    swing_voltage_factor: Double,
    transformers: Seq[String],
    swing: String = "hi",
    players: Seq[SimulationPlayerQuery],
    recorders: Seq[SimulationRecorderQuery],
    extras: Seq[SimulationExtraQuery],
    postprocessors: Seq[SimulationPostProcessor]
) extends JSONAble[SimulationJob]
{
    def optionString: String = cimreaderoptions.map(kv => s"${kv._1}=${kv._2}").mkString(",")

    /**
     * Get the highest run number + 1
     *
     * @param session  the session to use
     * @param keyspace the keyspace to query
     * @return the next run number
     */
    def nextRun (session: SparkSession, keyspace: String): Int =
    {
        CassandraConnector(session.sparkContext.getConf).withSessionDo(
            session =>
            {
                val resultset = session.execute(s"""select max(run) as hi from "$keyspace".simulation where id='$id'""")
                val row = resultset.one
                if (row.isNull(0))
                    1
                else
                    row.getInt(0) + 1
            }
        )
    }

    /**
     * Insert the simulation json into simulation table.
     *
     * @param session the Spark session to use
     * @param id      the id to store this job under
     */
    def save (session: SparkSession, keyspace: String, id: String, tasks: RDD[SimulationTask]): Unit =
    {
        val run = nextRun(session, keyspace)
        val current_time = Calendar.getInstance
        val json = session.sparkContext.parallelize(Seq(
            (
                id,
                run,
                name,
                description,
                cim,
                cimreaderoptions,
                current_time,
                interval.start_time,
                interval.end_time,
                temperatures.cim_temperature,
                temperatures.simulation_temperature,
                swing,
                swing_voltage_factor,
                keyspaces.input_keyspace,
                keyspaces.output_keyspace,
                transformers
            )
        ))

        json.saveToCassandra(keyspace, "simulation",
            SomeColumns("id", "run", "name", "description", "cim", "cimreaderoptions",
                "run_time", "start_time", "end_time",
                "cim_temperature", "simulation_temperature", "swing", "swing_voltage_factor",
                "input_keyspace", "output_keyspace", "transformers"))

        if (run == 1)
        {
            val players =
                tasks.flatMap(
                    task => task.players.map(player => (id, task.transformer, player.name, player.mrid, player.`type`, player.property)))
            players.saveToCassandra(keyspace, "simulation_player", SomeColumns("simulation", "transformer", "name", "mrid", "type", "property"))

            val recorders =
                tasks.flatMap(
                    task => task.recorders.map(recorder => (id, task.transformer, recorder.name, recorder.mrid, recorder.`type`, recorder.property, recorder.unit, recorder.interval, recorder.aggregationsMap)))
            recorders.saveToCassandra(keyspace, "simulation_recorder", SomeColumns("simulation", "transformer", "name", "mrid", "type", "property", "unit", "interval", "aggregations"))
        }
    }

    /**
     * Output equivalent JSON options.
     */
    override def toJSON: String = SimulationJob.toJSON(this)

    /**
     * Create one of these option objects from JSON.
     *
     * @param text the JSON text
     * @return either an error message in Left or the options instance in Right
     */
    override def fromJSON (text: String): Either[String, SimulationJob] = SimulationJob.fromJSON(text)
}

/**
 * Parse the JSON describing a simulation to be done.
 */
object SimulationJob extends JSON[SimulationJob]
{
    val log: Logger = LoggerFactory.getLogger(getClass)

    type objectParser[T] = (String, JsonObject) => Option[T]

    def readJSON (json: String): Option[JsonObject] =
    {
        try
        {
            try
                Json.createReader(new StringReader(json)).readObject match
                {
                    case obj: JsonObject => Some(obj)
                    case _ =>
                        log.error("not a JsonObject")
                        None
                }
            catch
            {
                case je: JsonException =>
                    log.error(s"""unparseable as JSON (${je.getMessage})""")
                    None
            }
        }
        catch
        {
            case e: Exception =>
                log.error(e.getMessage)
                None
        }
    }

    def storage_level_tostring (level: StorageLevel): String =
    {
        level match
        {
            case StorageLevel.NONE => "NONE"
            case StorageLevel.DISK_ONLY => "DISK_ONLY"
            case StorageLevel.DISK_ONLY_2 => "DISK_ONLY_2"
            case StorageLevel.MEMORY_ONLY => "MEMORY_ONLY"
            case StorageLevel.MEMORY_ONLY_2 => "MEMORY_ONLY_2"
            case StorageLevel.MEMORY_ONLY_SER => "MEMORY_ONLY_SER"
            case StorageLevel.MEMORY_ONLY_SER_2 => "MEMORY_ONLY_SER_2"
            case StorageLevel.MEMORY_AND_DISK => "MEMORY_AND_DISK"
            case StorageLevel.MEMORY_AND_DISK_2 => "MEMORY_AND_DISK_2"
            case StorageLevel.MEMORY_AND_DISK_SER => "MEMORY_AND_DISK_SER"
            case StorageLevel.MEMORY_AND_DISK_SER_2 => "MEMORY_AND_DISK_SER_2"
            case StorageLevel.OFF_HEAP => "OFF_HEAP"
            case _ => ""
        }
    }

    def typeString (value: JsonValue): String = value.getValueType.toString

    def parseArrayOfObjects[T] (name: String, member: String, parser: objectParser[T], json: JsonObject): Seq[T] =
    {
        if (json.containsKey(member))
        {
            val value = json.get(member)
            if (value.getValueType == JsonValue.ValueType.ARRAY)
            {
                value
                    .asJsonArray
                    .asScala
                    .flatMap(
                        item =>
                            if (item.getValueType == JsonValue.ValueType.OBJECT)
                                parser(name, item.asJsonObject)
                            else
                            {
                                log.error(s"""unexpected JSON type for $member element ("${typeString(item)}")""")
                                None
                            }
                    )
            }
            else
            {
                log.error(s"""unexpected JSON type for $member ("${typeString(value)}")""")
                Seq[T]()
            }
        }
        else
            Seq[T]()
    }

    def parseCIMReaderOptions (options: SimulationOptions, cim: String, json: JsonObject): Map[String, String] =
    {
        val MEMBERNAME = "cimreaderoptions"
        val map = Map[String, String](
            "path" -> cim, // add path to support multiple files
            "StorageLevel" -> storage_level_tostring(options.cim_options.storage) // add storage option from command line
        )

        if (json.containsKey(MEMBERNAME))
        {
            val value: JsonValue = json.get(MEMBERNAME)
            value match
            {
                case obj: JsonObject =>
                    val cimreaderoptions = obj.asScala
                    val opt = cimreaderoptions.map(x => (x._1, x._2 match
                    {
                        case s: JsonString => s.getString
                        case _ => x._2.toString
                    }))
                    map ++ opt
                case _ =>
                    log.warn(s"""JSON member "$MEMBERNAME" is not a JSON object (type "${typeString(value)}")""")
                    map
            }
        }
        else
            map
    }

    def parseKeyspaces (json: JsonObject): (String, String, Int) =
    {
        val MEMBERNAME = "keyspaces"
        var input: String = "cimapplication"
        var output: String = "cimapplication"
        var replication = 2

        if (json.containsKey(MEMBERNAME))
        {
            val value: JsonValue = json.get(MEMBERNAME)
            value match
            {
                case obj: JsonObject =>
                    val keyspaces = obj.asScala
                    keyspaces.foreach
                    {
                        case ("input", v: JsonString) => input = v.getString
                        case ("output", v: JsonString) => output = v.getString
                        case ("replication", v: JsonNumber) => replication = v.intValue
                        case (k: String, v: JsonValue) =>
                            log.warn(s"""unexpected JSON member or type: $MEMBERNAME["$k"] of type "${typeString(v)}"""")
                    }
                case _ =>
                    log.warn(s"""JSON member "$MEMBERNAME" is not a JSON object (type "${typeString(value)}")""")
            }
        }

        (input, output, replication)
    }

    def parseInterval (json: JsonObject): (Calendar, Calendar, Int) =
    {
        val MEMBERNAME = "interval"
        val calendar: Calendar = Calendar.getInstance()
        calendar.setTimeZone(TimeZone.getTimeZone("GMT"))
        calendar.setTimeInMillis(0L)
        var start: Calendar = calendar
        var end: Calendar = calendar
        var buffer: Int = 60 * 60 * 1000 // one hour buffer

        val iso_date_format: SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
        iso_date_format.setCalendar(calendar)

        def iso_parse (s: String): Calendar =
        {
            val ret = Calendar.getInstance()
            ret.setTime(iso_date_format.parse(s))
            ret
        }

        if (json.containsKey(MEMBERNAME))
        {
            val value = json.get(MEMBERNAME)
            value match
            {
                case obj: JsonObject =>
                    val interval = obj.asScala
                    interval.foreach
                    {
                        case ("start", v: JsonString) => start = iso_parse(v.getString)
                        case ("end", v: JsonString) => end = iso_parse(v.getString)
                        case ("buffer", v: JsonNumber) => buffer = v.intValue
                        case (k: String, v: JsonValue) =>
                            log.warn(s"""unexpected JSON member or type: $MEMBERNAME["$k"] of type "${typeString(v)}"""")
                    }
                case _ =>
                    log.warn(s"""JSON member "$MEMBERNAME" is not a JSON object (type "${typeString(value)}")""")
            }
        }
        else
            log.warn(s"""JSON member "$MEMBERNAME" not found, using defaults""")

        (start, end, buffer)
    }

    def parseTemperatures (json: JsonObject): (Double, Double) =
    {
        val MEMBERNAME = "temperatures"
        var cim_temperature: Double = 20.0
        var simulation_temperature: Double = 20.0

        if (json.containsKey(MEMBERNAME))
        {
            val value = json.get(MEMBERNAME)
            value match
            {
                case obj: JsonObject =>
                    val temperatures = obj.asScala
                    temperatures.foreach
                    {
                        case ("cim_temperature", v: JsonNumber) => cim_temperature = v.doubleValue
                        case ("simulation_temperature", v: JsonNumber) => simulation_temperature = v.doubleValue
                        case (k: String, v: JsonValue) =>
                            log.warn(s"""unexpected JSON member or type: $MEMBERNAME["$k"] of type "${typeString(v)}"""")
                    }
                case _ =>
                    log.warn(s"""JSON member "$MEMBERNAME" is not a JSON object (type "${typeString(value)}")""")
            }
        }

        (cim_temperature, simulation_temperature)
    }

    def getStrings (array: JsonArray, member: String): Seq[String] =
    {
        array.asScala.flatMap(
            item =>
            {
                item match
                {
                    case string: JsonString =>
                        Some(string.getString)
                    case _ =>
                        log.error(s"""unexpected JSON type for $member element ("${typeString(item)}")""")
                        None
                }
            }
        )
    }

    def parseTransformers (json: JsonObject): Seq[String] =
    {
        val MEMBERNAME = "transformers"

        if (json.containsKey(MEMBERNAME))
        {
            val value = json.get(MEMBERNAME)
            value match
            {
                case array: JsonArray =>
                    getStrings(array, MEMBERNAME)
                case _ =>
                    log.error(s"""unexpected JSON type for $MEMBERNAME ("${typeString(value)}")""")
                    Seq[String]()
            }
        }
        else
            Seq[String]()
    }

    def parseQuery (name: String, context: String, json: JsonObject): Option[Seq[String]] =
    {
        val MEMBERNAME = "query"

        if (json.containsKey(MEMBERNAME))
        {
            val value = json.get(MEMBERNAME)
            value match
            {
                case array: JsonArray =>
                    val queries = getStrings(array, MEMBERNAME)
                    if (queries.nonEmpty)
                        Some(queries)
                    else
                    {
                        log.error(s""""$name" has no valid queries for "$context"""")
                        None
                    }
                case string: JsonString =>
                    Some(Seq(string.getString))
                case _ =>
                    log.error(s"""JSON member "$MEMBERNAME" is not a JSON string or array (type "${typeString(value)}") in $context""")
                    None
            }
        }
        else
        {
            log.error(s""""$name" does not specify a query for "$context"""")
            None
        }
    }

    def parseTransform (name: String, context: String, json: JsonObject): Option[String] =
    {
        val MEMBERNAME = "transform"

        if (json.containsKey(MEMBERNAME))
        {
            val value = json.get(MEMBERNAME)
            value match
            {
                case program: JsonString =>
                    // try it
                    try
                    {
                        val tx = MeasurementTransform.build(program.getString)
                        val _ = tx.transform(
                            Array(
                                SimulationPlayerData(
                                    "TRA1234",
                                    "HAS5678",
                                    "energy",
                                    Calendar.getInstance.getTimeInMillis,
                                    900000,
                                    "Wh",
                                    Array(1.0, 2.0))))
                        Some(program.getString)
                    }
                    catch
                    {
                        case exception: Throwable =>
                            log.warn(s"""reverting to identity MeasurementTransform: "$name" in $context threw an exception: '${exception.getLocalizedMessage}'"""")
                            None
                    }
                case _ =>
                    log.error(s"""JSON member "$MEMBERNAME" is not a JSON string (type "${typeString(value)}") in $context""")
                    None
            }
        }
        else
            None
    }

    def parsePlayer (name: String, player: JsonObject): Option[SimulationPlayerQuery] =
    {
        val title = player.getString("title", "")
        val transform = parseTransform(name, s"player:$title", player)
        val queries: Option[Seq[String]] = parseQuery(name, s"player:$title", player)
        queries match
        {
            case Some(set) => Some(SimulationPlayerQuery(title, set, transform))
            case None => None
        }
    }

    def parseAggregation (aggregations: JsonObject): List[SimulationAggregate] =
    {
        val intervals = aggregations.getInt("intervals")
        val time = if (aggregations.isNull("ttl"))
            0
        else
            aggregations.getJsonNumber("ttl").intValue
        List(SimulationAggregate(intervals, time))
    }

    def parseRecorder (name: String, recorder: JsonObject): Option[SimulationRecorderQuery] =
    {
        val title = recorder.getString("title", "")
        val interval = recorder.getInt("interval", 900)
        val array = recorder.getJsonArray("aggregations").getValuesAs(classOf[JsonObject]).asScala
        val aggregations = array.flatMap(parseAggregation).toList
        val queries = parseQuery(name, s"recorder:$title", recorder)
        queries match
        {
            case Some(set) => Some(SimulationRecorderQuery(title, set, interval, aggregations))
            case None => None
        }
    }

    def parseExtra (name: String, extra: JsonObject): Option[SimulationExtraQuery] =
    {
        val title = extra.getString("title", "")
        val queries = parseQuery(name, s"extra:$title", extra)
        queries match
        {
            case Some(set) => Some(SimulationExtraQuery(title, set))
            case None => None
        }
    }

    def parseJob (options: SimulationOptions)(json: JsonObject): Option[SimulationJob] =
    {
        val id = json.getString("id", java.util.UUID.randomUUID.toString)
        val name = json.getString("name", "*unnamed*")
        val description = json.getString("description", "")
        val cim = json.getString("cim", "")
        if ("" == cim)
        {
            log.error(s""""$name" does not specify a CIM file""")
            None
        }
        else
        {
            val cimreaderoptions = parseCIMReaderOptions(options, cim, json)
            val (read, write, replication) = parseKeyspaces(json)
            val (start, end, buffer) = parseInterval(json)
            val (cim_temperature, simulation_temperature) = parseTemperatures(json)
            val swing = json.getString("swing", "hi")
            val swing_voltage_factor =
                if (json.containsKey("swing_voltage_factor"))
                    json.getJsonNumber("swing_voltage_factor").doubleValue()
                else
                    1.0
            val transformers = parseTransformers(json)
            val players = parseArrayOfObjects[SimulationPlayerQuery](name, "players", parsePlayer, json)
            val recorders = parseArrayOfObjects[SimulationRecorderQuery](name, "recorders", parseRecorder, json)
            val extras = parseArrayOfObjects[SimulationExtraQuery](name, "extras", parseExtra, json)
            val postprocessors = Seq[SimulationPostProcessor]() // parseArrayOfObjects[SimulationPostProcessor](name, "postprocessing", parsePostProcess, json)
            Some(SimulationJob(
                id = id,
                name = name,
                description = description,
                cim = cim,
                cimreaderoptions = cimreaderoptions,
                keyspaces = SimulationKeyspaces (read, write, replication),
                interval = SimulationInterval (start, end, buffer),
                temperatures = SimulationTemperatures (cim_temperature, simulation_temperature),
                swing_voltage_factor = swing_voltage_factor,
                transformers = transformers,
                swing = swing,
                players = players,
                recorders = recorders,
                extras = extras,
                postprocessors = postprocessors))
        }
    }

    def getAll (options: SimulationOptions): Seq[SimulationJob] =
    {
//        if (options.verbose)
//            org.apache.log4j.LogManager.getLogger(getClass.getName).setLevel(org.apache.log4j.Level.INFO)
//        val jsons = options.simulation.map(readJSON)
//        val simulations = jsons.flatten
//        if (jsons.length != simulations.length)
//            log.warn("not all simulations will be processed")
//        val jobs = simulations.flatMap(parseJob(options))
//        if (simulations.length != jobs.length)
//            log.warn("some simulation JSON files have errors")
//        jobs
        Seq()
    }

    /**
     * The name of the resource containing the JSON schema for the options.
     *
     * @return a resource name string for use by ClassLoader.getResourceAsStream
     */
    override def schemaResourceName: String = "SimulationJobSchema.json"

    /**
     * The mapping from URI in the schema to local URI.
     *
     * @return The map from global URI to local URI
     */
    override def schemaUriMap: Map[String, String] = Map[String,String](
        "https://raw.githubusercontent.com/derrickoswald/CIMApplication/master/json-schema/SimulationEventsSchema.json" -> "resource:SimulationEventsSchema.json",
        "https://raw.githubusercontent.com/derrickoswald/CIMApplication/master/json-schema/SimulationCoincidenceFactorSchema.json" -> "resource:SimulationCoincidenceFactorSchema.json",
        "https://raw.githubusercontent.com/derrickoswald/CIMApplication/master/json-schema/SimulationLoadFactorSchema.json" -> "resource:SimulationLoadFactorSchema.json",
        "https://raw.githubusercontent.com/derrickoswald/CIMApplication/master/json-schema/SimulationResponsibilityFactorSchema.json" -> "resource:SimulationResponsibilityFactorSchema.json",
        "https://raw.githubusercontent.com/derrickoswald/CIMApplication/master/json-schema/SimulationJobSchema.json" -> "resource:SimulationJobSchema.json"
    ) ++ SimulationKeyspaces.schemaUriMap ++ SimulationInterval.schemaUriMap ++ SimulationTemperatures.schemaUriMap ++
        SimulationPlayerQuery.schemaUriMap ++ SimulationRecorderQuery.schemaUriMap ++ SimulationExtraQuery.schemaUriMap ++ SimulationAggregate.schemaUriMap

    class SimulationEventsSerializer extends JSONCustomSerializer[SimulationEvents](
        (format: Formats) =>
            (
                {
                    case JObject(o) => SimulationEvents(SimulationEventsSerializer.triggers (o))
                },
                {
                    case x: SimulationEvents => JObject(
                        List(
                            ("class", JString("event")),
                            ("thresholds", JArray (
                                x.triggers.map (trigger => JObject(
                                    List(
                                        // ToDo: Fix this
                                        ("trigger", JString (trigger.getClass.getName match { case "ch.ninecode.sim.HighTrigger" => "high" case "ch.ninecode.sim.LowTrigger" => "low" })),
                                        ("type", JString (trigger.`type`)),
                                        ("severity", JInt (trigger.severity)),
                                        ("reference", JString (trigger.reference)),
                                        ("default", JDouble (trigger.default)),
                                        ("ratio", JDouble (trigger.ratio)),
                                        ("duration", JInt (trigger.duration))
                                    ))
                                ).toList
                            ))
                        ))
                }
            )
    )
    object SimulationEventsSerializer
    {
        def trigger (l: List[(String, JValue)]): Option[Trigger] =
        {
            val    `type` = l.find { case ("type", _) => true case _ => false }.map (_._2)  match { case Some (JString(s)) => s case _ => "voltage" }
            val  severity = l.find { case ("severity", _) => true case _ => false }.map (_._2)  match { case Some (JInt(i)) => i.intValue case _ => 1 }
            val reference = l.find { case ("reference", _) => true case _ => false }.map (_._2)  match { case Some (JString(s)) => s case _ => "ratedVoltage" }
            val   default = l.find { case ("default", _) => true case _ => false }.map (_._2)  match { case Some (JDouble(d)) => d case _ => 0.0 }
            val     ratio = l.find { case ("ratio", _) => true case _ => false }.map (_._2)  match { case Some (JDouble(d)) => d case _ => 1.0 }
            val  duration = l.find { case ("duration", _) => true case _ => false }.map (_._2)  match { case Some (JInt(i)) => i.intValue case _ => 900000 }
            l.find (_._1 == "trigger") match
            {
                case Some ((_, JString("high"))) => Some (HighTrigger(`type`, severity, reference, default, ratio, duration))
                case Some ((_, JString("low"))) => Some (LowTrigger(`type`, severity, reference, default, ratio, duration))
                case _ => None
            }
        }

        def triggers (l: List[(String, JValue)]): Iterable[Trigger] =
        {
            l.flatMap { case ("triggers", JArray(array)) => array.flatMap { case JObject(o) => trigger(o) case _ => None } case _ => Iterable.empty}
        }
    }

    /**
     * The list of custom serializers for the options.
     */
    override def customSerializers: Seq[JSONCustomSerializer[_]] =
        Seq (new SimulationEventsSerializer) ++ SimulationKeyspaces.customSerializers ++ SimulationInterval.customSerializers ++
            SimulationTemperatures.customSerializers ++ SimulationPlayerQuery.customSerializers ++ SimulationRecorderQuery.customSerializers ++
            SimulationExtraQuery.customSerializers ++ SimulationAggregate.customSerializers ++ Seq(new SimulationEventsSerializer)
}
