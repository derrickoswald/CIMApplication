package ch.ninecode.lv

import java.io.UnsupportedEncodingException
import java.net.URI
import java.net.URLDecoder
import java.util.Properties

import scala.collection.mutable.HashMap
import scala.tools.nsc.io.Jar
import scala.util.Random
import scopt.OptionParser

import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.LoggerFactory

import ch.ninecode.cim.CIMClasses
import ch.ninecode.cim.DefaultSource
import ch.ninecode.mfi.Einspeiseleistung
import ch.ninecode.gl.GridLABD

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
    val APPLICATION_NAME: String = "Low Voltage"
    val APPLICATION_VERSION: String = properties.getProperty ("version")
    val SPARK: String = properties.getProperty ("spark")

    object LogLevels extends Enumeration
    {
        type LogLevels = Value
        val ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN = Value
    }

    implicit val LogLevelsRead: scopt.Read[LogLevels.Value] = scopt.Read.reads (LogLevels.withName)

    implicit val mapRead: scopt.Read[Map[String, String]] = scopt.Read.reads (
        (s) =>
        {
            var ret = Map [String, String]()
            val ss = s.split (",")
            for (p <- ss)
            {
                val kv = p.split ("=")
                ret = ret + ((kv (0), kv (1)))
            }
            ret
        }
    )

    case class Arguments (
                             quiet: Boolean = false,
                             master: String = "",
                             opts: Map[String, String] = Map (),
                             storage: String = "MEMORY_AND_DISK_SER",
                             dedup: Boolean = false,
                             three: Boolean = false,
                             trafos: String = "",
                             log_level: LogLevels.Value = LogLevels.OFF,
                             checkpoint_dir: String = "",
                             workdir: String = "",
                             files: Seq[String] = Seq ()
                         )

    val parser: OptionParser[Arguments] = new scopt.OptionParser[Arguments](APPLICATION_NAME)
    {
        head (APPLICATION_NAME, APPLICATION_VERSION)

        opt [Unit]('q', "quiet").
            action ((_, c) => c.copy (quiet = true)).
            text ("suppress informational messages")

        opt [String]('m', "master").valueName ("MASTER_URL").
            action ((x, c) => c.copy (master = x)).
            text ("spark://host:port, mesos://host:port, yarn, or local[*]")

        opt [Map[String, String]]('o', "opts").valueName ("k1=v1,k2=v2").
            action ((x, c) => c.copy (opts = x)).
            text ("Spark options")

        opt [String]('s', "storage_level").
            action ((x, c) => c.copy (storage = x)).
            text ("storage level for RDD serialization (default: MEMORY_AND_DISK_SER)")

        opt [Unit]('u', "deduplicate").
            action ((_, c) => c.copy (dedup = true)).
            text ("de-duplicate input (striped) files")

        opt [Unit]('3', "three").
            action ((_, c) => c.copy (three = true)).
            text ("use three phase computations")

        opt [String]('t', "trafos").valueName ("<TRA file>").
            action ((x, c) => c.copy (trafos = x)).
            text ("file of transformer names (one per line) to process")

        opt [LogLevels.Value]('l', "logging").
            action ((x, c) => c.copy (log_level = x)).
            text ("log level, one of " + LogLevels.values.iterator.mkString (","))

        opt [String]('k', "checkpointdir").valueName ("<dir>").
            action ((x, c) => c.copy (checkpoint_dir = x)).
            text ("checkpoint directory on HDFS, e.g. hdfs://...")

        opt [String]('w', "workdir").valueName ("<dir>").
            action ((x, c) => c.copy (workdir = x)).
            text ("shared directory (HDFS or NFS share) with scheme (hdfs:// or file:/) for work files")

        help ("help").text ("prints this usage text")

        arg [String]("<CIM> <CIM> ...").unbounded ().
            action ((x, c) => c.copy (files = c.files :+ x)).
            text ("CIM rdf files to process")

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
    def derive_work_dir (files: Seq[String]): String =
    {
        val file = files.head.split (",")(0).replace (" ", "%20")
        val uri = new URI (file)
        if (null == uri.getScheme)
            "/simulation/"
        else
            uri.getScheme + "://" + (if (null == uri.getAuthority) "" else uri.getAuthority) + "/simulation/"
    }

    /**
     * Build jar with dependencies (target/LowVoltage-2.11-2.3.1-2.4.0-jar-with-dependencies.jar):
     * mvn package
     * Assuming the data files and csv files exist on hdfs in the data directory,
     * invoke (on the cluster) with:
     * spark-submit --master spark://sandbox:7077 --conf spark.driver.memory=2g --conf spark.executor.memory=4g /opt/code/LowVoltage-2.11-2.3.1-2.4.0-jar-with-dependencies.jar --three --trafos Export_Trafos.txt "hdfs://sandbox:8020/data/bkw_cim_export_equipmentsstripe5.rdf"
     */
    def main (args: Array[String])
    {
        // parser.parse returns Option[C]
        parser.parse (args, Arguments ()) match
        {
            case Some (arguments) =>

                if (!arguments.quiet) org.apache.log4j.LogManager.getLogger ("ch.ninecode.lv.Main$").setLevel (org.apache.log4j.Level.INFO)
                val log = LoggerFactory.getLogger (getClass)
                val begin = System.nanoTime ()

                // create the configuration
                val configuration = new SparkConf (false)
                configuration.setAppName (APPLICATION_NAME)
                if ("" != arguments.master)
                    configuration.setMaster (arguments.master)
                if (arguments.opts.nonEmpty)
                    arguments.opts.map ((pair: (String, String)) => configuration.set (pair._1, pair._2))

                // get the necessary jar files to send to the cluster
                if ("" != arguments.master)
                {
                    val s1 = jarForObject (new DefaultSource ())
                    val s2 = jarForObject (LowVoltageOptions ())
                    if (s1 != s2)
                        configuration.setJars (Array (s1, s2))
                    else
                        configuration.setJars (Array (s1))
                }

                val storage = StorageLevel.fromString (arguments.storage)
                if (storage.useDisk)
                {
                    // register CIMReader classes
                    configuration.registerKryoClasses (CIMClasses.list)
                    // register GridLAB-D classes
                    configuration.registerKryoClasses (GridLABD.classes)
                    // register Einspeiseleistung classes
                    configuration.registerKryoClasses (Einspeiseleistung.classes)
                }
                configuration.set ("spark.ui.showConsoleProgress", "false")

                // make a Spark session
                val session = SparkSession.builder ().config (configuration).getOrCreate ()
                session.sparkContext.setLogLevel (arguments.log_level.toString)
                if ("" != arguments.checkpoint_dir)
                    session.sparkContext.setCheckpointDir (arguments.checkpoint_dir)
                val version = session.version
                log.info (s"Spark $version session established")
                if (session.version != SPARK)
                    log.warn (s"Spark version ($version) does not match the version ($SPARK) used to build $APPLICATION_NAME")
                val setup = System.nanoTime ()
                log.info ("setup: " + (setup - begin) / 1e9 + " seconds")

                val options = LowVoltageOptions (
                    verbose = !arguments.quiet,
                    cim_reader_options = HashMap [String, String]("StorageLevel" → arguments.storage, "ch.ninecode.cim.do_deduplication" → arguments.dedup.toString),
                    three = arguments.three,
                    trafos = arguments.trafos,
                    workdir = if ("" == arguments.workdir) derive_work_dir (arguments.files) else arguments.workdir,
                    files = arguments.files
                )
                val lv = LowVoltage (session, storage, options)
                val count = lv.run ()

                val calculate = System.nanoTime ()
                log.info ("total: " + (calculate - begin) / 1e9 + " seconds " + count + " trafokreise")

                sys.exit (0)
            case None =>
                sys.exit (1)
        }
    }
}
