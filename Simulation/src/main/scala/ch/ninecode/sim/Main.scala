package ch.ninecode.sim

import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.util.Properties

import scala.tools.nsc.io.Jar
import scala.util.Random
import scopt.OptionParser

import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import org.slf4j.LoggerFactory
import org.slf4j.Logger

import ch.ninecode.cim.CIMClasses
import ch.ninecode.gl.GridLABD

object Main
{
    val log: Logger = LoggerFactory.getLogger (getClass)
    val properties: Properties =
    {
        val in = this.getClass.getResourceAsStream ("/application.properties")
        val p = new Properties ()
        p.load (in)
        in.close ()
        p
    }
    val APPLICATION_NAME: String = "Simulation"
    val APPLICATION_VERSION: String = properties.getProperty ("version")
    val SPARK: String = properties.getProperty ("spark")

    object LogLevels extends Enumeration
    {
        type LogLevels = Value
        val ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN = Value
    }

    implicit val LogLevelsRead: scopt.Read[LogLevels.Value] = scopt.Read.reads (LogLevels.withName)

    var do_exit = true

    val parser: OptionParser[SimulationOptions] = new scopt.OptionParser[SimulationOptions](APPLICATION_NAME)
    {
        head (APPLICATION_NAME, APPLICATION_VERSION)

        val default = new SimulationOptions

        override def terminate (exitState: Either[String, Unit]): Unit =
            if (do_exit)
                exitState match
                {
                    case Left (_) => sys.exit (1)
                    case Right (_) => sys.exit (0)
                }

        opt [Unit]("unittest").
            hidden ().
            action ((_, c) ⇒ c.copy (unittest = true)).
            text ("unit testing - don't call sys.exit() [%s]".format (default.unittest))

        opt [Unit]("verbose").
            action ((_, c) ⇒ c.copy (verbose = true)).
            text ("emit progress messages [%s]".format (default.verbose))

        opt [String]("master").valueName ("MASTER_URL").
            action ((x, c) ⇒ c.copy (master = x)).
            text ("local[*], spark://host:port, mesos://host:port or yarn [%s]".format (default.master))

        opt [String]("host").valueName ("<cassandra>").
            action ((x, c) ⇒ c.copy (host = x)).
            text ("Cassandra connection host (listen_address or seed in cassandra.yaml) [%s]".format (default.host))

        opt [LogLevels.Value]("logging").
            action ((x, c) ⇒ c.copy (log_level = x)).
            text ("log level, one of " + LogLevels.values.iterator.mkString (",") + " [%s]".format (default.log_level))

        opt [String]("checkpoint").valueName ("<dir>").
            action ((x, c) ⇒ c.copy (checkpoint = x)).
            text ("checkpoint directory on HDFS, e.g. hdfs://... [\"%s\"]".format (default.checkpoint))

        opt [String]("workdir").valueName ("<dir>").
            action ((x, c) ⇒
            {
                val sep = System.getProperty ("file.separator"); c.copy (workdir = if (x.endsWith (sep)) x else x + sep)
            }).
            text ("directory for work files on each executor [\"%s\"]".format (default.workdir))

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
                        log.error (e.getMessage)
                        throw new Exception ("bad input file name")
                }
            }).
            text ("simulation files to process")

        help ("help").text ("prints this usage text")
    }

    def jarForClass (cls: Class[_]): String =
    {
        // see https://stackoverflow.com/questions/320542/how-to-get-the-path-of-a-running-jar-file
        var ret = cls.getProtectionDomain.getCodeSource.getLocation.getPath
        try
        {
            ret = URLDecoder.decode (ret, "UTF-8")
        }
        catch
        {
            case e: UnsupportedEncodingException => e.printStackTrace ()
        }
        if (!ret.toLowerCase ().endsWith (".jar"))
        {
            // as an aid to debugging, make jar in tmp and pass that name
            val name = "/tmp/" + Random.nextInt (99999999) + ".jar"
            val writer = new Jar (new scala.reflect.io.File (new java.io.File (name))).jarWriter ()
            writer.addDirectory (new scala.reflect.io.Directory (new java.io.File (ret + "ch/")), "ch/")
            writer.close ()
            ret = name
        }

        ret
    }

    /**
     * Build jar with dependencies (creates target/program_name_and_version-jar-with-dependencies.jar):
     * mvn package
     * Invoke (on the cluster) with:
     * spark-submit --master spark://sandbox:7077 --conf spark.driver.memory=2g --conf spark.executor.memory=4g /opt/code/program_name_and_version-jar-with-dependencies.jar --verbose --host beach demodata.json
     * or on AWS:
     * /opt/spark/bin/spark-submit --master yarn /disktemp/transfer/program_name_and_version-jar-with-dependencies.jar --host sandbox demodata.json
     * Note: At the moment the "cim" property in the json file is file-system dependent, e.g. "cim": "data/DemoData.rdf", or "cim": "hdfs://sandbox:8020/data/DemoData.rdf".
     */
    def main (args: Array[String])
    {
        do_exit = !args.contains ("--unittest")

        // parser.parse returns Option[C]
        parser.parse (args, SimulationOptions ()) match
        {
            case Some (options) =>

                if (options.verbose) org.apache.log4j.LogManager.getLogger (getClass.getName).setLevel (org.apache.log4j.Level.INFO)

                if (options.simulation.nonEmpty)
                {
                    val begin = System.nanoTime ()

                    // create the configuration
                    val configuration = new SparkConf (false)
                    configuration.setAppName (APPLICATION_NAME)
                    if ("" != options.master)
                        configuration.setMaster (options.master)
                    if (options.options.nonEmpty)
                        options.options.map ((pair: (String, String)) => configuration.set (pair._1, pair._2))
                    if ("" != options.host)
                        configuration.set ("spark.cassandra.connection.host", options.host)

                    // get the necessary jar files to send to the cluster
                    val sim = jarForClass (SimulationOptions ().getClass)
                    val glm = jarForClass (Class.forName ("ch.ninecode.gl.GLMEdge"))
                    val reader = jarForClass (Class.forName ("ch.ninecode.cim.DefaultSource"))
                    val json = jarForClass (Class.forName ("javax.json.JsonStructure"))
                    val json_impl = jarForClass (Class.forName ("org.glassfish.json.JsonProviderImpl"))
                    val datastax = jarForClass (Class.forName ("com.datastax.driver.core.Cluster"))
                    val twitter = jarForClass (Class.forName ("com.twitter.jsr166e.LongAdder"))
                    configuration.setJars (Array (sim, glm, reader, json, json_impl, datastax, twitter))

                    configuration.set ("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
                    // register CIMReader classes
                    configuration.registerKryoClasses (CIMClasses.list)
                    // register GridLAB-D classes
                    configuration.registerKryoClasses (GridLABD.classes)
                    // register Simulation analysis classes
                    configuration.registerKryoClasses (Simulation.classes)
                    configuration.set ("spark.ui.showConsoleProgress", "false")

                    // make a Spark session
                    val session = SparkSession.builder ().config (configuration).getOrCreate ()
                    session.sparkContext.setLogLevel (options.log_level.toString)
                    if ("" != options.checkpoint)
                        session.sparkContext.setCheckpointDir (options.checkpoint)
                    val version = session.version
                    log.info (s"Spark $version session established")
                    if (version.take (SPARK.length) != SPARK.take (version.length))
                        log.warn (s"Spark version ($version) does not match the version ($SPARK) used to build $APPLICATION_NAME")

                    val setup = System.nanoTime ()
                    log.info ("setup: " + (setup - begin) / 1e9 + " seconds")

                    if (options.simulation.nonEmpty)
                        Simulation (session, options).run ()
                    else
                        log.error ("""no simulation JSON files specified""")

                    val calculate = System.nanoTime ()
                    log.info ("execution: " + (calculate - setup) / 1e9 + " seconds")
                }

                if (do_exit)
                    sys.exit (0)

            case None =>
                if (do_exit)
                    sys.exit (1)
        }
    }
}
