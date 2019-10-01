package ch.ninecode.copy

import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.util.Properties

import scala.tools.nsc.io.Jar
import scala.util.Random

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
     * or on AWS:
     * /opt/spark/bin/spark-submit --master yarn /disktemp/transfer/program_name_and_version-jar-with-dependencies.jar --host sandbox 20180405_073251_Belvis_STA206.csv
     */
    def main (args: Array[String])
    {
        val optionparser = new CopyOptionsParser (APPLICATION_NAME, APPLICATION_VERSION)

        def same (options: CopyOptions): Boolean =
        {
            options.source_host == options.target_host &&
            options.source_keyspace == options.target_keyspace
        }

        optionparser.parse (args, CopyOptions ()) match
        {
            case Some (options) =>
                if (options.valid)
                {
                    if (!same (options))
                    {
                        val begin = System.nanoTime ()

                        // create the configuration
                        val configuration = new SparkConf (false)
                        configuration.setAppName (APPLICATION_NAME)
                        if ("" != options.master)
                            configuration.setMaster (options.master)
                        if (options.options.nonEmpty)
                            options.options.map ((pair: (String, String)) => configuration.set (pair._1, pair._2))

                        // get the necessary jar files to send to the cluster
                        val s1 = jarForObject (CopyOptions ())
                        val s2 = jarForObject (com.datastax.spark.connector.mapper.ColumnMapper)
                        val s3 = jarForObject (new com.twitter.jsr166e.LongAdder ())
                        configuration.setJars (Array (s1, s2, s3))

                        configuration.set ("spark.ui.showConsoleProgress", "false")

                        // make a Spark session
                        val session = SparkSession.builder ().config (configuration).getOrCreate ()
                        session.sparkContext.setLogLevel (options.log_level.toString)
                        val version = session.version
                        log.info (s"Spark $version session established")
                        if (version.take (SPARK.length) != SPARK.take (version.length))
                            log.warn (s"Spark version ($version) does not match the version ($SPARK) used to build $APPLICATION_NAME")

                        val setup = System.nanoTime ()
                        log.info ("setup: " + (setup - begin) / 1e9 + " seconds")

                        val copy = Copy (session, options)
                        copy.run ()

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
