package ch.ninecode.on

import java.io.UnsupportedEncodingException
import java.net.URI
import java.net.URLDecoder
import java.util.Properties

import ch.ninecode.cim.CIMClasses
import ch.ninecode.cim.DefaultSource
import ch.ninecode.gl.GridLABD
import org.apache.spark.SparkConf
import org.apache.spark.graphx.GraphXUtils
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.LoggerFactory
import scopt.OptionParser

import scala.collection.mutable
import scala.tools.nsc.io.Jar
import scala.util.Random

object Main
{
    lazy val properties: Properties =
    {
        val in = this.getClass.getResourceAsStream ("/application.properties")
        val p = new Properties ()
        p.load (in)
        in.close ()
        p
    }
    val APPLICATION_NAME: String = "OneOfN"
    val APPLICATION_VERSION: String = properties.getProperty ("version")
    val SPARK: String = properties.getProperty ("spark")

    object LogLevels extends Enumeration
    {
        type LogLevels = Value
        val ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN = Value
    }

    implicit val LogLevelsRead: scopt.Read[LogLevels.Value] = scopt.Read.reads (LogLevels.withName)

    implicit val mapRead: scopt.Read[Map[String, String]] = scopt.Read.reads (
        s =>
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

    case class Arguments
    (
        /**
         * If <code>true</code>, don't call sys.exit().
         */
        unittest: Boolean = false,

        quiet: Boolean = false,
        master: String = "",
        opts: Map[String, String] = Map (
            "spark.graphx.pregel.checkpointInterval" → "8",
            "spark.serializer" → "org.apache.spark.serializer.KryoSerializer",
            "spark.ui.showConsoleProgress" → "false"
        ),
        storage: String = "MEMORY_AND_DISK_SER",
        dedup: Boolean = false,
        three: Boolean = false,
        base_temperature: Double = 20.0,
        temperature: Double = 20.0,
        log_level: LogLevels.Value = LogLevels.OFF,
        checkpoint_dir: String = "",
        workdir: String = "",
        files: Seq[String] = Seq ()
    )

    var do_exit = true

    val parser: OptionParser[Arguments] = new scopt.OptionParser[Arguments](APPLICATION_NAME)
    {
        head (APPLICATION_NAME, APPLICATION_VERSION)

        note ("Creates GridLAB-D .glm models for all medium voltage (N5 network) feeder service areas for one-of-N analysis.\n")

        help ("help").text ("prints this usage text")

        version ("version").text ("Scala: %s, Spark: %s, %s: %s".format (
            APPLICATION_VERSION.split ("-")(0),
            APPLICATION_VERSION.split ("-")(1),
            APPLICATION_NAME,
            APPLICATION_VERSION.split ("-")(2)
        ))

        val default = new Arguments

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

        opt [Unit]("quiet").
            action ((_, c) => c.copy (quiet = true)).
            text ("suppress informational messages [%s]".format (default.quiet))

        opt [String]("master").valueName ("MASTER_URL").
            action ((x, c) => c.copy (master = x)).
            text ("local[*], spark://host:port, mesos://host:port, yarn [%s]".format (default.master))

        opt [Map[String, String]]("opts").valueName ("k1=v1,k2=v2").
            action ((x, c) => c.copy (opts = c.opts ++ x)).
            text ("Spark options [%s]".format (default.opts.map (x ⇒ x._1 + "=" + x._2).mkString (",")))

        opt [String]("storage_level").
            action ((x, c) => c.copy (storage = x)).
            text ("storage level for RDD serialization [%s]".format (default.storage))

        opt [Unit]("deduplicate").
            action ((_, c) => c.copy (dedup = true)).
            text ("de-duplicate input (striped) files [%s]".format (default.dedup))

        opt [Unit]("three").
            action ((_, c) => c.copy (three = true)).
            text ("use three phase computations [%s]".format (default.three))

        opt [Double]("tbase").valueName ("<value>").
            action ((x, c) ⇒ c.copy (base_temperature = x)).
            text ("temperature assumed in CIM file (°C) [%g]".format (default.base_temperature))

        opt [Double]("temp").valueName ("<value>").
            action ((x, c) ⇒ c.copy (temperature = x)).
            text ("low temperature for maximum fault (°C) [%g]".format (default.temperature))

        opt [LogLevels.Value]("logging").
            action ((x, c) => c.copy (log_level = x)).
            text ("log level, one of %s [%s]".format (LogLevels.values.iterator.mkString (","), default.log_level))

        opt [String]("checkpoint").valueName ("<dir>").
            action ((x, c) => c.copy (checkpoint_dir = x)).
            text ("checkpoint directory on HDFS, e.g. hdfs://... [%s]".format (default.checkpoint_dir))

        opt [String]("workdir").valueName ("<dir>").
            action ((x, c) => c.copy (workdir = x)).
            text ("shared directory (HDFS or NFS share) with scheme (hdfs:// or file:/) for work files [%s]".format (default.workdir))

        arg [String]("<CIM> <CIM> ...").optional ().unbounded ().
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
     * Build jar with dependencies (creates target/program_name_and_version-jar-with-dependencies.jar):
     * mvn package
     * Invoke (on the cluster) with:
     * spark-submit --master spark://sandbox:7077 --conf spark.driver.memory=2g --conf spark.executor.memory=2g /opt/code/program_name_and_version-jar-with-dependencies.jar "hdfs://sandbox:8020/data/rdffilename.rdf"
     * or on AWS:
     * /opt/spark/bin/spark-submit --master yarn /disktemp/transfer/program_name_and_version-jar-with-dependencies.jar hdfs://hmaster:9000/data/NIS_CIM_Export_sias_current_20161220_Sample4.rdf
     */
    def main (args: Array[String])
    {
        do_exit = !args.contains ("--unittest")

        // parser.parse returns Option[C]
        parser.parse (args, Arguments ()) match
        {
            case Some (arguments) =>

                if (!arguments.quiet) org.apache.log4j.LogManager.getLogger ("ch.ninecode.on.Main$").setLevel (org.apache.log4j.Level.INFO)
                val log = LoggerFactory.getLogger (getClass)
                val begin = System.nanoTime ()

                if (arguments.files.nonEmpty)
                {
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
                        val s2 = jarForObject (OneOfNOptions ())
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
                        // register OneOfN classes
                        configuration.registerKryoClasses (OneOfN.classes)
                        // register GraphX classes
                        GraphXUtils.registerKryoClasses (configuration)
                    }

                    // make a Spark session
                    val session = SparkSession.builder ().config (configuration).getOrCreate ()
                    session.sparkContext.setLogLevel (arguments.log_level.toString)
                    if ("" != arguments.checkpoint_dir)
                        session.sparkContext.setCheckpointDir (arguments.checkpoint_dir)
                    val version = session.version
                    log.info (s"Spark $version session established")
                    if (version.take (SPARK.length) != SPARK.take (version.length))
                        log.warn (s"Spark version ($version) does not match the version ($SPARK) used to build $APPLICATION_NAME")
                    val setup = System.nanoTime ()
                    log.info ("setup: " + (setup - begin) / 1e9 + " seconds")

                    val options = OneOfNOptions (
                        verbose = !arguments.quiet,
                        cim_reader_options = mutable.HashMap [String, String]("StorageLevel" → arguments.storage, "ch.ninecode.cim.do_deduplication" → arguments.dedup.toString),
                        three = arguments.three,
                        base_temperature = arguments.base_temperature,
                        temperature = arguments.temperature,
                        storage = storage,
                        workdir = if ("" == arguments.workdir) derive_work_dir (arguments.files) else arguments.workdir,
                        files = arguments.files
                    )
                    val on = OneOfN (session, options)
                    val count = on.run ()
                    log.info ("%s models processed".format (count))
                }

                val calculate = System.nanoTime ()
                log.info ("total: " + (calculate - begin) / 1e9 + " seconds")

                if (do_exit)
                    sys.exit (0)
            case None =>
                if (do_exit)
                    sys.exit (1)
        }
    }
}
