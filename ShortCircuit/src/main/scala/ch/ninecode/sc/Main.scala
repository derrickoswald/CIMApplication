package ch.ninecode.sc

import java.io.UnsupportedEncodingException
import java.net.URI
import java.net.URLDecoder
import java.util.Properties

import scala.collection.mutable.HashMap
import scala.tools.nsc.io.Jar
import scala.util.Random
import scopt.OptionParser

import org.apache.spark.SparkConf
import org.apache.spark.graphx.GraphXUtils
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.LoggerFactory

import ch.ninecode.cim.CIMClasses
import ch.ninecode.cim.CIMNetworkTopologyProcessor
import ch.ninecode.cim.CIMTopologyOptions
import ch.ninecode.cim.DefaultSource
import ch.ninecode.cim.ForceTrue
import ch.ninecode.cim.Unforced
import ch.ninecode.gl.Complex
import ch.ninecode.gl.GridLABD
import ch.ninecode.model.Element

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
    val APPLICATION_NAME: String = "ShortCircuit"
    val APPLICATION_VERSION: String = properties.getProperty ("version")
    val SPARK: String = properties.getProperty ("spark")

    object LogLevels extends Enumeration
    {
        type LogLevels = Value
        val ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN = Value
    }

    implicit val LogLevelsRead: scopt.Read[LogLevels.Value] = scopt.Read.reads (LogLevels.withName)

    implicit val mapRead: scopt.Read[Map[String, String]] = scopt.Read.reads (
        s ⇒
        {
            var ret = Map [String, String]()
            val ss = s.split (",")
            for (p ← ss)
            {
                val kv = p.split ("=")
                ret = ret + ((kv (0), kv (1)))
            }
            ret
        }
    )

    implicit val complexRead: scopt.Read[Complex] = scopt.Read.reads (
        s ⇒ Complex.fromString (s)
    )

    case class Arguments
    (
        quiet: Boolean = false,
        master: String = "",
        opts: Map[String, String] = Map (
            "spark.graphx.pregel.checkpointInterval" → "8",
            "spark.serializer" → "org.apache.spark.serializer.KryoSerializer",
            "spark.ui.showConsoleProgress" → "false"
        ),
        storage: String = "MEMORY_AND_DISK_SER",
        splitsize: Long = 67108864L,
        dedup: Boolean = false,
        log_level: LogLevels.Value = LogLevels.OFF,
        checkpoint_dir: String = "",
        description: String = "",
        trafos: String = "",
        default_network_power_max: Double = 200e6,
        default_network_impedance_max: Complex = Complex (0.437785783, -1.202806555),
        default_network_angle_max: Double = Double.NaN,
        default_network_power_min: Double = 100e6,
        default_network_impedance_min: Complex = Complex (0.875571570, -2.405613110),
        default_network_angle_min: Double = Double.NaN,
        default_transformer_power: Double = 630000.0,
        default_transformer_impedance: Complex = Complex (0.005899999998374999, 0.039562482211875),
        base_temperature: Double = 20.0,
        low_temperature: Double = 60.0,
        high_temperature: Double = 90.0,
        cmax: Double = 1.0,
        cmin: Double = 0.90,
        worstcasepf: Boolean = true,
        cosphi: Double = 1.0,
        fuse_table: Int = 1,
        messagemax: Int = 5,
        batchsize: Long = 10000L,
        cable_impedance_limit: Double = 5.0,
        workdir: String = "",
        files: Seq[String] = Seq ())

    val parser: OptionParser[Arguments] = new scopt.OptionParser[Arguments](APPLICATION_NAME)
    {
        head (APPLICATION_NAME, APPLICATION_VERSION)

        val default = new Arguments

        opt [Unit]("quiet").
            action ((_, c) ⇒ c.copy (quiet = true)).
            text ("suppress informational messages [false]")

        opt [String]("master").valueName ("MASTER_URL").
            action ((x, c) ⇒ c.copy (master = x)).
            text ("spark://host:port, mesos://host:port, yarn, or local[*]")

        opt [Map[String, String]]("opts").valueName ("k1=v1,k2=v2").
            action ((x, c) ⇒ c.copy (opts = c.opts ++ x)).
            text ("Spark options [%s]".format (default.opts.map (x ⇒ x._1 + "=" + x._2).mkString (",")))

        opt [String]("storage").
            action ((x, c) ⇒ c.copy (storage = x)).
            text ("storage level for RDD serialization [%s]".format (default.storage))

        opt [Long]("splitsize").
            action ((x, c) ⇒ c.copy (splitsize = x)).
            text ("file input format maximum size [%s]".format (default.splitsize))

        opt [Unit]("deduplicate").
            action ((_, c) ⇒ c.copy (dedup = true)).
            text ("de-duplicate input (striped) files [false]")

        opt [LogLevels.Value]("logging").
            action ((x, c) ⇒ c.copy (log_level = x)).
            text ("log level, one of " + LogLevels.values.iterator.mkString (",") + " [%s]".format (default.log_level))

        opt [String]("checkpoint").valueName ("<dir>").
            action ((x, c) ⇒ c.copy (checkpoint_dir = x)).
            text ("checkpoint directory on HDFS, e.g. hdfs://...")

        opt [String]("description").valueName ("<text>").
            action ((x, c) ⇒ c.copy (description = x)).
            text ("text describing this program execution for SQLite run table")

        opt [Double]("netp_max").valueName ("<Sk_max>").
            action ((x, c) ⇒ c.copy (default_network_power_max = x)).
            text ("maximum network power if not in CIM, VA [%g]".format (default.default_network_power_max))

        opt [Complex]("netz_max").valueName ("<r + xj>").
            action ((x, c) ⇒ c.copy (default_network_impedance_max = x)).
            text ("network impedance at maximum power if not in CIM, Ω [%s]".format (default.default_network_impedance_max))

        opt [Double]("neta_max").valueName ("<angle>").
            action ((x, c) ⇒ c.copy (default_network_angle_max = x)).
            text ("network power factor angle at maximum power if not in CIM, overrides impedance, ° [%s]".format (default.default_network_angle_max))

        opt [Double]("netp_min").valueName ("<Sk_min>").
            action ((x, c) ⇒ c.copy (default_network_power_min = x)).
            text ("minimum network power if not in CIM, VA [%g]".format (default.default_network_power_min))

        opt [Complex]("netz_min").valueName ("<r + xj>").
            action ((x, c) ⇒ c.copy (default_network_impedance_min = x)).
            text ("network impedance at minumum power if not in CIM, Ω [%s]".format (default.default_network_impedance_min))

        opt [Double]("neta_min").valueName ("<angle>").
            action ((x, c) ⇒ c.copy (default_network_angle_min = x)).
            text ("network power factor angle at minimum power if not in CIM, overrides impedance, ° [%s]".format (default.default_network_angle_min))

        opt [Double]("tbase").valueName ("<value>").
            action ((x, c) ⇒ c.copy (base_temperature = x)).
            text ("temperature assumed in CIM file (°C) [%g]".format (default.base_temperature))

        opt [Double]("tlow").valueName ("<value>").
            action ((x, c) ⇒ c.copy (low_temperature = x)).
            text ("low temperature for maximum fault (°C) [%g]".format (default.low_temperature))

        opt [Double]("thigh").valueName ("<value>").
            action ((x, c) ⇒ c.copy (high_temperature = x)).
            text ("high temperature for minimum fault (°C) [%g]".format (default.high_temperature))

        opt [String]("trafos").valueName ("<TRA file>").
            action ((x, c) => c.copy (trafos = x)).
            text ("file of transformer names (one per line) to process")

        opt [Double]("trafop").valueName ("<ratedS>").
            action ((x, c) => c.copy (default_transformer_power = x)).
            text ("transformer power if not in CIM, VA [%g]".format (default.default_transformer_power))

        opt [Complex]("trafoz").valueName ("<r + xj>").
            action ((x, c) => c.copy (default_transformer_impedance = x)).
            text ("transformer impedance if not in CIM, Ω [%s]".format (default.default_transformer_impedance))

        opt [Double]("cmax").
            action ((x, c) => c.copy (cmax = x)).
            text ("voltage factor for maximum fault level, used for rating equipment [%g]".format (default.cmax))

        opt [Double]("cmin").
            action ((x, c) => c.copy (cmin = x)).
            text ("voltage factor for minimum fault level, used for protections settings [%g]".format (default.cmin))

        opt [Double]("cosphi").
            action ((x, c) => c.copy (cosphi = x, worstcasepf = false)).
            text ("load power factor, used for maximum inrush current [worst case]")

        opt [Int]("fuse_table").
            action ((x, c) => c.copy (fuse_table = x)).
            text ("recommended fuse sizing table, #1 from 65A⇒25 to 2400A⇒630, #2 from 28A⇒10 to 2500A⇒630 [%s], #3 DIN as #1, SEV 200A⇒60 to 1150A⇒400".format (default.fuse_table))

        opt [Int]("messagemax").
            action ((x, c) => c.copy (messagemax = x)).
            text ("maximum number of warning and error messages per node [%d]".format (default.messagemax))

        opt [Long]("batchsize").
            action ((x, c) => c.copy (batchsize = x)).
            text ("size of result collections for driver database writes [%d]".format (default.batchsize))

        opt [Double]("cable_impedance_limit").valueName ("<value>").
            action ((x, c) ⇒ c.copy (cable_impedance_limit = x)).
            text ("cables with higher impedances for R1 will not be processed with gridlabd [%g]".format (default.cable_impedance_limit))

        opt [String]("workdir").valueName ("<dir>").
            action ((x, c) ⇒ c.copy (workdir = x)).
            text ("shared directory (HDFS or NFS share) with scheme (hdfs:// or file:/) for work files")

        help ("help").text ("prints this usage text")

        arg [String]("<CIM>,<CIM>...").unbounded ().
            action ((x, c) ⇒ c.copy (files = c.files :+ x)).
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
            case e: UnsupportedEncodingException ⇒ e.printStackTrace ()
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
     * Build jar with dependencies (target/ShortCircuit-2.11-2.4.4-2.7.0-jar-with-dependencies.jar):
     * mvn package
     * Assuming the data files and csv files exist on hdfs in the data directory,
     * invoke (on the cluster) with:
     * spark-submit --master spark://sandbox:7077 --conf spark.driver.memory=2g --conf spark.executor.memory=4g /opt/code/ShortCircuit-2.11-2.4.4-2.7.0-jar-with-dependencies.jar --csv "hdfs://sandbox:8020/data/KS_Leistungen.csv" --logging "INFO" "hdfs://sandbox:8020/data/bkw_cim_export_schopfen_all.rdf"
     */

    def read_cim (session: SparkSession, arguments: Arguments): RDD[Element] =
    {
        val log = LoggerFactory.getLogger (getClass)
        val start = System.nanoTime ()
        val storage = StorageLevel.fromString (arguments.storage)
        val reader_options = new HashMap[String, String]()
        reader_options.put ("StorageLevel", arguments.storage)
        reader_options.put ("ch.ninecode.cim.do_deduplication", arguments.dedup.toString)
        reader_options.put ("path", arguments.files.mkString (","))
        reader_options.put ("ch.ninecode.cim.make_edges", "false")
        reader_options.put ("ch.ninecode.cim.do_join", "false")
        reader_options.put ("ch.ninecode.cim.do_topo", "false") // use the topological processor after reading
        reader_options.put ("ch.ninecode.cim.do_topo_islands", "false")
        reader_options.put ("ch.ninecode.cim.split_maxsize", arguments.splitsize.toString)
        val elements = session.read.format ("ch.ninecode.cim").options (reader_options).load (arguments.files: _*).persist (storage)
        log.info (elements.count () + " elements")

        val read = System.nanoTime ()
        log.info ("read: " + (read - start) / 1e9 + " seconds")

        // identify topological nodes if necessary
        val tns = session.sparkContext.getPersistentRDDs.filter (_._2.name == "TopologicalNode")
        val ele = if (tns.isEmpty || tns.head._2.isEmpty)
        {
            val ntp = CIMNetworkTopologyProcessor (session)
            val elements = ntp.process (
                CIMTopologyOptions (
                    identify_islands = true,
                    force_retain_switches = Unforced,
                    force_retain_fuses = ForceTrue,
                    default_switch_open_state = false,
                    debug = false,
                    storage = storage)
            )
            log.info (elements.count () + " elements")
            val topo = System.nanoTime ()
            log.info ("topology: " + (topo - read) / 1e9 + " seconds")
            elements
        }
        else
        {
            val elements = session.sparkContext.getPersistentRDDs.filter (_._2.name == "Elements").head._2.asInstanceOf [RDD[Element]]
            log.info (elements.count () + " elements")
            elements
        }
        ele.name = "Elements"
        ele.persist (storage)
        ele
    }

    def main (args: Array[String])
    {
        // parser.parse returns Option[C]
        parser.parse (args, Arguments ()) match
        {
            case Some (arguments) ⇒

                if (!arguments.quiet)
                {
                    org.apache.log4j.LogManager.getLogger ("ch.ninecode.sc.Main$").setLevel (org.apache.log4j.Level.INFO)
                    org.apache.log4j.LogManager.getLogger ("ch.ninecode.sc.ShortCircuit").setLevel (org.apache.log4j.Level.INFO)
                    org.apache.log4j.LogManager.getLogger ("ch.ninecode.sc.Database").setLevel (org.apache.log4j.Level.INFO)
                }
                val log = LoggerFactory.getLogger (getClass)
                val begin = System.nanoTime ()

                // create the configuration
                val configuration = new SparkConf (false)
                configuration.setAppName (APPLICATION_NAME)
                if ("" != arguments.master)
                    configuration.setMaster (arguments.master)
                if (arguments.opts.nonEmpty)
                    arguments.opts.map ((pair: (String, String)) ⇒ configuration.set (pair._1, pair._2))

                // get the necessary jar files to send to the cluster
                if ("" != arguments.master)
                {
                    val s1 = jarForObject (new DefaultSource ())
                    val s2 = jarForObject (ShortCircuitOptions ())
                    if (s1 != s2)
                        configuration.setJars (Array (s1, s2))
                    else
                        configuration.setJars (Array (s1))
                }

                val storage = StorageLevel.fromString (arguments.storage)
                // register CIMReader classes
                configuration.registerKryoClasses (CIMClasses.list)
                // register GridLAB-D classes
                configuration.registerKryoClasses (GridLABD.classes)
                // register ShortCircuit analysis classes
                configuration.registerKryoClasses (ShortCircuit.classes)
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
                log.info ("setup: " + (setup - begin) / 1e9 + " seconds")

                read_cim (session, arguments)

                val workdir = if ("" == arguments.workdir) derive_work_dir (arguments.files) else arguments.workdir
                val options = ShortCircuitOptions (
                    verbose = !arguments.quiet,
                    description = arguments.description,
                    default_short_circuit_power_max = arguments.default_network_power_max,
                    default_short_circuit_impedance_max = arguments.default_network_impedance_max,
                    default_short_circuit_angle_max = arguments.default_network_angle_max,
                    default_short_circuit_power_min = arguments.default_network_power_min,
                    default_short_circuit_impedance_min = arguments.default_network_impedance_min,
                    default_short_circuit_angle_min = arguments.default_network_angle_min,
                    default_transformer_power_rating = arguments.default_transformer_power,
                    default_transformer_impedance = arguments.default_transformer_impedance,
                    base_temperature = arguments.base_temperature,
                    low_temperature = arguments.low_temperature,
                    high_temperature = arguments.high_temperature,
                    cmax = arguments.cmax,
                    cmin = arguments.cmin,
                    worstcasepf = arguments.worstcasepf,
                    cosphi = arguments.cosphi,
                    fuse_table = arguments.fuse_table,
                    messagemax = arguments.messagemax,
                    batchsize = arguments.batchsize,
                    trafos = arguments.trafos,
                    cable_impedance_limit = arguments.cable_impedance_limit,
                    workdir = workdir
                )
                val shortcircuit = ShortCircuit (session, storage, options)
                val results = shortcircuit.run ()

                // output SQLite database
                Database.store (options)(results)

                val calculate = System.nanoTime ()
                log.info ("total: " + (calculate - begin) / 1e9 + " seconds, " + results.count + " node results calculated")

                sys.exit (0)
            case None ⇒
                sys.exit (1)
        }
    }
}
