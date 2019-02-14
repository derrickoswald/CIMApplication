package ch.ninecode.sim

import java.io.Closeable
import java.io.File
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

import javax.json.Json
import javax.json.JsonObject

import scala.collection.mutable.ListBuffer
import scala.io.Source
import scala.sys.process.Process
import scala.sys.process.ProcessLogger

import com.datastax.driver.core.ResultSetFuture

import org.apache.log4j.LogManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.gl.Complex
import ch.ninecode.gl.ThreePhaseComplexDataElement

/**
 * Perform a GridLAB-D simulation.
 *
 * There are four stages (rough idea of time taken in parenthesis):
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
    def glm_format (datum: SimulationPlayerData): String =
    {
        glm_date_format.format (datum.time) + "," + datum.real + "," + datum.imag
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

    def create_player_csv (file_prefix: String)(arg: (SimulationPlayer, Iterable[SimulationPlayerData])): Unit =
    {
        val player = arg._1
        val data = if (arg._2.isEmpty) Array (SimulationPlayerData (player.mrid, player.`type`, 0L, 0.0, 0.0)) else arg._2.toArray.sortBy (_.time)
        val text = data.map (glm_format).mkString ("\n")
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

    def execute (args: (SimulationTrafoKreis, Iterable[(String, Iterable[SimulationPlayerData])])): Iterable[SimulationResult] =
    {
        val trafo = args._1
        val data = args._2
        log.info (trafo.island + " from " + iso_date_format.format (trafo.start_time.getTime) + " to " + iso_date_format.format (trafo.finish_time.getTime))

        write_glm (trafo, workdir)

        // match the players to the data
        val players: Array[(SimulationPlayer, Iterable[SimulationPlayerData])] =
        trafo.players.map (
            player ⇒
            {
                data.find (x ⇒ x._1 == player.mrid) match
                {
                    case Some (records) ⇒
                        (player, records._2)
                    case None ⇒
                        (player, List())
                }
            }
        )

        // create the player files
        players.foreach (create_player_csv (workdir + trafo.directory))
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
                                recorder.`type`,
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
