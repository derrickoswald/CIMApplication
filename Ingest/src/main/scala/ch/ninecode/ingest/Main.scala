package ch.ninecode.ingest

import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Properties
import java.util.TimeZone

import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import scopt.OptionParser

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
    val APPLICATION_NAME: String = "Ingest"
    val APPLICATION_VERSION: String = properties.getProperty ("version")
    val SPARK: String = properties.getProperty ("spark")

    object LogLevels extends Enumeration
    {
        type LogLevels = Value
        val ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN = Value
    }

    implicit val LogLevelsRead: scopt.Read[LogLevels.Value] = scopt.Read.reads (LogLevels.withName)

    var do_exit = true

    val parser: OptionParser[IngestOptions] = new scopt.OptionParser[IngestOptions](APPLICATION_NAME)
    {
        head (APPLICATION_NAME, APPLICATION_VERSION)

        val default = new IngestOptions

        override def terminate (exitState: Either[String, Unit]): Unit =
            if (do_exit)
                exitState match
                {
                    case Left (_) => sys.exit (1)
                    case Right (_) => sys.exit (0)
                }

        def parseTime (options: IngestOptions, time: String): Long =
        {
            val MeasurementTimeZone: TimeZone = TimeZone.getTimeZone (options.timezone)
            val MeasurementCalendar: Calendar = Calendar.getInstance ()
            MeasurementCalendar.setTimeZone (MeasurementTimeZone)
            val MeasurementTimestampFormat: SimpleDateFormat = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss")
            MeasurementTimestampFormat.setCalendar (MeasurementCalendar)
            MeasurementTimestampFormat.parse (time).getTime
        }

        def formatTime (options: IngestOptions, time: Long): String =
        {
            val MeasurementTimeZone: TimeZone = TimeZone.getTimeZone (options.timezone)
            val MeasurementCalendar: Calendar = Calendar.getInstance ()
            MeasurementCalendar.setTimeZone (MeasurementTimeZone)
            val MeasurementTimestampFormat: SimpleDateFormat = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss")
            MeasurementTimestampFormat.setCalendar (MeasurementCalendar)
            MeasurementTimestampFormat.format (time)
        }

        opt [Unit]("unittest").
            hidden ().
            action ((_, c) ⇒ c.copy (unittest = true)).
            text ("unit testing - don't call sys.exit() [%s]".format (default.unittest))

        opt [Unit]("verbose").
            action ((_, c) ⇒ c.copy (verbose = true)).
            text ("emit progress messages [%s]".format (default.verbose))

        opt [String]("master").valueName ("MASTER_URL").
            action ((x, c) ⇒ c.copy (master = x)).
            text ("local[*], spark://host:port, mesos://host:port or yarn [%s]".format (default.master))

        opt [String]("host").valueName ("Cassandra").
            action ((x, c) ⇒ c.copy (host = x)).
            text ("Cassandra connection host (listen_address or seed in cassandra.yaml) [%s]".format (default.host))

        opt [String]("storage").
            action ((x, c) ⇒ c.copy (storage = x)).
            text ("storage level for RDD serialization [%s]".format (default.storage))

        opt [LogLevels.Value]("logging").
            action ((x, c) ⇒ c.copy (log_level = x)).
            text ("log level, one of " + LogLevels.values.iterator.mkString (",") + " [%s]".format (default.log_level))

        opt [String]("mapping").
            action ((x, c) ⇒ c.copy (mapping = x)).
            text ("file name of mapping CSV [%s] (required)".format (default.mapping))

        opt [String]("metercol").
            action ((x, c) ⇒ c.copy (metercol = x)).
            text ("column name of meter id in mapping CSV [%s]".format (default.metercol))

        opt [String]("mridcol").
            action ((x, c) ⇒ c.copy (mridcol = x)).
            text ("column name of CIM mRID in mapping CSV [%s]".format (default.mridcol))

        opt [String]("timezone").
            action ((x, c) ⇒ c.copy (timezone = x)).
            text ("measurement time zone for measurements [%s]".format (default.timezone))

        opt [String]("mintime").
            action ((x, c) ⇒ c.copy (mintime = parseTime (c, x))).
            text ("minimum time for ingestion timespan [%s]".format (formatTime (default, default.mintime)))

        opt [String]("maxtime").
            action ((x, c) ⇒ c.copy (maxtime = parseTime (c, x))).
            text ("maximum time for ingestion timespan [%s]".format (formatTime (default, default.maxtime)))

        arg [String]("<ZIP> or <CSV>...").optional ().unbounded ().
            action ((x, c) ⇒
            {
                try
                {
                    val sep = System.getProperty ("file.separator")
                    val file = if (x.startsWith (sep)) x else new java.io.File (".").getCanonicalPath + sep + x
                    c.copy (belvis = c.belvis :+ file.toString)
                }
                catch
                {
                    case e: Exception =>
                        log.error (e.getMessage)
                        throw new Exception ("bad input file name")
                }
            }).
            text ("Belvis files to process")

        help ("help").text ("prints this usage text")
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
        do_exit = !args.contains ("--unittest")

        // parser.parse returns Option[C]
        parser.parse (args, IngestOptions ()) match
        {
            case Some (options) =>

                if (options.verbose) org.apache.log4j.LogManager.getLogger (getClass.getName).setLevel (org.apache.log4j.Level.INFO)

                if ("" != options.mapping)
                {
                    val begin = System.nanoTime ()

                    // create the configuration
                    val configuration = new SparkConf (false)
                    configuration.setAppName (APPLICATION_NAME)
                    if ("" != options.master)
                        configuration.setMaster (options.master)
                    if (options.options.nonEmpty)
                        options.options.map ((pair: (String, String)) => configuration.set (pair._1, pair._2))
                    if ("" != options.host)
                        configuration.set ("spark.cassandra.connection.host", options.host)

                    // get the necessary jar files to send to the cluster
                    val s1 = jarForObject (IngestOptions ())
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

                    val ingest = Ingest (session, options)
                    ingest.run ()

                    val calculate = System.nanoTime ()
                    log.info ("execution: " + (calculate - setup) / 1e9 + " seconds")
                }
                else
                    log.error ("""mapping file not specified""")

                if (do_exit)
                    sys.exit (0)

            case None =>
                if (do_exit)
                    sys.exit (1)
        }
    }
}
