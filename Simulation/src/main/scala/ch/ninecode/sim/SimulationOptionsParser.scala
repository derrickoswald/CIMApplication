package ch.ninecode.sim

import org.slf4j.LoggerFactory

import scopt.OptionParser

/**
 * Parser for command line operation.
 *
 * @param APPLICATION_NAME    the name of the program
 * @param APPLICATION_VERSION the version of the program.
 */
@SuppressWarnings (Array ("org.wartremover.warts.NonUnitStatements"))
class SimulationOptionsParser (APPLICATION_NAME: String, APPLICATION_VERSION: String)
    extends OptionParser[SimulationOptions](APPLICATION_NAME)
{
    head (APPLICATION_NAME, APPLICATION_VERSION)

    val default: SimulationOptions = SimulationOptions ()
    var unittest = false
    var helpout = false
    var versionout = false
    val COMMA = ","
    val EQUAL = "="

    implicit val LogLevelsRead: scopt.Read[LogLevels.Value] = scopt.Read.reads (LogLevels.withName)

    implicit val mapRead: scopt.Read[Map[String, String]] = scopt.Read.reads (
        s =>
        {
            val pairs = for (p <- s.split (COMMA); kv = p.split (EQUAL))
                yield
                    {
                        if (2 == kv.length)
                            Some ((kv (0), kv (1)))
                        else
                        {
                            reportError (s"unrecognized key=value pair '$p'")
                            helpout = true
                            None
                        }
                    }
            pairs.flatten.toMap
        }
    )

    override def terminate (exitState: Either[String, Unit]): Unit =
    {
        if ((helpout || versionout) && !unittest)
            exitState match
            {
                case Left (_) => sys.exit (1)
                case Right (_) => sys.exit (0)
            }
    }

    opt [Unit]("unittest")
        .hidden ()
        .action ((_, c) =>
        {
            unittest = true;
            c.copy (unittest = true)
        })
        .text (s"unit testing - don't call sys.exit() [${default.unittest}]")

    version ("version")
        .validate (Unit =>
        {
            versionout = true;
            Right (Unit)
        })
        .text (
            {
                val version = APPLICATION_VERSION.split ("-")
                s"Scala: ${version (0)}, Spark: ${version (1)}, $APPLICATION_NAME: ${version (2)}"
            }
        )

    opt [Unit]("verbose")
        .action ((_, c) => c.copy (verbose = true))
        .text (s"emit progress messages [${default.verbose}]")

    opt [String]("master").valueName ("MASTER_URL")
        .action ((x, c) => c.copy (master = x))
        .text (s"local[*], spark://host:port, mesos://host:port or yarn [${default.master}]")

    opt [Map[String, String]]("opts").valueName ("k1=v1,k2=v2")
        .action ((x, c) => c.copy (options = c.options ++ x))
        .text (s"Spark options [${default.options.map (x => s"${x._1}$EQUAL${x._2}").mkString (COMMA)}]")

    opt [String]("host").valueName ("Cassandra")
        .action ((x, c) => c.copy (host = x))
        .text (s"Cassandra connection host (listen_address or seed in cassandra.yaml) [${default.host}]")

    opt [Int]("port").valueName ("<port_number>")
        .action ((x, c) => c.copy (port = x))
        .text (s"Cassandra connection port [${default.port}]")

    opt [LogLevels.Value]("log")
        .action ((x, c) => c.copy (log_level = x))
        .text (s"log level, one of ${LogLevels.values.mkString (COMMA)} [${default.log_level}]")

    opt [String]("checkpoint").valueName ("<dir>")
        .action ((x, c) => c.copy (checkpoint = x))
        .text (s"checkpoint directory on HDFS, e.g. hdfs://... [${default.checkpoint}]")

    opt [String]("workdir").valueName ("<dir>")
        .action (
            (x, c) =>
            {
                val sep = System.getProperty ("file.separator")
                c.copy (workdir = if (x.endsWith (sep)) x else s"$x$sep")
            }
        )
        .text (s"directory for work files on each executor [${default.workdir}]")

    opt [Unit]("three")
        .action ((_, c) => c.copy (three_phase = true))
        .text (s"perform simulation using three phase load-flow [${default.three_phase}]")

    opt [Unit]("fake")
        .action ((_, c) => c.copy (fake_three_phase = true))
        .text (s"convert single phase measurements into three phase [${default.fake_three_phase}]")

    opt [Unit]("keep")
        .action ((_, c) => c.copy (keep = true))
        .text (s"keep intermediate glm and input/output files in workdir [${default.keep}]")

    opt [Unit]("simulationonly")
        .action ((_, c) => c.copy (simulationonly = true))
        .text (s"perform simulation operations only [${default.simulationonly}]")

    opt [Unit]("postprocessonly")
        .action ((_, c) => c.copy (postprocessonly = true))
        .text (s"perform postprocessing operations only [${default.postprocessonly}]")

    opt [Double]("cable_impedance_limit")
        .action ((x, c) => c.copy (cable_impedance_limit = x))
        .text ("cables with higher impedances for R1 will not be processed with gridlabd [%g]".format (default.cable_impedance_limit))

    arg [String]("<JSON> <JSON>...")
        .optional ()
        .unbounded ()
        .action (
            (x, c) =>
            {
                try
                {
                    val sep = System.getProperty ("file.separator")
                    val file = if (x.startsWith (sep)) x else s"${new java.io.File (".").getCanonicalPath}$sep$x"
                    val source = scala.io.Source.fromFile (file, "UTF-8")
                    val text = source.mkString
                    source.close
                    c.copy (simulation = c.simulation :+ text)
                }
                catch
                {
                    case e: Exception =>
                        val log = LoggerFactory.getLogger (getClass.getName)
                        log.error ("bad input file name", e)
                        helpout = true
                        c
                }
            }
        )
        .text ("simulation files to process")

    help ("help")
        .hidden ()
        .validate (Unit =>
        {
            helpout = true;
            Right (Unit)
        })

    checkConfig (o =>
    {
        o.valid = !(helpout || versionout);
        Right (Unit)
    })

    note (
        """
Simulates networks based on a CIM model and smart meter measurements.

The program uses one or more JSON files to specify the simulation parameters.
One or more CIM files specified by the JSON are read in and a topological analysis
is performed to produce a number of transformer service areas.
For each area it generates a GridLAB-D model file and input player
files based on the smart meter data in Cassandra.
It then execute load-flow analysis for each area, and the output recorder
files are saved in Cassandra.
There are a number of optional postprocessing steps to summarize the results.
"""
    )
}
