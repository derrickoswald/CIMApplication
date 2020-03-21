package ch.ninecode.mfi

import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.net.URI
import java.util.Properties

import scala.tools.nsc.io.Jar
import scala.util.Random

import org.apache.spark.SparkConf
import org.apache.spark.graphx.GraphXUtils
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.LoggerFactory

import ch.ninecode.cim.CIMClasses
import ch.ninecode.cim.DefaultSource
import ch.ninecode.gl.GridLABD
import ch.ninecode.net.Net
import ch.ninecode.util.Util

object MaximumFeedIn
{
    val properties: Properties =
    {
        val in = this.getClass.getResourceAsStream ("/application.properties")
        val p = new Properties ()
        p.load (in)
        in.close ()
        p
    }
    val APPLICATION_NAME: String = properties.getProperty ("artifactId")
    val APPLICATION_VERSION: String = properties.getProperty ("version")
    val SPARK: String = properties.getProperty ("spark")

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
     * Build jar with dependencies (target/MaximumFeedIn-2.11-2.4.5-2.7.1-jar-with-dependencies.jar):
     * mvn package
     * Invoke (on the cluster) with:
     * spark-submit --master spark://sandbox:7077 --conf spark.driver.memory=2g --conf spark.executor.memory=2g MaximumFeedIn-*-with-dependencies.jar --verbose --all hdfs://sandbox:8020/DemoData.rdf
     */
    def main (args: Array[String])
    {
        new EinspeiseleistungOptionsParser (APPLICATION_NAME, APPLICATION_VERSION).parse (args, EinspeiseleistungOptions ()) match
        {
            case Some (arguments) =>
                if (arguments.valid)
                {
                    if (arguments.verbose) org.apache.log4j.LogManager.getLogger (getClass.getName).setLevel (org.apache.log4j.Level.INFO)
                    val log = LoggerFactory.getLogger (getClass)
                    val begin = System.nanoTime ()

                    var count = 0L
                    if (arguments.files.nonEmpty)
                    {
                        // create the configuration
                        val configuration = new SparkConf (false)
                        configuration.setAppName (APPLICATION_NAME)
                        if ("" != arguments.master)
                            configuration.setMaster (arguments.master)
                        if (arguments.spark_options.nonEmpty)
                            arguments.spark_options.map ((pair: (String, String)) => configuration.set (pair._1, pair._2))

                        // get the necessary jar files to send to the cluster
                        if ("" != arguments.master)
                        {
                            val jars = Set (jarForObject (new DefaultSource ()), jarForObject (EinspeiseleistungOptions ()))
                            configuration.setJars (jars.toArray)
                        }

                        if (StorageLevel.fromString (arguments.storage).useDisk)
                        {
                            // register CIMReader classes
                            configuration.registerKryoClasses (CIMClasses.list)
                            // register Einspeiseleistung classes
                            configuration.registerKryoClasses (Einspeiseleistung.classes)
                            // register GridLAB-D classes
                            configuration.registerKryoClasses (GridLABD.classes)
                            // register Net classes
                            configuration.registerKryoClasses (Net.classes)
                            // register Util classes
                            configuration.registerKryoClasses (Util.classes)
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

                        val options = arguments.copy (
                            cim_reader_options =
                                Map (
                                    "StorageLevel" → arguments.storage,
                                    "ch.ninecode.cim.do_deduplication" → arguments.dedup.toString
                                ),
                            workdir =
                                if ("" == arguments.workdir)
                                    derive_work_dir (arguments.files)
                                else
                                    arguments.workdir
                        )
                        val eins = Einspeiseleistung (session, options)
                        count = eins.run ()
                    }

                    val calculate = System.nanoTime ()
                    log.info ("total: " + (calculate - begin) / 1e9 + " seconds " + count + " trafokreise")
                }
                if (!arguments.unittest)
                    sys.exit (0)
            case None =>
                sys.exit (1)
        }
    }
}
