package ch.ninecode.sim

import java.io.Closeable
import java.io.StringReader
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

import javax.json.Json
import javax.json.JsonArray
import javax.json.JsonException
import javax.json.JsonObject
import javax.json.stream.JsonGenerator

import scala.collection.JavaConverters._

import org.apache.log4j.LogManager
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import com.datastax.spark.connector._
import com.datastax.spark.connector.writer.TTLOption
import com.datastax.spark.connector.writer.WriteConf

import ch.ninecode.cim.CIMNetworkTopologyProcessor
import ch.ninecode.cim.CIMRDD
import ch.ninecode.cim.CIMTopologyOptions
import ch.ninecode.gl.GLMEdge
import ch.ninecode.gl.GLMNode
import ch.ninecode.gl.Island
import ch.ninecode.gl.Island._
import ch.ninecode.gl.TransformerSet
import ch.ninecode.gl.TransformerData
import ch.ninecode.gl.TransformerServiceArea
import ch.ninecode.gl.Transformers
import ch.ninecode.model.BaseVoltage
import ch.ninecode.model.DiagramObject
import ch.ninecode.model.DiagramObjectPoint
import ch.ninecode.model.Element
import ch.ninecode.model.PositionPoint
import ch.ninecode.model.PowerSystemResource
import ch.ninecode.model.PowerTransformer
import ch.ninecode.model.Terminal
import ch.ninecode.model.TopologicalNode

/**
 * Execute simulations using GridLAB-D.
 *
 * Input is in the form of one or more JSON files with all the details of a particular simulation.
 * The terms used in this context are:
 *
 *  - '''Job''': The in-memory representation of a simulation JSON file.
 *  - '''Batch''': Jobs with the same RDF file and CIMReader parameters (used to avoid redundant reading and topological analysis over multiple jobs).
 *  - '''Task''': One topological island including nodes, edges, players and recorders.
 *  - '''Simulation''': One transformer service area (corresponds to one topological island) including the transformer set, nodes, edges, players and recorders.
 *  - '''TransformerSet''': Usually one transformer, but where transformers are ganged together to provide more power it is the parallel combination of transformers.
 *
 * Processing consists of the following steps:
 *
 *  - all input JSON files are read and parsed into Jobs
 *  - Jobs with the same RDF file are gathered into Batches
 *  - For each batch:
 *   -    the RDF file is read into Spark RDDs
 *   -    topological processing adds topological nodes and islands
 *   -    any 'extra' queries (for data to be attached to GeoJSON objects) are performed against Spark RDDs as DataFrames
 *   -    a list of transformer sets is created
 *   -    jobs are converted into individual transformer area tasks, possibly limited by the transformers specified in the job, by:
 *    -        performing the player queries to determine player files that need to be generated
 *    -        performing the recorder queries to determine the recorder files that will be created
 *    -        identifying the nodes and edges that belong to each transformer area (topological island)
 *   -    for each task (spread out over the cluster of executors) do the simulation as the following steps:
 *    -        generate the GridLAB-D glm file
 *    -        query Cassandra for each player file
 *    -        perform the gridlabd load-flow analysis
 *    -        insert the contents of each recorder file into Cassandra
 **/
case class Simulation (session: SparkSession, options: SimulationOptions) extends CIMRDD
{
    type Trafo = String
    type House = String

    if (options.verbose)
    {
        LogManager.getLogger (getClass.getName).setLevel (org.apache.log4j.Level.INFO)
        LogManager.getLogger ("ch.ninecode.gl.TransformerServiceArea").setLevel (org.apache.log4j.Level.INFO)
        LogManager.getLogger ("ch.ninecode.sim.SimulationSparkQuery").setLevel (org.apache.log4j.Level.INFO)
        LogManager.getLogger ("ch.ninecode.cim.CIMNetworkTopologyProcessor").setLevel (org.apache.log4j.Level.INFO)
    }
    implicit val log: Logger = LoggerFactory.getLogger (getClass)
    implicit val spark: SparkSession = session

    val calendar: Calendar = Calendar.getInstance ()
    calendar.setTimeZone (TimeZone.getTimeZone ("GMT"))
    calendar.setTimeInMillis (0L)

    val glm_date_format: SimpleDateFormat = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss z")
    glm_date_format.setCalendar (calendar)

    val iso_date_format: SimpleDateFormat = new SimpleDateFormat ("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    iso_date_format.setCalendar (calendar)

    def read (rdf: String, reader_options: Map[String, String] = Map (), storage_level: StorageLevel = StorageLevel.fromString ("MEMORY_AND_DISK_SER"))
    {
        log.info ("""reading "%s"""".format (rdf))
        val start = System.nanoTime ()
        val elements = session.read.format ("ch.ninecode.cim").options (reader_options).load (rdf)
        log.info (elements.count () + " elements")
        val read = System.nanoTime ()
        log.info ("read: " + (read - start) / 1e9 + " seconds")
        session.sparkContext.getPersistentRDDs.find (_._2.name == "TopologicalIsland") match
        {
            case Some (_) =>
                log.info ("topology exists")
            case None =>
                log.info ("generating topology")
                val ntp = CIMNetworkTopologyProcessor (session)
                val ele = ntp.process (
                    CIMTopologyOptions (
                        identify_islands = true,
                        storage = storage_level))
                log.info (ele.count () + " elements after topology creation")
                val topology = System.nanoTime ()
                log.info ("topology: " + (topology - read) / 1e9 + " seconds")
        }
    }

    def dump (obj: JsonObject): Unit =
    {
        val o = obj.asScala
        val strings = o.map (x ⇒ x._1 + "=" + x._2.toString)
        log.info (strings.mkString (" "))
    }

    def stringify (resultset: Seq[JsonObject]): String =
    {
        val array = Json.createArrayBuilder
        for (i ← resultset.indices)
            array.add (resultset (i))
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
                    Seq ()
            }
        catch
        {
            case je: JsonException ⇒
                log.error (""" string could not be parsed as JSON (%s)""".format (je.getMessage))
                Seq ()
        }
    }

    def using[T <: Closeable, R] (resource: T)(block: T => R): R =
    {
        try
        {
            block (resource)
        }
        finally
        {
            resource.close ()
        }
    }

    def generate_player_csv (player: SimulationPlayerResult, begin: Long, end: Long): List[SimulationPlayer] =
    {
        val from = iso_date_format.format (new Date (begin))
        val to = iso_date_format.format (new Date (end))
        if (begin > end)
        {
            log.error ("""player "%s" has a start time (%s) after the end time (%s)""".format (player.title, from, to))
            return List ()
        }
        // log.info ("""resolving "%s" %s [%s, %s)""".format (player.title, player.name, from, to))
        val span =
            """time >= %s and time <= %s""".format (begin, end)
        val file = "input_data/" + player.name + ".csv"
        List (
            SimulationPlayer (
                player.name,
                player.parent,
                player.`type`,
                player.property,
                file,
                player.mrid,
                begin,
                end,
                player.transform)
        )
    }

    def generate_recorder_csv (recorder: SimulationRecorderResult, start: Long, end: Long): List[SimulationRecorder] =
    {
        val t0 = Calendar.getInstance ()
        t0.setTimeZone (TimeZone.getTimeZone ("GMT"))
        t0.setTimeInMillis (start)
        val t1 = Calendar.getInstance ()
        t1.setTimeZone (TimeZone.getTimeZone ("GMT"))
        t1.setTimeInMillis (end)
        // log.info ("""resolving "%s" %s [%s, %s)""".format (recorder.title, recorder.name, iso_date_format.format (t0.getTime), iso_date_format.format (t1.getTime)))
        val file = "output_data/" + recorder.name + ".csv"
        List (
            SimulationRecorder (
                recorder.name,
                recorder.mrid,
                recorder.parent,
                recorder.`type`,
                recorder.property,
                recorder.unit,
                file,
                recorder.interval,
                recorder.aggregations)
        )
    }

    def pack (string: String): String =
    {
        string.replace ("\n", " ").replaceAll ("[ ]+", " ")
    }

    def positionPointToCoordinates (points: Option[Iterable[PositionPoint]]): Array[(Double, Double)] =
    {
        points match
        {
            case Some (positions) ⇒
                positions.toArray.sortWith (_.sequenceNumber < _.sequenceNumber).map (p ⇒ (p.xPosition.toDouble, p.yPosition.toDouble))
            case _ ⇒
                null
        }
    }

    def diagramObjectPointToCoordinates (points: Option[Iterable[DiagramObjectPoint]]): Array[(Double, Double)] =
    {
        points match
        {
            case Some (positions) ⇒
                positions.toArray.sortWith (_.sequenceNumber < _.sequenceNumber).map (p ⇒ (p.xPosition.toDouble, p.yPosition.toDouble))
            case _ ⇒
                null
        }
    }

    def node_maker (rdd: RDD[(node_id, Iterable[(identifier, (Terminal, Element, BaseVoltage))])]): RDD[(identifier, GLMNode)] =
    {
        val just_one: RDD[(node_id, (identifier, (Terminal, Element, BaseVoltage)))] = rdd.map (x ⇒ (x._1, x._2.head))
        val with_psr: RDD[((node_id, (identifier, (Terminal, Element, BaseVoltage))), PowerSystemResource)] = just_one.keyBy (_._2._2._2.id).join (get [PowerSystemResource].keyBy (_.id)).values

        val world_points = get [PositionPoint].groupBy (_.Location)
        val schematic_points = getOrElse[DiagramObject].keyBy (_.id).join (getOrElse[DiagramObjectPoint].groupBy (_.DiagramObject)).values.map (x ⇒ (x._1.IdentifiedObject_attr, x._2))
        val with_world = with_psr.map (x ⇒ (x._2.Location, x._1)).leftOuterJoin (world_points).values.mapValues (positionPointToCoordinates)
        val with_coordinates: RDD[((node_id, (identifier, (Terminal, Element, BaseVoltage))), Array[(Double, Double)], Array[(Double, Double)])] =
            with_world.map (x ⇒ (x._1._2._2._2.id, (x._1, x._2))).leftOuterJoin (schematic_points).values.mapValues (diagramObjectPointToCoordinates).map (x ⇒ (x._1._1, x._1._2, x._2))
        with_coordinates.map (x ⇒ (x._1._2._1, SimulationNode (x._1._2._2._1.TopologicalNode, x._1._2._2._3.nominalVoltage * 1000.0, x._1._2._2._2.id, if (null != x._2) x._2(0) else null, if (null != x._3) x._3(0) else null)))
    }

    def edge_maker (rdd: RDD[Iterable[(Iterable[(identifier, Terminal)], Element)]]): RDD[(identifier, GLMEdge)] =
    {
        // the terminals may be different for each element, but their TopologicalNode values are the same, and the geometry should be similar, so use the head
        val with_psr: RDD[(Iterable[(Iterable[(identifier, Terminal)], Element)], PowerSystemResource)] = rdd.keyBy (_.head._2.id).join (get [PowerSystemResource].keyBy (_.id)).values
        val world_points = get [PositionPoint].groupBy (_.Location)
        val schematic_points = getOrElse[DiagramObject].keyBy (_.id).join (getOrElse[DiagramObjectPoint].groupBy (_.DiagramObject)).values.map (x ⇒ (x._1.IdentifiedObject_attr, x._2))
        val with_world = with_psr.map (x ⇒ (x._2.Location, x._1)).leftOuterJoin (world_points).values.mapValues (positionPointToCoordinates)
        val with_coordinates: RDD[(Iterable[(Iterable[(identifier, Terminal)], Element)], Array[(Double, Double)], Array[(Double, Double)])] =
            with_world.map (x ⇒ (x._1.head._2.id, (x._1, x._2))).leftOuterJoin (schematic_points).values.mapValues (diagramObjectPointToCoordinates).map (x ⇒ (x._1._1, x._1._2, x._2))
        with_coordinates.map (
            x ⇒
            {
                val id_cn_1 = x._1.head._1.head._2.TopologicalNode
                val id_cn_2 = x._1.head._1.tail.head._2.TopologicalNode
                val elements = x._1.map (_._2).toArray.sortWith (_.id < _.id)
                val raw = GLMEdge.toGLMEdge (elements, id_cn_1, id_cn_2)
                (x._1.head._1.head._1, SimulationEdge (elements(0).id, id_cn_1, id_cn_2, raw, x._2, x._3, null, null))
            }
        )
    }

    def make_tasks (job: SimulationJob, id: String): RDD[SimulationTask] =
    {
        log.info ("""preparing simulation job "%s"""".format (job.name))

        // get all transformer set secondary TopologicalIsland names
        val islands_trafos = get [PowerTransformer]
            .keyBy (_.id)
            .join (
                get [Terminal]
                    .filter (_.ACDCTerminal.sequenceNumber == 2)
                    .keyBy (_.ConductingEquipment))
            .map (x ⇒ (x._2._2.TopologicalNode, x._1)) // (nodeid, trafoid)
            .join (
                get [TopologicalNode]
                .keyBy (_.id))
            .map (x ⇒ (x._2._2.TopologicalIsland, x._2._1)) // (islandid, trafoid)
            .groupByKey.mapValues (_.toArray.sortWith (_ < _).mkString ("_")).persist (options.storage_level).setName (id + "_island_trafo") // (islandid, trafosetname)
        val numtrafos = islands_trafos.count
        log.info ("""%d transformer island%s found""".format (numtrafos, if (1 == numtrafos) "" else "s"))

        // transformer area calculations
        val tsa = TransformerServiceArea (session, options.storage_level)
        // only proceed if topological processing was done (there are TopologicalIslands)
        if (tsa.hasIslands)
        {
            val islands_trafos: RDD[(island_id, identifier)] = tsa.getTransformerServiceAreas
            val trafos_islands: RDD[(identifier, island_id)] = islands_trafos.map (_.swap)

            val q = SimulationSparkQuery (session, options.storage_level, options.verbose)

            // query the players
            log.info ("""querying players""")
            val playersets = session.sparkContext.union (job.players.map (query ⇒ q.executePlayerQuery (query))).groupByKey

            // query the recorders
            log.info ("""querying recorders""")
            val recordersets = session.sparkContext.union (job.recorders.map (query ⇒ q.executeRecorderQuery (query))).groupByKey

            // get the starting and ending times
            val start = job.start_time
            val end = job.end_time

            // maybe reduce the set of islands
            val islands_to_do: RDD[(identifier, island_id)] = if (0 != job.transformers.size) trafos_islands.filter (pair ⇒ job.transformers.contains (pair._1)) else trafos_islands

            val island_helper = new Island (session, options.storage_level)
            val graph_stuff: (Nodes, Edges) = island_helper.queryNetwork (islands_to_do, node_maker, edge_maker)
            val areas: RDD[(identifier, (Iterable[GLMNode], Iterable[GLMEdge]))] = graph_stuff._1.groupByKey.join (graph_stuff._2.groupByKey)
                .persist (options.storage_level).setName (id + "_areas")

            // ToDo: this is backwards, but until we get the simulation classes using service area instead of island, we use the island of the transformer secondary
            val fuckedup_areas: RDD[(island_id, (identifier, Iterable[GLMNode], Iterable[GLMEdge]))] = areas.join (trafos_islands)
                .map (x ⇒ (x._2._2, (x._1, x._2._1._1, x._2._1._2))) // (island, (trafo, [nodes], [edges]))
            val rdd2: RDD[(island_id, (identifier, Iterable[GLMNode], Iterable[GLMEdge], Iterable[SimulationPlayerResult]))] = fuckedup_areas.join (playersets).map (l ⇒ (l._1, (l._2._1._1, l._2._1._2, l._2._1._3, l._2._2))) // (island, (trafo, [nodes], [edges], [players]))
            val rdd3: RDD[(identifier, island_id, Iterable[GLMNode], Iterable[GLMEdge], Iterable[SimulationPlayerResult], Iterable[SimulationRecorderResult])] = rdd2.join (recordersets).map (l ⇒ (l._2._1._1, l._1, l._2._1._2, l._2._1._3, l._2._1._4, l._2._2)) // (island, [nodes], [edges], [players], [recorders])
            log.info ("""generating simulation tasks""")
            rdd3.map (l ⇒
            {
                val players = l._5.flatMap (x ⇒ generate_player_csv (x, start.getTimeInMillis, end.getTimeInMillis))
                val recorders = l._6.flatMap (x ⇒ generate_recorder_csv (x, start.getTimeInMillis, end.getTimeInMillis))
                SimulationTask (
                    l._1, // trafo
                    l._2, // island
                    start.clone.asInstanceOf [Calendar],
                    end.clone.asInstanceOf [Calendar],
                    l._3, // nodes
                    l._4, // edges
                    players,
                    recorders)
            }
            ).persist (options.storage_level).setName (id + "_tasks")
        }
        else
        {
            log.error ("""topology without islands""")
            session.sparkContext.emptyRDD
        }
    }

    def query_cassandra (keyspace: String, `type`: String, start_time: Calendar, finish_time: Calendar) (players: (String, Iterable[(String, String)])): RDD[SimulationPlayerData] =
    {
        val date_format: SimpleDateFormat =
        {
            val format = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss.SSSZ")
            format.setTimeZone (TimeZone.getTimeZone ("UTC"))
            format
        }

        val transformer = players._1
        val mrids_transforms: Iterable[(String, String)] = players._2
        val transform_map: Map[String, String] = mrids_transforms.toMap
        val inclause = mrids_transforms.map (_._1).mkString ("mrid in ('", "','", "')")
        val where = "%s and type = '%s' and time >= '%s' and time < '%s'".format (inclause, `type`, date_format.format (start_time.getTime), date_format.format (finish_time.getTime))
        spark
            .read
            .format ("org.apache.spark.sql.cassandra")
            .options (Map ("table" -> "measured_value", "keyspace" -> keyspace))
            .load
            .filter (where)
            .select ("mrid", "time", "real_a", "imag_a", "period") // ToDo: 3 phase
            .rdd
            .map (
                row ⇒
                {
                    val mrid = row.getString (0)
                    val period = row.getInt (4)
                    val time = row.getTimestamp (1).getTime - period // start time
                    // ToDo: should also check units
                    val factor = `type` match
                    {
                        case "energy" ⇒
                            (60.0 * 60.0 * 1000.0) / period
                        case _ ⇒
                            1.0
                    }
                    val real = row.getDouble (2) * factor
                    val imag = row.getDouble (3) * factor
                    val program = MeasurementTransform.compile (transform_map(mrid))
                    val (re, im) = program.transform (real, imag)
                    // ToDo: should we keep the period so we can tell if a value is missing?
                    SimulationPlayerData (transformer, mrid, `type`, time, re, im)
                }
            )
    }

    def simulate (batch: Seq[SimulationJob]): Seq[String] =
    {
        log.info ("""starting simulations""")
        val ajob = batch.head // assumes that all jobs in a batch should have the same cluster state

        // clean up in case there was a file already loaded
        session.sparkContext.getPersistentRDDs.foreach (
            named ⇒
            {
                named._2.unpersist (false)
                named._2.name = null
            }
        )
        read (ajob.cim, ajob.cimreaderoptions, options.storage_level)

        // get the transformer(s)
        val transformer_data = new Transformers (session, options.storage_level).getTransformers ()
        val tx = transformer_data.keyBy (_.node1) // (low_voltage_node_name, TransformerData)
            .join (get [TopologicalNode].keyBy (_.id)) // (low_voltage_node_name, (TransformerData, TopologicalNode))
            .map (x ⇒ (x._1, (x._2._1, x._2._2.TopologicalIsland))) // (low_voltage_node_name, (TransformerData, island))
            .groupByKey.values
        def toTransformerSet (transformers: Iterable[(TransformerData, String)]): (String, TransformerSet) =
        {
            val island = transformers.head._2
            if (!transformers.forall (_._2 == island))
            // log.error ("""multiple transformer sets for island %s, (%s)""".format (task.island, tx.map (_.transformer_name).mkString (",")))
                log.error ("""not all transformers are members of the same island (%s)""".format (island))
            (island, TransformerSet (transformers.map (_._1).toArray))
        }

        val transformers = tx.map (toTransformerSet).collect.toMap

        var batchno = 1
        val ids = batch.map (
            job ⇒
            {
                val id = java.util.UUID.randomUUID.toString
                log.info ("""starting simulation %s""".format (id))

                val schema = Schema (session, job.output_keyspace, job.replication, options.verbose)
                if (schema.make)
                {
                    // perform the extra queries and insert into the key_value table
                    log.info ("""executing %d extra queries""".format (job.extras.length))
                    job.extras.foreach (
                        extra ⇒
                        {
                            log.info ("""executing "%s" as %s""".format (extra.title, extra.query))
                            val df: DataFrame = session.sql (extra.query)
                            if (df.count > 0)
                            {
                                val fields = df.schema.fieldNames
                                if (!fields.contains ("key") || !fields.contains ("value"))
                                    log.error ("""extra query "%s" schema does not contain either a "key" or a "value" field: %s""".format (extra.title, fields.mkString))
                                else
                                {
                                    val keyindex = df.schema.fieldIndex ("key")
                                    val valueindex = df.schema.fieldIndex ("value")
                                    val keytype = df.schema.fields (keyindex).dataType.simpleString
                                    val valuetype = df.schema.fields (valueindex).dataType.simpleString
                                    if ((keytype != "string") || (valuetype != "string"))
                                        log.error ("""extra query "%s" schema fields key and value are not both strings (key=%s, value=%s)""".format (extra.title, keytype, valuetype))
                                    else
                                        df.rdd.map (row ⇒ (id, extra.title, row.getString (keyindex), row.getString (valueindex))).saveToCassandra (job.output_keyspace, "key_value", SomeColumns ("simulation", "query", "key", "value"))
                                }
                            }
                            else
                                log.warn ("""extra query "%s" returned no rows""".format (extra.title))
                        }
                    )

                    val tasks = make_tasks (job, id)
                    job.save (session, job.output_keyspace, id, tasks)

                    log.info ("""matching tasks to topological islands""")
                    val simulations =
                        tasks.flatMap (
                            task ⇒
                            {
                                transformers.get (task.island) match
                                {
                                    case Some (transformerset) ⇒
                                        List (
                                            SimulationTrafoKreis (
                                                id,
                                                task.island,
                                                transformerset,
                                                task.nodes,
                                                task.edges,
                                                task.start,
                                                task.end,
                                                task.players,
                                                task.recorders,
                                                transformerset.transformer_name + System.getProperty ("file.separator")
                                            )
                                        )
                                    case None ⇒
                                        log.error ("""no transformer sets for island %s""".format (task.island))
                                        List ()
                                }
                            }
                        ).persist (options.storage_level).setName (id + "_simulations")
                    val numsimulations = simulations.count.asInstanceOf [Int]
                    log.info ("""%d GridLAB-D simulation%s to do for simulation %s batch %d""".format (numsimulations, if (1 == numsimulations) "" else "s", id, batchno))

                    if (0 != numsimulations)
                    {
                        log.info ("""storing GeoJSON data""")
                        val geo = SimulationGeometry (session, job.output_keyspace)
                        geo.storeGeometry (simulations)

                        log.info ("""querying measured values""")
                        // determine which players for which transformers
                        val trafo_house = simulations.flatMap (x ⇒ x.players.map (y ⇒ (x.transformer.transformer_name, (y.mrid, y.transform)))).groupByKey.collect

                        // query Cassandra to make one RDD for each trafo
                        val rdds: Array[RDD[SimulationPlayerData]] = trafo_house.map (query_cassandra (job.input_keyspace, "energy", simulations.first.start_time, simulations.first.finish_time))

                        // build one giant RDD
                        val measurements = session.sparkContext.union (rdds)

                        // join to the simulation
                        val packages: RDD[(SimulationTrafoKreis, Map[String, Iterable[SimulationPlayerData]])] = simulations.keyBy (_.transformer.transformer_name).join (measurements.groupBy (_.transformer)).values
                            .mapValues (_.groupBy (_.mrid))

                        log.info ("""performing %d GridLAB-D simulation%s""".format (numsimulations, if (numsimulations == 1) "" else "s"))
                        val runner = SimulationRunner (options.host, job.output_keyspace, options.workdir, options.keep, options.verbose)
                        val results = packages.flatMap (runner.execute).persist (options.storage_level).setName (id + "_results")

                        // save the results
                        log.info ("""saving GridLAB-D simulation results""")
                        results.saveToCassandra (job.output_keyspace, "simulated_value", writeConf = WriteConf (ttl = TTLOption.perRow ("ttl")))
                        log.info ("""saved GridLAB-D simulation results""")
                    }

                    // clean up
                    session.sparkContext.getPersistentRDDs.foreach (
                        named ⇒
                        {
                            named._2.unpersist (false)
                            named._2.name = null
                        }
                    )

                    batchno = batchno + 1
                }
                id
            }
        )
        ids
    }

    def postprocess (ids: Seq[String], jobs: Seq[SimulationJob])
    {
        val keyspaces = jobs.map (_.output_keyspace).distinct
        val lookup = keyspaces.flatMap (
            keyspace ⇒
            {
                spark
                    .read
                    .format ("org.apache.spark.sql.cassandra")
                    .options (Map ("table" -> "simulation", "keyspace" -> keyspace))
                    .load
                    .select ("id", "input_keyspace", "output_keyspace")
                    .rdd
                    .map (row ⇒ (row.getString (0), row.getString (1), row.getString (2)))
                    .collect
            }
        )

        for (simulation ← ids)
        {
            val found = lookup.find (_._1 == simulation)
            found match
            {
                case Some ((id, input, output)) ⇒
                    implicit val access: SimulationCassandraAccess =
                        SimulationCassandraAccess (spark, options.storage_level, id, input, output, options.verbose, options.unittest)
                    // ToDo: this isn't quite right, take the first job matching the output keyspace
                    val batches = jobs.groupBy (_.output_keyspace)
                    val job = batches (output).head
                    job.postprocessors.foreach (
                        processor ⇒
                        {
                            val runner = processor (session, options)
                            runner.run (access)
                        }
                    )
                case None ⇒
                    log.error ("""simulation %s not found in keyspaces (%s)""".format (simulation, lookup.map (_._2).mkString (",")))
            }
        }

    }

    def run (): Seq[String] =
    {
        val jobs = SimulationJob.getAll (options)

        // simulate
        val ids = if (!options.postprocessonly)
        {
            // organize by same RDF, options and output keyspace
            val batches = jobs.groupBy (job ⇒ job.cim + job.optionString + job.output_keyspace).values
            batches.flatMap (simulate).toSeq
        }
        else
        {
            // get all simulations from the output keyspace(s)
            val keyspaces = jobs.map (_.output_keyspace).distinct
            log.info ("""using keyspace%s %s""".format (if (1 < keyspaces.length) "s" else "", keyspaces.mkString (",")))
            keyspaces.flatMap (
                keyspace ⇒
                {
                    spark
                        .read
                        .format ("org.apache.spark.sql.cassandra")
                        .options (Map ("table" -> "simulation", "keyspace" -> keyspace))
                        .load
                        .select ("id")
                        .collect
                        .map (_.getString (0))
                }
            )
        }
        log.info ("""simulation%s %s""".format (if (ids.size > 1) "s" else "", ids.mkString (",")))

        // postprocess
        if (!options.simulationonly)
            postprocess (ids, jobs)

        ids
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
            classOf [ch.ninecode.sim.Simulation],
            classOf [ch.ninecode.sim.SimulationAggregate],
            classOf [ch.ninecode.sim.SimulationEdge],
            classOf [ch.ninecode.sim.SimulationGLMGenerator],
            classOf [ch.ninecode.sim.SimulationJob],
            classOf [ch.ninecode.sim.SimulationNode],
            classOf [ch.ninecode.sim.SimulationOptions],
            classOf [ch.ninecode.sim.SimulationPlayer],
            classOf [ch.ninecode.sim.SimulationPlayerQuery],
            classOf [ch.ninecode.sim.SimulationPlayerResult],
            classOf [ch.ninecode.sim.SimulationRecorder],
            classOf [ch.ninecode.sim.SimulationRecorderQuery],
            classOf [ch.ninecode.sim.SimulationRecorderResult],
            classOf [ch.ninecode.sim.SimulationSparkQuery],
            classOf [ch.ninecode.sim.SimulationTask],
            classOf [ch.ninecode.sim.SimulationTrafoKreis]
        )
    }
}
