package ch.ninecode.util

import java.io.UnsupportedEncodingException
import java.net.URLDecoder

import scala.tools.nsc.io.Jar
import scala.util.Random

import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Common functionality to initialize a Spark session in a main program.
 *
 * @tparam T the type of options needed for session creation
 */
trait SparkInitializer[T <: Mainable with Sparkable]
{
    // logger for main class
    implicit val log: Logger = LoggerFactory.getLogger (getClass)

    // utility timing block
    def time[R](template: String)(block: => R): R =
    {
        val t0 = System.nanoTime ()
        val ret = block
        val t1 = System.nanoTime ()
        log.info (template.format ((t1 - t0) / 1e9), None)
        ret
    }

    // get the containing jar name for an object
    def jarForObject (obj: Object): String =
    {
        var ret = obj.getClass.getProtectionDomain.getCodeSource.getLocation.getPath
        try
        {
            ret = URLDecoder.decode (ret, "UTF-8")
        }
        catch
        {
            case e: UnsupportedEncodingException => e.printStackTrace ()
        }
        if (!ret.toLowerCase.endsWith (".jar"))
        {
            // as an aid to debugging, make a jar in /tmp and return that name
            val name = s"/tmp/${Random.nextInt (99999999)}.jar"
            val writer = new Jar (new scala.reflect.io.File (new java.io.File (name))).jarWriter ()
            writer.addDirectory (new scala.reflect.io.Directory (new java.io.File (s"${ret}ch/")), "ch/")
            writer.close ()
            ret = name
        }

        ret
    }

    // get the Spark version from a three part semantic version string
    def sparkVersion (options: T): String =
    {
        val version = options.main_options.version.split ("-")
        if (3 == version.length)
            version(1)
        else
            "unknown"
    }

    /**
     * Create a Spark session according to the given parameters.
     *
     * @param options options to affect the created Spark session
     * @return A Spark session with the options in effect
     */
    def createSession (options: T): SparkSession =
    {
        time ("setup: %s seconds")
        {
            // create the configuration
            val configuration =
                new SparkConf (false)
                    .setAppName (options.main_options.application)
                    .setMaster (options.spark_options.master)
                    .setJars (options.spark_options.jars)
                    .registerKryoClasses (options.spark_options.kryo)
            options.spark_options.options.foreach ((pair: (String, String)) => configuration.set (pair._1, pair._2))

            // make a Spark session
            val session = SparkSession.builder ().config (configuration).getOrCreate ()
            session.sparkContext.setLogLevel (options.spark_options.logAsString)
            if ("" != options.spark_options.checkpoint)
                session.sparkContext.setCheckpointDir (options.spark_options.checkpoint)
            val version = session.version
            log.info (s"Spark $version session established")
            val spark = sparkVersion (options)
            if (version.take (spark.length) != spark.take (version.length))
                log.warn (s"Spark version ($version) does not match the version ($spark) used to build ${options.main_options.application}")
            session
        }
    }
}
