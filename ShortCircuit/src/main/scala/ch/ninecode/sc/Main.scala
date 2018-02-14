package ch.ninecode.sc

import java.io.UnsupportedEncodingException
import java.net.URI
import java.net.URLDecoder
import java.util.Properties

import scala.collection.mutable.HashMap
import scala.tools.nsc.io.Jar
import scala.util.Random
import org.apache.spark.SparkConf
import org.apache.spark.graphx.GraphXUtils
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.LoggerFactory
import scopt.OptionParser
import ch.ninecode.cim.CIMClasses
import ch.ninecode.cim.CIMExport
import ch.ninecode.cim.CIMNetworkTopologyProcessor
import ch.ninecode.cim.DefaultSource
import ch.ninecode.model.Element
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path

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

    object LogLevels extends Enumeration {
        type LogLevels = Value
        val ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN = Value
    }
    implicit val LogLevelsRead: scopt.Read[LogLevels.Value] = scopt.Read.reads (LogLevels.withName)

    implicit val mapRead: scopt.Read[Map[String, String]] = scopt.Read.reads (
        (s) ⇒
            {
                var ret = Map[String, String] ()
                val ss = s.split (",")
                for (p ← ss) {
                    val kv = p.split ("=")
                    ret = ret + ((kv(0), kv(1)))
                }
                ret
            }
    )

    implicit val complexRead: scopt.Read[Complex] = scopt.Read.reads (
        (s) ⇒ Complex.fromString (s)
    )

    case class Arguments (
        quiet: Boolean = false,
        master: String = "",
        opts: Map[String, String] = Map (),
        storage: String = "MEMORY_AND_DISK_SER",
        dedup: Boolean = false,
        log_level: LogLevels.Value = LogLevels.OFF,
        checkpoint_dir: String = "",
        csv_file: String = "",
        trafos: String = "",
        default_network_power: Double = 200e6,
        default_network_impedance: Complex = Complex (0.437785783, -1.202806555),
        default_transformer_power: Double = 630000.0,
        default_transformer_impedance: Complex = Complex (0.005899999998374999, 0.039562482211875),
        cmax: Double = 1.0,
        cmin: Double = 0.90,
        cosphi: Double = 1.0,
        starting_ratio: Double = 1.0,
        workdir: String = "",
        files: Seq[String] = Seq ())

    val parser: OptionParser[Arguments] = new scopt.OptionParser[Arguments] (APPLICATION_NAME) {
        head (APPLICATION_NAME, APPLICATION_VERSION)

        opt[Unit]('q', "quiet").
            action ((_, c) ⇒ c.copy (quiet = true)).
            text ("supress informational messages")

        opt[String]('m', "master").valueName ("MASTER_URL").
            action ((x, c) ⇒ c.copy (master = x)).
            text ("spark://host:port, mesos://host:port, yarn, or local[*]")

        opt[Map[String, String]]('o', "opts").valueName ("k1=v1,k2=v2").
            action ((x, c) ⇒ c.copy (opts = x)).
            text ("other Spark options")

        opt[String]('s', "storage_level").
            action ((x, c) ⇒ c.copy (storage = x)).
            text ("storage level for RDD serialization (default: MEMORY_AND_DISK_SER)")

        opt[Unit]('u', "deduplicate").
            action ((_, c) ⇒ c.copy (dedup = true)).
            text ("de-duplicate input (striped) files")

        opt[LogLevels.Value]('l', "logging").
            action ((x, c) ⇒ c.copy (log_level = x)).
            text ("log level, one of " + LogLevels.values.iterator.mkString (","))

        opt[String]('k', "checkpointdir").valueName ("<dir>").
            action ((x, c) ⇒ c.copy (checkpoint_dir = x)).
            text ("checkpoint directory on HDFS, e.g. hdfs://...")

        opt[String]('c', "csv").valueName ("<file>").
            action ((x, c) ⇒ c.copy (csv_file = x)).
            text ("csv file of available power at station data (KS_leistungen.csv)")

        opt[Double]('p', "netp").valueName ("<Sk>").
            action ((x, c) ⇒ c.copy (default_network_power = x)).
            text ("network power if not in CIM, VA")

        opt[Complex]('e', "netz").valueName ("<r + xj>").
            action ((x, c) ⇒ c.copy (default_network_impedance = x)).
            text ("network impedance if not in CIM, Ω")

        opt[String]('t', "trafos").valueName ("<TRA file>").
            action ((x, c) => c.copy (trafos = x)).
            text ("file of transformer names (one per line) to process")

        opt[Double]('x', "trafop").valueName ("<ratedS>").
            action ((x, c) => c.copy (default_transformer_power = x)).
            text ("transformer power if not in CIM, VA")

        opt[Complex]('z', "trafoz").valueName ("<r + xj>").
            action ((x, c) => c.copy (default_transformer_impedance = x)).
            text ("transformer impedance if not in CIM, Ω")

        opt[Double]('a', "cmax").
            action ((x, c) => c.copy (cmax = x)).
            text ("voltage factor for maximum fault level, used for rating equipment")

        opt[Double]('i', "cmin").
            action ((x, c) => c.copy (cmin = x)).
            text ("voltage factor for minimum fault level, used for protections settings")

        opt[Double]('f', "cosphi").
            action ((x, c) => c.copy (cosphi = x)).
            text ("load power factor, used for maximum inrush current")

        opt[Double]('r', "starting_ratio").
            action ((x, c) => c.copy (starting_ratio = x)).
            text ("load current starting ratio, used for maximum inrush current")

        opt[String]('w', "workdir").valueName ("<dir>").
            action ((x, c) ⇒ c.copy (workdir = x)).
            text ("shared directory (HDFS or NFS share) with scheme (hdfs:// or file:/) for work files")

        help ("help").text ("prints this usage text")

        arg[String]("<CIM> <CIM> ...").unbounded ().
            action ((x, c) ⇒ c.copy (files = c.files :+ x)).
            text ("CIM rdf files to process")

    }

    def jarForObject (obj: Object): String =
    {
        // see https://stackoverflow.com/questions/320542/how-to-get-the-path-of-a-running-jar-file
        var ret = obj.getClass.getProtectionDomain.getCodeSource.getLocation.getPath
        try {
            ret = URLDecoder.decode (ret, "UTF-8")
        }
        catch {
            case e: UnsupportedEncodingException ⇒ e.printStackTrace ()
        }
        if (!ret.toLowerCase ().endsWith (".jar")) {
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
     * Build jar with dependencies (target/ShortCircuit-2.11-2.2.0-2.3.9-jar-with-dependencies.jar):
     *     mvn package
     * Assuming the data files and csv files exist on hdfs in the data directory,
     * invoke (on the cluster) with:
     *     spark-submit --master spark://sandbox:7077 --conf spark.driver.memory=2g --conf spark.executor.memory=4g /opt/code/ShortCircuit-2.11-2.2.0-2.4.0-jar-with-dependencies.jar --csv "hdfs://sandbox:8020/data/KS_Leistungen.csv" --logging "INFO" "hdfs://sandbox:8020/data/bkw_cim_export_schopfen_all.rdf"
     */

    def read_cim (session: SparkSession, arguments: Arguments, storage_level: StorageLevel): RDD[Element] =
    {
        val log = LoggerFactory.getLogger (getClass)
        val start = System.nanoTime ()
        val reader_options = new HashMap[String, String] ()
        reader_options.put ("StorageLevel", arguments.storage)
        reader_options.put ("ch.ninecode.cim.do_deduplication", arguments.dedup.toString)
        reader_options.put ("path", arguments.files.mkString (","))
        reader_options.put ("ch.ninecode.cim.make_edges", "false")
        reader_options.put ("ch.ninecode.cim.do_join", "false")
        reader_options.put ("ch.ninecode.cim.do_topo", "false") // use the topological processor after reading
        reader_options.put ("ch.ninecode.cim.do_topo_islands", "false")
        val elements = session.read.format ("ch.ninecode.cim").options (reader_options).load (arguments.files: _*).persist (storage_level)

        if (-1 != session.sparkContext.master.indexOf ("sandbox")) // are we in development
            elements.explain
        else
            log.info (elements.count () + " elements")

        val read = System.nanoTime ()
        log.info ("read: " + (read - start) / 1e9 + " seconds")

        // identify topological nodes
        val ntp = new CIMNetworkTopologyProcessor (session, StorageLevel.fromString (arguments.storage), true, true)
        val ele: RDD[Element] = ntp.process (false)
        val topo = System.nanoTime ()
        log.info ("topology: " + (topo - read) / 1e9 + " seconds")
        ele.name = "Elements"
        ele.persist (storage_level)
        ele
    }

    def main (args: Array[String])
    {
        // parser.parse returns Option[C]
        parser.parse (args, Arguments ()) match {
            case Some (arguments) ⇒

                if (!arguments.quiet) org.apache.log4j.LogManager.getLogger ("ch.ninecode.sc.Main$").setLevel (org.apache.log4j.Level.INFO)
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
                if ("" != arguments.master) {
                    val s1 = jarForObject (new DefaultSource ())
                    val s2 = jarForObject (ShortCircuitOptions ())
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
                    // register ShortCircuit analysis classes
                    configuration.registerKryoClasses (Array (
                        classOf[ch.ninecode.sc.Complex],
                        classOf[ch.ninecode.sc.Graphable],
                        classOf[ch.ninecode.sc.ScResult],
                        classOf[ch.ninecode.sc.Impedanzen],
                        classOf[ch.ninecode.sc.ScEdge],
                        classOf[ch.ninecode.sc.ScNode],
                        classOf[ch.ninecode.sc.ShortCircuit],
                        classOf[ch.ninecode.sc.ShortCircuitInfo],
                        classOf[ch.ninecode.sc.ShortCircuitOptions],
                        classOf[ch.ninecode.sc.StartingTrafos],
                        classOf[ch.ninecode.sc.TData],
                        classOf[ch.ninecode.sc.Transformers],
                        classOf[ch.ninecode.sc.TransformerSet]))
                    GraphXUtils.registerKryoClasses (configuration)
                }
                configuration.set ("spark.ui.showConsoleProgress", "false")

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

                read_cim (session, arguments, storage)

                val workdir = if ("" == arguments.workdir) derive_work_dir (arguments.files) else arguments.workdir
                val options = ShortCircuitOptions (
                    verbose = !arguments.quiet,
                    default_supply_network_short_circuit_power = arguments.default_network_power,
                    default_supply_network_short_circuit_impedance = arguments.default_network_impedance,
                    trafos = arguments.trafos,
                    default_transformer_power_rating = arguments.default_transformer_power,
                    default_transformer_impedance = arguments.default_transformer_impedance,
                    cmax = arguments.cmax,
                    cmin = arguments.cmin,
                    cosphi = arguments.cosphi,
                    starting_ratio = arguments.starting_ratio,
                    workdir = workdir
                )

                // if a csv file was supplied, create EquivalentInjections and merge them into the superclass RDDs
                if ("" != arguments.csv_file)
                {
                    val infos = ShortCircuitInfo (session, StorageLevel.MEMORY_AND_DISK_SER)
                    val equivalents = infos.getShortCircuitInfo (arguments.csv_file)
                    val export = new CIMExport (session)
                    export.export (equivalents, arguments.csv_file.replace (".csv", ".rdf"), "generated from " + arguments.csv_file)
                    infos.merge (equivalents)
                }

                val shortcircuit = ShortCircuit (session, storage, options)
                val results = shortcircuit.run ()

                val string = results.sortBy (_.tx).map (h ⇒ {
                    h.equipment + ";" + h.tx + ";" + h.ik + ";" + h.ik3pol + ";" + h.ip + ";" + h.r + ";" + h.r0 + ";" + h.x + ";" + h.x0 + ";" + h.sk + ";" + (if (null == h.fuses) "" else h.fuses.mkString (",")) + ";" + FData.fuse (h.ik) + ";" + FData.fuseOK (h.ik, h.fuses) + ";" + h.motor_3ph_max_low + ";" + h.motor_1ph_max_low + ";" + h.motor_l_l_max_low + ";" + h.motor_3ph_max_med + ";" + h.motor_1ph_max_med + ";" + h.motor_l_l_max_med
                })
                log.info ("output_path: " + workdir)
                val hdfs_configuration = new Configuration()
                hdfs_configuration.set ("fs.hdfs.impl", "org.apache.hadoop.hdfs.DistributedFileSystem")
                hdfs_configuration.set ("fs.file.impl", "org.apache.hadoop.fs.LocalFileSystem")
                val hdfs = FileSystem.get (URI.create (workdir), hdfs_configuration)

                val directory = new Path (workdir)
                hdfs.delete (directory, true)
                string.saveAsTextFile (workdir)

                // output SQLite database
                Database.store ("test", options) (results.collect)

                val calculate = System.nanoTime ()
                log.info ("total: " + (calculate - begin) / 1e9 + " seconds, " + results.count + " node results calculated")

                sys.exit (0)
            case None ⇒
                sys.exit (1)
        }
    }
}
