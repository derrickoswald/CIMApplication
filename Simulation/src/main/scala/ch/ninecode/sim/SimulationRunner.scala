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
import com.datastax.driver.core.HostDistance
import com.datastax.driver.core.PoolingOptions

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
 * @param cassandra a Cassandra seed node name
 * @param keyspace  the keyspace to store the results (the keyspace for reading is set by the Cassandra query in the player)
 * @param batchsize the number of insert statements per UNLOGGED batch insert
 * @param workdir   the directory to create the .glm and location of /input_data and /output_data directories
 * @param keep      when <code>true</code> do not delete the generated .glm, player and recorder files
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
                    case "time" ⇒ time = x._2.asInstanceOf [JsonNumber].longValue
                    case "real" ⇒ real = x._2.asInstanceOf [JsonNumber].doubleValue
                    case "imag" ⇒ imag = x._2.asInstanceOf [JsonNumber].doubleValue
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

    def gridlabd (trafo: SimulationTrafoKreis, workdir: String): (Boolean, String) =
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
        else
            if (0 != warningLines)
                log.warn ("GridLAB-D: %d warning%s, %d error%s: %s".format (warningLines, if (1 == warningLines) "" else "s", errorLines, if (1 == errorLines) "" else "s", lines.mkString ("\n\n", "\n", "\n\n")))

        ((0 == exit_code) && (0 == errorLines), if (0 == exit_code) lines.mkString ("\n\n", "\n", "\n\n") else "gridlabd exit code %d".format (exit_code))
    }

    def read_recorder_csv (workdir: String, file: String, element: String, one_phase: Boolean, units: String): Array[ThreePhaseComplexDataElement] =
    {
        val name = new File (workdir + file)
        if (!name.exists)
        {
            log.error ("""recorder file %s does not exist""".format (name.getCanonicalPath))
            Array ()
        }
        else
        {
            val handle = Source.fromFile (name, "UTF-8")
            val text = handle.getLines ().filter (line ⇒ (line != "") && !line.startsWith ("#"))
            val date_format = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss z")

            def toTimeStamp (string: String): Long =
            {
                date_format.parse (string).getTime
            }

            val ret = text.map (
                line ⇒
                {
                    val fields = line.split (",")
                    if (one_phase)
                        if (fields.length == 2)
                            ThreePhaseComplexDataElement (element, toTimeStamp (fields (0)), Complex.fromString (fields (1)), Complex (0.0), Complex (0.0), units)
                        else
                            ThreePhaseComplexDataElement (element, toTimeStamp (fields (0)), Complex (fields (1).toDouble, fields (2).toDouble), Complex (0.0), Complex (0.0), units)
                    else
                        if (fields.length == 4)
                            ThreePhaseComplexDataElement (element, toTimeStamp (fields (0)), Complex.fromString (fields (1)), Complex.fromString (fields (2)), Complex.fromString (fields (3)), units)
                        else
                            ThreePhaseComplexDataElement (element, toTimeStamp (fields (0)), Complex (fields (1).toDouble, fields (2).toDouble), Complex (fields (3).toDouble, fields (4).toDouble), Complex (fields (5).toDouble, fields (6).toDouble), units)
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

    def execute (trafo: SimulationTrafoKreis): Iterable[SimulationResult] =
    {
        log.info (trafo.island + " from " + iso_date_format.format (trafo.start_time.getTime) + " to " + iso_date_format.format (trafo.finish_time.getTime))

        write_glm (trafo, workdir)

        // Note: According to "4 simple rules when using the DataStax drivers for Cassandra"
        // https://www.datastax.com/dev/blog/4-simple-rules-when-using-the-datastax-drivers-for-cassandra
        //  1) Use one Cluster instance per (physical) cluster (per application lifetime)
        // but Cluster does not implement Serializable, so we can't pass it from master to worker,
        // so we instantiate one per runner execution.
        // It would be better to establish it once per worker JVM.
        //
        // establish some better pooling options
        val pooling = new PoolingOptions ()
        pooling.setPoolTimeoutMillis (20000)
        pooling.setMaxConnectionsPerHost (HostDistance.LOCAL, 4)
        using (Cluster.builder.addContactPoint (cassandra).withPoolingOptions (pooling).build)
        {
            cluster ⇒
                using (cluster.connect)
                {
                    // Note: The above also says:
                    //   2) Use at most one Session per keyspace, or use a single Session and explicitly specify the keyspace in your queries
                    // so we make one here and pass it as a method parameter
                    session ⇒
                        // create the player files
                        trafo.players.foreach (x ⇒ create_player_csv (session, x, workdir + trafo.directory))
                        // execute gridlabd
                        new File (workdir + trafo.directory + "output_data/").mkdirs
                        val ret = gridlabd (trafo, workdir)
                        // read the recorder files
                        if (ret._1)
                            trafo.recorders.flatMap (
                                recorder ⇒
                                {
                                    read_recorder_csv (workdir, trafo.directory + recorder.file, recorder.mrid, one_phase = true, recorder.unit).map (
                                        entry ⇒
                                            SimulationResult
                                            (
                                                entry.element,
                                                recorder.typ,
                                                recorder.interval * recorder.aggregations.head.intervals * 1000, // ToDo: aggregations
                                                entry.millis,
                                                entry.value_a.im,
                                                entry.value_b.im,
                                                entry.value_c.im,
                                                entry.value_a.re,
                                                entry.value_b.re,
                                                entry.value_c.re,
                                                trafo.simulation,
                                                entry.units
                                            )
                                    )
                                }
                            )
                        else
                        {
                            log.error ("""GridLAB-D failed for %s: %s""".format (trafo.name, ret._2))
                            List()
                        }
// ToDo: delete gridlab intermediate files
//                        if (!keep)
//                            FileUtils.deleteQuietly (new File (workdir + trafo.directory))

                }
        }
    }
}
