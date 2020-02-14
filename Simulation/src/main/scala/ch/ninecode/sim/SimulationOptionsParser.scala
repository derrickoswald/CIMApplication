package ch.ninecode.sim

import scopt.OptionParser

class SimulationOptionsParser (APPLICATION_NAME: String, APPLICATION_VERSION: String)
    extends OptionParser[SimulationOptions](APPLICATION_NAME)
{
    head (APPLICATION_NAME, APPLICATION_VERSION)

    val default: SimulationOptions = SimulationOptions ()
    var unittest = false
    var helpout = false
    var versionout = false

    implicit val LogLevelsRead: scopt.Read[LogLevels.Value] = scopt.Read.reads (LogLevels.withName)
    implicit val mapRead: scopt.Read[Map[String, String]] = scopt.Read.reads (
        s =>
        {
            val ret = for (p <- s.split (","))
                yield
                {
                    val kv = p.split ("=")
                    (kv (0), kv (1))
                }
            ret.toMap
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

    opt [Unit]("unittest").
        hidden ().
        action ((_, c) => { unittest = true; c.copy (unittest = true) }).
        text ("unit testing - don't call sys.exit() [%s]".format (default.unittest))

    version ("version").
        validate (Unit => { versionout = true; Right (Unit) }).
            text ("Scala: %s, Spark: %s, %s: %s".format (
                APPLICATION_VERSION.split ("-")(0),
                APPLICATION_VERSION.split ("-")(1),
                APPLICATION_NAME,
                APPLICATION_VERSION.split ("-")(2)
            )
        )

    opt [Unit]("verbose").
        action ((_, c) ⇒ c.copy (verbose = true)).
        text ("emit progress messages [%s]".format (default.verbose))

    opt [String]("master").valueName ("MASTER_URL").
        action ((x, c) ⇒ c.copy (master = x)).
        text ("local[*], spark://host:port, mesos://host:port or yarn [%s]".format (default.master))

    opt [Map[String, String]]("opts").valueName ("k1=v1,k2=v2").
        action ((x, c) => c.copy (options = c.options ++ x)).
        text ("Spark options [%s]".format (default.options.map (x ⇒ x._1 + "=" + x._2).mkString (",")))

    opt [String]("host").valueName ("<cassandra>").
        action ((x, c) ⇒ c.copy (host = x)).
        text ("Cassandra connection host (listen_address or seed in cassandra.yaml) [%s]".format (default.host))

    opt [Int]("port").valueName ("<port_number>").
        action ((x, c) ⇒ c.copy (port = x)).
        text ("Cassandra connection port [%s]".format (default.port))

    opt [LogLevels.Value]("log").
        action ((x, c) => c.copy (log_level = x)).
        text ("log level, one of %s [%s]".format (LogLevels.values.iterator.mkString (","), default.log_level))

    opt [String]("checkpoint").valueName ("<dir>").
        action ((x, c) ⇒ c.copy (checkpoint = x)).
        text ("checkpoint directory on HDFS, e.g. hdfs://... [\"%s\"]".format (default.checkpoint))

    opt [String]("workdir").valueName ("<dir>").
        action ((x, c) ⇒
        {
            val sep = System.getProperty ("file.separator"); c.copy (workdir = if (x.endsWith (sep)) x else x + sep)
        }).
        text ("directory for work files on each executor [\"%s\"]".format (default.workdir))

    opt [Unit]("three").
        action ((_, c) ⇒ c.copy (three_phase = true)).
        text ("perform simulation using three phase load-flow [%s]".format (default.three_phase))

    opt [Unit]("fake").
        action ((_, c) ⇒ c.copy (fake_three_phase = true)).
        text ("convert single phase measurements into three phase [%s]".format (default.fake_three_phase))

    opt [Unit]("keep").
        action ((_, c) ⇒ c.copy (keep = true)).
        text ("keep intermediate glm and input/output files in workdir [%s]".format (default.keep))

    opt [Unit]("simulationonly").
        action ((_, c) ⇒ c.copy (simulationonly = true)).
        text ("perform simulation operations only [%s]".format (default.simulationonly))

    opt [Unit]("postprocessonly").
        action ((_, c) ⇒ c.copy (postprocessonly = true)).
        text ("perform postprocessing operations only [%s]".format (default.postprocessonly))

    arg [String]("<JSON> <JSON>...").optional ().unbounded ().
        action ((x, c) ⇒
        {
            try
            {
                val sep = System.getProperty ("file.separator")
                val file = if (x.startsWith (sep)) x else new java.io.File (".").getCanonicalPath + sep + x
                val source = scala.io.Source.fromFile (file, "UTF-8")
                val text = source.mkString
                source.close
                c.copy (simulation = c.simulation :+ text)
            }
            catch
            {
                case e: Exception =>
                    throw new Exception ("bad input file name", e)
            }
        }).
        text ("simulation files to process")

    help ("help").
        hidden ().
        validate (Unit => { helpout = true; Right (Unit) })

    checkConfig (o => { o.valid = !(helpout || versionout); Right (Unit) })

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
