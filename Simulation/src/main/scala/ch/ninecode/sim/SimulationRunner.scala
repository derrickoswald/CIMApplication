package ch.ninecode.sim

import java.io.Closeable
import java.io.File
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

import javax.json.Json
import javax.json.JsonNumber
import javax.json.JsonObject

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.io.Source
import scala.sys.process.Process
import scala.sys.process.ProcessLogger
import com.datastax.driver.core.Cluster
import com.datastax.driver.core.ResultSet
import com.datastax.driver.core.ResultSetFuture
import com.datastax.driver.core.Session
import org.apache.commons.io.FileUtils
import org.apache.log4j.LogManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.gl.Complex
import ch.ninecode.gl.ThreePhaseComplexDataElement

/**
 * Perform a GridLAB-D simulation.
 *
 * There are four stages (rough idea of time taken in parentheis):
 *
 *  - create the GridLAB-D .glm file from the nodes and edges in the SimulationTrafoKreis (0:06)
 *  - query Cassandra for each Player and write the player .csv file (7:05)
 *  - execute gridlabd (2:41)
 *  - store each Recorder .csv file in Cassandra (3:30)
 *
 *
 * @param cassandra a Cassandra seed node name
 * @param keyspace the keyspace to store the results (the keyspace for reading is set by the Cassandra query in the player)
 * @param batchsize the number of insert statements per UNLOGGED batch insert
 * @param workdir the directory to create the .glm and location of /input_data and /output_data directories
 * @param keep when <code>true</code> do not delete the generated .glm, player and recorder files
 */
case class SimulationRunner (cassandra: String, keyspace: String, batchsize: Int, workdir: String, keep: Boolean = false, verbose: Boolean = false) extends Serializable
{
    if (verbose)
        LogManager.getLogger (getClass.getName).setLevel (org.apache.log4j.Level.INFO)
    val log: Logger = LoggerFactory.getLogger (getClass)

    val calendar: Calendar = Calendar.getInstance ()
    calendar.setTimeZone (TimeZone.getTimeZone ("GMT"))
    calendar.setTimeInMillis (0L)

    val glm_date_format: SimpleDateFormat = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss z")
    glm_date_format.setCalendar (calendar)

    val iso_date_format: SimpleDateFormat = new SimpleDateFormat ("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    iso_date_format.setCalendar (calendar)

    def using[T <: Closeable, R](resource: T)(block: T => R): R =
    {
        try { block (resource) }
        finally { resource.close () }
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

    def write_glm (trafo: SimulationTrafoKreis, workdir: String): Unit =
    {
        log.info ("""generating %s""".format (trafo.directory + trafo.transformer.transformer_name + ".glm"))
        val generator = SimulationGLMGenerator (one_phase = true, date_format = glm_date_format, trafo)
        val text = generator.make_glm ()
        val file = new File (workdir + trafo.directory + trafo.transformer.transformer_name + ".glm")
        file.getParentFile.mkdirs
        using (new PrintWriter (file, "UTF-8"))
        {
            writer ⇒
                writer.write (text)
        }
    }

    def store_geojson_points (session: Session, keyspace: String, trafo: SimulationTrafoKreis, extra: Array[(String, String, String)]): Unit =
    {
        val sql = """insert into %s.geojson_points json ?""".format (keyspace)
        val prepared = session.prepare (sql)
        val statement = prepared.bind ()
        for (n <- trafo.nodes)
        {
            val node = n.asInstanceOf[SimulationNode]
            val properties = extra.collect (
                {
                    case (query, id, value) if id == node.equipment =>
                        (query, value)
                }
            ).map (
                pair ⇒
                    """"%s": "%s"""".format (pair._1, pair._2)
            ).mkString ("{", ",", "}")
            val json = """{ "simulation": "%s", "mrid": "%s", "transformer": "%s", "type": "Feature", "geometry": { "type": "Point", "coordinates": [ %s, %s ] }, "properties": %s }"""
                .format (trafo.simulation, node.equipment, trafo.transformer.transformer_name, node.position._1, node.position._2, properties)
            statement.setString (0, json)
            session.execute (statement)
        }
        // log.info ("""%d geojson point features stored for "%s"""".format (trafo.nodes.size, trafo.name))
    }

    def store_geojson_lines (session: Session, keyspace: String, trafo: SimulationTrafoKreis, extra: Array[(String, String, String)]): Unit =
    {
        val sql = """insert into %s.geojson_lines json ?""".format (keyspace)
        val prepared = session.prepare (sql)
        val statement = prepared.bind ()
        for (raw <- trafo.edges)
        {
            val edge = raw.asInstanceOf[Iterable[SimulationEdge]].head // ToDo: parallel edges?
        val coordinates = edge.position.map (p ⇒ """[%s,%s]""".format (p._1, p._2)).mkString (",") // [75.68, 42.72], [75.35, 42.75]
        val properties = extra.collect (
            {
                case (query, id, value) if id == edge.id_equ =>
                    (query, value)
            }
        ).map (
            pair ⇒
                """"%s": "%s"""".format (pair._1, pair._2)
        ).mkString ("{", ",", "}")
            val json = """{ "simulation": "%s", "mrid": "%s", "transformer": "%s", "type": "Feature", "geometry": { "type": "LineString", "coordinates": [ %s ] }, "properties": %s }"""
                .format (trafo.simulation, edge.element.id, trafo.transformer.transformer_name, coordinates, properties)
            statement.setString (0, json)
            session.execute (statement)
        }
        // log.info ("""%d geojson line features stored for "%s"""".format (trafo.edges.size, trafo.name))
    }

    def get_points (trafo: SimulationTrafoKreis): Iterable[(Double, Double)] =
    {
        var points =
            for (raw <- trafo.nodes)
                yield raw.asInstanceOf[SimulationNode].position
        for (raw <- trafo.edges)
            points = points ++ raw.asInstanceOf[Iterable[SimulationEdge]].head.position.toIterable
        points
    }

    def store_geojson_polygons (session: Session, keyspace: String, trafo: SimulationTrafoKreis, extra: Array[(String, String, String)]): Unit =
    {
        val sql = """insert into %s.geojson_polygons json ?""".format (keyspace)
        val prepared = session.prepare (sql)
        val statement = prepared.bind ()
        val hull = Hull.scan (get_points (trafo).toList)
        val coordinates = hull.map (p ⇒ """[%s,%s]""".format (p._1, p._2)).mkString (",")
        val properties = extra.collect (
            {
                case (query, id, value) if id == trafo.transformer.transformer_name =>
                    (query, value)
            }
        ).map (
            pair ⇒
                """"%s": "%s"""".format (pair._1, pair._2)
        ).mkString ("{", ",", "}")
        val json = """{ "simulation": "%s", "mrid": "%s", "type": "Feature", "geometry": { "type": "Polygon", "coordinates": [ [ %s ] ] }, "properties": %s }"""
            .format (trafo.simulation, trafo.transformer.transformer_name, coordinates, properties)
        statement.setString (0, json)
        session.execute (statement)
        // log.info ("""geojson polygon feature stored for "%s"""".format (trafo.name))
    }

    def query_extra (session: Session, keyspace: String, trafo: SimulationTrafoKreis): Array[(String, String, String)] =
    {
        val sql = """select * from %s.key_value where simulation='%s'""".format (keyspace, trafo.simulation)
        val resultset = session.execute (sql)
        if (resultset.nonEmpty)
        {
            val definitions = resultset.getColumnDefinitions
            val index_q = definitions.getIndexOf ("query")
            val index_k = definitions.getIndexOf ("key")
            val index_v = definitions.getIndexOf ("value")
            resultset.iterator.map (row ⇒ (row.getString (index_q), row.getString (index_k), row.getString (index_v))).toArray
        }
        else
            Array()
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

    def write_player_csv (name: String, text: String): Unit =
    {
        val file = new File (name)
        file.getParentFile.mkdirs
        if (null != text)
            using (new PrintWriter (file, "UTF-8"))
            {
                writer ⇒
                    writer.write (text)
            }
    }

    def create_player_csv (session: Session, player: SimulationPlayer, file_prefix: String)
    {
        val query = SimulationCassandraQuery (session, player.sql)
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

    def gridlabd (trafo: SimulationTrafoKreis, workdir: String): (Boolean, String)=
    {
        log.info ("""executing GridLAB-D for %s""".format (trafo.name))

        val command = Seq ("bash", "-c", """pushd "%s%s";gridlabd --quiet "%s.glm";popd;""".format (workdir, trafo.directory, trafo.name))
        var lines = new ListBuffer[String]()
        var warningLines = 0
        var errorLines = 0
        def check (line: String): Unit =
        {
            if (line.trim != "")
                lines += line
            if (line.contains ("WARNING")) warningLines += 1
            if (line.contains ("ERROR")) errorLines += 1
            if (line.contains ("FATAL")) errorLines += 1
        }
        val countLogger = ProcessLogger (check, check)
        val process = Process (command).run (countLogger)
        // wait for the process to finish
        val exit_code = process.exitValue
        if (0 != errorLines)
            log.error ("GridLAB-D: %d warning%s, %d error%s: %s".format (warningLines, if (1 == warningLines) "" else "s", errorLines, if (1 == errorLines) "" else "s", lines.mkString ("\n\n", "\n", "\n\n")))
        else if (0 != warningLines)
            log.warn ("GridLAB-D: %d warning%s, %d error%s: %s".format (warningLines, if (1 == warningLines) "" else "s", errorLines, if (1 == errorLines) "" else "s", lines.mkString ("\n\n", "\n", "\n\n")))

        ((0 == exit_code) && (0 == errorLines), if (0 == exit_code) lines.mkString ("\n\n", "\n", "\n\n") else "gridlabd exit code %d".format (exit_code))
    }

    def read_recorder_csv (workdir: String, file: String, element: String, one_phase: Boolean, units: String): Array[ThreePhaseComplexDataElement] =
    {
        val name = new File (workdir + file)
        if (!name.exists)
        {
            log.error ("""recorder file %s does not exist""".format (name.getCanonicalPath))
            Array()
        }
        else
        {
            val handle = Source.fromFile (name, "UTF-8")
            val text = handle.getLines ().filter (line ⇒ (line != "") && !line.startsWith ("#"))
            val date_format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z")
            def toTimeStamp (string: String): Long =
            {
                date_format.parse (string).getTime
            }
            val ret = text.map (
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
            ).toArray
            handle.close
            ret
        }
    }

    def store_recorder_csv (insert: SimulationCassandraInsert, recorder: SimulationRecorder, simulation: String, workdir: String, file_prefix: String): List[(String, List[ResultSetFuture])] =
    {
        val data = read_recorder_csv (workdir, file_prefix + recorder.file, recorder.mrid, one_phase = true, recorder.unit)
        val (count, resultsets) = insert.execute (recorder.name, data, recorder.typ, recorder.interval, simulation, recorder.aggregations)
        log.info ("""%d records stored for "%s"""".format (count, recorder.name))
        resultsets.filter (_._2.exists (!_.isDone))
    }

    def closeIfDone (resultset: ResultSetFuture): List[ResultSetFuture] =
    {
        if (resultset.isDone)
        {
            //val r: ResultSet = resultset.getUninterruptibly
            val r: ResultSet = resultset.get
            if (!r.wasApplied)
                log.error ("""insert was not applied""")
            val rows = r.all // hopefully this shuts down the socket
            if (!r.isFullyFetched)
                log.error ("""insert resultset was not fully fetched""")
            List()
        }
        else
            List (resultset)
    }


    def execute (trafo: SimulationTrafoKreis): (Boolean, String) =
    {
        log.info (trafo.island + " from " + iso_date_format.format (trafo.start_time.getTime) + " to " + iso_date_format.format (trafo.finish_time.getTime))

        var ret = (false, "default 'failed' return value")
        write_glm (trafo, workdir)
        // Note: According to "4 simple rules when using the DataStax drivers for Cassandra"
        // https://www.datastax.com/dev/blog/4-simple-rules-when-using-the-datastax-drivers-for-cassandra
        //  1) Use one Cluster instance per (physical) cluster (per application lifetime)
        // but Cluster does not implement Serializable, so we can't pass it from master to worker,
        // so we instantiate one per runner execution.
        // It would be better to establish it once per worker JVM.
        using (Cluster.builder.addContactPoint (cassandra).build)
        {
            cluster ⇒
                using (cluster.connect)
                {
                    // Note: The above also says:
                    //   2) Use at most one Session per keyspace, or use a single Session and explicitly specify the keyspace in your queries
                    // so we make one here and pass it as a method parameter
                    session ⇒
                        trafo.players.foreach (x ⇒ create_player_csv (session, x, workdir + trafo.directory))
                        new File (workdir + trafo.directory + "output_data/").mkdirs
                        ret = gridlabd (trafo, workdir)
                        val insert = SimulationCassandraInsert (session, keyspace, batchsize)
                        val resultsets = if (ret._1)
                        {
                            var undone = List[(String, List[ResultSetFuture])] ()
                            for (index ← trafo.recorders.indices)
                            {
                                val recorder = trafo.recorders(index)
                                val next = store_recorder_csv (insert, recorder, trafo.simulation, workdir, trafo.directory)
                                undone = undone.map (r ⇒ (r._1, r._2.flatMap (closeIfDone))).filter (_._2.nonEmpty) ::: next
                            }
                            undone.map (r ⇒ (r._1, r._2.flatMap (closeIfDone))).filter (_._2.nonEmpty).toArray
                        }
                        else
                        {
                            log.warn ("""skipping recorder input for "%s"""".format (trafo.name))
                            Array()
                        }

                        if (!keep)
                            FileUtils.deleteQuietly (new File (workdir + trafo.directory))

                        val extra = query_extra (session, keyspace, trafo)
                        store_geojson_points (session, keyspace, trafo, extra)
                        store_geojson_lines (session, keyspace, trafo, extra)
                        store_geojson_polygons (session, keyspace, trafo, extra)

                        // note: you cannot ask for the resultset, otherwise it will time out
                        // resultsets.foreach (resultset ⇒ if (!resultset.isDone) resultset.getUninterruptibly)
                        val remainder = resultsets.map (r ⇒ (r._1, r._2.flatMap (closeIfDone))).filter (_._2.nonEmpty)
                        remainder.foreach (resultset ⇒ log.warn ("""result set %s is not done yet""".format (resultset._1)))
                }
        }

        ret
    }
}
