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

import org.apache.log4j.LogManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.util.ThreePhaseComplexDataElement
import ch.ninecode.util._

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
 * @param workdir   the directory to create the .glm and location of /input_data and /output_data directories
 * @param three_phase if <code>true</code> simulate in three phase
 * @param fake_three_phase if <code>true</code> convert single phase readings on phase A into three phase
 * @param cim_temperature the temperature of the elements in the CIM file (°C)
 * @param simulation_temperature the temperature at which to run the simulation (°C)
 * @param swing_voltage_factor factor to apply to the nominal slack voltage, e.g. 1.03 = 103% of nominal
 * @param keep      when <code>true</code> do not delete the generated .glm, player and recorder files
 * @param verbose   when <code>true</code> set the log level for this class as INFO
 */
case class SimulationRunner (
    cassandra: String,
    keyspace: String,
    workdir: String,
    three_phase: Boolean,
    fake_three_phase: Boolean,
    cim_temperature: Double = 20.0,
    simulation_temperature: Double = 20.0,
    swing_voltage_factor: Double = 1.0,
    keep: Boolean = false,
    verbose: Boolean = false) extends Serializable
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
        val generator = SimulationGLMGenerator (
            one_phase = !three_phase,
            date_format = glm_date_format,
            cim_temperature = cim_temperature,
            simulation_temperature = simulation_temperature,
            swing_voltage_factor = swing_voltage_factor,
            kreis = trafo)

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
    def glm_format (index: Int) (datum: SimulationPlayerData): String =
    {
        val time = glm_date_format.format (datum.time)
        val (r, i) = (datum.readings(index), datum.readings(index + 1))
        val (real, imag) = if (three_phase && fake_three_phase) (r / 3.0, i / 3.0) else (r, i)
        s"$time,$real,$imag"
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

    // relies on the player file being of the form: "input_data/" + player.name + ".csv"
    def phase_file (file: String, suffix: String): String =
    {
        val base = file.substring (0, file.length - 4)
        s"$base$suffix.csv"
    }

    def create_player_csv (file_prefix: String)(arg: (SimulationPlayer, Iterable[SimulationPlayerData])): Unit =
    {
        val (player, player_data) = arg

        // transform the data
        val program = MeasurementTransform.compile (player.transform)
        val data = if (player_data.isEmpty)
            Array (SimulationPlayerData ())
        else
            program.transform (player_data.toArray.sortBy (_.time))

        val file = file_prefix + player.file
        if (three_phase)
        {
            if (fake_three_phase)
            {
                val text = data.map (glm_format (0)).mkString ("\n")
                write_player_csv (phase_file (file, "_R"), text)
                write_player_csv (phase_file (file, "_S"), text)
                write_player_csv (phase_file (file, "_T"), text)
            }
            else
            {
                write_player_csv (phase_file (file, "_R"), data.map (glm_format (0)).mkString ("\n"))
                write_player_csv (phase_file (file, "_S"), data.map (glm_format (2)).mkString ("\n"))
                write_player_csv (phase_file (file, "_T"), data.map (glm_format (4)).mkString ("\n"))
            }
        }
        else
            write_player_csv (file, data.map (glm_format (0)).mkString ("\n"))
    }

    def gridlabd (trafo: SimulationTrafoKreis, workdir: String): (Boolean, List[String]) =
    {
        log.info ("""executing GridLAB-D for %s""".format (trafo.name))

        var dir = trafo.directory
        if (dir.takeRight(1) == """\""")
            dir = dir.slice(0, dir.length - 1)
        val bash = """pushd "%s%s";gridlabd --quiet "%s.glm";popd;""".format (workdir, dir, trafo.name)
        val command = Seq ("bash", "-c", bash)
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

        val problems: List[String] = (if (0 != exit_code) List(s"gridlabd exit code $exit_code") else List[String] ()) ++ lines.toList
        ((0 == exit_code) && (0 == errorLines), problems)
    }

    def read_recorder_csv (workdir: String, file: String, element: String, units: String, multiplier: Double): Array[ThreePhaseComplexDataElement] =
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
                    if (three_phase)
                        if (fields.length == 4)
                            ThreePhaseComplexDataElement (element, toTimeStamp (fields (0)), multiplier * Complex.fromString (fields (1)), multiplier * Complex.fromString (fields (2)), multiplier * Complex.fromString (fields (3)), units)
                        else
                            ThreePhaseComplexDataElement (element, toTimeStamp (fields (0)), multiplier * Complex (fields (1).toDouble, fields (2).toDouble), multiplier * Complex (fields (3).toDouble, fields (4).toDouble), multiplier * Complex (fields (5).toDouble, fields (6).toDouble), units)
                    else
                        if (fields.length == 2)
                            ThreePhaseComplexDataElement (element, toTimeStamp (fields (0)), multiplier * Complex.fromString (fields (1)), Complex (0.0), Complex (0.0), units)
                        else
                            ThreePhaseComplexDataElement (element, toTimeStamp (fields (0)), multiplier * Complex (fields (1).toDouble, fields (2).toDouble), Complex (0.0), Complex (0.0), units)
                }
            ).toArray
            handle.close
            if (!keep)
                name.delete

            ret
        }
    }

    case class Accumulator
    (
        intervals: Int,
        average: Boolean,
        ttl: Int,
        var count: Int = 0,
        var value_a_re: Double = 0.0,
        var value_a_im: Double = 0.0,
        var value_b_re: Double = 0.0,
        var value_b_im: Double = 0.0,
        var value_c_re: Double = 0.0,
        var value_c_im: Double = 0.0
    )
    {
        def reset (): Unit =
        {
            count = 0
            value_a_re = 0.0
            value_a_im = 0.0
            value_b_re = 0.0
            value_b_im = 0.0
            value_c_re = 0.0
            value_c_im = 0.0
        }
    }

    def read_recorders_and_accumulate (trafo: SimulationTrafoKreis): Iterable[SimulationResult] =
    {
        trafo.recorders.flatMap (
            recorder ⇒
            {
                val measures = recorder.aggregations.find (_.intervals == 1) match
                {
                    case Some (baseline: SimulationAggregate) ⇒
                        // compensate for single phase simulated current using line-line voltage scaling
                        val factor = if (!three_phase && recorder.`type` == "current") 1.0 / math.sqrt (3) else 1.0
                        val multiplier = trafo.directions.getOrElse (recorder.mrid, 1).toDouble * factor
                        val records = read_recorder_csv (workdir, trafo.directory + recorder.file, recorder.mrid, recorder.unit, multiplier).map (
                            entry ⇒
                                SimulationResult
                                (
                                    trafo.simulation,
                                    entry.element,
                                    recorder.`type`,
                                    entry.millis,
                                    recorder.interval * 1000,
                                    entry.value_a.re,
                                    entry.value_a.im,
                                    entry.value_b.re,
                                    entry.value_b.im,
                                    entry.value_c.re,
                                    entry.value_c.im,
                                    entry.units,
                                    baseline.time_to_live
                                )
                        )

                        // these should already be in order, but sort them anyway
                        val sorted = records.sortBy (_.time)

                        // get an accumulator for every interval to be aggregated
                        val accumulators = recorder.aggregations.map (x ⇒ Accumulator (x.intervals, recorder.`type` != "energy", x.time_to_live))

                        // aggregate over the accumulators
                        val accumulated = sorted.flatMap (
                            entry ⇒
                            {
                                accumulators.flatMap (
                                    accumulator ⇒
                                    {
                                        if (accumulator.intervals == 1)
                                            Some (entry)
                                        else
                                        {
                                            accumulator.count = accumulator.count + 1
                                            accumulator.value_a_re = accumulator.value_a_re + entry.real_a
                                            accumulator.value_a_im = accumulator.value_a_im + entry.imag_a
                                            accumulator.value_b_re = accumulator.value_b_re + entry.real_b
                                            accumulator.value_b_im = accumulator.value_b_im + entry.imag_b
                                            accumulator.value_c_re = accumulator.value_c_re + entry.real_c
                                            accumulator.value_c_im = accumulator.value_c_im + entry.imag_c
                                            if (accumulator.count >= accumulator.intervals)
                                            {
                                                if (accumulator.average)
                                                {
                                                    accumulator.value_a_re = accumulator.value_a_re / accumulator.count
                                                    accumulator.value_a_im = accumulator.value_a_im / accumulator.count
                                                    accumulator.value_b_re = accumulator.value_b_re / accumulator.count
                                                    accumulator.value_b_im = accumulator.value_b_im / accumulator.count
                                                    accumulator.value_c_re = accumulator.value_c_re / accumulator.count
                                                    accumulator.value_c_im = accumulator.value_c_im / accumulator.count
                                                }

                                                val timepoint = entry.time - (entry.period * (accumulator.intervals - 1))
                                                val period = entry.period * accumulator.intervals
                                                val new_entry = SimulationResult (
                                                    entry.simulation,
                                                    entry.mrid,
                                                    entry.`type`,
                                                    timepoint,
                                                    period,
                                                    accumulator.value_a_re,
                                                    accumulator.value_a_im,
                                                    accumulator.value_b_re,
                                                    accumulator.value_b_im,
                                                    accumulator.value_c_re,
                                                    accumulator.value_c_im,
                                                    entry.units,
                                                    accumulator.ttl
                                                )
                                                accumulator.reset ()
                                                Some (new_entry)
                                            }
                                            else
                                                None
                                        }
                                    }
                                )
                            }
                        )
                        accumulated
                    case None ⇒
                        log.error ("""no baseline interval ("intervals" = 1) in recorder (%s)""".format (recorder.toString))
                        Array[SimulationResult] ()
                }
                measures
            }
        )
    }

    def execute (trafo: SimulationTrafoKreis, data: Map[String, Iterable[SimulationPlayerData]]): (List[String], Iterable[SimulationResult]) =
    {
        log.info (trafo.island + " from " + iso_date_format.format (trafo.start_time.getTime) + " to " + iso_date_format.format (trafo.finish_time.getTime))

        write_glm (trafo, workdir)

        // match the players to the data
        val players: Iterable[(SimulationPlayer, Iterable[SimulationPlayerData])] =
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
            (List (), read_recorders_and_accumulate (trafo))
        else
        {
            (s"GridLAB-D failed for ${trafo.name}" :: ret._2, List())
        }
    }
}
