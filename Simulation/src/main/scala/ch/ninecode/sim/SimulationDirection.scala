package ch.ninecode.sim

import java.io.Closeable
import java.io.File
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

import scala.collection.mutable.ListBuffer
import scala.io.Source
import scala.sys.process.Process
import scala.sys.process.ProcessLogger

import org.apache.log4j.LogManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.gl.Complex
import ch.ninecode.gl.ThreePhaseComplexDataElement
import ch.ninecode.gl.LineEdge

/**
 * Determine the direction of current in the passive network.
 *
 * This is to handle "Rücklieferung" when a PV installation is supplying
 * more power than the EnergyConsumer is using and feeding back into the network.
 *
 * Each node is loaded with 1kVA in a GridLAB-D load-flow and
 * the current through each cable is simulated and given a sign value (±1)
 * according to whether the simulated value is negative or positive.
 *
 * @param workdir the directory to create the .glm and location of /input_data and /output_data directories
 * @param verbose when <code>true</code> set the log level for this class as INFO
 */
case class SimulationDirection (workdir: String, verbose: Boolean = false)
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

    def write_glm (trafo: SimulationTrafoKreis, workdir: String): Unit =
    {
        log.info ("""generating %s""".format (trafo.directory + trafo.transformer.transformer_name + ".glm"))
        val generator = SimulationDirectionGenerator (one_phase = true, date_format = glm_date_format, trafo)
        val text = generator.make_glm ()
        val file = new File (workdir + trafo.directory + trafo.transformer.transformer_name + ".glm")
        file.getParentFile.mkdirs
        using (new PrintWriter (file, "UTF-8"))
        {
            writer ⇒
                writer.write (text)
        }
    }

    def gridlabd (trafo: SimulationTrafoKreis, workdir: String): (Boolean, String) =
    {
        log.info ("""executing GridLAB-D for %s""".format (trafo.name))

        var dir = trafo.directory
        if (dir.takeRight (1) == """\""")
            dir = dir.slice (0, dir.length - 1)
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

        ((0 == exit_code) && (0 == errorLines), if (0 == exit_code) lines.mkString ("\n\n", "\n", "\n\n") else "gridlabd exit code %d".format (exit_code))
    }

    def read_voltage_dump_csv (workdir: String, file: String, time: Long, units: String): Array[ThreePhaseComplexDataElement] =
    {
        val name = new File (workdir + file)
        if (!name.exists)
        {
            log.error ("""voltage dump file %s does not exist""".format (name.getCanonicalPath))
            Array ()
        }
        else
        {
            val handle = Source.fromFile (name, "UTF-8")
            val text = handle.getLines ().filter (line ⇒ (line != "") && !line.startsWith ("#") && !line.startsWith ("node_name"))

            val ret = text.map (
                line ⇒
                {
                    val fields = line.split (",")
                    ThreePhaseComplexDataElement (fields (0), time, Complex.fromPolar (fields (1).toDouble, fields (2).toDouble), Complex.fromPolar (fields (3).toDouble, fields (4).toDouble), Complex.fromPolar (fields (5).toDouble, fields (6).toDouble), units)
                }
            ).toArray
            handle.close

            ret
        }
    }

    def execute (trafo: SimulationTrafoKreis): Map[String, Int] =
    {
        log.info (trafo.island + " direction detection")

        write_glm (trafo, workdir)

        // execute gridlabd
        new File (workdir + trafo.directory + "output_data/").mkdirs
        val ret = gridlabd (trafo, workdir)

        // read the voltage dump file and convert to direction
        val list = if (ret._1)
        {
            val records = read_voltage_dump_csv (workdir, trafo.directory + "output_data/" + trafo.name + "_voltdump.csv", trafo.start_time.getTimeInMillis, "V")
            val lookup = records.map (x ⇒ (x.element, x)).toMap
            trafo.edges
                .filter (_.rawedge match
                { case line: LineEdge ⇒ true;
                    case _ ⇒ false
                })
                .map (
                    edge ⇒
                    {
                        val v1 = lookup (edge.cn1)
                        val v2 = lookup (edge.cn2)
                        if (v1.value_a.modulus > v2.value_a.modulus) // ToDo: three phase ?
                            (edge.id, +1)
                        else
                            (edge.id, -1)
                    }
                ) ++ Seq ((trafo.transformer.transformer_name, +1))
        }
        else
        {
            log.error ("""GridLAB-D failed for %s: %s""".format (trafo.name, ret._2))
            List ()
        }
        list.toMap
    }
}
