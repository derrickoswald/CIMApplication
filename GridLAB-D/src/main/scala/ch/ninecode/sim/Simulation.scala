package ch.ninecode.sim

import java.io.Closeable
import java.io.File
import java.io.PrintWriter
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
import javax.json.JsonNumber
import javax.json.JsonObject
import javax.json.JsonString
import javax.json.stream.JsonGenerator

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.io.Source
import scala.sys.process._
import com.datastax.driver.core.Cluster
import com.datastax.driver.core.Session
import org.apache.commons.io.FileUtils
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import ch.ninecode.cim.CIMNetworkTopologyProcessor
import ch.ninecode.cim.CIMRDD
import ch.ninecode.gl.Complex
import ch.ninecode.gl.TransformerSet
import ch.ninecode.gl.GLMEdge
import ch.ninecode.gl.GLMNode
import ch.ninecode.gl.ThreePhaseComplexDataElement
import ch.ninecode.gl.Transformers
import ch.ninecode.model.BaseVoltage
import ch.ninecode.model.ConductingEquipment
import ch.ninecode.model.Element
import ch.ninecode.model.PositionPoint
import ch.ninecode.model.Terminal
import ch.ninecode.model.TopologicalNode
import org.apache.spark.sql.DataFrame


case class Simulation (session: SparkSession, options: SimulationOptions) extends CIMRDD
{
    if (options.verbose)
        org.apache.log4j.LogManager.getLogger (getClass.getName).setLevel (org.apache.log4j.Level.INFO)
    implicit val log: Logger = LoggerFactory.getLogger (getClass)
    implicit val spark: SparkSession = session

    val calendar: Calendar = Calendar.getInstance ()
    calendar.setTimeZone (TimeZone.getTimeZone ("GMT"))
    calendar.setTimeInMillis (0L)

    val glm_date_format: SimpleDateFormat = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss z")
    glm_date_format.setCalendar (calendar)

    val iso_date_format: SimpleDateFormat = new SimpleDateFormat ("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    iso_date_format.setCalendar (calendar)

    val just_date: SimpleDateFormat = new SimpleDateFormat ("yyyy-MM-dd")
    just_date.setCalendar (calendar)

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

    // make string like: 2017-07-18 00:00:00 UTC,0.4,0.0
    def glm_format (obj: JsonObject): String =
    {
        var time = 0L
        var real = 0.0
        var imag = 0.0
        val o = obj.asScala
        o.foreach (
            x ⇒
                x._1 match
                {
                    case "time" ⇒ time = x._2.asInstanceOf[JsonNumber].longValue
                    case "real" ⇒ real = x._2.asInstanceOf[JsonNumber].doubleValue
                    case "imag" ⇒ imag = x._2.asInstanceOf[JsonNumber].doubleValue
                }
        )
        glm_date_format.format (time) + "," + real + "," + imag
    }

    def generate_player_csv (player: SimulationPlayerQuery, begin: Long, end: Long): Seq[SimulationPlayer] =
    {
        var from = iso_date_format.format (new Date (begin))
        var   to = iso_date_format.format (new Date (end))
        if (begin > end)
        {
            log.error ("""player "%s" has a start time (%s) after the end time (%s)""".format (player.title, from, to))
            return Seq()
        }
        log.info ("""resolving "%s" [%s, %s)""".format (player.title, from, to))
        var ret = List[SimulationPlayer]()
        val day1 = just_date.format (begin)
        val  day2 = just_date.format (end)
        val range =
            if (day1 == day2)
                "date = '%s'".format (day1)
            else
            {
                val start = iso_parse (from)
                val stop = iso_parse (to)
                var dates = List[String]()
                while (0 >= start.compareTo (stop))
                {
                    dates = dates :+ just_date.format (start.getTimeInMillis)
                    start.add (Calendar.DAY_OF_MONTH, 1)
                }
                dates.mkString("date in ('", "','", "')")
            }
        val jsons = destringify (player.jsons)
        jsons.foreach (
            x ⇒
            {
                val json = x.asScala
                val substitutions = player.bind.map (y ⇒ json(y).asInstanceOf[JsonString].getString)
                val sql = player.cassandraquery.format (substitutions: _*) + " and " + range + " allow filtering"
                val name = json("name").asInstanceOf[JsonString].getString
                val file = "input_data/" + name + "_" + day1 + ".csv" // ToDo: use both dates?
                ret = ret :+ SimulationPlayer (
                    name,
                    json("parent").asInstanceOf[JsonString].getString,
                    json("type").asInstanceOf[JsonString].getString,
                    json("property").asInstanceOf[JsonString].getString,
                    file,
                    sql,
                    begin,
                    end)
            }
        )
        ret
    }

    def generate_recorder_csv (recorder: SimulationRecorderQuery, start: Long, end: Long): Seq[SimulationRecorder] =
    {
        val t0 = Calendar.getInstance ()
        t0.setTimeZone (TimeZone.getTimeZone ("GMT"))
        t0.setTimeInMillis (start)
        val t1 = Calendar.getInstance ()
        t1.setTimeZone (TimeZone.getTimeZone ("GMT"))
        t1.setTimeInMillis (end)
        log.info ("""resolving "%s" [%s, %s)""".format (recorder.title, iso_date_format.format (t0.getTime), iso_date_format.format (t1.getTime)))
        var ret = List[SimulationRecorder]()
        val jsons = destringify (recorder.jsons)
        jsons.foreach (
            x ⇒
            {
                val json = x.asScala
                val name = json("name").asInstanceOf[JsonString].getString
                val file = "output_data/" + name + "_" + just_date.format (start) + ".csv"
                ret = ret :+ SimulationRecorder (
                    name,
                    json("parent").asInstanceOf[JsonString].getString,
                    json("type").asInstanceOf[JsonString].getString,
                    json("property").asInstanceOf[JsonString].getString,
                    json("unit").asInstanceOf[JsonString].getString,
                    file,
                    recorder.interval,
                    recorder.aggregations)
            }
        )
        ret
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

    def pickone (singles: Array[String]) (nodes: Iterable[ch.ninecode.sim.SimulationNode]): SimulationNode =
    {
        nodes.find (node ⇒ singles.contains (node.equipment)) match
        {
            case Some (node) ⇒ node
            case _ ⇒ nodes.head // just take the first
        }
    }

    def queryNetwork (island: String): (Iterable[GLMNode], Iterable[Iterable[GLMEdge]]) =
    {
        // get nodes in the TopologicalIsland
        val members = get[TopologicalNode].filter (_.TopologicalIsland == island)
        // get terminals in the TopologicalIsland
        val terminals = get[Terminal].keyBy (_.TopologicalNode).join (members.keyBy (_.id)).values.map (_._1)
        // get equipment in the TopologicalIsland and associated Terminal
        val equipment_terminals = get[ConductingEquipment].keyBy (_.id).join (terminals.keyBy (_.ConductingEquipment)).values
        // make a list of all single terminal equipment as the preferred association to the node
        val singles = equipment_terminals.groupBy (_._2.ConductingEquipment).filter (1 == _._2.size).map (_._2.head._1.id).collect
        // compose ConductingEquipment, Terminal, and PositionPoint(s)
        val equipment = equipment_terminals.keyBy (_._1.Equipment.PowerSystemResource.Location).leftOuterJoin (get[PositionPoint].groupBy (_.Location))
            .map (x ⇒ (x._2._1._1, x._2._1._2, x._2._2)).distinct
        // get all nodes with their voltage - it is assumed that some equipment on the transformer secondary (secondaries) has a voltage
        // but this doesn't include the transformer primary node - it's not part of the topology
        // ToDo: fix this 1kV multiplier on the voltages
        val nodes = equipment.keyBy (_._1.BaseVoltage).join (get[BaseVoltage].keyBy (_.id)).values.map (
            node ⇒ SimulationNode (node._1._2.TopologicalNode, node._1._1.id, toCoordinate (node._1._2.ACDCTerminal.sequenceNumber, node._1._3), node._2.nominalVoltage * 1000.0)
        ).groupBy (_.id_seq).values.map (pickone (singles)).collect
        // get all equipment with two nodes in the topology that separate different TopologicalNode
        val two_terminal_equipment = equipment.keyBy (_._1.id).groupByKey.filter (
            edge ⇒ edge._2.size > 1 && edge._2.head._2.TopologicalNode != edge._2.tail.head._2.TopologicalNode
        )
        // convert ConductingEquipment to Element with Terminal(s) and PositionPoints
        // all copies of the PositionPoints will be the same or just parallel conductors, so we arbitrarily take the head
        val elements = get[Element]("Elements").keyBy (_.id).join (two_terminal_equipment)
            .values.map (x ⇒ (x._1, x._2.map (_._2), x._2.head._3))
        // combine parallel equipment
        val eq3 = elements.keyBy (_._2.map (_.id).toArray.sortWith (_ < _).mkString ("_")).groupByKey.values
        val edges = eq3.map (
            _.map (
                edge ⇒ SimulationEdge (edge._1.id, edge._2.head.TopologicalNode, edge._2.tail.head.TopologicalNode, edge._1, toCoordinates (edge._3))
            )
        ).collect
        log.info ("""island "%s: %d nodes, %d edges"""".format (island, nodes.length, edges.length))
        (nodes, edges)
    }

    def all_transformers (trafo_islands: Map[String, String]): Seq[String] =
        trafo_islands.groupBy (_._2).map (_._2.keys.toArray.sortWith (_ < _).mkString ("_")).toSeq

    def make_tasks (job: SimulationJob): Seq[SimulationTask] =
    {
        log.info ("""preparing simulation job "%s"""".format (job.name))
        var ret = List[SimulationTask]()

        // get all transformer secondary TopologicalIsland names
        val sql =
            pack (
                """select
                  |    p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID, n.TopologicalIsland
                  |from
                  |    Terminal t,
                  |    PowerTransformer p,
                  |    TopologicalNode n
                  |where
                  |    t.ConductingEquipment = p.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID
                  |and t.ACDCTerminal.sequenceNumber = 2
                  |and t.TopologicalNode = n.IdentifiedObject.mRID""".stripMargin)
        log.info ("""executing "%s"""".format (sql))
        val df = session.sql (sql)
        import session.implicits._
        val trafo_islands = df.map (row ⇒ (row.getString (0), row.getString (1))).collect.toMap
        log.info ("""%d transformer island%s found""".format (trafo_islands.size, if (1 == trafo_islands.size) "" else "s"))

        // query the players
        val playersets = job.players.map (
            player ⇒
            {
                log.info ("""executing "%s" as %s""".format (player.title, player.rdfquery))
                val resultset = session.sql (player.rdfquery)
                val index = resultset.head.schema.fieldIndex ("island")
                (player, resultset, index)
            }
        )
        // query the recorders
        val recordersets = job.recorders.map (
            recorder ⇒
            {
                log.info ("""executing "%s" as %s""".format (recorder.title, recorder.query))
                val resultset = session.sql (recorder.query)
                val index = resultset.head.schema.fieldIndex ("island")
                (recorder, resultset, index)
            }
        )

        // process the list of transformers
        val transformers = if (0 != job.transformers.size) job.transformers else all_transformers (trafo_islands)
        val query = SimulationSparkQuery ()
        transformers.foreach (
            transformer ⇒
            {
                // handle ganged transformers
                val names = transformer.split ("_").map (x ⇒ trafo_islands.getOrElse (x, null)).filter (_ != null)
                val island = if (0 == names.length) null else names(0)
                if (null == island)
                    log.error ("""topological island not found for transformer "%s" secondary""".format (transformer))
                else
                {
                    if (!names.forall (_ == island))
                        log.error ("""transformer "%s" has different topological islands (%s) on its secondary connections, using %s""".format (transformer, names.mkString (", "), island))

                    val (nodes, edges) = queryNetwork (island)
                    val players = playersets.map (df ⇒ df._1.copy (jsons = stringify (query.pack (df._2.filter (row ⇒ row.getString (df._3) == island)))))
                    val recorders = recordersets.map (df ⇒ df._1.copy (jsons = stringify (query.pack (df._2.filter (row ⇒ row.getString (df._3) == island)))))
                    val start = iso_parse (job.interval("start"))
                    val end = iso_parse (job.interval("end"))
                    val task = SimulationTask (
                        island,
                        start.clone.asInstanceOf[Calendar],
                        end.clone.asInstanceOf[Calendar],
                        nodes,
                        edges,
                        players.flatMap (x ⇒ generate_player_csv (x, start.getTimeInMillis, end.getTimeInMillis)).toArray,
                        recorders.flatMap (x ⇒ generate_recorder_csv (x, start.getTimeInMillis, end.getTimeInMillis)).toArray
                    )
                    ret = ret :+ task
                }
            }
        )
        ret
    }

    def write_player_csv (name: String, text: String): Unit =
    {
        val file = new File (options.workdir + name)
        file.getParentFile.mkdirs
        if (null != text)
            using (new PrintWriter (file, "UTF-8"))
            {
                writer =>
                    writer.write (text)
            }
    }

    def make_record (time: Long, real: Double, imag: Double): JsonObject =
        Json.createObjectBuilder ()
            .add ("time", time)
            .add ("real", real)
            .add ("imag", imag)
            .build ()

    // zero player
    // "1970-01-01 00:00:00,0.0,0.0"
    def zero: JsonObject = make_record (0L, 0.0, 0.0)

    def create_player_csv (cluster: Cluster, player: SimulationPlayer, file_prefix: String)
    {
        val query = SimulationCassandraQuery (cluster, player.sql)
        val resultset = query.execute ()
        val count = resultset.length

        val set =
            if (0 == count)
            {
                log.warn ("""0 records found for "%s" as %s""".format (player.name, player.sql))
                List (zero)
            }
            else
            {
                val found = resultset.filter (
                    j ⇒
                    {
                        val time = j.getJsonNumber ("time").longValue
                        time >= player.start && time < player.end
                    }
                )
                log.info ("""%d records found for "%s" as %s""".format (found.size, player.name, player.sql))
                found.sortBy (_.getJsonNumber ("time").longValue)
                // if it is necessary, bookend records could be added:
                // make_record (player.start, 0.0, 0.0) +: sorted :+ make_record (player.end, 0.0, 0.0)
            }
        val text = set.map (glm_format).mkString ("\n")
        write_player_csv (file_prefix + player.file, text)
    }

    def gridlabd (trafo: SimulationTrafoKreis): Boolean =
    {
        val command = Seq ("bash", "-c", """pushd "%s%s";gridlabd "%s.glm";popd;""".format (options.workdir, trafo.directory, trafo.name))
        var lines = new ListBuffer[String]()
        var warningLines = 0
        var errorLines = 0
        def check (line: String): Unit =
        {
            lines += line
            if (line.contains ("WARNING")) warningLines += 1
            if (line.contains ("ERROR")) errorLines += 1
        }
        val countLogger = ProcessLogger (check, check)
        val p: Process = Process (command).run (countLogger)
        // wait for the process to finish
        val exit_code = p.exitValue
        if (0 != errorLines)
            log.error ("%d warnings, %d errors: %s".format (warningLines, errorLines, lines.mkString ("\n\n", "\n", "\n\n")))
        else if (0 != warningLines)
            log.warn ("%d warnings, %d errors: %s".format (warningLines, errorLines, lines.mkString ("\n\n", "\n", "\n\n")))

        (0 == exit_code) && (0 == errorLines)
    }

    def read_recorder_csv (file: String, element: String, one_phase: Boolean, units: String): Iterator[ThreePhaseComplexDataElement] =
    {
        val name = new File (options.workdir + file)
        val text: Iterator[String] = Source.fromFile (name, "UTF-8").getLines ().filter (line ⇒ (line != "") && !line.startsWith ("#"))
        val date_format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z")
        def toTimeStamp (string: String): Long =
        {
            date_format.parse (string).getTime
        }
        text.map (
            line ⇒
            {
                val fields = line.split(",")
                if (one_phase)
                    if (fields.length == 2)
                        ThreePhaseComplexDataElement(element, toTimeStamp(fields(0)), Complex.fromString (fields(1)), Complex(0.0), Complex(0.0), units)
                    else
                        ThreePhaseComplexDataElement(element, toTimeStamp(fields(0)), Complex(fields(1).toDouble, fields(2).toDouble), Complex(0.0), Complex(0.0), units)
                else
                    if (fields.length == 4)
                        ThreePhaseComplexDataElement(element, toTimeStamp(fields(0)), Complex.fromString (fields(1)), Complex.fromString (fields(2)), Complex.fromString (fields(3)), units)
                    else
                        ThreePhaseComplexDataElement(element, toTimeStamp(fields(0)), Complex(fields(1).toDouble, fields(2).toDouble), Complex(fields(3).toDouble, fields(4).toDouble), Complex(fields(5).toDouble, fields(6).toDouble), units)
            }
        )
    }

    def store_recorder_csv (cluster: Cluster, recorder: SimulationRecorder, simulation: String, file_prefix: String): Unit =
    {
        val data = read_recorder_csv (file_prefix + recorder.file, recorder.parent, one_phase = true, recorder.unit)
        val insert = SimulationCassandraInsert (cluster)
        val count = insert.execute (data, recorder.typ, recorder.interval, simulation, recorder.aggregations)
        log.info ("""%d records stored for "%s"""".format (count, recorder.name))
    }

    def write_glm (trafo: SimulationTrafoKreis): Unit =
    {
        log.info ("""generating %s""".format (trafo.directory + trafo.transformer.transformer_name + ".glm"))
        val generator = SimulationGLMGenerator (one_phase = true, date_format = glm_date_format, trafo)
        val text = generator.make_glm ()
        val file = new File (options.workdir + trafo.directory + trafo.transformer.transformer_name + ".glm")
        file.getParentFile.mkdirs
        using (new PrintWriter (file, "UTF-8"))
        {
            writer =>
                writer.write (text)
        }
    }

    def store_geojson_points (cluster: Cluster, trafo: SimulationTrafoKreis): Unit =
    {
        val session: Session = cluster.connect
        val sql = """insert into cimapplication.geojson_points json ?"""
        val prepared = session.prepare (sql)
        val statement = prepared.bind ()
        for (n <- trafo.nodes)
        {
            val node = n.asInstanceOf[SimulationNode]
            val json = """{ "simulation": "%s", "mrid": "%s", "type": "Feature", "geometry": { "type": "Point", "coordinates": [ %g, %g ] } }"""
                .format (trafo.simulation, node.equipment, node.position._1, node.position._2)
            statement.setString (0, json)
            session.execute (statement)
        }
        log.info ("""%d geojson point features stored for "%s"""".format (trafo.nodes.size, trafo.name))
    }

    def store_geojson_lines (cluster: Cluster, trafo: SimulationTrafoKreis): Unit =
    {
        val session: Session = cluster.connect
        val sql = """insert into cimapplication.geojson_lines json ?"""
        val prepared = session.prepare (sql)
        val statement = prepared.bind ()
        for (raw <- trafo.edges)
        {
            val edge = raw.asInstanceOf[Iterable[SimulationEdge]].head // ToDo: parallel edges?
            val json = """{ "simulation": "%s", "mrid": "%s", "type": "Feature", "geometry": { "type": "LineString", "coordinates": [ %s ] } }"""
                .format (trafo.simulation, edge.element.id, edge.position.map (p ⇒ """[%g,%g]""".format (p._1, p._2)).mkString (",")) // [75.68, 42.72], [75.35, 42.75]
            statement.setString (0, json)
            session.execute (statement)
        }
        log.info ("""%d geojson line features stored for "%s"""".format (trafo.edges.size, trafo.name))
    }

    def get_points (trafo: SimulationTrafoKreis): Iterable[(Double, Double)] =
    {
        for (raw <- trafo.nodes)
            yield raw.asInstanceOf[SimulationNode].position
    }

    def store_geojson_polygons (cluster: Cluster, trafo: SimulationTrafoKreis): Unit =
    {
        val session: Session = cluster.connect
        val sql = """insert into cimapplication.geojson_polygons json ?"""
        val prepared = session.prepare (sql)
        val statement = prepared.bind ()
        val hull = Hull.scan (get_points (trafo).toList)
        val json = """{ "simulation": "%s", "mrid": "%s", "type": "Feature", "geometry": { "type": "Polygon", "coordinates": [ [ %s ] ] } }"""
            .format (trafo.simulation, trafo.transformer.transformer_name, hull.map (p ⇒ """[%g,%g]""".format (p._1, p._2)).mkString (","))
        statement.setString (0, json)
        session.execute (statement)
        log.info ("""geojson polygon feature stored for "%s"""".format (trafo.name))
    }

    def execute (trafo: SimulationTrafoKreis): Unit =
    {
        log.info (trafo.island + " from " + iso_date_format.format (trafo.start_time.getTime) + " to " + iso_date_format.format (trafo.finish_time.getTime))
        write_glm (trafo)
        val cluster = Cluster.builder.addContactPoint (options.host).build
        trafo.players.foreach (x ⇒ create_player_csv (cluster, x, trafo.directory))
        new File (options.workdir + trafo.directory + "output_data/").mkdirs
        if (gridlabd (trafo))
            trafo.recorders.foreach (x ⇒ store_recorder_csv (cluster, x, trafo.simulation, trafo.directory))
        else
            log.warn ("""skipping recorder input for "%s"""".format (trafo.name))
        if (!options.keep)
            FileUtils.deleteQuietly (new File (options.workdir + trafo.directory))
        store_geojson_points (cluster, trafo)
        store_geojson_lines (cluster, trafo)
        store_geojson_polygons (cluster, trafo)
    }

    def process (batch: Seq[SimulationJob]): String =
    {
        val storage = StorageLevel.fromString (options.storage)

        val ajob = batch.head // assumes that all jobs in a batch should have the same cluster state
        read (ajob.cim, ajob.cimreaderoptions, storage)

        val tasks = batch.flatMap (make_tasks)
        val id = java.util.UUID.randomUUID.toString
        log.info ("""%d task%s to do for simulation %s""".format (tasks.size, if (1 == tasks.size) "" else "s", id))

        val transformers = new Transformers (session, storage)
        val tdata = transformers.getTransformerData (topological_nodes = true, null)
        val trafokreise = tasks.map (
            task ⇒
            {
                // get the transformer(s)
                val transformers: Array[TransformerSet] = tdata.keyBy (_.node1) // (low_voltage_node_name, TData)
                    .join (get[TopologicalNode].keyBy (_.id)) // (low_voltage_node_name, (TData, TopologicalNode))
                    .filter (_._2._2.TopologicalIsland == task.island) // ... for this Trafokreis
                    .map (x ⇒ (x._1, x._2._1)) // (low_voltage_node_name, TData)
                    .groupByKey.values.map (_.toArray).map (TransformerSet).collect
                if (transformers.length > 1)
                    log.error ("""multiple transformer sets for island %s, (%s)""".format (task.island, transformers.map (_.transformer_name).mkString (",")))
                val date = just_date.format (task.start.getTime)
                SimulationTrafoKreis (
                    id,
                    task.island,
                    transformers(0),
                    task.nodes,
                    task.edges,
                    task.start,
                    task.end,
                    task.players,
                    task.recorders,
                    transformers(0).transformer_name + "_" + date + System.getProperty ("file.separator")
                )
            }
        )

        val executors = Math.max (1, session.sparkContext.getExecutorMemoryStatus.keys.size - 1)
        val simulations = session.sparkContext.parallelize (trafokreise, executors)
        simulations.foreach (execute)
        // clean up
        session.sparkContext.getPersistentRDDs.foreach (
            named ⇒
            {
                named._2.unpersist (false)
                named._2.name = null
            }
        )

        // insert into simulation table and return the UUID
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
        for (x ← tasks.head.players)
        {
            val player = Json.createObjectBuilder
            player.add ("name", x.name)
            player.add ("parent", x.parent)
            player.add ("typ", x.typ)
            player.add ("property", x.property)
            player.add ("file", x.file)
            player.add ("sql", x.sql)
            player.add ("start", iso_date_format.format (new Date (x.start)))
            player.add ("end", iso_date_format.format (new Date (x.end)))
            players.add (player)
        }
        record.add ("players", players)
        val recorders = Json.createArrayBuilder
        for (x ← tasks.head.recorders)
        {
            val recorder = Json.createObjectBuilder
            recorder.add ("name", x.name)
            recorder.add ("parent", x.parent)
            recorder.add ("typ", x.typ)
            recorder.add ("property", x.property)
            recorder.add ("unit", x.unit)
            recorder.add ("file", x.file)
            recorder.add ("interval", x.interval.toString)
            recorder.add ("aggregations", x.aggregations.map (y ⇒ if (y.time_to_live == "") y.intervals.toString else y.intervals.toString + "@" + y.time_to_live.substring (y.time_to_live.lastIndexOf (" ") + 1)).mkString (","))
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
        val prepared = c.prepare ("""insert into cimapplication.simulation json ?""")
        val bound = prepared.bind ()
        bound.setString (0, string.toString)
        c.execute (bound)

        id
    }

    def run (): Seq[String] =
    {
        val jobs = SimulationJob.getAll (options)
        // organize by same RDF and same options
        val batches = jobs.groupBy (job ⇒ job.cim + job.optionString)
        batches.values.map (process).toSeq
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
            classOf[ch.ninecode.sim.SimulationRecorder],
            classOf[ch.ninecode.sim.SimulationRecorderQuery],
            classOf[ch.ninecode.sim.SimulationSparkQuery],
            classOf[ch.ninecode.sim.SimulationTask],
            classOf[ch.ninecode.sim.SimulationTrafoKreis]
        )
    }
}
