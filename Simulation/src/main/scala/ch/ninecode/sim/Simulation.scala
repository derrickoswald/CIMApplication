package ch.ninecode.sim

import java.io.Closeable
import java.io.StringReader
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

import javax.json.Json
import javax.json.JsonArray
import javax.json.JsonException
import javax.json.JsonObject
import javax.json.stream.JsonGenerator

import scala.collection.JavaConversions.mapAsJavaMap
import scala.collection.JavaConverters.mapAsScalaMapConverter
import scala.collection.JavaConverters.asScalaBufferConverter

import org.apache.log4j.LogManager
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.apache.spark.sql.Row
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import com.datastax.oss.driver.api.core.ConsistencyLevel
import com.datastax.spark.connector._
import com.datastax.spark.connector.writer.TTLOption
import com.datastax.spark.connector.writer.WriteConf

import ch.ninecode.cim.CIMNetworkTopologyProcessor
import ch.ninecode.cim.CIMRDD
import ch.ninecode.cim.CIMTopologyOptions
import ch.ninecode.model.TopologicalNode
import ch.ninecode.net.Island.identifier
import ch.ninecode.net.Island.island_id
import ch.ninecode.net.TransformerData
import ch.ninecode.net.TransformerServiceArea
import ch.ninecode.net.TransformerSet
import ch.ninecode.net.Transformers
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
final case class Simulation (session: SparkSession, options: SimulationOptions) extends CIMRDD
{
    type Trafo = String
    type House = String
    type Typ = String

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
        log.info (s"""reading "$rdf"""")
        val start = System.nanoTime ()
        val elements = session.read.format ("ch.ninecode.cim").options (reader_options).load (rdf)
        log.info (s"${elements.count ()} elements")
        val read = System.nanoTime ()
        log.info (s"read: ${(read - start) / 1e9} seconds")
        session.sparkContext.getPersistentRDDs.find (_._2.name == "TopologicalIsland") match
        {
            case Some (_) =>
                log.info ("topology exists")
            case None =>
                log.info ("generating topology")
                val ntp = CIMNetworkTopologyProcessor (
                    session,
                    CIMTopologyOptions (
                        identify_islands = true,
                        storage = storage_level))
                val ele = ntp.process
                log.info (s"${ele.count ()} elements after topology creation")
                val topology = System.nanoTime ()
                log.info (s"topology: ${(topology - read) / 1e9} seconds")
        }
    }

    def dump (obj: JsonObject): Unit =
    {
        val o = obj.asScala
        val strings = o.map (x => s"${x._1}=${x._2.toString}")
        log.info (strings.mkString (" "))
    }

    def stringify (resultset: Seq[JsonObject]): String =
    {
        val array = Json.createArrayBuilder
        for (i <- resultset.indices)
            array.add (resultset (i))
        val string = new StringWriter
        val properties = Map[String, AnyRef](
            JsonGenerator.PRETTY_PRINTING -> "true")
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
                log.error (s" string could not be parsed as JSON (${je.getMessage})")
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
            log.error (s"""player "${player.title}" has a start time ($from) after the end time ($to)""")
            end
        }
        else
            begin
        val file = s"input_data/${player.name}.csv"
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
        val file = s"output_data/${recorder.name}.csv"
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

    @SuppressWarnings (Array ("org.wartremover.warts.AsInstanceOf"))
    def dupTime (calendar: Calendar): Calendar =
    {
        calendar.clone.asInstanceOf [Calendar]
    }

    def plural (number: Int): String = if (1 == number) "" else "s"

    def make_tasks (job: SimulationJob): RDD[SimulationTask] =
    {
        log.info (s"""preparing simulation job "${job.name}"""")

        // transformer area calculations
        val tsa = TransformerServiceArea (session, options.storage_level)
        // only proceed if topological processing was done (there are TopologicalIslands)
        if (tsa.hasIslands)
        {
            val islands_trafos: RDD[(island_id, identifier)] = tsa.getTransformerServiceAreas
            val numareas = islands_trafos.map (_._2).distinct.count.toInt
            log.info (s"""$numareas transformer service area${plural (numareas)} found""")

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

            log.info ("""generating simulation tasks""")

            // maybe reduce the set of islands
            val islands_to_do: RDD[(island_id, identifier)] =
                if (0 != job.transformers.size)
                    islands_trafos.filter (pair => job.transformers.contains (pair._2))
                else
                    islands_trafos

            val island_helper = new SimulationIsland (session, options.storage_level)
            val graph_stuff = island_helper.queryNetwork (islands_to_do.map (_.swap))
            val areas: RDD[(identifier, (Iterable[SimulationNode], Iterable[SimulationEdge]))] =
                graph_stuff
                    ._1.asInstanceOf[RDD[(identifier, SimulationNode)]]
                    .groupByKey
                    .join (
                        graph_stuff._2.asInstanceOf[RDD[(identifier, SimulationEdge)]]
                        .groupByKey)

            val players_and_recorders_by_area =
                islands_trafos
                .join (players_recorders)
                .map (
                    (x: (island_id, (identifier, (Iterable[SimulationPlayerResult], Iterable[SimulationRecorderResult])))) =>
                    {
                        val (_, (area, (players, recorders))) = x
                        (area, (players, recorders))
                    }
                )
                .groupByKey
                .map (
                    (x: (identifier, Iterable[(Iterable[SimulationPlayerResult], Iterable[SimulationRecorderResult])])) =>
                    {
                        val (area, pla_rec) = x
                        (area, (pla_rec.flatMap (_._1), pla_rec.flatMap (_._2)))
                    }
                )
            val ret =
                areas
                .join (players_and_recorders_by_area)
                .map (
                    (x: (identifier, ((Iterable[SimulationNode], Iterable[SimulationEdge]), (Iterable[SimulationPlayerResult], Iterable[SimulationRecorderResult])))) =>
                    {
                        val (area, ((nodes, edges), (pla, rec))) = x
                        val players: Iterable[SimulationPlayer] = pla.map (generate_player_csv (start.getTimeInMillis, end.getTimeInMillis))
                        val recorders: Iterable[SimulationRecorder] = rec.map (generate_recorder_csv)
                        SimulationTask (
                            area, // trafo
                            area, // island
                            dupTime (start),
                            dupTime (end),
                            nodes, // nodes
                            edges, // edges
                            players,
                            recorders)
                    }
                )
                .persist (options.storage_level)
                .setName (s"${job.id}_tasks")
            ret
        }
        else
        {
            log.error ("""topology without islands""")
            session.sparkContext.emptyRDD
        }
    }

    def columns (primaryKey: String) (phases: Int): Seq[String] =
    {
        if (phases == 3)
            Seq (primaryKey, "type", "time", "period", "units", "real_a", "imag_a", "real_b", "imag_b", "real_c", "imag_c")
        else
            Seq (primaryKey, "type", "time", "period", "units", "real_a", "imag_a")
    }

    def toThreePhase (transformer: String) (row: Row): SimulationPlayerData =
    {
        val id = row.getString (0)
        val typ = row.getString (1)
        val t = row.getTimestamp (2).getTime
        val period = row.getInt (3)
        val units = row.getString (4)
        val rea = row.getDouble (5)
        val ima = row.getDouble (6)
        val reb = row.getDouble (7)
        val imb = row.getDouble (8)
        val rec = row.getDouble (9)
        val imc = row.getDouble (10)
        SimulationPlayerData (transformer, id, typ, t, period, units, Array (rea, ima, reb, imb, rec, imc))
    }

    def toOnePhase (transformer: String) (row: Row): SimulationPlayerData =
    {
        val id = row.getString (0)
        val typ = row.getString (1)
        val t = row.getTimestamp (2).getTime
        val period = row.getInt (3)
        val units = row.getString (4)
        val re = row.getDouble (5)
        val im = row.getDouble (6)
        SimulationPlayerData (transformer, id, typ, t, period, units, Array (re, im))
    }

    def queryValues (job: SimulationJob, simulations: RDD[SimulationTrafoKreis]): RDD[(Trafo, Iterable[(House, Iterable[SimulationPlayerData])])] =
    {
        val phases = if (options.three_phase && !options.fake_three_phase) 3 else 1
        val start = job.start_time.clone ().asInstanceOf[Calendar]
        start.add (Calendar.MILLISECOND, - job.buffer)
        val end = job.end_time.clone ().asInstanceOf[Calendar]
        end.add (Calendar.MILLISECOND, job.buffer)
        val low = cassandra_date_format.format (start.getTime)
        val high = cassandra_date_format.format (end.getTime)
        val time_filter = s"time >= '$low' and time <= '$high'"
        val measured_df = spark
            .read
            .format ("org.apache.spark.sql.cassandra")
            .options (Map ("table" -> "measured_value", "keyspace" -> job.input_keyspace))
            .load
            .filter (time_filter)
            .selectExpr (columns ("mrid") (phases): _*)
        val synthesised_df = spark
            .read
            .format ("org.apache.spark.sql.cassandra")
            .options (Map ("table" -> "synthesized_value", "keyspace" -> job.input_keyspace))
            .load
            .filter (time_filter)
            .selectExpr (columns ("synthesis") (phases): _*)
            .withColumnRenamed ("synthesis", "mrid")
        val function = if (phases == 3) toThreePhase _ else toOnePhase _
        val raw_measured: RDD[SimulationPlayerData] =
            measured_df
                .rdd
                .map (function ("")) // convert to case class early
                .persist (options.storage_level)
        val raw_synthesised: RDD[SimulationPlayerData] =
            synthesised_df
                .rdd
                .map (function ("")) // convert to case class early
                .persist (options.storage_level)
        val queries_measured: RDD[(House, Trafo)] = simulations.flatMap (
            x =>
            {
                x.players.filter (null == _.synthesis).map (y => (y.mrid, x.name))
            }
        )
        val queries_synthesised: RDD[(House, Trafo)] = simulations.flatMap (
            x =>
            {
                x.players.filter (null != _.synthesis).map (y => (y.synthesis, x.name))
            }
        )
        val ret = raw_measured
            .keyBy (_.mrid)
            .join (queries_measured)
            .union (
                raw_synthesised
                    .keyBy (_.mrid)
                    .join (queries_synthesised))
            .mapValues (x => x._1.copy (transformer = x._2)) // use real trafo
            .groupByKey
            .groupBy (_._2.head.transformer)
            .persist (options.storage_level)
            .setName (s"${job.id}_player_data")
        log.info (s"""${ret.count} player data results""")
        raw_measured.unpersist (false)
        raw_synthesised.unpersist (false)
        ret
    }

    @SuppressWarnings (Array ("org.wartremover.warts.Null"))
    def vanishRDD (rdd: RDD[_]): Unit =
    {
        val _ = rdd
            .unpersist (false)
            .setName (null)
    }

    def cleanRDDs (): Unit =
    {
        session.sparkContext.getPersistentRDDs.foreach (x => vanishRDD (x._2))
    }

    def performExtraQueries (job: SimulationJob): Unit =
    {
        log.info (s"executing ${job.extras.length} extra queries")
        job.extras.foreach (
            extra =>
            {
                log.info (s"""executing "${extra.title}" as ${extra.query}""")
                val df: DataFrame = session.sql (extra.query).persist ()
                if (df.count > 0)
                {
                    val fields = df.schema.fieldNames
                    if (!fields.contains ("key") || !fields.contains ("value"))
                        log.error (s"""extra query "${extra.title}" schema either does not contain a "key" or a "value" field: ${fields.mkString}""")
                    else
                    {
                        val keyindex = df.schema.fieldIndex ("key")
                        val valueindex = df.schema.fieldIndex ("value")
                        val keytype = df.schema.fields (keyindex).dataType.simpleString
                        val valuetype = df.schema.fields (valueindex).dataType.simpleString
                        if ((keytype != "string") || (valuetype != "string"))
                            log.error (s"""extra query "${extra.title}" schema fields key and value are not both strings (key=$keytype, value=$valuetype)""")
                        else
                            df.rdd.map (row => (job.id, extra.title, row.getString (keyindex), row.getString (valueindex))).saveToCassandra (job.output_keyspace, "key_value", SomeColumns ("simulation", "query", "key", "value"))
                    }
                }
                else
                    log.warn (s"""extra query "${extra.title}" returned no rows""")
                df.unpersist (false)
            }
        )
    }

    def getTransformers: (Map[String, TransformerSet], Array[TransformerData]) =
    {
        val transformer_data = new Transformers (session, options.storage_level).getTransformers ()
        val tx = transformer_data.keyBy (_.node1.id) // (low_voltage_node_name, TransformerData)
            .join (get[TopologicalNode].keyBy (_.id)) // (low_voltage_node_name, (TransformerData, TopologicalNode))
            .map (x => (x._1, (x._2._1, x._2._2.TopologicalIsland))) // (low_voltage_node_name, (TransformerData, island))
            .groupByKey.values
        def toTransformerSet (transformers: Iterable[(TransformerData, String)]): (String, TransformerSet) =
        {
            val islands = transformers.map (_._2).toSet
            if (islands.size > 1)
                log.error (s"not all transformers are members of the same island (${islands.mkString (",")})")
            val set = TransformerSet (transformers.map (_._1).toArray)
            (set.transformer_name, set)
        }
        val transformers: Map[String, TransformerSet] = tx.map (toTransformerSet).collect.toMap

        // pick out the subtransmission transformers
        def subtransmission (trafo: TransformerData): Boolean =
        {
            trafo.ends.length == 2 &&
                trafo.voltages.exists (v => (v._2 <= 1000.0) && (v._2 > 400.0)) // ToDo: don't hard code these voltage values
        }
        val subtransmission_trafos = transformer_data.filter (subtransmission).collect
        (transformers, subtransmission_trafos)
    }

    def performGridlabSimulations (job: SimulationJob, simulations: RDD[SimulationTrafoKreis], player_rdd: RDD[(Trafo, Iterable[(House, Iterable[SimulationPlayerData])])]): RDD[SimulationResult] =
    {
        val numSimulations = simulations.count ().toInt
        log.info (s"""performing ${numSimulations} GridLAB-D simulation${plural (numSimulations)}""")
        val runner = SimulationRunner (
            options.host, job.output_keyspace, options.workdir,
            options.three_phase, options.fake_three_phase,
            job.cim_temperature, job.simulation_temperature, job.swing_voltage_factor,
            options.keep, options.verbose)
        val raw_results =
            simulations
                .keyBy (_.name)
                .join (player_rdd)
                .values
                .map (x => runner.execute (x._1, x._2.toMap))
                .persist (options.storage_level)
        raw_results.flatMap (_._1).collect.foreach (log.error)
        val results = raw_results.flatMap (_._2).persist (options.storage_level).setName (s"${job.id}_results")
        results
    }

    def createSimulationTasks (job: SimulationJob, batchno: Int): RDD[SimulationTrafoKreis] =
    {
        val (transformers, _) = getTransformers
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
                                    task.edges.filter (edge => edge.id != transformerset.transformer_name),
                                    task.start,
                                    task.end,
                                    task.players,
                                    task.recorders,
                                    s"${transformerset.transformer_name}${System.getProperty ("file.separator")}"
                                )
                            )
                        case None =>
                            log.error (s"no transformer sets for island ${task.island}")
                            List ()
                    }
                }
            ).persist (options.storage_level).setName (s"${job.id}_simulations")
        val numsimulations = _simulations.count.toInt
        log.info (s"${numsimulations} GridLAB-D simulation${plural (numsimulations)} to do for simulation ${job.id} batch $batchno")

        var simulations: RDD[SimulationTrafoKreis] = spark.sparkContext.emptyRDD
        if (0 != numsimulations)
        {
            val direction = SimulationDirection (options.workdir, options.verbose)
            simulations = _simulations.map (x => x.copy (directions = direction.execute (x)))
                .persist (options.storage_level).setName (s"${job.id}_simulations")
            _simulations.unpersist (false)
        }
        simulations
    }

    def simulate (batch: Seq[SimulationJob]): Seq[String] =
    {
        log.info ("""starting simulations""")
        val ajob = batch.head // assumes that all jobs in a batch should have the same cluster state

        // clean up in case there was a file already loaded
        cleanRDDs ()
        read (ajob.cim, ajob.cimreaderoptions, options.storage_level)

        var batchno = 1
        val ids = batch.map (
            job =>
            {
                log.info (s"starting simulation ${job.id}")

                val schema = Schema (session, "/simulation_schema.sql", options.verbose)
                if (schema.make (keyspace = job.output_keyspace, replication = job.replication))
                {
                    // perform the extra queries and insert into the key_value table
                    performExtraQueries (job)

                    val simulations: RDD[SimulationTrafoKreis] = createSimulationTasks (job, batchno)

                    if (!simulations.isEmpty())
                    {
                        log.info ("""storing GeoJSON data""")
                        val geo = SimulationGeometry (session, job.output_keyspace)
                        geo.storeGeometry (job.id, simulations)

                        log.info ("""querying player data""")
                        val player_rdd = queryValues (job, simulations)

                        val simulationResults: RDD[SimulationResult] = performGridlabSimulations (job, simulations, player_rdd)

                        // save the results
                        log.info ("""saving GridLAB-D simulation results""")
                        simulationResults.saveToCassandra (job.output_keyspace, "simulated_value", writeConf = WriteConf (ttl = TTLOption.perRow ("ttl"), consistencyLevel = ConsistencyLevel.ANY))
                        log.info ("""saved GridLAB-D simulation results""")
                        vanishRDD (player_rdd)
                    }

                    cleanRDDs ()
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

        for (simulation <- ids)
        {
            lookup.find (_._1 == simulation) match
            {
                case Some ((id, input, output)) =>
                    implicit val access: SimulationCassandraAccess =
                        SimulationCassandraAccess (spark, options.storage_level, id, input, output, options.verbose)
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
                    log.error (s"simulation $simulation not found in keyspaces (${lookup.map (_._2).mkString (",")})")
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
            val batches = jobs.groupBy (job => s"${job.cim}${job.optionString}${job.output_keyspace}").values
            batches.flatMap (simulate).toSeq
        }
        else
        {
            // get all simulations from the output keyspace(s)
            val keyspaces = jobs.map (_.output_keyspace).distinct
            log.info (s"""using keyspace${plural (keyspaces.length)} ${keyspaces.mkString (",")}""")
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
        log.info (s"""simulation${plural (ids.size)} ${ids.mkString (",")}""")

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
            classOf [ch.ninecode.sim.SimulationDirectionGenerator],
            classOf [ch.ninecode.sim.SimulationEdge],
            classOf [ch.ninecode.sim.SimulationExtraQuery],
            classOf [ch.ninecode.sim.SimulationGLMGenerator],
            classOf [ch.ninecode.sim.SimulationJob],
            classOf [ch.ninecode.sim.SimulationNode],
            classOf [ch.ninecode.sim.SimulationOptions],
            classOf [ch.ninecode.sim.SimulationPlayer],
            classOf [ch.ninecode.sim.SimulationPlayerData],
            classOf [ch.ninecode.sim.SimulationPlayerQuery],
            classOf [ch.ninecode.sim.SimulationPlayerResult],
            classOf [ch.ninecode.sim.SimulationRecorder],
            classOf [ch.ninecode.sim.SimulationRecorderQuery],
            classOf [ch.ninecode.sim.SimulationRecorderResult],
            classOf [ch.ninecode.sim.SimulationResult],
            classOf [ch.ninecode.sim.SimulationSparkQuery],
            classOf [ch.ninecode.sim.SimulationTask],
            classOf [ch.ninecode.sim.SimulationTrafoKreis]
        )
    }
}
