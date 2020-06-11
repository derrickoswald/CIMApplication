package ch.ninecode.np

import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.util.Properties

import ch.ninecode.cim.CIMClasses
import ch.ninecode.cim.CIMExport
import ch.ninecode.cim.CIMNetworkTopologyProcessor
import ch.ninecode.cim.CIMTopologyOptions
import ch.ninecode.cim.DefaultSource
import ch.ninecode.cim.ForceTrue
import ch.ninecode.cim.Unforced
import ch.ninecode.model.Element
import org.apache.spark.SparkConf
import org.apache.spark.graphx.GraphXUtils
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.LoggerFactory
import scopt.OptionParser

import scala.collection.mutable
import scala.tools.nsc.io.Jar
import scala.util.Random

@SuppressWarnings (Array ("org.wartremover.warts.NonUnitStatements"))
object MainCustomer3
{
    val properties: Properties =
    {
        val in = this.getClass.getResourceAsStream ("/application.properties")
        val p = new Properties ()
        p.load (in)
        in.close ()
        p
    }
    val APPLICATION_NAME: String = "Customer3_NetworkParameters"
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
            var ret = Map[String, String]()
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
            "spark.graphx.pregel.checkpointInterval" -> "8",
            "spark.serializer" -> "org.apache.spark.serializer.KryoSerializer",
            "spark.ui.showConsoleProgress" -> "false"
        ),
        storage: String = "MEMORY_AND_DISK_SER",
        dedup: Boolean = false,
        log_level: LogLevels.Value = LogLevels.OFF,
        checkpoint_dir: String = "",
        csv_file: String = "20181002_Transformatoren.csv",
        export: String = "",
        files: Seq[String] = Seq ())

    var do_exit = true

    val parser: OptionParser[Arguments] = new scopt.OptionParser[Arguments](APPLICATION_NAME)
    {
        head (APPLICATION_NAME, APPLICATION_VERSION)

        note ("Creates EquivalentInjection objects for the network supplying transformers from an external data file.\n")

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

        opt[Unit]("unittest").
            hidden ().
            action ((_, c) => c.copy (unittest = true)).
            text ("unit testing - don't call sys.exit() [%s]".format (default.unittest))

        opt[Unit]("quiet").
            action ((_, c) => c.copy (quiet = true)).
            text ("suppress informational messages [%s]".format (default.quiet))

        opt[String]("master").valueName ("MASTER_URL").
            action ((x, c) => c.copy (master = x)).
            text ("local[*], spark://host:port, mesos://host:port, yarn [%s]".format (default.master))

        opt[Map[String, String]]("opts").valueName ("k1=v1,k2=v2").
            action ((x, c) => c.copy (opts = c.opts ++ x)).
            text ("Spark options [%s]".format (default.opts.map (x => x._1 + "=" + x._2).mkString (",")))

        opt[String]("storage").
            action ((x, c) => c.copy (storage = x)).
            text ("storage level for RDD serialization [%s]".format (default.storage))

        opt[Unit]("deduplicate").
            action ((_, c) => c.copy (dedup = true)).
            text ("de-duplicate input (striped) files [%s]".format (default.dedup))

        opt[LogLevels.Value]("logging").
            action ((x, c) => c.copy (log_level = x)).
            text ("log level, one of %s [%s]".format (LogLevels.values.iterator.mkString (","), default.log_level))

        opt[String]("checkpoint").valueName ("<dir>").
            action ((x, c) => c.copy (checkpoint_dir = x)).
            text ("checkpoint directory on HDFS, e.g. hdfs://server:8020/... [%s]".format (default.checkpoint_dir))

        opt[String]("csv").valueName ("<file>").
            action ((x, c) => c.copy (csv_file = x)).
            text ("csv file of available power at transformer data [%s]".format (default.csv_file))

        opt[String]("export").valueName ("<CIM>").
            action ((x, c) => c.copy (export = x)).
            text ("name of deduped + topologically processed CIM file [%s]".format (default.export))

        arg[String]("<CIM>,<CIM>...").optional ().unbounded ().
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
            val name = s"/tmp/${Random.nextInt (99999999)}.jar"
            val writer = new Jar (new scala.reflect.io.File (new java.io.File (name))).jarWriter ()
            writer.addDirectory (new scala.reflect.io.Directory (new java.io.File (ret + "ch/")), "ch/")
            writer.close ()
            ret = name
        }

        ret
    }

    /**
     * Build jar with dependencies (target/Customer<#>_NetworkParameters-<verson>-jar-with-dependencies.jar):
     * mvn package
     * Assuming the data files and csv files exist on hdfs in the data directory,
     * invoke (on the cluster) with:
     * spark-submit --master spark://sandbox:7077 --conf spark.driver.memory=2g --conf spark.executor.memory=4g /opt/code/Customer<#>_NetworkParameters-<verson>-jar-with-dependencies.jar --csv "hdfs://sandbox:8020/data/KS_Leistungen.csv" --logging "INFO" "hdfs://sandbox:8020/data/bkw_cim_export_schopfen_all.rdf"
     */

    def read_cim (session: SparkSession, arguments: Arguments): RDD[Element] =
    {
        val log = LoggerFactory.getLogger (getClass)
        val start = System.nanoTime ()
        val storage = StorageLevel.fromString (arguments.storage)
        val reader_options = new mutable.HashMap[String, String]()
        reader_options.put ("StorageLevel", arguments.storage)
        reader_options.put ("ch.ninecode.cim.do_deduplication", arguments.dedup.toString)
        reader_options.put ("path", arguments.files.mkString (","))
        reader_options.put ("ch.ninecode.cim.make_edges", "false")
        reader_options.put ("ch.ninecode.cim.do_join", "false")
        reader_options.put ("ch.ninecode.cim.do_topo", "false") // use the topological processor after reading
        reader_options.put ("ch.ninecode.cim.do_topo_islands", "false")
        reader_options.put ("StorageLevel", arguments.storage)
        val elements = session.read.format ("ch.ninecode.cim").options (reader_options).load (arguments.files: _*).persist (storage)
        log.info (s"${elements.count ()} elements")

        val read = System.nanoTime ()
        log.info (s"read: ${(read - start) / 1e9} seconds")

        // identify topological nodes
        val ntp = CIMNetworkTopologyProcessor (session)
        val ele = ntp.process (
            CIMTopologyOptions (
                identify_islands = true,
                force_retain_switches = Unforced,
                force_retain_fuses = ForceTrue,
                debug = true,
                storage = storage))
        val topo = System.nanoTime ()
        log.info (s"topology: ${(topo - read) / 1e9} seconds")
        ele.name = "Elements"
        ele.persist (storage)
        ele
    }

    def main (args: Array[String])
    {
        do_exit = !args.contains ("--unittest")

        // parser.parse returns Option[C]
        parser.parse (args, Arguments ()) match
        {
            case Some (arguments) =>

                if (!arguments.quiet)
                {
                    org.apache.log4j.LogManager.getLogger ("ch.ninecode.np.MainCustomer3$").setLevel (org.apache.log4j.Level.INFO)
                    org.apache.log4j.LogManager.getLogger ("ch.ninecode.np.ShortCircuitInfo3").setLevel (org.apache.log4j.Level.INFO)
                }
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
                        configuration.setJars (Array (s1))
                    }

                    val storage = StorageLevel.fromString (arguments.storage)
                    // register CIMReader classes
                    configuration.registerKryoClasses (CIMClasses.list)
                    // register GraphX classes
                    GraphXUtils.registerKryoClasses (configuration)

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
                    val export = new CIMExport (session)
                    log.info (s"setup: ${(setup - begin) / 1e9} seconds")

                    val elements = read_cim (session, arguments)
                    val initialization = System.nanoTime ()
                    log.info (s"initialization: ${(initialization - setup) / 1e9} seconds")

                    if (arguments.export != "")
                    {
                        export.export (elements, arguments.export)
                        val save = System.nanoTime ()
                        log.info (s"export: ${(save - initialization) / 1e9} seconds")
                    }

                    // if a csv file was supplied, create EquivalentInjections and merge them into the superclass RDDs
                    if ("" != arguments.csv_file)
                    {
                        val infos = ShortCircuitInfo3 (session, storage)
                        val equivalents = infos.getShortCircuitInfo (arguments.csv_file)
                        export.export (equivalents, arguments.csv_file.replace (".csv", ".rdf"))
                        infos.merge (equivalents)
                    }
                    else
                        log.error ("""--csv not specified""")
                }

                val calculate = System.nanoTime ()
                log.info (s"total: ${(calculate - begin) / 1e9} seconds")

                if (do_exit)
                    sys.exit (0)
            case None =>
                if (do_exit)
                    sys.exit (1)
        }
    }
}
