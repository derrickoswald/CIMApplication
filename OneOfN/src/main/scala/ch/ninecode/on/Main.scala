package ch.ninecode.on

import java.io.UnsupportedEncodingException
import java.net.URI
import java.net.URLDecoder
import java.util.Properties

import scala.tools.nsc.io.Jar
import scala.util.Random

import org.apache.log4j.Level
import org.apache.log4j.LogManager
import org.apache.spark.SparkConf
import org.apache.spark.graphx.GraphXUtils
import org.apache.spark.sql.SparkSession
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.cim.CIMClasses
import ch.ninecode.cim.DefaultSource
import ch.ninecode.gl.GridLABD
import ch.ninecode.net.Net

import ch.ninecode.util.Util

object Main
{
    val log: Logger = LoggerFactory.getLogger (getClass)
    lazy val properties: Properties =
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
            writer.addDirectory (new scala.reflect.io.Directory (new java.io.File (s"${ret}ch/")), "ch/")
            writer.close ()
            ret = name
        }

        ret
    }

    def time[R](template: String)(block: => R): R =
    {
        val t0 = System.nanoTime ()
        val ret = block
        val t1 = System.nanoTime ()
        log.info (template.format ((t1 - t0) / 1e9), None)
        ret
    }

    def createSession (options: OneOfNOptions): SparkSession =
    {
        // get the necessary jar files to send to the cluster
        val jars = Set (
            jarForObject (OneOfNOptions ()),
            jarForObject (new DefaultSource ())
        ).toArray

        // create the configuration
        val configuration =
            new SparkConf (false)
                .setAppName (APPLICATION_NAME)
                .setMaster (options.master)
                .set ("spark.ui.showConsoleProgress", "false")
                .setJars (jars)
                // register CIMReader classes
                .registerKryoClasses (CIMClasses.list)
                // use the custom registrator
                .set ("spark.kryo.registrator", "ch.ninecode.cim.CIMRegistrator")
                // register GridLAB-D classes
                .registerKryoClasses (GridLABD.classes)
                // register Net classes
                .registerKryoClasses (Net.classes)
                // register OneOfN classes
                .registerKryoClasses (OneOfN.classes)
                // register OneOfN classes
                .registerKryoClasses (Util.classes)
        // register GraphX classes
        GraphXUtils.registerKryoClasses (configuration)
        options.options.foreach ((pair: (String, String)) => configuration.set (pair._1, pair._2))

        // make a Spark session
        val session = SparkSession.builder ().config (configuration).getOrCreate ()
        session.sparkContext.setLogLevel (options.log_level.toString)
        if ("" != options.checkpoint_dir)
            session.sparkContext.setCheckpointDir (options.checkpoint_dir)
        val version = session.version
        log.info (s"Spark $version session established")
        if (version.take (SPARK.length) != SPARK.take (version.length))
            log.warn (s"Spark version ($version) does not match the version ($SPARK) used to build $APPLICATION_NAME")
        session
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
     * spark-submit --master spark://sandbox:7077 /opt/code/program_name_and_version-jar-with-dependencies.jar "hdfs://sandbox:8020/data/rdffilename.rdf"
     * or on AWS:
     * /opt/spark/bin/spark-submit --master yarn /disktemp/transfer/program_name_and_version-jar-with-dependencies.jar hdfs://hmaster:9000/data/NIS_CIM_Export_sias_current_20161220_Sample4.rdf
     */
    def main (args: Array[String])
    {
        new OneOfNOptionsParser (APPLICATION_NAME, APPLICATION_VERSION).parse (args, OneOfNOptions ()) match
        {
            case Some (options) =>
                if (options.valid)
                {
                    if (options.verbose) LogManager.getLogger (getClass).setLevel (Level.INFO)
                    if (options.files.nonEmpty)
                    {
                        val session = time ("setup: %s seconds") { createSession (options) }
                        time ("execution: %s seconds")
                        {
                            val opts = if ("" == options.workdir)
                                options.copy (workdir = derive_work_dir (options.files))
                            else
                                options
                            val oneofn = new OneOfN (session, opts)
                            val count = oneofn.run ()
                            log.info (s"$count models processed")
                        }
                    }
                    else
                        log.error ("no CIM files specified")
                }
                if (!options.unittest)
                    sys.exit (0)
            case None =>
                sys.exit (1)
        }
    }
}
