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
import com.datastax.driver.core.ConsistencyLevel
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
import ch.ninecode.gl.LineEdge
import ch.ninecode.gl.SwitchEdge
import ch.ninecode.gl.TransformerEdge
import ch.ninecode.model.DiagramObject
import ch.ninecode.model.DiagramObjectPoint
import ch.ninecode.model.PositionPoint
import ch.ninecode.model.PowerSystemResource
import ch.ninecode.model.PowerTransformer
import ch.ninecode.model.Terminal
import ch.ninecode.model.TopologicalNode
import ch.ninecode.net._
import ch.ninecode.util.Schema

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

    val cassandra_date_format: SimpleDateFormat = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss.SSSZ")
    cassandra_date_format.setCalendar (calendar)

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
        val strings = o.map (x => x._1 + "=" + x._2.toString)
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
                case obj: JsonArray =>
                    obj.getValuesAs (classOf[JsonObject]).asScala
                case _ =>
                    log.error ("""not a JsonArray""")
                    Seq ()
            }
        catch
        {
            case je: JsonException =>
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

    def generate_player_csv (begin: Long, end: Long) (player: SimulationPlayerResult): SimulationPlayer =
    {
        val start = if (begin > end)
        {
            val from = iso_date_format.format (new Date (begin))
            val to = iso_date_format.format (new Date (end))
            log.error ("""player "%s" has a start time (%s) after the end time (%s)""".format (player.title, from, to))
            end
        }
        else
            begin
        val file = "input_data/" + player.name + ".csv"
        SimulationPlayer (
            player.name,
            player.parent,
            player.`type`,
            player.property,
            file,
            player.mrid,
            start,
            end,
            player.transform,
            player.synthesis)
    }

    def generate_recorder_csv (recorder: SimulationRecorderResult): SimulationRecorder =
    {
        val file = "output_data/" + recorder.name + ".csv"
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
    }

    def pack (string: String): String =
    {
        string.replace ("\n", " ").replaceAll ("[ ]+", " ")
    }

    def positionPointToCoordinates (points: Option[Iterable[PositionPoint]]): Array[(Double, Double)] =
    {
        points match
        {
            case Some (positions) =>
                positions.toArray.sortWith (_.sequenceNumber < _.sequenceNumber).map (p => (p.xPosition.toDouble, p.yPosition.toDouble))
            case _ =>
                null
        }
    }

    def diagramObjectPointToCoordinates (points: Option[Iterable[DiagramObjectPoint]]): Array[(Double, Double)] =
    {
        points match
        {
            case Some (positions) =>
                positions.toArray.sortWith (_.sequenceNumber < _.sequenceNumber).map (p => (p.xPosition.toDouble, p.yPosition.toDouble))
            case _ =>
                null
        }
    }

    lazy val world_points: RDD[(String, Iterable[PositionPoint])] = get[PositionPoint].groupBy (_.Location)
    lazy val schematic_points: RDD[(String, Iterable[DiagramObjectPoint])] = getOrElse[DiagramObject].keyBy (_.id).join (getOrElse[DiagramObjectPoint].groupBy (_.DiagramObject)).values.map (x => (x._1.IdentifiedObject_attr, x._2))

    def node_maker (rdd: RDD[NodeParts]): RDD[Node] =
    {
        val just_one: RDD[TerminalData] = rdd.map (_.head)
        val with_psr: RDD[(TerminalData, PowerSystemResource)] = just_one.keyBy (_._5.id).join (get[PowerSystemResource].keyBy (_.id)).values

        val with_world = with_psr.map (x => (x._2.Location, x._1)).leftOuterJoin (world_points).values.mapValues (positionPointToCoordinates)
        val with_coordinates =
            with_world.map (x => (x._1._5.id, (x._1, x._2))).leftOuterJoin (schematic_points).values.mapValues (diagramObjectPointToCoordinates).map (x => (x._1._1, x._1._2, x._2))
        with_coordinates.map (x => (x._1._2, SimulationNode (x._1._3.id, x._1._4, x._1._5.id, if (null != x._2) x._2(0) else null, if (null != x._3) x._3(0) else null)))
    }

    def line_maker (rdd: RDD[(LineData, (identifier, GLMNode))]): RDD[(identifier, GLMEdge)] =
    {
        val with_psr = rdd.keyBy (_._1.lines.head.line.id).join (get[PowerSystemResource].keyBy (_.id)).values
        val with_world = with_psr.map (x => (x._2.Location, x._1)).leftOuterJoin (world_points).values.mapValues (positionPointToCoordinates)
        val with_coordinates =
            with_world.map (x => (x._1._1.lines.head.line.id, (x._1, x._2))).leftOuterJoin (schematic_points).values.mapValues (diagramObjectPointToCoordinates).map (x => (x._1._1, x._1._2, x._2))
        with_coordinates.map (
            x =>
            {
                val raw = LineEdge (x._1._1)
                (x._1._2._1, SimulationEdge (raw, x._2, x._3))
            }
        )
    }

    def switch_maker (rdd: RDD[Iterable[(SwitchData, (identifier, GLMNode))]]): RDD[(identifier, GLMEdge)] =
    {
        val switches = rdd.flatMap (
            x =>
            {
                val unique_identifiers = x.map (_._2._1).toList.distinct
                unique_identifiers.map (y => x.find (_._2._1 == y).get)
            }
        )
        val with_psr = switches.keyBy (_._1.switches.head.element.id).join (get[PowerSystemResource].keyBy (_.id)).values
        val with_world = with_psr.map (x => (x._2.Location, x._1)).leftOuterJoin (world_points).values.mapValues (positionPointToCoordinates)
        val with_coordinates =
            with_world.map (x => (x._1._1.switches.head.element.id, (x._1, x._2))).leftOuterJoin (schematic_points).values.mapValues (diagramObjectPointToCoordinates).map (x => (x._1._1, x._1._2, x._2))
        with_coordinates.map (
            x =>
            {
                val raw = SwitchEdge (x._1._1)
                (x._1._2._1, SimulationEdge (raw, x._2, x._3))
            }
        )
    }

    def transformer_maker (rdd: RDD[Iterable[(TransformerSet, (identifier, GLMNode))]]): RDD[(identifier, GLMEdge)] =
    {
        val transformers = rdd.flatMap (
            x =>
            {
                val unique_identifiers = x.map (_._2._1).toList.distinct
                unique_identifiers.map (y => x.find (_._2._1 == y).get)
            }
        )
        val with_psr = transformers.keyBy (_._1.transformers.head.transformer.id).join (get[PowerSystemResource].keyBy (_.id)).values
        val with_world = with_psr.map (x => (x._2.Location, x._1)).leftOuterJoin (world_points).values.mapValues (positionPointToCoordinates)
        val with_coordinates =
            with_world.map (x => (x._1._1.transformers.head.transformer.id, (x._1, x._2))).leftOuterJoin (schematic_points).values.mapValues (diagramObjectPointToCoordinates).map (x => (x._1._1, x._1._2, x._2))
        with_coordinates.map (
            x =>
            {
                val raw = TransformerEdge (x._1._1)
                (x._1._2._1, SimulationEdge (raw, x._2, x._3))
            }
        )
    }

    def make_tasks (job: SimulationJob): RDD[SimulationTask] =
    {
        log.info ("""preparing simulation job "%s"""".format (job.name))

        // get all transformer set secondary TopologicalIsland names
        val islands_trafos: RDD[(island_id, identifier)] = get[PowerTransformer]
            .keyBy (_.id)
            .join (
                get[Terminal]
                    .filter (_.ACDCTerminal.sequenceNumber == 2)
                    .keyBy (_.ConductingEquipment))
            .map (x => (x._2._2.TopologicalNode, x._1)) // (nodeid, trafoid)
            .join (
                get[TopologicalNode]
                .keyBy (_.id))
            .map (x => (x._2._2.TopologicalIsland, x._2._1)) // (islandid, trafoid)
            .groupByKey.mapValues (_.toArray.sortWith (_ < _).mkString ("_")).persist (options.storage_level).setName (s"${job.id}_island_trafo") // (islandid, trafosetname)
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
            val playersets: RDD[(island_id, Iterable[SimulationPlayerResult])] = session.sparkContext.union (job.players.map (query => q.executePlayerQuery (query))).groupByKey

            // query the recorders
            log.info ("""querying recorders""")
            val recordersets: RDD[(island_id, Iterable[SimulationRecorderResult])] = session.sparkContext.union (job.recorders.map (query => q.executeRecorderQuery (query))).groupByKey

            // join players and recorders
            val players_recorders: RDD[(island_id, (Iterable[SimulationPlayerResult], Iterable[SimulationRecorderResult]))] = playersets.join (recordersets)

            // get the starting and ending times
            val start = job.start_time
            val end = job.end_time

            // maybe reduce the set of islands
            val islands_to_do: RDD[(identifier, island_id)] = if (0 != job.transformers.size) trafos_islands.filter (pair => job.transformers.contains (pair._1)) else trafos_islands

            val island_helper = new Island (session, options.storage_level)
            val graph_stuff: (Nodes, Edges) = island_helper.queryNetwork (islands_to_do, node_maker, line_maker, switch_maker, transformer_maker)
            val areas: RDD[(identifier, (Iterable[GLMNode], Iterable[GLMEdge]))] = graph_stuff._1.groupByKey.join (graph_stuff._2.groupByKey)

            log.info ("""generating simulation tasks""")
            val islands_network: RDD[(island_id, (identifier, Iterable[GLMNode], Iterable[GLMEdge]))] = areas.join (trafos_islands)
                .map (x => (x._2._2, (x._1, x._2._1._1, x._2._1._2))) // (island, (trafo, [nodes], [edges]))
            val islands_networks_with_players_and_recorders: RDD[(identifier, island_id, Iterable[GLMNode], Iterable[GLMEdge], Iterable[SimulationPlayerResult], Iterable[SimulationRecorderResult])] = islands_network.join (players_recorders)
                .map (x => (x._2._1._1, x._1, x._2._1._2, x._2._1._3, x._2._2._1, x._2._2._2))  // (trafo, island, [nodes], [edges], [players], [recorders]))
            val ret = islands_networks_with_players_and_recorders.map (
                net =>
                {
                    val players: Iterable[SimulationPlayer] = net._5.map (generate_player_csv (start.getTimeInMillis, end.getTimeInMillis))
                    val recorders: Iterable[SimulationRecorder] = net._6.map (generate_recorder_csv)
                    SimulationTask (
                        net._1, // trafo
                        net._2, // island
                        start.clone.asInstanceOf[Calendar],
                        end.clone.asInstanceOf[Calendar],
                        net._3, // nodes
                        net._4, // edges
                        players,
                        recorders)
                }
            ).persist (options.storage_level).setName (s"${job.id}_tasks")
            ret
        }
        else
        {
            log.error ("""topology without islands""")
            session.sparkContext.emptyRDD
        }
    }

    def timestamp (time: Long, buffer: Int): String =
    {
        val disposable = calendar.clone.asInstanceOf[Calendar]
        disposable.setTimeInMillis (time + buffer)
        cassandra_date_format.format (disposable.getTime)
    }

    def query_value (keyspace: String, buffer: Int)(set: (Trafo, Iterable[SimulationPlayer])): Iterable[(House, Iterable[SimulationPlayerData])] =
    {
        val (transformer, players) = set
        val phases = if (options.three_phase && !options.fake_three_phase) 3 else 1
        val data: Iterable[(House, Iterable[SimulationPlayerData])] = players.map (
            player =>
            {
                val (table, id, typ, where) = if (null == player.synthesis)
                    (
                        "measured_value",
                        player.mrid,
                        player.`type`,
                        s"mrid = '${player.mrid}' and type = '${player.`type`}' and time >= '${timestamp (player.start, -buffer)}' and time <= '${timestamp (player.end, buffer)}'"
                    )
                else
                    (
                        "synthesized_value",
                        player.synthesis,
                        player.`type`,
                        // ToDo: avoid this hard-coded period
                        s"synthesis = '${player.synthesis}' and type = '${player.`type`}' period = 900000 and time >= '${timestamp (player.start, -buffer)}' and time <= '${timestamp (player.end, buffer)}'"
                    )
                val columns = if (phases == 3)
                    Seq ("time", "period", "units", "real_a", "imag_a", "real_b", "imag_b", "real_c", "imag_c")
                else
                    Seq ("time", "period", "units", "real_a", "imag_a")
                val raw =
                    spark
                    .read
                    .format ("org.apache.spark.sql.cassandra")
                    .options (Map ("table" -> table, "keyspace" -> keyspace))
                    .load
                    .filter (where)
                    .selectExpr (columns: _*)
                    .rdd
                val transformed: RDD[SimulationPlayerData] =
                    if (phases == 3)
                        raw
                        .map (
                            row =>
                            {
                                val t = row.getTimestamp (0).getTime
                                val period = row.getInt (1)
                                val units = row.getString (2)
                                val rea = row.getDouble (3)
                                val ima = row.getDouble (4)
                                val reb = row.getDouble (5)
                                val imb = row.getDouble (6)
                                val rec = row.getDouble (7)
                                val imc = row.getDouble (8)
                                SimulationPlayerData (transformer, id, typ, t, period, units, Array (rea, ima, reb, imb, rec, imc))
                            }
                        )
                    else
                        raw
                        .map (
                            row =>
                            {
                                val t = row.getTimestamp (0).getTime
                                val period = row.getInt (1)
                                val units = row.getString (2)
                                val re = row.getDouble (3)
                                val im = row.getDouble (4)
                                SimulationPlayerData (transformer, id, typ, t, period, units, Array (re, im))
                            }
                        )
                (player.mrid, transformed.toLocalIterator.toIterable)
            }
        )
        data
    }

    def simulate (batch: Seq[SimulationJob]): Seq[String] =
    {
        log.info ("""starting simulations""")
        val ajob = batch.head // assumes that all jobs in a batch should have the same cluster state

        // clean up in case there was a file already loaded
        session.sparkContext.getPersistentRDDs.foreach (
            named =>
            {
                named._2.unpersist (false)
                named._2.name = null
            }
        )
        read (ajob.cim, ajob.cimreaderoptions, options.storage_level)

        // get the transformer(s)
        val transformer_data = new Transformers (session, options.storage_level).getTransformers ()
        val tx = transformer_data.keyBy (_.node1.id) // (low_voltage_node_name, TransformerData)
            .join (get[TopologicalNode].keyBy (_.id)) // (low_voltage_node_name, (TransformerData, TopologicalNode))
            .map (x => (x._1, (x._2._1, x._2._2.TopologicalIsland))) // (low_voltage_node_name, (TransformerData, island))
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
            job =>
            {
                log.info ("""starting simulation %s""".format (job.id))

                val schema = Schema (session, "/simulation_schema.sql", job.output_keyspace, job.replication, options.verbose)
                if (schema.make ())
                {
                    // perform the extra queries and insert into the key_value table
                    log.info ("""executing %d extra queries""".format (job.extras.length))
                    job.extras.foreach (
                        extra =>
                        {
                            log.info ("""executing "%s" as %s""".format (extra.title, extra.query))
                            val df: DataFrame = session.sql (extra.query).persist ()
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
                                        df.rdd.map (row => (job.id, extra.title, row.getString (keyindex), row.getString (valueindex))).saveToCassandra (job.output_keyspace, "key_value", SomeColumns ("simulation", "query", "key", "value"))
                                }
                            }
                            else
                                log.warn ("""extra query "%s" returned no rows""".format (extra.title))
                            df.unpersist (false)
                        }
                    )

                    val tasks = make_tasks (job)
                    job.save (session, job.output_keyspace, job.id, tasks)

                    log.info ("""matching tasks to topological islands""")
                    val _simulations =
                        tasks.flatMap (
                            task =>
                            {
                                transformers.get (task.island) match
                                {
                                    case Some (transformerset) =>
                                        List (
                                            SimulationTrafoKreis (
                                                job.id,
                                                task.island,
                                                transformerset,
                                                job.swing,
                                                task.nodes,
                                                task.edges,
                                                task.start,
                                                task.end,
                                                task.players,
                                                task.recorders,
                                                transformerset.transformer_name + System.getProperty ("file.separator")
                                            )
                                        )
                                    case None =>
                                        log.error ("""no transformer sets for island %s""".format (task.island))
                                        List ()
                                }
                            }
                        ).persist (options.storage_level).setName (s"${job.id}_simulations")
                    val numsimulations = _simulations.count.asInstanceOf[Int]
                    log.info ("""%d GridLAB-D simulation%s to do for simulation %s batch %d""".format (numsimulations, if (1 == numsimulations) "" else "s", job.id, batchno))

                    if (0 != numsimulations)
                    {
                        val direction = SimulationDirection (options.workdir, options.verbose)
                        val simulations: RDD[SimulationTrafoKreis] = _simulations.map (x => x.copy (directions = direction.execute (x)))
                            .persist (options.storage_level).setName (s"${job.id}_simulations")
                        _simulations.unpersist (false)

                        log.info ("""storing GeoJSON data""")
                        val geo = SimulationGeometry (session, job.output_keyspace)
                        geo.storeGeometry (job.id, simulations)

                        log.info ("""querying player data""")
                        val query = query_value (job.input_keyspace, job.buffer)_
                        val player_data = simulations
                            .map (simulation => (simulation.transformer.transformer_name, simulation.players))
                            .collect
                            .map (x => (x._1, query (x)))
                        val player_rdd: RDD[(Trafo, Iterable[(House, Iterable[SimulationPlayerData])])] = spark.sparkContext.parallelize (player_data).persist (options.storage_level)

                        log.info ("""performing %d GridLAB-D simulation%s""".format (numsimulations, if (numsimulations == 1) "" else "s"))
                        val runner = SimulationRunner (
                            options.host, job.output_keyspace, options.workdir,
                            options.three_phase, options.fake_three_phase,
                            job.cim_temperature, job.simulation_temperature, job.swing_voltage_factor,
                            options.keep, options.verbose)
                        val raw_results =
                            simulations
                            .keyBy (_.transformer.transformer_name)
                            .join (player_rdd)
                            .values
                            .map (x => runner.execute (x._1, x._2.toMap))
                            .persist (options.storage_level)
                        raw_results.flatMap (_._1).collect.foreach (log.error)
                        val results = raw_results.flatMap (_._2).persist (options.storage_level).setName (s"${job.id}_results")

                        // save the results
                        log.info ("""saving GridLAB-D simulation results""")
                        results.saveToCassandra (job.output_keyspace, "simulated_value", writeConf = WriteConf (ttl = TTLOption.perRow ("ttl"), consistencyLevel = ConsistencyLevel.ANY))
                        log.info ("""saved GridLAB-D simulation results""")
                        results.unpersist (false)
                        raw_results.unpersist (false)
                        player_rdd.unpersist (false)
                    }

                    // clean up
                    session.sparkContext.getPersistentRDDs.foreach (
                        named =>
                        {
                            named._2.unpersist (false)
                            named._2.name = null
                        }
                    )

                    batchno = batchno + 1
                }
                job.id
            }
        )
        ids
    }

    def postprocess (ids: Seq[String], jobs: Seq[SimulationJob])
    {
        val keyspaces = jobs.map (_.output_keyspace).distinct
        val lookup = keyspaces.flatMap (
            keyspace =>
            {
                spark
                    .read
                    .format ("org.apache.spark.sql.cassandra")
                    .options (Map ("table" -> "simulation", "keyspace" -> keyspace))
                    .load
                    .select ("id", "input_keyspace", "output_keyspace")
                    .where (s"id in ${ids.mkString ("('", "','", "')")}")
                    .rdd
                    .map (row => (row.getString (0), row.getString (1), row.getString (2)))
                    .collect
            }
        )

        for (simulation ← ids)
        {
            val found = lookup.find (_._1 == simulation)
            found match
            {
                case Some ((id, input, output)) =>
                    implicit val access: SimulationCassandraAccess =
                        SimulationCassandraAccess (spark, options.storage_level, id, input, output, options.verbose, options.unittest)
                    // ToDo: this isn't quite right, take the first job matching the output keyspace
                    val batches = jobs.groupBy (_.output_keyspace)
                    val job = batches (output).head
                    job.postprocessors.foreach (
                        processor =>
                        {
                            val runner = processor (session, options)
                            runner.run (access)
                        }
                    )
                case None =>
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
            val batches = jobs.groupBy (job => job.cim + job.optionString + job.output_keyspace).values
            batches.flatMap (simulate).toSeq
        }
        else
        {
            // get all simulations from the output keyspace(s)
            val keyspaces = jobs.map (_.output_keyspace).distinct
            log.info ("""using keyspace%s %s""".format (if (1 < keyspaces.length) "s" else "", keyspaces.mkString (",")))
            keyspaces.flatMap (
                keyspace =>
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
            classOf[ch.ninecode.sim.Simulation],
            classOf[ch.ninecode.sim.SimulationAggregate],
            classOf[ch.ninecode.sim.SimulationDirectionGenerator],
            classOf[ch.ninecode.sim.SimulationEdge],
            classOf[ch.ninecode.sim.SimulationExtraQuery],
            classOf[ch.ninecode.sim.SimulationGLMGenerator],
            classOf[ch.ninecode.sim.SimulationJob],
            classOf[ch.ninecode.sim.SimulationNode],
            classOf[ch.ninecode.sim.SimulationOptions],
            classOf[ch.ninecode.sim.SimulationPlayer],
            classOf[ch.ninecode.sim.SimulationPlayerData],
            classOf[ch.ninecode.sim.SimulationPlayerQuery],
            classOf[ch.ninecode.sim.SimulationPlayerResult],
            classOf[ch.ninecode.sim.SimulationRecorder],
            classOf[ch.ninecode.sim.SimulationRecorderQuery],
            classOf[ch.ninecode.sim.SimulationRecorderResult],
            classOf[ch.ninecode.sim.SimulationResult],
            classOf[ch.ninecode.sim.SimulationSparkQuery],
            classOf[ch.ninecode.sim.SimulationTask],
            classOf[ch.ninecode.sim.SimulationTrafoKreis]
        )
    }
}
