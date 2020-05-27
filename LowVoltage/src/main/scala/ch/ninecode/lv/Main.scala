package ch.ninecode.lv

import java.io.UnsupportedEncodingException
import java.net.URI
import java.net.URLDecoder
import java.util.Properties

import scala.tools.nsc.io.Jar
import scala.util.Random

import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import org.slf4j.LoggerFactory

import ch.ninecode.cim.CIMClasses
import ch.ninecode.cim.DefaultSource
import ch.ninecode.gl.GridLABD
import ch.ninecode.mfi.Einspeiseleistung
import ch.ninecode.net.Net
import ch.ninecode.util.Util

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
            val name = s"/tmp/${Random.nextInt (99999999)}.jar"
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
        files.toList match
        {
            case paths :: _ =>
                val file = paths.split (",")(0).replace (" ", "%20")
                val uri = new URI (file)
                if (null == uri.getScheme)
                    "/simulation/"
                else
                    uri.getScheme + "://" + (if (null == uri.getAuthority) "" else uri.getAuthority) + "/simulation/"
            case _ =>
                "/simulation/"
        }
    }

    def getSparkSession (arguments: LowVoltageOptions): SparkSession =
    {
        // get the necessary jar files to send to the cluster
        val s1 = jarForObject (new DefaultSource ())
        val s2 = jarForObject (LowVoltageOptions ())

        // create the configuration
        val configuration = new SparkConf (false)
            .setAppName (APPLICATION_NAME)
            .setJars (Set (s1, s2).toArray)
            .set ("spark.ui.showConsoleProgress", "false")
            // register CIMReader classes
            .registerKryoClasses (CIMClasses.list)
            // register Net classes
            .registerKryoClasses (Net.classes)
            // register GridLAB-D classes
            .registerKryoClasses (GridLABD.classes)
            // register Einspeiseleistung classes
            .registerKryoClasses (Einspeiseleistung.classes)
            // register Util classes
            .registerKryoClasses (Util.classes)
        if ("" != arguments.master)
        {
            val _ = configuration.setMaster (arguments.master)
        }
        if (arguments.spark_options.nonEmpty)
            arguments.spark_options.foreach ((pair: (String, String)) => configuration.set (pair._1, pair._2))

        // make a Spark session
        val session = SparkSession.builder ().config (configuration).getOrCreate ()
        session.sparkContext.setLogLevel (arguments.log_level.toString)
        if ("" != arguments.checkpoint_dir)
            session.sparkContext.setCheckpointDir (arguments.checkpoint_dir)

        session
    }

    /**
     * Build jar with dependencies (target/LowVoltage-<version>>-jar-with-dependencies.jar):
     * mvn package
     * Assuming the data files exist on hdfs in the data directory,
     * invoke (on the cluster) with:
     * spark-submit --master spark://sandbox:7077 --conf spark.driver.memory=2g --conf spark.executor.memory=4g /opt/code/LowVoltage-<version>>-jar-with-dependencies.jar --three "hdfs://sandbox:8020/cimfile.rdf"
     */
    def main (args: Array[String])
    {
        new LowVoltageOptionsParser (APPLICATION_NAME, APPLICATION_VERSION).parse (args, LowVoltageOptions ()) match
        {
            case Some (arguments) =>
                if (arguments.valid)
                {
                    val options = if ("" == arguments.workdir)
                        arguments.copy (workdir = derive_work_dir (arguments.files))
                    else
                        arguments
                    if (options.verbose) org.apache.log4j.LogManager.getLogger (getClass.getName).setLevel (org.apache.log4j.Level.INFO)
                    val log = LoggerFactory.getLogger (getClass)
                    val begin = System.nanoTime ()
                    val session = getSparkSession (options)
                    val version = session.version
                    log.info (s"Spark $version session established")
                    if (session.version != SPARK)
                        log.warn (s"Spark version ($version) does not match the version ($SPARK) used to build $APPLICATION_NAME")
                    val setup = System.nanoTime ()
                    log.info (s"setup: ${(setup - begin) / 1e9} seconds")

                    val lv = LowVoltage (session, options)
                    val count = lv.run ()

                    log.info (s"execution: ${(System.nanoTime () - setup) / 1e9} seconds $count trafokreise")
                }
                if (!arguments.unittest)
                    sys.exit (0)
            case None =>
                sys.exit (1)
        }
    }
}
