package ch.ninecode.ingest

import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.util.Properties

import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import scala.tools.nsc.io.Jar
import scala.util.Random

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
            val name = "/tmp/" + Random.nextInt (99999999) + ".jar"
            val writer = new Jar (new scala.reflect.io.File (new java.io.File (name))).jarWriter ()
            writer.addDirectory (new scala.reflect.io.Directory (new java.io.File (ret + "ch/")), "ch/")
            writer.close ()
            ret = name
        }

        ret
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
                    if (options.verbose) org.apache.log4j.LogManager.getLogger (getClass.getName).setLevel (org.apache.log4j.Level.INFO)
                    if ("" != options.mapping)
                    {
                        val begin = System.nanoTime ()

                        // create the configuration
                        val configuration = new SparkConf (false)
                        configuration.setAppName (APPLICATION_NAME)
                        configuration.setMaster (options.master)
                        if (options.options.nonEmpty)
                            options.options.map ((pair: (String, String)) => configuration.set (pair._1, pair._2))
                        configuration.set ("spark.cassandra.connection.host", options.host)
                        configuration.set ("spark.cassandra.connection.port", options.port.toString)

                        // get the necessary jar files to send to the cluster
                        val s1 = jarForObject (IngestOptions ())
                        val s2 = jarForObject (com.datastax.spark.connector.mapper.ColumnMapper)
                        val s3 = jarForObject (new com.twitter.jsr166e.LongAdder ())

                        configuration.setJars (Set (s1, s2, s3).toArray)

                        configuration.set ("spark.ui.showConsoleProgress", "false")

                        // make a Spark session
                        val session = SparkSession.builder ().config (configuration).getOrCreate ()
                        session.sparkContext.setLogLevel (options.log_level.toString)

                        if (options.aws_s3a_access_key.trim.nonEmpty && options.aws_s3a_secret_key.trim.nonEmpty)
                        {
                            System.setProperty("com.amazonaws.services.s3.enableV4", "true")
                            session.sparkContext.hadoopConfiguration.set("com.amazonaws.services.s3.enableV4", "true")
                            session.sparkContext.hadoopConfiguration.set("fs.s3a.access.key", options.aws_s3a_access_key)
                            session.sparkContext.hadoopConfiguration.set("fs.s3a.secret.key", options.aws_s3a_secret_key)
                            session.sparkContext.hadoopConfiguration.set("fs.s3a.endpoint","s3.eu-central-1.amazonaws.com")
                            session.sparkContext.hadoopConfiguration.set("fs.s3a.impl","org.apache.hadoop.fs.s3a.S3AFileSystem")
                        }

                        val version = session.version
                        log.info (s"Spark $version session established")
                        if (version.take (SPARK.length) != SPARK.take (version.length))
                            log.warn (s"Spark version ($version) does not match the version ($SPARK) used to build $APPLICATION_NAME")

                        val setup = System.nanoTime ()
                        log.info ("setup: " + (setup - begin) / 1e9 + " seconds")

                        val ingest = Ingest (session, options)
                        ingest.run ()

                        val calculate = System.nanoTime ()
                        log.info ("execution: " + (calculate - setup) / 1e9 + " seconds")
                    }
                    else
                        log.error ("""mapping file not specified""")
                }
                if (!options.unittest)
                    sys.exit (0)
            case None =>
                sys.exit (1)
        }
    }
}
