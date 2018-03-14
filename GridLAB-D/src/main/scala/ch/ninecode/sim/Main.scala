package ch.ninecode.sim

import java.io.UnsupportedEncodingException
import java.net.URI
import java.net.URLDecoder
import java.util.Properties

import ch.ninecode.cim.CIMClasses
import ch.ninecode.cim.DefaultSource
import org.apache.spark.SparkConf
import org.apache.spark.graphx.GraphXUtils
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.LoggerFactory
import scopt.OptionParser

import scala.collection.mutable.HashMap
import scala.tools.nsc.io.Jar
import scala.util.Random

object Main
{
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

    implicit val mapRead: scopt.Read[Map[String,String]] = scopt.Read.reads (
        (s) =>
        {
            var ret = Map[String, String] ()
            val ss = s.split (",")
            for (p <- ss)
            {
                val kv = p.split ("=")
                ret = ret + ((kv(0), kv(1)))
            }
            ret
        }
    )

    val parser: OptionParser[SimulationOptions] = new scopt.OptionParser[SimulationOptions](APPLICATION_NAME)
    {
        head (APPLICATION_NAME, APPLICATION_VERSION)

        val default = new SimulationOptions

        opt[Unit]("verbose").
            action ((_, c) ⇒ c.copy (verbose = false)).
            text ("emit progress messages [%s]".format (default.verbose))

        opt[String]("master").valueName ("MASTER_URL").
            action ((x, c) ⇒ c.copy (master = x)).
            text ("local[*], spark://host:port, mesos://host:port or yarn [%s]".format (default.master))

        opt[String]("storage").
            action ((x, c) ⇒ c.copy (storage = x)).
            text ("storage level for RDD serialization [%s]".format (default.storage))

        opt[LogLevels.Value]("logging").
            action ((x, c) ⇒ c.copy (log_level = x)).
            text ("log level, one of " + LogLevels.values.iterator.mkString (",") + " [%s]".format (default.log_level))

        opt[String]("checkpoint").valueName ("<dir>").
            action ((x, c) ⇒ c.copy (checkpoint = x)).
            text ("checkpoint directory on HDFS, e.g. hdfs://... [\"%s\"]".format (default.checkpoint))

        opt[String]("workdir").valueName ("<dir>").
            action ((x, c) ⇒ c.copy (workdir = x)).
            text ("shared directory (HDFS or NFS share) with scheme (hdfs:// or file:/) for work files [\"%s\"]".format (default.workdir))

        arg[String]("<JSON> <JSON>...").unbounded ().
            action ((x, c) ⇒ c.copy (simulation = c.simulation :+ x)).
            text ("simulation files to process")

        help ("help").text ("prints this usage text")
    }

    def jarForObject (obj: Object): String =
    {
        // see https://stackoverflow.com/questions/320542/how-to-get-the-path-of-a-running-jar-file
        var ret = obj.getClass.getProtectionDomain.getCodeSource.getLocation.getPath
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
     * Generate a working directory matching the files.
     */
//    def derive_work_dir (files: Seq[String]): String =
//    {
//        val file = files.head.split (",")(0).replace (" ", "%20")
//        val uri = new URI (file)
//        if (null == uri.getScheme)
//            "/simulation/"
//        else
//            uri.getScheme + "://" + (if (null == uri.getAuthority) "" else uri.getAuthority) + "/simulation/"
//    }

    /**
     * Build jar with dependencies (creates target/program_name_and_version-jar-with-dependencies.jar):
     *     mvn package
     * Invoke (on the cluster) with:
     *     spark-submit --master spark://sandbox:7077 --conf spark.driver.memory=2g --conf spark.executor.memory=4g /opt/code/program_name_and_version-jar-with-dependencies.jar "hdfs://sandbox:8020/data/filename.rdf"
     * or on AWS:
     *     /opt/spark/bin/spark-submit --master yarn /disktemp/transfer/program_name_and_version-jar-with-dependencies.jar hdfs://hmaster:9000/data/filename.rdf
     */
    def main (args: Array[String])
    {
        // parser.parse returns Option[C]
        parser.parse (args, SimulationOptions ()) match
        {
            case Some (options) =>

                if (options.verbose) org.apache.log4j.LogManager.getLogger ("ch.ninecode.sim.Main$").setLevel (org.apache.log4j.Level.INFO)
                val log = LoggerFactory.getLogger (getClass)
                val begin = System.nanoTime ()

                // create the configuration
                val configuration = new SparkConf (false)
                configuration.setAppName (APPLICATION_NAME)
                if ("" != options.master)
                    configuration.setMaster (options.master)
//                if (options.opts.nonEmpty)
//                    options.opts.map ((pair: (String, String)) => configuration.set (pair._1, pair._2))

                // get the necessary jar files to send to the cluster
//                if ("" != options.master)
//                {
//                    val s1 = jarForObject (new DefaultSource ())
//                    val s2 = jarForObject (Simulation ())
//                    if (s1 != s2)
//                        configuration.setJars (Array (s1, s2))
//                    else
//                        configuration.setJars (Array (s1))
//                }

                val storage = StorageLevel.fromString (options.storage)
                configuration.set ("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
                // register CIMReader classes
                configuration.registerKryoClasses (CIMClasses.list)
                // register Simulation analysis classes
//                configuration.registerKryoClasses (Simulation.classes)
                // register GraphX classes
                GraphXUtils.registerKryoClasses (configuration)
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
//                val sim = Simulation (session, options)
//                sim.run ()

                val calculate = System.nanoTime ()
                log.info ("execution: " + (calculate - begin) / 1e9 + " seconds")

                sys.exit (0)
            case None =>
                sys.exit (1)
        }
    }
}
