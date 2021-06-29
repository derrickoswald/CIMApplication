package ch.ninecode.sim

import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
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
 * @param input_keyspace         The Cassandra keyspace to read measured data from.
 *                               A table named measured_value with an appropriate schema (see simulation_schema.sql) is expected.
 * @param output_keyspace        The Cassandra keyspace to save results to.
 *                               A table named simulated_value with an appropriate schema (see simulation_schema.sql) and if summarization
 *                               operations are performed additional tables with appropriate schema are expected.
 * @param replication            The Cassandra keyspace replication.
 *                               The replication factor used in the <code>create keyspace if not exists</code> DDL, and hence
 *                               <em>used only if the <code>output_keyspace</code> is created.</em>
 * @param start_time             The starting date and time of the simulation.
 * @param end_time               The ending date and time of the simulation.
 * @param buffer                 The number of milliseconds of buffer either side of the start=>end interval to read from measured data.
 * @param cim_temperature        The temperature of the elements in the CIM file (°C).
 * @param simulation_temperature The temperature at which the simulation is to be run (°C).
 * @param swing_voltage_factor   The factor to apply to the nominal slack voltage, e.g. 1.03 = 103% of nominal.
 * @param transformers           The name of the transformers to simulate.
 *                               If this list is empty all transformers in the CIM file will be processed.
 *                               The names should reflect "ganged" transformers. For example, if TRA1234 and TRA1235 share a common
 *                               low voltage topological node, then the name would be "TRA1234_TRA1235".
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
    input_keyspace: String,
    output_keyspace: String,
    replication: Int,
    start_time: Calendar,
    end_time: Calendar,
    buffer: Int,
    cim_temperature: Double,
    simulation_temperature: Double,
    swing_voltage_factor: Double,
    transformers: Seq[String],
    swing: String = "hi",
    players: Seq[SimulationPlayerQuery],
    recorders: Seq[SimulationRecorderQuery],
    extras: Seq[SimulationExtraQuery],
    postprocessors: Seq[(SparkSession, SimulationOptions) => SimulationPostProcessor],
    house_trafo_mappings: String = ""
)
{
    val log: Logger = LoggerFactory.getLogger(getClass)

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
                start_time,
                end_time,
                cim_temperature,
                simulation_temperature,
                swing,
                swing_voltage_factor,
                input_keyspace,
                output_keyspace,
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

    def start_in_millis(): Long = {
        val calendar: Calendar = Calendar.getInstance()
        calendar.setTimeZone(TimeZone.getTimeZone("GMT"))
        calendar.setTimeInMillis(0L)
        val iso_date_format: SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
        calendar.setTimeInMillis(0L)
        iso_date_format.setCalendar(calendar)

        val begin = start_time.getTimeInMillis
        val end = end_time.getTimeInMillis
        if (begin > end) {
            val from = iso_date_format.format(new Date(begin))
            val to = iso_date_format.format(new Date(end))
            log.error(s"job $id / $name has a start time ($from) after the end time ($to)")
            end
        }
        else
            begin
    }

    def end_in_millis(): Long = {
        end_time.getTimeInMillis
    }

}

/**
 * Parse the JSON describing a simulation to be done.
 */
object SimulationJob
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

    def lookup (cls: String): Option[JsonObject => (SparkSession, SimulationOptions) => SimulationPostProcessor] =
    {
        cls match
        {
            case "event" => Some(SimulationEvents.parser())
            case "coincidence_factor" => Some(SimulationCoincidenceFactor.parser())
            case "load_factor" => Some(SimulationLoadFactor.parser())
            case "responsibility_factor" => Some(SimulationResponsibilityFactor.parser())
            case _ =>
                log.error(s"""cls $cls is not recognized as a post processor""")
                None
        }
    }

    def parsePostProcess (name: String, post: JsonObject): Option[(SparkSession, SimulationOptions) => SimulationPostProcessor] =
    {
        val cls = post.getString("class", "")
        val class_parser = lookup(cls)
        class_parser match
        {
            case Some(parser) => Some(parser(post))
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
            val postprocessors = parseArrayOfObjects[(SparkSession, SimulationOptions) => SimulationPostProcessor](name, "postprocessing", parsePostProcess, json)
            val house_trafo_mappings = json.getString("house_trafo_mapping")
            Some(SimulationJob(
                id = id,
                name = name,
                description = description,
                cim = cim,
                cimreaderoptions = cimreaderoptions,
                input_keyspace = read,
                output_keyspace = write,
                replication = replication,
                start_time = start,
                end_time = end,
                buffer = buffer,
                cim_temperature = cim_temperature,
                simulation_temperature = simulation_temperature,
                swing_voltage_factor = swing_voltage_factor,
                transformers = transformers,
                swing = swing,
                players = players,
                recorders = recorders,
                extras = extras,
                postprocessors = postprocessors,
                house_trafo_mappings = house_trafo_mappings
            ))
        }
    }

    def getAll (options: SimulationOptions): Seq[SimulationJob] =
    {
        if (options.verbose)
            org.apache.log4j.LogManager.getLogger(getClass.getName).setLevel(org.apache.log4j.Level.INFO)
        val jsons = options.simulation.map(readJSON)
        val simulations = jsons.flatten
        if (jsons.length != simulations.length)
            log.warn("not all simulations will be processed")
        val jobs = simulations.flatMap(parseJob(options))
        if (simulations.length != jobs.length)
            log.warn("some simulation JSON files have errors")
        jobs
    }
}
