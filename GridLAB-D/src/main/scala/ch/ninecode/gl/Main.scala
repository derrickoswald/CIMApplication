package ch.ninecode.gl

import java.io.UnsupportedEncodingException
import java.net.URLDecoder

import scala.collection.mutable.HashMap
import scala.tools.nsc.io.Jar
import scala.util.Random

import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel

import ch.ninecode.cim._
import ch.ninecode.model._

object Main
{
    val APPLICATION_NAME = "GridLAB-D"
    val APPLICATION_VERSION = "2.2.0"

    object LogLevels extends Enumeration
    {
        type LogLevels = Value
        val ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN = Value
    }
    implicit val LogLevelsRead: scopt.Read[LogLevels.Value] = scopt.Read.reads (LogLevels.withName (_))

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

    case class Arguments (
        master: String = "",
        opts: Map[String,String] = Map(),
        storage: String = "MEMORY_AND_DISK_SER",
        dedup: Boolean = false,
        three: Boolean = false,
        precalculation: Boolean = false,
        trafos: String = "",
        export_only: Boolean = false,
        all: Boolean = false,
        erase: Boolean = false,
        log_level: LogLevels.Value = LogLevels.OFF,
        checkpoint_dir: String = "",
        simulation: Int = -1,
        reference: Int = -1,
        delta: Double = 1e-6,
        number: Int = -1,
        short_circuit: String = "",
        files: Seq[String] = Seq()
    )

    val parser = new scopt.OptionParser[Arguments](APPLICATION_NAME)
    {
        head (APPLICATION_NAME, APPLICATION_VERSION)

        opt[String]('m', "master").valueName ("MASTER_URL").
            action ((x, c) => c.copy (master = x)).
            text ("spark://host:port, mesos://host:port, yarn, or local[*]")

        opt[Map[String,String]]('o', "opts").valueName ("k1=v1,k2=v2").
            action ((x, c) => c.copy (opts = x)).
            text ("other Spark options")

        opt[String]('g', "storage_level").
            action ((x, c) => c.copy (storage = x)).
            text ("storage level for RDD serialization (default: MEMORY_AND_DISK_SER)")

        opt[Unit]('u', "deduplicate").
            action ((_, c) => c.copy (dedup = true)).
            text ("de-duplicate input (striped) files")

        opt[Unit]('3', "three").
            action ((_, c) => c.copy (three = true)).
            text ("use three phase computations")

        opt[Unit]('p', "precalculation").
            action ((_, c) => c.copy (precalculation = true)).
            text ("calculates threshold and EEA existence for all HAS, assuming no EEA")

        opt[String]('t', "trafos").valueName ("<TRA file>").
            action ((x, c) => c.copy (trafos = x)).
            text ("file of transformer names (one per line) to process")

        opt[Unit]('x', "export_only").
            action ((_, c) => c.copy (export_only = true)).
            text ("generates glm files only - no solve or analyse operations")

        opt[Unit]('a', "all").
            action ((_, c) => c.copy (all = true)).
            text ("process all transformers (not just those with EEA)")

        opt[Unit]('e', "erase").
            action ((_, c) => c.copy (erase = true)).
            text ("clean up (delete) simulation HDFS files")

        opt[LogLevels.Value]('l', "logging").
            action ((x, c) => c.copy (log_level = x)).
            text ("log level, one of " + LogLevels.values.iterator.mkString (","))

        opt[String]('k', "checkpointdir").valueName ("<dir>").
            action ((x, c) => c.copy (checkpoint_dir = x)).
            text ("checkpoint directory on HDFS, e.g. hdfs://...")

        opt[Int]('s', "simulation").valueName ("N").
            action ((x, c) => c.copy (simulation = x)).
            text ("simulation number (precalc) to use for transformer list")

        opt[Int]('r', "reference").valueName ("N").
            action ((x, c) => c.copy (reference = x)).
            text ("simulation number (precalc) to use as reference for transformer list")

        opt[Int]('d', "delta").valueName ("D").
            action ((x, c) => c.copy (delta = x)).
            text ("delta power difference threshold for reference comparison")

        opt[Int]('n', "number").valueName ("N").
            action ((x, c) => c.copy (number = x)).
            text ("number of transformers to process")

        opt[String]('c', "csv").valueName ("<file>").
            action ((x, c) => c.copy (short_circuit = x)).
            text ("short circuit power file")

        help ("help").text ("prints this usage text")

        arg[String]("<CIM> <CIM> ...").unbounded ().
            action ((x, c) => c.copy (files = c.files :+ x)).
            text ("CIM rdf files to process")

    }

    def jarForObject (obj: Object): String =
    {
        // see https://stackoverflow.com/questions/320542/how-to-get-the-path-of-a-running-jar-file
        var ret = obj.getClass.getProtectionDomain ().getCodeSource ().getLocation ().getPath ()
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

        return (ret)
    }

    /**
     * Build jar with dependencies (target/GridLAB-D-2.0.0-jar-with-dependencies.jar):
     *     mvn package
     * Invoke (on the cluster) with:
     *     spark-submit --master spark://sandbox:7077 --conf spark.driver.memory=2g --conf spark.executor.memory=4g /opt/code/GridLAB-D-2.0.0-jar-with-dependencies.jar hdfs://sandbox:8020/data/bkw_cim_export_haelig.rdf
     * or on AWS:
     *     /opt/spark/bin/spark-submit --master yarn /disktemp/transfer/GridLAB-D-2.0.0-jar-with-dependencies.jar hdfs://hmaster:9000/data/NIS_CIM_Export_sias_current_20161220_Sample4.rdf
     */
    def main (args: Array[String])
    {
        // parser.parse returns Option[C]
        parser.parse (args, Arguments ()) match
        {
            case Some (arguments) =>

                val begin = System.nanoTime ()

                // create the configuration
                val configuration = new SparkConf (false)
                configuration.setAppName (APPLICATION_NAME)
                if ("" != arguments.master)
                    configuration.setMaster (arguments.master)
                if (arguments.opts.size != 0)
                    arguments.opts.map ((pair: Tuple2[String, String]) => configuration.set (pair._1, pair._2))

                // get the necessary jar files to send to the cluster
                if ("" != arguments.master)
                {
                    val s1 = jarForObject (new DefaultSource ())
                    val s2 = jarForObject (Complex (0.0, 0.0))
                    if (s1 != s2)
                        configuration.setJars (Array (s1, s2))
                    else
                        configuration.setJars (Array (s1))
                }

                if (StorageLevel.fromString (arguments.storage).useDisk)
                {
                    // register low level classes
                    configuration.registerKryoClasses (Array (classOf[Element], classOf[BasicElement], classOf[Unknown]))
                    // register CIM case classes
                    CHIM.apply_to_all_classes { x => configuration.registerKryoClasses (Array (x.runtime_class)) }
                    // register edge related classes
                    configuration.registerKryoClasses (Array (classOf[PreEdge], classOf[Extremum], classOf[PostEdge]))
                    // register topological classes
                    configuration.registerKryoClasses (Array (classOf[CuttingEdge], classOf[TopologicalData]))
                    // register GridLAB-D classes
                    configuration.registerKryoClasses (Array (
                        classOf[ch.ninecode.gl.PreNode],
                        classOf[ch.ninecode.gl.PreEdge],
                        classOf[ch.ninecode.gl.PV],
                        classOf[ch.ninecode.gl.ThreePhaseComplexDataElement]))
                }
                configuration.set ("spark.ui.showConsoleProgress", "false")

                // make a Spark session
                val session = SparkSession.builder ().config (configuration).getOrCreate ()
                session.sparkContext.setLogLevel (arguments.log_level.toString ())
                if ("" != arguments.checkpoint_dir)
                    session.sparkContext.setCheckpointDir (arguments.checkpoint_dir)

                val setup = System.nanoTime ()
                println ("setup : " + (setup - begin) / 1e9 + " seconds")
                val ro = HashMap[String,String] ()
                ro.put ("StorageLevel", arguments.storage)
                ro.put ("ch.ninecode.cim.do_deduplication", arguments.dedup.toString)
                val options = EinspeiseleistungOptions (
                    cim_reader_options = ro,
                    three = arguments.three,
                    precalculation = arguments.precalculation,
                    trafos = arguments.trafos,
                    export_only = arguments.export_only,
                    all = arguments.all,
                    erase = arguments.erase,
                    simulation = arguments.simulation,
                    reference = arguments.reference,
                    delta = arguments.delta,
                    number = arguments.number,
                    short_circuit = arguments.short_circuit,
                    files = arguments.files
                )
                val eins = Einspeiseleistung (session, options)
                eins.run ()

                val calculate = System.nanoTime ()
                println ("total: " + (calculate - begin) / 1e9 + " seconds")

                sys.exit (0)
            case None =>
                sys.exit (1)
        }
    }
}
