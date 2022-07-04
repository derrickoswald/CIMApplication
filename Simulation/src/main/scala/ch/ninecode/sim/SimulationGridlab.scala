package ch.ninecode.sim

import java.io.File
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

import scala.collection.mutable.ListBuffer
import scala.sys.process.Process
import scala.sys.process.ProcessLogger

import org.apache.log4j.LogManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.util.Using

/**
 * @param workdir the directory to create the .glm and location of /input_data and /output_data directories
 * @param verbose when <code>true</code> set the log level for this class as INFO
 */
abstract class SimulationGridlab (workdir: String, verbose: Boolean = false) extends Using with Serializable
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

    def run_gridlabd (trafo: SimulationTrafoKreis): (Boolean, String) =
    {
        log.info(s"executing GridLAB-D for ${trafo.name}")
        val _ = new File(workdir + trafo.directory + "output_data/").mkdirs
        val gridlab_work_dir = get_gridlab_work_dir(trafo)
        val bash = s"""pushd "$gridlab_work_dir";gridlabd --quiet "${trafo.name}.glm";popd;"""
        val command = Seq("bash", "-c", bash)

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

    def get_gridlab_work_dir (trafo: SimulationTrafoKreis): String =
    {
        var dir = trafo.directory
        if (dir.takeRight(1) == """\""")
            dir = dir.slice(0, dir.length - 1)

        val os = System.getProperty("os.name")
        if (os.startsWith("Windows"))
        {
            log.info("Running GridLABD on Windows")
            val uri = new URI(workdir.replace("\\", "/"))
            val pattern = """([A-Z])""".r
            val scheme = pattern.replaceAllIn(uri.getScheme, m =>
            {
                "/mnt/" + m.group(1).toLowerCase
            })

            s"$scheme${uri.getPath}$dir"
        } else
        {
            s"$workdir$dir"
        }
    }
}
