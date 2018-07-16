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

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._

import com.datastax.driver.core.Cluster
import com.datastax.spark.connector.SomeColumns
import com.datastax.spark.connector._
import org.apache.log4j.LogManager
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.apache.spark.HashPartitioner
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.cim.CIMNetworkTopologyProcessor
import ch.ninecode.cim.CIMRDD
import ch.ninecode.gl.TransformerSet
import ch.ninecode.gl.TData
import ch.ninecode.gl.Transformers
import ch.ninecode.model.BaseVoltage
import ch.ninecode.model.ConductingEquipment
import ch.ninecode.model.Element
import ch.ninecode.model.PositionPoint
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
    if (options.verbose)
        LogManager.getLogger (getClass.getName).setLevel (org.apache.log4j.Level.INFO)
    implicit val log: Logger = LoggerFactory.getLogger (getClass)
    implicit val spark: SparkSession = session

    val calendar: Calendar = Calendar.getInstance ()
    calendar.setTimeZone (TimeZone.getTimeZone ("GMT"))
    calendar.setTimeInMillis (0L)

    val glm_date_format: SimpleDateFormat = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss z")
    glm_date_format.setCalendar (calendar)

    val iso_date_format: SimpleDateFormat = new SimpleDateFormat ("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    iso_date_format.setCalendar (calendar)

    def read (rdf: String, reader_options: Map[String,String] = Map(), storage_level: StorageLevel = StorageLevel.fromString ("MEMORY_AND_DISK_SER"))
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
            case None =>
                val ntp = new CIMNetworkTopologyProcessor (session, storage_level)
                val ele = ntp.process (true)
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

    def using[T <: Closeable, R](resource: T)(block: T => R): R =
    {
        try { block (resource) }
        finally { resource.close () }
    }

    def generate_player_csv (player: SimulationPlayerResult, begin: Long, end: Long): List[SimulationPlayer] =
    {
        val from = iso_date_format.format (new Date (begin))
        val   to = iso_date_format.format (new Date (end))
        if (begin > end)
        {
            log.error ("""player "%s" has a start time (%s) after the end time (%s)""".format (player.title, from, to))
            return List()
        }
        // log.info ("""resolving "%s" %s [%s, %s)""".format (player.title, player.name, from, to))
        val span = """time >= %s and time <= %s""".format (begin, end)
        val file = "input_data/" + player.name + ".csv"
        val sql = player.cassandraquery.format (player.substitutions: _*) + " and " + span + " allow filtering"
        List (
            SimulationPlayer (
                player.name,
                player.parent,
                player.`type`,
                player.property,
                file,
                sql,
                begin,
                end)
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

    def iso_parse (s: String): Calendar =
    {
        val ret = Calendar.getInstance ()
        ret.setTime (iso_date_format.parse (s))
        ret
    }

    def pack (string: String): String =
    {
        string.replace ("\n", " ").replaceAll ("[ ]+", " ")
    }

    def toCoordinates (points: Option[Iterable[PositionPoint]]): Array[(Double, Double)] =
    {
        points match
        {
            case Some (positions) ⇒
                positions.toArray.sortWith (_.sequenceNumber < _.sequenceNumber).map (p ⇒ (p.xPosition.toDouble, p.yPosition.toDouble))
            case _ ⇒
                null
        }
    }

    def toCoordinate (terminal: Int, points: Option[Iterable[PositionPoint]]): (Double, Double) =
    {
        val coordinates = toCoordinates (points)
        if (null != coordinates)
        {
            if (terminal == 1)
                coordinates(0)
            else
                coordinates(coordinates.length - 1)
        }
        else
            null
    }

    def pickone (singles: Array[String]) (nodes: Iterable[(String, ch.ninecode.sim.SimulationNode)]): (String, SimulationNode) =
    {
        nodes.find (node ⇒ singles.contains (node._2.equipment)) match
        {
            case Some (node) ⇒ node
            case _ ⇒ nodes.head // just take the first
        }
    }

    def queryNetwork: RDD[(String, (Iterable[SimulationNode], Iterable[Iterable[SimulationEdge]]))] =
    {
        log.info ("""resolving nodes and edges by topological island""")

        // get nodes by TopologicalIsland
        val members = get[TopologicalNode].map (node ⇒ (node.id, node.TopologicalIsland)) // (nodeid, islandid)
        // get terminals by TopologicalIsland
        val terminals = get[Terminal].keyBy (_.TopologicalNode).join (members).map (x ⇒ (x._2._2, x._2._1)) // (islandid, terminal)
        // get equipment in the TopologicalIsland and associated Terminal
        val equipment_terminals = get[ConductingEquipment].keyBy (_.id).join (terminals.keyBy (_._2.ConductingEquipment)).values.map (x ⇒ (x._2._1, x._1, x._2._2)) // (island, equipment, terminal)
        // make a list of all single terminal equipment as the preferred association to the node
        val singles = equipment_terminals.groupBy (_._3.ConductingEquipment).filter (1 == _._2.size).map (_._2.head._2.id).collect
        // compose Island, ConductingEquipment, Terminal, and PositionPoint(s)
        val equipment = equipment_terminals.keyBy (_._2.Equipment.PowerSystemResource.Location).leftOuterJoin (get[PositionPoint].groupBy (_.Location))
            .map (x ⇒ (x._2._1._1, x._2._1._2, x._2._1._3, x._2._2)).distinct // (islandid, equipment, terminal, [points])
        // get all nodes with their voltage - it is assumed that some equipment on the transformer secondary (secondaries) has a voltage
        // but this doesn't include the transformer primary node - it's not part of the topology
        // ToDo: fix this 1kV multiplier on the voltages
        val nodes = equipment.keyBy (_._2.BaseVoltage).join (get[BaseVoltage].keyBy (_.id)).values.map (
            node ⇒ (node._1._1, SimulationNode (node._1._3.TopologicalNode, node._1._2.id, toCoordinate (node._1._3.ACDCTerminal.sequenceNumber, node._1._4), node._2.nominalVoltage * 1000.0))
        ).groupBy (_._2.id_seq).values.map (pickone (singles)) // (islandid, node)
        // get all equipment with two nodes in the island that separate different TopologicalNode (these are the edges)
        val two_terminal_equipment = equipment.groupBy (_._2.id).filter (
            edge ⇒ edge._2.size > 1 && (edge._2.head._1 == edge._2.tail.head._1) && (edge._2.head._3.TopologicalNode != edge._2.tail.head._3.TopologicalNode)
        )
        // convert ConductingEquipment to Element with Terminal(s) and PositionPoints
        // all copies of the PositionPoints will be the same or just parallel conductors, so we arbitrarily take the head
        val elements = get[Element]("Elements").keyBy (_.id).join (two_terminal_equipment)
            .values.map (x ⇒ (x._2.head._1, (x._1, x._2.map (_._3), x._2.head._4)))
        // combine parallel equipment
        val eq3 = elements.keyBy (_._2._2.map (_.TopologicalNode).toArray.sortWith (_ < _).mkString ("_")).groupByKey.values
        // the island is the same, so pull it out of the Iterator
        val eq4 = eq3.map (x ⇒ (x.head._1, x.map (y ⇒ y._2)))
        // create the edges keyed by island
        val edges = eq4.map (v ⇒
            (v._1,
                v._2.map (
                    edge ⇒ SimulationEdge (edge._1.id, edge._2.head.TopologicalNode, edge._2.tail.head.TopologicalNode, edge._1, toCoordinates (edge._3))
                )
            )
        )
        nodes.groupByKey.join (edges.groupByKey).cache
    }

    def make_tasks (job: SimulationJob): RDD[SimulationTask] =
    {
        log.info ("""preparing simulation job "%s"""".format (job.name))

        // get all transformer set secondary TopologicalIsland names
        val islands_trafos = get[PowerTransformer]
            .keyBy (_.id)
            .join (
                get[Terminal]
                .filter (_.ACDCTerminal.sequenceNumber == 2)
                .keyBy (_.ConductingEquipment))
            .map (x ⇒ (x._2._2.TopologicalNode, x._1)) // (nodeid, trafoid)
            .join (
                get[TopologicalNode]
                .keyBy (_.id))
            .map (x ⇒ (x._2._2.TopologicalIsland, x._2._1)) // (islandid, trafoid)
            .groupByKey.mapValues (_.toArray.sortWith (_ < _).mkString ("_")).cache // (islandid, trafosetname)
        val numtrafos = islands_trafos.count
        log.info ("""%d transformer island%s found""".format (numtrafos, if (1 == numtrafos) "" else "s"))

        val q = SimulationSparkQuery (session, options.verbose)

        // query the players
        val emptyPlayers = session.sparkContext.emptyRDD[(String, SimulationPlayerResult)]
        def combinePlayers (acc: RDD[(String, SimulationPlayerResult)], query: SimulationPlayerQuery): RDD[(String, SimulationPlayerResult)] = acc.union (q.executePlayerQuery (query))
        val playersets = job.players.foldLeft (emptyPlayers) (combinePlayers).groupByKey.flatMapValues (List (_)).cache

        // query the recorders
        val emptyRecorders = session.sparkContext.emptyRDD[(String, SimulationRecorderResult)]
        def combineRecorders (acc: RDD[(String, SimulationRecorderResult)], query: SimulationRecorderQuery): RDD[(String, SimulationRecorderResult)] = acc.union (q.executeRecorderQuery (query))
        val recordersets = job.recorders.foldLeft (emptyRecorders) (combineRecorders).groupByKey.flatMapValues (List (_)).cache

        // get the starting and ending times
        val start = iso_parse (job.interval("start"))
        val end = iso_parse (job.interval("end"))

        // process the list of islands
        val islands = (if (0 != job.transformers.size) islands_trafos.filter (island ⇒ job.transformers.contains (island._2)) else islands_trafos).map (_._1)
        val rdd1 = queryNetwork.join (islands.keyBy (x ⇒ x)).map (x ⇒ (x._1,  x._2._1))  // (island, ([nodes], [edges]))
        val rdd2 = rdd1.join (playersets).map (l ⇒ (l._1, (l._2._1._1, l._2._1._2, l._2._2))).cache  // (island, ([nodes], [edges], [players]))
        val rdd3 = rdd2.join (recordersets).map (l ⇒ (l._1, l._2._1._1, l._2._1._2, l._2._1._3, l._2._2)).cache  // (island, [nodes], [edges], [players], [recorders])
        rdd3.map (l ⇒
            {
                val players = l._4.flatMap (x ⇒ generate_player_csv (x, start.getTimeInMillis, end.getTimeInMillis)).toArray
                val recorders = l._5.flatMap (x ⇒ generate_recorder_csv (x, start.getTimeInMillis, end.getTimeInMillis)).toArray
                SimulationTask (
                    l._1, // island
                    start.clone.asInstanceOf[Calendar],
                    end.clone.asInstanceOf[Calendar],
                    l._2, // nodes
                    l._3, // edges
                    players,
                    recorders)
            }
        ).cache
    }

    def process (batch: Seq[SimulationJob]): String =
    {
        val storage = StorageLevel.fromString (options.storage)

        val id = java.util.UUID.randomUUID.toString
        log.info ("""starting simulation %s""".format (id))

        val ajob = batch.head // assumes that all jobs in a batch should have the same cluster state
        // clean up in case there was a file already loaded
        session.sparkContext.getPersistentRDDs.foreach (
            named ⇒
            {
                named._2.unpersist (false)
                named._2.name = null
            }
        )
        read (ajob.cim, ajob.cimreaderoptions, storage)

        // perform the extra queries and insert into the key_value table
        log.info ("""executing %d extra queries""".format (ajob.extras.length))
        ajob.extras.foreach (
            extra ⇒
            {
                log.info ("""executing %s""".format (extra.query))
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
                        val keytype = df.schema.fields(keyindex).dataType.simpleString
                        val valuetype = df.schema.fields(valueindex).dataType.simpleString
                        if ((keytype != "string") || (valuetype != "string"))
                            log.error ("""extra query "%s" schema fields key and value are not both strings (key=%s, value=%s)""".format (extra.title, keytype, valuetype))
                        else
                            df.rdd.map (row ⇒ (id, extra.title, row.getString (keyindex), row.getString (valueindex))).saveToCassandra (options.keyspace, "key_value", SomeColumns ("simulation", "query", "key", "value"))
                    }
                }
                else
                    log.warn ("""extra query "%s" returned no rows""".format (extra.title))
            }
        )

        // get the transformer(s)
        val tdata = new Transformers (session, storage).getTransformerData (topological_nodes = true)
        val tx = tdata.keyBy (_.node1) // (low_voltage_node_name, TData)
            .join (get[TopologicalNode].keyBy (_.id)) // (low_voltage_node_name, (TData, TopologicalNode))
            .map (x ⇒ (x._1, (x._2._1, x._2._2.TopologicalIsland))) // (low_voltage_node_name, (TData, island))
            .groupByKey.values // Iterable[(TData, island)]
        def toTransformerSet (transformers: Iterable[(TData, String)]): (String, TransformerSet) =
        {
            val island = transformers.head._2
            if (!transformers.forall (_._2 == island))
            // log.error ("""multiple transformer sets for island %s, (%s)""".format (task.island, tx.map (_.transformer_name).mkString (",")))
                log.error ("""not all transformers are members of the same island (%s)""".format (island))
            (island, TransformerSet (transformers.map (_._1).toArray))
        }
        val transformers = tx.map (toTransformerSet).collect.toMap

        var batchno = 1
        batch.foreach (
            job ⇒
            {
                val tasks = make_tasks (job)
                val numtasks: Long = tasks.count
                log.info ("""%d task%s to do for simulation %s batch %d""".format (numtasks, if (1 == numtasks) "" else "s", id, batchno))
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
                    ).cache
                simulations.name = "simulations"
                val numsimulations = simulations.count.asInstanceOf[Int]
                log.info ("""%d GridLAB-D simulation%s to do for simulation %s batch %d""".format (numsimulations, if (1 == numsimulations) "" else "s", id, batchno))

                // spread the simulations over the cluster
                val exec = SimulationExecutors (session)
                val map = exec.getActiveWorkerHostSet
                val executors = map.keys.toArray
                log.info ("""executors: %s""".format (executors.mkString (", ")))

                val gridlabd = if (0 != executors.length)
                {
                    val raw = simulations.zipWithIndex.map (x ⇒ (x._2, x._1)).partitionBy (new HashPartitioner (numsimulations)).map (_._2).cache
                    raw.name = "raw"
                    val raw_count = raw.count
                    log.info ("""raw RDD has %d elements in %d partitions""".format (raw_count, raw.getNumPartitions))
    //                raw.partitions.foreach (
    //                    partition ⇒
    //                    {
    //                        val locations = raw.preferredLocations (partition)
    //                        log.info ("""partition %s (hash %s) has preferred location(s) %s""".format (partition.index, partition.hashCode, locations.mkString (", ")))
    //                    }
    //                )
                    raw.coalesce (executors.length, false, Some(SimulationCoalescer (executors))).cache
                }
                else
                    simulations
                gridlabd.name = "gridlabd"
                val gridlabd_count = gridlabd.count
                log.info ("""gridlabd RDD has %d elements in %d partitions""".format (gridlabd_count, gridlabd.getNumPartitions))
//                gridlabd.partitions.foreach (
//                    partition ⇒
//                    {
//                        val locations = gridlabd.preferredLocations (partition)
//                        log.info ("""partition %s (hash %s) has preferred location(s) %s""".format (partition.index, partition.hashCode, locations.mkString (", ")))
//                    }
//                )

                log.info ("""performing %d GridLAB-D simulations on the cluster""".format (gridlabd_count))
                val results = gridlabd.map (
                    trafokreis ⇒
                    {
                        val runner = SimulationRunner (options.host, options.keyspace, options.batchsize, options.workdir, options.keep, options.verbose)
                        runner.execute (trafokreis)
                    }
                ).cache
                val results_count = results.count
                log.info ("""gridlabd results has %d elements""".format (results_count))
                val failures = results.filter (!_._1)
                if (!failures.isEmpty)
                {
                    val failed = failures.count
                    log.error ("%s %s not successful:\n\n".format (failed, if (failed > 1L) "tasks were" else "task was"))
                    log.error (failures.map (_._2).collect.mkString ("\n"))
                }
                else
                    log.info ("all tasks were successful")

                // clean up
                session.sparkContext.getPersistentRDDs.foreach (
                    named ⇒
                    {
                        named._2.unpersist (false)
                        named._2.name = null
                    }
                )

                // insert the simulation json into simulation table
                val record = Json.createObjectBuilder
                record.add ("id", id)
                record.add ("name", ajob.name)
                record.add ("description", ajob.description)
                record.add ("cim", ajob.cim)
                val cimreaderoptions = Json.createObjectBuilder
                for (x ← ajob.cimreaderoptions) cimreaderoptions.add (x._1, x._2)
                record.add ("cimreaderoptions", cimreaderoptions)
                val interval = Json.createObjectBuilder
                for (x ← ajob.interval) interval.add (x._1, x._2)
                record.add ("interval", interval)
                val players = Json.createArrayBuilder
                if (0 != tasks.count)
                    for (x ← tasks.first.players)
                    {
                        val player = Json.createObjectBuilder
                        player.add ("name", x.name)
                        player.add ("mrid", x.parent)
                        player.add ("typ", x.typ)
                        player.add ("property", x.property)
                        // player.add ("file", x.file)
                        // player.add ("sql", x.sql)
                        // player.add ("start", iso_date_format.format (new Date (x.start)))
                        // player.add ("end", iso_date_format.format (new Date (x.end)))
                        players.add (player)
                    }
                record.add ("players", players)
                val recorders = Json.createArrayBuilder
                if (0 != tasks.count)
                    for (x ← tasks.first.recorders)
                    {
                        val recorder = Json.createObjectBuilder
                        recorder.add ("name", x.name)
                        recorder.add ("mrid", x.mrid)
                        // recorder.add ("parent", x.parent)
                        recorder.add ("typ", x.typ)
                        recorder.add ("property", x.property)
                        recorder.add ("unit", x.unit)
                        // recorder.add ("file", x.file)
                        recorder.add ("interval", x.interval.toString)
                        // recorder.add ("aggregations", x.aggregations.map (y ⇒ if (y.time_to_live == "") y.intervals.toString else y.intervals.toString + "@" + y.time_to_live.substring (y.time_to_live.lastIndexOf (" ") + 1)).mkString (","))
                        recorders.add (recorder)
                    }
                record.add ("recorders", recorders)
                val trans = Json.createArrayBuilder
                for (x ← ajob.transformers) trans.add (x)
                record.add ("transformers", trans)

                val string = new StringWriter
                val properties = new util.HashMap[String, AnyRef](1)
                properties.put (JsonGenerator.PRETTY_PRINTING, "true")
                val writer = Json.createWriterFactory (properties).createWriter (string)
                writer.write (record.build)
                writer.close ()

                val cluster = Cluster.builder.addContactPoint (options.host).build
                val c = cluster.connect
                val prepared = c.prepare ("""insert into %s.simulation json ?""".format (options.keyspace))
                val bound = prepared.bind ()
                bound.setString (0, string.toString)
                c.execute (bound)

                batchno = batchno + 1
            }
        )
        id
    }

    def run (): Seq[String] =
    {
        val schema = Schema (session, options)
        if (schema.make)
        {
            val jobs = SimulationJob.getAll (options)
            // organize by same RDF and same options
            val batches = jobs.groupBy (job ⇒ job.cim + job.optionString)
            batches.values.map (process).toSeq
        }
        else
            List()
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
            classOf[ch.ninecode.sim.SimulationCassandraInsert],
            classOf[ch.ninecode.sim.SimulationCassandraQuery],
            classOf[ch.ninecode.sim.SimulationEdge],
            classOf[ch.ninecode.sim.SimulationGLMGenerator],
            classOf[ch.ninecode.sim.SimulationJob],
            classOf[ch.ninecode.sim.SimulationNode],
            classOf[ch.ninecode.sim.SimulationOptions],
            classOf[ch.ninecode.sim.SimulationPlayer],
            classOf[ch.ninecode.sim.SimulationPlayerQuery],
            classOf[ch.ninecode.sim.SimulationPlayerResult],
            classOf[ch.ninecode.sim.SimulationRecorder],
            classOf[ch.ninecode.sim.SimulationRecorderQuery],
            classOf[ch.ninecode.sim.SimulationRecorderResult],
            classOf[ch.ninecode.sim.SimulationSparkQuery],
            classOf[ch.ninecode.sim.SimulationTask],
            classOf[ch.ninecode.sim.SimulationTrafoKreis]
        )
    }
}
