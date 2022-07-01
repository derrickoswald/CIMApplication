package ch.ninecode.sim

import java.io.Closeable
import java.io.File
import java.io.PrintWriter
import java.net.URI
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

import ch.ninecode.net.LineEdge
import ch.ninecode.util.Complex
import ch.ninecode.util.ThreePhaseComplexDataElement

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
        LogManager.getLogger(getClass.getName).setLevel(org.apache.log4j.Level.INFO)
    val log: Logger = LoggerFactory.getLogger(getClass)

    val calendar: Calendar = Calendar.getInstance()
    calendar.setTimeZone(TimeZone.getTimeZone("GMT"))
    calendar.setTimeInMillis(0L)

    val glm_date_format: SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z")
    glm_date_format.setCalendar(calendar)

    val iso_date_format: SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    iso_date_format.setCalendar(calendar)

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

    def write_glm (trafo: SimulationTrafoKreis, workdir: String): Unit =
    {
        val filename = s"${trafo.directory}${trafo.transformer.transformer_name}.glm"
        log.info(s"generating $filename")
        val generator = SimulationDirectionGenerator(one_phase = true, date_format = glm_date_format, trafo)
        val text = generator.make_glm()
        val file = new File(s"$workdir$filename")
        val _ = file.getParentFile.mkdirs
        using(new PrintWriter(file, "UTF-8"))
        {
            writer =>
                writer.write(text)
        }
    }

    def gridlabd (trafo: SimulationTrafoKreis, workdir: String): (Boolean, String) =
    {
        log.info(s"executing GridLAB-D for ${trafo.name}")

        var dir = trafo.directory
        if (dir.takeRight(1) == """\""")
            dir = dir.slice(0, dir.length - 1)

        val os = System.getProperty("os.name")
        val command =
            if (os.startsWith("Windows"))
            {
                log.info("Running GridLABD on Windows")
                val pipeFileName = "./src/test/resources/pipe.sh"
                val pipeContent =
                    s"""#!/bin/bash
                       |while read line; do
                       |    pushd $$1 > /dev/null;
                       |    gridlabd.exe --quiet $$2 > /dev/null;
                       |    popd > /dev/null;
                       |done""".stripMargin
                using(new PrintWriter(pipeFileName))
                {
                    writer =>
                        writer.write(pipeContent)
                }

                val uri = new URI(workdir.replace("\\", "/"))
                val pattern = """([A-Z])""".r
                val scheme = pattern.replaceAllIn(uri.getScheme, m =>
                {
                    "/" + m.group(1).toLowerCase
                })

                val workdir_win_bash = scheme + uri.getPath
                Seq("bash",
                    pipeFileName,
                    s"$workdir_win_bash$dir",
                    s"${trafo.name}.glm"
                )
            } else
            {
                val bash = s"""pushd "$workdir$dir";gridlabd --quiet "${trafo.name}.glm";popd;"""
                Seq("bash", "-c", bash)
            }

        val lines = new ListBuffer[String]()
        var warningLines = 0
        var errorLines = 0

        def check (line: String): Unit =
        {
            if (line.trim != "")
            {
                val _ = lines += line
            }
            if (line.contains("WARNING")) warningLines += 1
            if (line.contains("ERROR")) errorLines += 1
            if (line.contains("FATAL")) errorLines += 1
        }

        val countLogger = ProcessLogger(check, check)
        val process = Process(command).run(countLogger)
        // wait for the process to finish
        val exit_code = process.exitValue

        def plural (i: Int): String = if (1 == i) "" else "s"

        def message: String =
            s"GridLAB-D: $warningLines warning${plural(warningLines)}, $errorLines error${plural(errorLines)}: ${lines.mkString("\n\n", "\n", "\n\n")}"

        if (0 != errorLines)
            log.error(message)
        else
            if (0 != warningLines)
                log.warn(message)

        ((0 == exit_code) && (0 == errorLines), if (0 == exit_code) lines.mkString("\n\n", "\n", "\n\n") else s"gridlabd exit code $exit_code")
    }

    def read_voltage_dump_csv (workdir: String, file: String, time: Long, units: String): Array[ThreePhaseComplexDataElement] =
    {
        val name = new File(workdir + file)
        if (!name.exists)
        {
            log.error(s"voltage dump file ${name.getCanonicalPath} does not exist")
            Array()
        }
        else
        {
            val handle = Source.fromFile(name, "UTF-8")
            val text = handle.getLines().filter(line => (line != "") && !line.startsWith("#") && !line.startsWith("node_name"))

            val ret = text.map(
                line =>
                {
                    val fields = line.split(",")
                    ThreePhaseComplexDataElement(
                        fields(0),
                        time,
                        Complex.fromPolar(fields(1).toDouble, fields(2).toDouble),
                        Complex.fromPolar(fields(3).toDouble, fields(4).toDouble),
                        Complex.fromPolar(fields(5).toDouble, fields(6).toDouble),
                        units)
                }
            ).toArray
            handle.close

            ret
        }
    }

    def execute (trafo: SimulationTrafoKreis): Map[String, Int] =
    {
        log.info(trafo.island + " direction detection")

        write_glm(trafo, workdir)

        // execute gridlabd
        val _ = new File(workdir + trafo.directory + "output_data/").mkdirs
        val ret = gridlabd(trafo, workdir)

        // read the voltage dump file and convert to direction
        val list = if (ret._1)
        {
            val file = s"${trafo.directory}output_data/${trafo.name}_voltdump.csv"
            val records = read_voltage_dump_csv(workdir, file, trafo.start_time.getTimeInMillis, "V")
            val lookup = records.map(x => (x.element, x)).toMap
            trafo.edges
                .filter(_.rawedge match
                { case _: LineEdge => true;
                    case _ => false
                })
                .map(
                    edge =>
                    {
                        val v1 = lookup(edge.cn1)
                        val v2 = lookup(edge.cn2)
                        if (v1.value_a.modulus > v2.value_a.modulus) // ToDo: three phase ?
                            (edge.id, +1)
                        else
                            (edge.id, -1)
                    }
                ) ++ Seq((trafo.transformer.transformer_name, +1))
        }
        else
        {
            log.error(s"GridLAB-D failed for ${trafo.name}: ${ret._2}")
            List()
        }
        list.toMap
    }
}
