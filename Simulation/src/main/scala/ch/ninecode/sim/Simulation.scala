package ch.ninecode.sim

import java.io.Closeable
import java.io.StringReader
import java.io.StringWriter
import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.collection.JavaConverters.mapAsScalaMapConverter
import scala.tools.nsc.io.Jar
import scala.util.Random

import com.datastax.oss.driver.api.core.ConsistencyLevel
import com.datastax.spark.connector._
import com.datastax.spark.connector.writer.TTLOption
import com.datastax.spark.connector.writer.WriteConf
import javax.json.Json
import javax.json.JsonArray
import javax.json.JsonException
import javax.json.JsonObject
import javax.json.stream.JsonGenerator
import org.apache.log4j.Level
import org.apache.log4j.LogManager
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.Row
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.cim.CIMClasses
import ch.ninecode.cim.CIMNetworkTopologyProcessor
import ch.ninecode.cim.CIMRDD
import ch.ninecode.cim.CIMTopologyOptions
import ch.ninecode.gl.GLMNode
import ch.ninecode.gl.GridLABD
import ch.ninecode.model.TopologicalNode
import ch.ninecode.net.Island.identifier
import ch.ninecode.net.Island.island_id
import ch.ninecode.net.Lines
import ch.ninecode.net.Net
import ch.ninecode.net.TransformerData
import ch.ninecode.net.TransformerServiceArea
import ch.ninecode.net.TransformerSet
import ch.ninecode.net.Transformers
import ch.ninecode.util.CIMInitializer
import ch.ninecode.util.Main
import ch.ninecode.util.MainOptions
import ch.ninecode.util.Schema
import ch.ninecode.util.SparkOptions
import ch.ninecode.util.Util

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
 *   - the RDF file is read into Spark RDDs
 *   - topological processing adds topological nodes and islands
 *   - any 'extra' queries (for data to be attached to GeoJSON objects) are performed against Spark RDDs as DataFrames
 *   - a list of transformer sets is created
 *   - jobs are converted into individual transformer area tasks, possibly limited by the transformers specified in the job, by:
 *    - performing the player queries to determine player files that need to be generated
 *    - performing the recorder queries to determine the recorder files that will be created
 *    - identifying the nodes and edges that belong to each transformer area (topological island)
 *   - for each task (spread out over the cluster of executors) do the simulation as the following steps:
 *    - generate the GridLAB-D glm file
 *    - query Cassandra for each player file
 *    - perform the gridlabd load-flow analysis
 *    - insert the contents of each recorder file into Cassandra
 * */
final case class Simulation (session: SparkSession, options: SimulationOptions) extends CIMRDD
{
    type Trafo = String
    type House = String
    type Typ = String
    type PlayerData = Iterable[(House, Iterable[SimulationPlayerData])]

    if (options.verbose)
    {
        LogManager.getLogger(getClass.getName).setLevel(org.apache.log4j.Level.INFO)
        LogManager.getLogger("ch.ninecode.gl.SimulationJob").setLevel(org.apache.log4j.Level.INFO)
        LogManager.getLogger("ch.ninecode.gl.TransformerServiceArea").setLevel(org.apache.log4j.Level.INFO)
        LogManager.getLogger("ch.ninecode.sim.SimulationSparkQuery").setLevel(org.apache.log4j.Level.INFO)
        LogManager.getLogger("ch.ninecode.cim.CIMNetworkTopologyProcessor").setLevel(org.apache.log4j.Level.INFO)
    }
    implicit val log: Logger = LoggerFactory.getLogger(getClass)
    implicit val spark: SparkSession = session

    val calendar: Calendar = Calendar.getInstance()
    calendar.setTimeZone(TimeZone.getTimeZone("GMT"))
    calendar.setTimeInMillis(0L)

    val glm_date_format: SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z")
    glm_date_format.setCalendar(calendar)

    val cassandra_date_format: SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ")
    cassandra_date_format.setCalendar(calendar)

    def read (rdf: String, reader_options: Map[String, String] = Map(), storage_level: StorageLevel = StorageLevel.fromString("MEMORY_AND_DISK_SER"))
    {
        log.info(s"""reading "$rdf"""")
        val start = System.nanoTime()
        if (rdf.startsWith("s3") && options.aws_s3a_access_key.trim.nonEmpty && options.aws_s3a_secret_key.trim.nonEmpty)
        {
            val _ = System.setProperty("com.amazonaws.services.s3.enableV4", "true")
            session.sparkContext.hadoopConfiguration.set("com.amazonaws.services.s3.enableV4", "true")
            session.sparkContext.hadoopConfiguration.set("fs.s3a.access.key", options.aws_s3a_access_key)
            session.sparkContext.hadoopConfiguration.set("fs.s3a.secret.key", options.aws_s3a_secret_key)
            session.sparkContext.hadoopConfiguration.set("fs.s3a.endpoint", "s3.eu-central-1.amazonaws.com")
            session.sparkContext.hadoopConfiguration.set("fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem")
        }
        val elements = session.read.format("ch.ninecode.cim").options(reader_options).load(rdf)
        log.info(s"${elements.count()} elements")
        val read = System.nanoTime()
        log.info(s"read: ${(read - start) / 1e9} seconds")
        session.sparkContext.getPersistentRDDs.find(_._2.name == "TopologicalIsland") match
        {
            case Some(_) =>
                log.info("topology exists")
            case None =>
                log.info("generating topology")
                val ntp = CIMNetworkTopologyProcessor(
                    session,
                    CIMTopologyOptions(
                        identify_islands = true,
                        storage = storage_level))
                val ele = ntp.process
                log.info(s"${ele.count()} elements after topology creation")
                val topology = System.nanoTime()
                log.info(s"topology: ${(topology - read) / 1e9} seconds")
        }
    }

    def dump (obj: JsonObject): Unit =
    {
        val o = obj.asScala
        val strings = o.map(x => s"${x._1}=${x._2.toString}")
        log.info(strings.mkString(" "))
    }

    def stringify (resultset: Seq[JsonObject]): String =
    {
        val array = Json.createArrayBuilder
        for (i <- resultset.indices)
            array.add(resultset(i))
        val string = new StringWriter
        val properties = Map[String, AnyRef](
            JsonGenerator.PRETTY_PRINTING -> "true")
        val writer = Json.createWriterFactory(properties.asJava).createWriter(string)
        writer.write(array.build)
        writer.close()
        string.toString
    }

    def destringify (string: String): Seq[JsonObject] =
    {
        try
            Json.createReader(new StringReader(string)).readArray match
            {
                case obj: JsonArray =>
                    obj.getValuesAs(classOf[JsonObject]).asScala
                case _ =>
                    log.error("""not a JsonArray""")
                    Seq()
            }
        catch
        {
            case je: JsonException =>
                log.error(s" string could not be parsed as JSON (${je.getMessage})")
                Seq()
        }
    }

    def using[T <: Closeable, R] (resource: T)(block: T => R): R =
    {
        try
        {
            block(resource)
        }
        finally
        {
            resource.close()
        }
    }

    def generate_player_csv (job: SimulationJob)(player: SimulationPlayerResult): SimulationPlayer =
    {
        val file = s"input_data/${player.name}.csv"
        SimulationPlayer(
            player.name,
            player.parent,
            player.`type`,
            player.property,
            file,
            player.mrid,
            job.start_in_millis(),
            job.end_in_millis(),
            player.transform,
            player.synthesis)
    }

    def generate_recorder_csv (recorder: SimulationRecorderResult): SimulationRecorder =
    {
        val file = s"output_data/${recorder.name}.csv"
        SimulationRecorder(
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
        string.replace("\n", " ").replaceAll("[ ]+", " ")
    }

    @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
    def dupTime (calendar: Calendar): Calendar =
    {
        calendar.clone.asInstanceOf[Calendar]
    }

    def plural (number: Int): String = if (1 == number) "" else "s"

    def make_tasks (job: SimulationJob): RDD[SimulationTask] =
    {
        log.info(s"""preparing simulation job "${job.name}"""")

        // transformer area calculations
        val tsa = TransformerServiceArea(session, options.cim_options.storage)
        // only proceed if topological processing was done (there are TopologicalIslands)
        if (tsa.hasIslands)
        {
            val islands_trafos: RDD[(island_id, identifier)] = tsa.getTransformerServiceAreas
            val numareas = islands_trafos.map(_._2).distinct.count.toInt
            log.info(s"""$numareas transformer service area${plural(numareas)} found""")

            val q = SimulationSparkQuery(session, options.cim_options.storage, options.verbose)

            // query the players
            log.info("""querying players""")
            val playersets: RDD[(island_id, Iterable[SimulationPlayerResult])] = session.sparkContext.union(job.players.map(query => q.executePlayerQuery(query))).groupByKey

            // query the recorders
            log.info("""querying recorders""")
            val recordersets: RDD[(island_id, Iterable[SimulationRecorderResult])] = session.sparkContext.union(job.recorders.map(query => q.executeRecorderQuery(query))).groupByKey

            // join players and recorders
            val players_recorders: RDD[(island_id, (Iterable[SimulationPlayerResult], Iterable[SimulationRecorderResult]))] = playersets.join(recordersets)

            log.info("""generating simulation tasks""")

            // maybe reduce the set of islands
            val islands_to_do: RDD[(island_id, identifier)] =
                if (0 != job.transformers.size)
                    islands_trafos.filter(pair => job.transformers.contains(pair._2))
                else
                    islands_trafos

            val island_helper = new SimulationIsland(session, options.cim_options.storage)
            Lines.DEFAULT_CABLE_RESISTANCE_LIMIT = options.cable_impedance_limit
            val graph_stuff = island_helper.queryNetwork(islands_to_do.map(_.swap))
            val areas: RDD[(identifier, (Iterable[SimulationNode], Iterable[SimulationEdge]))] =
                graph_stuff
                    ._1.asInstanceOf[RDD[(identifier, SimulationNode)]]
                    .groupByKey
                    .join(
                        graph_stuff._2.asInstanceOf[RDD[(identifier, SimulationEdge)]]
                            .groupByKey)

            val players_and_recorders_by_area =
                islands_trafos
                    .join(players_recorders)
                    .map(
                        (x: (island_id, (identifier, (Iterable[SimulationPlayerResult], Iterable[SimulationRecorderResult])))) =>
                        {
                            val (_, (area, (players, recorders))) = x
                            (area, (players, recorders))
                        }
                    )
                    .groupByKey
                    .map(
                        (x: (identifier, Iterable[(Iterable[SimulationPlayerResult], Iterable[SimulationRecorderResult])])) =>
                        {
                            val (area, pla_rec) = x
                            (area, (pla_rec.flatMap(_._1), pla_rec.flatMap(_._2)))
                        }
                    )
            val ret =
                areas
                    .join(players_and_recorders_by_area)
                    .map(
                        (x: (identifier, ((Iterable[SimulationNode], Iterable[SimulationEdge]), (Iterable[SimulationPlayerResult], Iterable[SimulationRecorderResult])))) =>
                        {
                            val (area, ((nodes, edges), (pla, rec))) = x
                            val players: Iterable[SimulationPlayer] = pla.map(generate_player_csv(job))
                            val recorders: Iterable[SimulationRecorder] = rec.map(generate_recorder_csv)
                            SimulationTask(
                                area, // trafo
                                area, // island
                                dupTime(job.start_time),
                                dupTime(job.end_time),
                                nodes, // nodes
                                edges, // edges
                                players,
                                recorders)
                        }
                    )
                    .persist(options.cim_options.storage)
                    .setName(s"${job.id}_tasks")
            ret
        }
        else
        {
            log.error("""topology without islands""")
            session.sparkContext.emptyRDD
        }
    }

    def columns (primaryKey: String)(phases: Int): Seq[String] =
    {
        if (phases == 3)
            Seq(primaryKey, "type", "time", "period", "units", "real_a", "imag_a", "real_b", "imag_b", "real_c", "imag_c")
        else
            Seq(primaryKey, "type", "time", "period", "units", "real_a", "imag_a")
    }

    def toThreePhase (transformer: String)(row: Row): SimulationPlayerData =
    {
        val id = row.getString(0)
        val typ = row.getString(1)
        val t = row.getTimestamp(2).getTime
        val period = row.getInt(3)
        val units = row.getString(4)
        val rea = row.getDouble(5)
        val ima = row.getDouble(6)
        val reb = row.getDouble(7)
        val imb = row.getDouble(8)
        val rec = row.getDouble(9)
        val imc = row.getDouble(10)
        SimulationPlayerData(transformer, id, typ, t, period, units, Array(rea, ima, reb, imb, rec, imc))
    }

    def toOnePhase (transformer: String)(row: Row): SimulationPlayerData =
    {
        val id = row.getString(0)
        val typ = row.getString(1)
        val t = row.getTimestamp(2).getTime
        val period = row.getInt(3)
        val units = row.getString(4)
        val re = row.getDouble(5)
        val im = row.getDouble(6)
        SimulationPlayerData(transformer, id, typ, t, period, units, Array(re, im))
    }

    /**
     * Calendar duplication utility function.
     *
     * @param c The Calendar value to be cloned.
     */
    @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
    def dup (c: Calendar): Calendar = c.clone().asInstanceOf[Calendar]

    /**
     * Get the transformer from a set of mRID player data.
     *
     * @param arg the grouped player data
     *            assumes they all have the same transformer
     * @return the transformer name from the player data
     */
    @SuppressWarnings(Array("org.wartremover.warts.TraversableOps"))
    def theTransformer (arg: (String, Iterable[SimulationPlayerData])): String = arg._2.head.transformer

    def queryValues (job: SimulationJob, simulations: RDD[SimulationTrafoKreis]): RDD[(Trafo, PlayerData)] =
    {
        val phases = if (options.three_phase && !options.fake_three_phase) 3 else 1
        val start = dup(job.start_time)
        start.add(Calendar.MILLISECOND, -job.buffer)
        val end = dup(job.end_time)
        end.add(Calendar.MILLISECOND, job.buffer)
        val low = cassandra_date_format.format(start.getTime)
        val high = cassandra_date_format.format(end.getTime)
        val time_filter = s"time >= '$low' and time <= '$high'"
        val measured_df = spark
            .read
            .format("org.apache.spark.sql.cassandra")
            .options(Map("table" -> "measured_value", "keyspace" -> job.input_keyspace))
            .load
            .filter(time_filter)
            .selectExpr(columns("mrid")(phases): _*)
        val synthesised_df = spark
            .read
            .format("org.apache.spark.sql.cassandra")
            .options(Map("table" -> "synthesized_value", "keyspace" -> job.input_keyspace))
            .load
            .filter(time_filter)
            .selectExpr(columns("synthesis")(phases): _*)
            .withColumnRenamed("synthesis", "mrid")
        val function = if (phases == 3) toThreePhase _ else toOnePhase _
        val raw_measured: RDD[SimulationPlayerData] =
            measured_df
                .rdd
                .map(function("")) // convert to case class early
                .persist(options.cim_options.storage)
        val raw_synthesised: RDD[SimulationPlayerData] =
            synthesised_df
                .rdd
                .map(function("")) // convert to case class early
                .persist(options.cim_options.storage)
        val queries_measured: RDD[(House, Trafo)] = simulations.flatMap(
            x =>
            {
                x.players.filter("" == _.synthesis).map(y => (y.mrid, x.name))
            }
        )
        val queries_synthesised: RDD[(House, Trafo)] = simulations.flatMap(
            x =>
            {
                x.players.filter("" != _.synthesis).map(y => (y.synthesis, x.name))
            }
        )
        val ret = raw_measured
            .keyBy(_.mrid)
            .join(queries_measured)
            .union(
                raw_synthesised
                    .keyBy(_.mrid)
                    .join(queries_synthesised))
            .mapValues(x => x._1.copy(transformer = x._2)) // use real trafo
            .groupByKey
            .groupBy(theTransformer)
            .persist(options.cim_options.storage)
            .setName(s"${job.id}_player_data")
        log.info(s"""${ret.count} player data results""")
        vanishRDD(raw_measured)
        vanishRDD(raw_synthesised)
        ret
    }

    @SuppressWarnings(Array("org.wartremover.warts.Null"))
    def vanishRDD (rdd: RDD[_]): Unit =
    {
        val _ = rdd
            .unpersist(false)
            .setName(null)
    }

    def vanishRDDs (rddList: List[RDD[_]]): Unit =
    {
        rddList.foreach(vanishRDD)
    }

    def cleanRDDs (): Unit =
    {
        session.sparkContext.getPersistentRDDs.foreach(x => vanishRDD(x._2))
    }

    def performExtraQueries (job: SimulationJob): Unit =
    {
        log.info(s"executing ${job.extras.length} extra queries")
        job.extras.foreach(
            extra =>
            {
                log.info(s"""executing "${extra.title}" as ${extra.query}""")
                val df: DataFrame = session.sql(extra.query).persist()
                if (df.count > 0)
                {
                    val fields = df.schema.fieldNames
                    if (!fields.contains("key") || !fields.contains("value"))
                        log.error(s"""extra query "${extra.title}" schema either does not contain a "key" or a "value" field: ${fields.mkString}""")
                    else
                    {
                        val keyindex = df.schema.fieldIndex("key")
                        val valueindex = df.schema.fieldIndex("value")
                        val keytype = df.schema.fields(keyindex).dataType.simpleString
                        val valuetype = df.schema.fields(valueindex).dataType.simpleString
                        if ((keytype != "string") || (valuetype != "string"))
                            log.error(s"""extra query "${extra.title}" schema fields key and value are not both strings (key=$keytype, value=$valuetype)""")
                        else
                            df.rdd.map(row => (job.id, extra.title, row.getString(keyindex), row.getString(valueindex))).saveToCassandra(job.output_keyspace, "key_value", SomeColumns("simulation", "query", "key", "value"))
                    }
                }
                else
                    log.warn(s"""extra query "${extra.title}" returned no rows""")
                df.unpersist(false)
            }
        )
    }

    def getTransformers: (Map[String, TransformerSet], Array[TransformerData]) =
    {
        val transformer_data = Transformers(session, options.cim_options.storage).getTransformers()
        val tx = transformer_data.keyBy(_.node1.id) // (low_voltage_node_name, TransformerData)
            .join(get[TopologicalNode].keyBy(_.id)) // (low_voltage_node_name, (TransformerData, TopologicalNode))
            .map(x => (x._1, (x._2._1, x._2._2.TopologicalIsland))) // (low_voltage_node_name, (TransformerData, island))
            .groupByKey.values

        def toTransformerSet (transformers: Iterable[(TransformerData, String)]): (String, TransformerSet) =
        {
            val islands = transformers.map(_._2).toSet
            if (islands.size > 1)
                log.error(s"not all transformers are members of the same island (${islands.mkString(",")})")
            val set = TransformerSet(transformers.map(_._1).toArray)
            (set.transformer_name, set)
        }

        val transformers: Map[String, TransformerSet] = tx.map(toTransformerSet).collect.toMap

        // pick out the subtransmission transformers
        def subtransmission (trafo: TransformerData): Boolean =
        {
            trafo.ends.length == 2 &&
                trafo.voltages.exists(v => (v._2 <= 1000.0) && (v._2 > 400.0)) // ToDo: don't hard code these voltage values
        }

        val subtransmission_trafos = transformer_data.filter(subtransmission).collect
        (transformers, subtransmission_trafos)
    }

    def performGridlabSimulations (
        job: SimulationJob,
        simulations: RDD[SimulationTrafoKreis],
        player_rdd: RDD[(Trafo, PlayerData)],
        swing_nominal_voltage: Boolean = true): RDD[SimulationResult] =
    {
        val numSimulations = simulations.count().toInt
        log.info(s"""performing $numSimulations GridLAB-D simulation${plural(numSimulations)}""")
        val runner = SimulationRunner(
            job.output_keyspace, options.workdir,
            options.three_phase, options.fake_three_phase,
            job.cim_temperature, job.simulation_temperature, job.swing_voltage_factor,
            options.keep, options.verbose)

        val simulation_with_player = simulations.keyBy(_.name).join(player_rdd).values
        val raw_results = simulation_with_player
            .map(x => runner.execute(x._1, x._2.toMap, swing_nominal_voltage))
            .persist(options.cim_options.storage)

        raw_results.flatMap(_._1).collect.foreach(log.error)
        val results = raw_results.flatMap(_._2).persist(options.cim_options.storage).setName(s"${job.id}_results")
        results
    }

    def createSimulationTasks (house_trafo_mapping_dataframe: DataFrame, job: SimulationJob): RDD[SimulationTrafoKreis] =
    {
        val (transformers, _) = getTransformers
        val tasks = make_tasks(job)
        val transformer_house_mapping: Map[String, String] = transformers.map((row) =>
        {
            val island = row._1
            val first_house_for_trafo = house_trafo_mapping_dataframe.collect().filter(_.getString(1) == island)
            val house_for_trafo = first_house_for_trafo.headOption match
            {
                case Some(x) => x.getString(0)
                case _ => ""
            }
            (island, house_for_trafo)
        })

        job.save(session, job.output_keyspace, job.id, tasks)

        log.info("""matching tasks to topological islands""")
        val _simulations =
            tasks.flatMap(
                task =>
                {
                    transformers.get(task.island) match
                    {
                        case Some(transformerset) =>
                            List(
                                SimulationTrafoKreis(
                                    job.id,
                                    task.island,
                                    transformerset,
                                    job.swing,
                                    task.nodes,
                                    task.edges.filter(edge => edge.id != transformerset.transformer_name),
                                    task.start,
                                    task.end,
                                    task.players,
                                    task.recorders,
                                    s"${transformerset.transformer_name}${System.getProperty("file.separator")}",
                                    house_for_voltage_calculation = transformer_house_mapping.getOrElse(task.island, "")
                                )
                            )
                        case None =>
                            log.error(s"no transformer sets for island ${task.island}")
                            List()
                    }
                }
            ).persist(options.cim_options.storage).setName(s"${job.id}_simulations")
        val numsimulations = _simulations.count.toInt
        log.info(s"$numsimulations GridLAB-D simulation${plural(numsimulations)} to do for simulation ${job.id} batch ${job.name}")

        var simulations: RDD[SimulationTrafoKreis] = spark.sparkContext.emptyRDD
        if (0 != numsimulations)
        {
            val direction = SimulationDirection(options.workdir, options.verbose)
            simulations = _simulations.map(x => x.copy(directions = direction.execute(x)))
                .persist(options.cim_options.storage).setName(s"${job.id}_simulations")
            val _ = _simulations.unpersist(false)
        }
        simulations
    }

    /**
     * Pick a job out of the batch.
     *
     * @param batch the batch of similar jobs
     *              assumes that all jobs in a batch should have the same cluster state
     * @return the first job in the batch
     */
    @SuppressWarnings(Array("org.wartremover.warts.TraversableOps"))
    def aJob (batch: Seq[SimulationJob]): SimulationJob = batch.head

    def read_house_trafo_csv (house_trafo_mappings: String): DataFrame =
    {
        if (house_trafo_mappings.eq(""))
        {
            spark.emptyDataFrame
        }
        else
        {
            spark.read.format("csv").option("header", "true").option("delimiter", "\t").load(house_trafo_mappings)
        }
    }

    def simulate (batch: Seq[SimulationJob]): Seq[String] =
    {
        log.info("""starting simulations""")
        val ajob = aJob(batch)

        // clean up in case there was a file already loaded
        cleanRDDs()
        read(ajob.cim, ajob.cimreaderoptions, options.cim_options.storage)
        val house_trafo_mapping = read_house_trafo_csv(ajob.house_trafo_mappings)
        batch.map(simulateJob(house_trafo_mapping))
    }

    @SuppressWarnings(Array("org.wartremover.warts.Null"))
    def extendSimulationWithVoltage (job: SimulationJob)(original_simulation: SimulationTrafoKreis): SimulationTrafoKreis =
    {
        val voltage_player: Iterable[SimulationPlayer] = original_simulation.swing_nodes.map((swing: GLMNode) =>
        {
            val mrid = swing.id // as we do not have access to mrid, we use topo node instead
            val file = s"input_data/${mrid}_voltage.csv"
            SimulationPlayer(
                swing.id + "_player",
                swing.id,
                "voltage",
                "voltage",
                file,
                mrid,
                job.start_in_millis(),
                job.end_in_millis(),
                null,
                "")
        })

        val players: Iterable[SimulationPlayer] = original_simulation.players ++ voltage_player
        original_simulation.copy(players = players)
    }


    def generateFakeVoltageForTrafos (
        job: SimulationJob,
        simulations: RDD[SimulationTrafoKreis]
    ): RDD[(Trafo, PlayerData)] =
    {
        simulations.map(trafokreis =>
        {
            val topo_node = trafokreis.swing_nodes.head.id
            val player_list_data: SimulationPlayerData = SimulationPlayerData(
                trafokreis.name,
                topo_node,
                "voltage",
                job.start_in_millis(),
                90000,
                "V",
                Array(21000, 0.0)
            )
            val player_list: PlayerData = List((topo_node, List(player_list_data)))
            (trafokreis.name, player_list)
        })
    }

    def extendPlayersRDDWithTrafoVoltage (
        player_rdd: RDD[(Trafo, PlayerData)],
        trafo_voltage_players_rdd: RDD[(Trafo, PlayerData)]): RDD[(Trafo, PlayerData)] =
    {
        val extended_player: RDD[(Trafo, PlayerData)] = player_rdd.union(trafo_voltage_players_rdd)
        extended_player.groupByKey().map(b => (b._1, b._2.flatten))
    }

    def simulateJob (house_trafo_mapping: DataFrame)(job: SimulationJob): String =
    {
        log.info(s"starting simulation ${job.id}")

        val schema = Schema(session, "/simulation_schema.sql", options.verbose)
        if (schema.make(keyspace = job.output_keyspace, replication = job.replication))
        {
            val include_voltage = house_trafo_mapping.head(1).isEmpty

            // perform the extra queries and insert into the key_value table
            performExtraQueries(job)

            var simulations: RDD[SimulationTrafoKreis] = createSimulationTasks(house_trafo_mapping, job)

            if (!simulations.isEmpty())
            {
                log.info("""storing GeoJSON data""")
                val geo = SimulationGeometry(session, job.output_keyspace)
                geo.storeGeometry(job.id, simulations)

                log.info("""querying player data""")
                var player_rdd: RDD[(Trafo, PlayerData)] = queryValues(job, simulations)

                if (include_voltage)
                {
                    // how to find/handle missing mappings in trafo_has_mapping ??
                    // shit in --> shit out ? oder filtern durch join?
                    //val mapping = session.sparkContext.parallelize(trafo_has_mapping.toSeq)
                    //val simulations_with_mapping: RDD[SimulationTrafoKreis] =
                    //simulations.keyBy(_.name).join(mapping).values.map(_._1)
                    //
                    //val trafo_power_players_rdd = simulate_trafo_power(job, simulations_with_mapping, trafo_has_mapping, player_rdd)
                    //val hak_voltage_players_rdd = queryHakVoltageValue(job, simulations_with_mapping, trafo_has_mapping)
                    //val trafo_voltage_players_rdd = simulate_trafo_voltage(job, simulations_with_mapping, trafo_has_mapping, trafo_power_players_rdd.union(hak_voltage_players_rdd))

                    // TODO: remove
                    val trafo_voltage_players_rdd = generateFakeVoltageForTrafos(job, simulations)

                    player_rdd = extendPlayersRDDWithTrafoVoltage(player_rdd, trafo_voltage_players_rdd)
                    simulations = simulations.map(extendSimulationWithVoltage(job))
                }

                val simulationResults: RDD[SimulationResult] = performGridlabSimulations(job, simulations, player_rdd, !include_voltage)

                // save the results
                log.info("""saving GridLAB-D simulation results""")
                val conf = WriteConf.fromSparkConf(spark.sparkContext.getConf).copy(ttl = TTLOption.perRow("ttl"), consistencyLevel = ConsistencyLevel.ANY)
                simulationResults.saveToCassandra(job.output_keyspace, "simulated_value", writeConf = conf)
                log.info("""saved GridLAB-D simulation results""")
                vanishRDDs(List(simulations, player_rdd, simulationResults))
            }
        }
        job.id
    }

    def postprocess (ids: Seq[String], jobs: Seq[SimulationJob])
    {
        val keyspaces = jobs.map(_.output_keyspace).distinct
        val lookup = keyspaces.flatMap(
            keyspace =>
            {
                spark
                    .read
                    .format("org.apache.spark.sql.cassandra")
                    .options(Map("table" -> "simulation", "keyspace" -> keyspace))
                    .load
                    .select("id", "input_keyspace", "output_keyspace")
                    .where(s"id in ${ids.mkString("('", "','", "')")}")
                    .rdd
                    .map(row => (row.getString(0), row.getString(1), row.getString(2)))
                    .collect
            }
        )

        for (simulation <- ids)
        {
            lookup.find(_._1 == simulation) match
            {
                case Some((id, input, output)) =>
                    implicit val access: SimulationCassandraAccess =
                        SimulationCassandraAccess(spark, options.cim_options.storage, id, input, output, options.verbose)
                    // ToDo: this isn't quite right, take the first job matching the output keyspace
                    val batches = jobs.groupBy(_.output_keyspace)
                    val job = aJob(batches(output))
                    job.postprocessors.foreach(
                        processor =>
                        {
                            val runner = processor(session, options)
                            runner.run(access)
                        }
                    )
                case None =>
                    log.error(s"simulation $simulation not found in keyspaces (${lookup.map(_._2).mkString(",")})")
            }
        }

    }

    def run (): Seq[String] =
    {
        val jobs = SimulationJob.getAll(options)

        // simulate
        val ids = if (!options.postprocessonly)
        {
            // organize by same RDF, options and output keyspace
            val batches: Iterable[Seq[SimulationJob]] = jobs.groupBy(job => s"${job.cim}${job.optionString}${job.output_keyspace}").values
            batches.flatMap(simulate).toSeq
        }
        else
        {
            // get all simulations from the output keyspace(s)
            val keyspaces = jobs.map(_.output_keyspace).distinct
            log.info(s"""using keyspace${plural(keyspaces.length)} ${keyspaces.mkString(",")}""")
            keyspaces.flatMap(
                keyspace =>
                {
                    spark
                        .read
                        .format("org.apache.spark.sql.cassandra")
                        .options(Map("table" -> "simulation", "keyspace" -> keyspace))
                        .load
                        .select("id")
                        .collect
                        .map(_.getString(0))
                }
            )
        }
        log.info(s"""simulation${plural(ids.size)} ${ids.mkString(",")}""")

        // postprocess
        if (!options.simulationonly)
            postprocess(ids, jobs)

        ids
    }
}

object Simulation extends CIMInitializer[SimulationOptions] with Main
{
    /**
     * The list of classes that can be persisted in RDD.
     */
    lazy val classes: Array[Class[_]] =
    {
        Array(
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

    def jarForClass (cls: Class[_]): String =
    {
        // see https://stackoverflow.com/questions/320542/how-to-get-the-path-of-a-running-jar-file
        var ret = cls.getProtectionDomain.getCodeSource.getLocation.getPath
        try
        {
            ret = URLDecoder.decode(ret, "UTF-8")
        }
        catch
        {
            case e: UnsupportedEncodingException => e.printStackTrace()
        }
        if (!ret.toLowerCase().endsWith(".jar"))
        {
            // as an aid to debugging, make jar in tmp and pass that name
            val name = s"/tmp/${Random.nextInt(99999999)}.jar"
            val writer = new Jar(new scala.reflect.io.File(new java.io.File(name))).jarWriter()
            writer.addDirectory(new scala.reflect.io.Directory(new java.io.File(ret + "ch/")), "ch/")
            writer.close()
            ret = name
        }

        ret
    }

    def run (options: SimulationOptions): Unit =
    {
        if (options.verbose)
            LogManager.getLogger(getClass).setLevel(Level.INFO)
        if (options.main_options.valid)
        {
            if (options.simulation.nonEmpty)
            {
                val session: SparkSession = createSession(options)
                time("execution: %s seconds")
                {
                    val ids = Simulation(session, options).run()
                    log.info(s"simulated ${ids.mkString(",")}")
                }
            }
            else
                log.error("no simulation JSON files specified")
        }
    }

    def main (args: Array[String])
    {
        val have = scala.util.Properties.versionNumberString
        val need = scala_library_version
        if (have != need)
        {
            log.error(s"Scala version ($have) does not match the version ($need) used to build $application_name")
            sys.exit(1)
        }
        else
        {
            // get the necessary jar files to send to the cluster
            val jars = Set(
                jarForClass(SimulationOptions().getClass), // sim
                jarForClass(Class.forName("ch.ninecode.gl.GLMEdge")), // glm
                jarForClass(Class.forName("ch.ninecode.cim.DefaultSource")), // CIMReader
                jarForClass(Class.forName("ch.ninecode.util.Complex")), // util
                jarForClass(Class.forName("javax.json.JsonStructure")), // json
                jarForClass(Class.forName("org.glassfish.json.JsonProviderImpl")), // json_impl
                jarForClass(Class.forName("com.datastax.oss.driver.api.core.CqlSession")) // Cassandra
            ).toArray

            // compose the classes to be registered with Kryo
            val kryo = Array.concat(
                // register CIMReader classes
                CIMClasses.list,
                // register GridLAB-D classes
                GridLABD.classes,
                // register Net classes
                Net.classes,
                // register GridLAB-D classes
                GridLABD.classes,
                // register Simulation analysis classes
                Simulation.classes,
                // register Util classes
                Util.classes)

            // initialize the default options
            val temp = SimulationOptions()
            val default = SimulationOptions(
                main_options = MainOptions(application_name, application_version),
                spark_options = SparkOptions(jars = jars, kryo = kryo),
                cim_options = temp.cim_options
            )

            // parse the command line arguments
            new SimulationOptionsParser(default).parse(args, default) match
            {
                case Some(options) =>
                    // execute the main program if everything checks out
                    run(options)
                    if (!options.main_options.unittest)
                        sys.exit(0)
                case None =>
                    sys.exit(1)
            }
        }
    }
}
