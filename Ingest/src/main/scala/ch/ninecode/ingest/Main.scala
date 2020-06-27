package ch.ninecode.ingest

import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.util.Properties

import scala.tools.nsc.io.Jar
import scala.util.Random

import org.apache.log4j.Level
import org.apache.log4j.LogManager
import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object Main
{
    val log: Logger = LoggerFactory.getLogger (getClass)
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

    def time[R](template: String)(block: => R): R =
    {
        val t0 = System.nanoTime ()
        val ret = block
        val t1 = System.nanoTime ()
        log.info (template.format ((t1 - t0) / 1e9), None)
        ret
    }

    def createSession (options: IngestOptions): SparkSession =
    {
        // get the necessary jar files to send to the cluster
        val jars = Set (
            jarForObject (IngestOptions ()),
            jarForObject (com.datastax.spark.connector.mapper.ColumnMapper)
        ).toArray

        // create the configuration
        val configuration =
            new SparkConf (false)
                .setAppName (APPLICATION_NAME)
                .setMaster (options.master)
                .set ("spark.cassandra.connection.host", options.host)
                .set ("spark.cassandra.connection.port", options.port.toString)
                .set ("spark.ui.showConsoleProgress", "false")
                .setJars (jars)
        options.options.foreach ((pair: (String, String)) => configuration.set (pair._1, pair._2))

        // make a Spark session
        val session = SparkSession.builder ().config (configuration).getOrCreate ()
        session.sparkContext.setLogLevel (options.log_level.toString)
        val version = session.version
        log.info (s"Spark $version session established")
        if (version.take (SPARK.length) != SPARK.take (version.length))
            log.warn (s"Spark version ($version) does not match the version ($SPARK) used to build $APPLICATION_NAME")
        session
    }

    /**
     * Build jar with dependencies (creates target/program_name_and_version-jar-with-dependencies.jar):
     * mvn package
     * Invoke (on the cluster) with:
     * spark-submit --master spark://sandbox:7077 --conf spark.driver.memory=2g --conf spark.executor.memory=4g /opt/code/program_name_and_version-jar-with-dependencies.jar --verbose --host sandbox 20180405_073251_Belvis_STA206.csv
     */
    def main (args: Array[String])
    {
        new IngestOptionsParser (APPLICATION_NAME, APPLICATION_VERSION).parse (args, IngestOptions ()) match
        {
            case Some (options) =>
                if (options.valid)
                {
                    if (options.verbose) LogManager.getLogger (getClass).setLevel (Level.INFO)
                    val session = time ("setup: %s seconds") { createSession (options) }
                    time ("execution: %s seconds")
                    {
                        val ingest = new Ingest (session, options)
                        ingest.run ()
                    }
                }
                if (!options.unittest)
                    sys.exit (0)
            case None =>
                sys.exit (1)
        }
    }
}
