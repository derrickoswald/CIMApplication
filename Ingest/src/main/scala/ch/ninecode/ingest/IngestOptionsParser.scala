package ch.ninecode.ingest

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

import scopt.OptionParser

class IngestOptionsParser (APPLICATION_NAME: String, APPLICATION_VERSION: String)
    extends OptionParser[IngestOptions](APPLICATION_NAME)
{
    head (APPLICATION_NAME, APPLICATION_VERSION)

    val default = new IngestOptions
    var unittest = false
    var helpout = false
    var versionout = false

    implicit val LogLevelsRead: scopt.Read[LogLevels.Value] = scopt.Read.reads (LogLevels.withName)
    implicit val FormatsRead: scopt.Read[Formats.Value] = scopt.Read.reads (Formats.withName)

    override def terminate (exitState: Either[String, Unit]): Unit =
    {
        if ((helpout || versionout) && !unittest)
            exitState match
            {
                case Left (_) => sys.exit (1)
                case Right (_) => sys.exit (0)
            }
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
        action ((_, c) => { unittest = true; c.copy (unittest = true) }).
        text ("unit testing - don't call sys.exit() [%s]".format (default.unittest))

    opt [LogLevels.Value]("log").
        action ((x, c) => c.copy (log_level = x)).
        text ("log level, one of %s [%s]".format (LogLevels.values.iterator.mkString (","), default.log_level))

    opt [Unit]("verbose").
        action ((_, c) ⇒ c.copy (verbose = true)).
        text ("emit progress messages [%s]".format (default.verbose))

    opt [String]("master").valueName ("MASTER_URL").
        action ((x, c) ⇒ c.copy (master = x)).
        text ("local[*], spark://host:port, mesos://host:port or yarn [%s]".format (default.master))

    opt [String]("host").valueName ("Cassandra").
        action ((x, c) ⇒ c.copy (host = x)).
        text ("Cassandra connection host (listen_address or seed in cassandra.yaml) [%s]".format (default.host))

    opt [Int]("port").valueName ("<port_number>").
        action ((x, c) ⇒ c.copy (port = x)).
        text ("Cassandra connection port [%s]".format (default.port))

    opt [String]("storage").
        action ((x, c) ⇒ c.copy (storage = x)).
        text ("storage level for RDD serialization [%s]".format (default.storage))

    opt [Unit]("nocopy").
        action ((_, c) ⇒ c.copy (nocopy = true)).
        text ("use files 'as is' without unzipping and copying to HDFS [%s]".format (default.nocopy))

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

    opt [String]("keyspace").
        action ((x, c) ⇒ c.copy (keyspace = x)).
        text ("target Cassandra keyspace [%s]".format (default.keyspace))

    opt [Int]("replication").
        action ((x, c) ⇒ c.copy (replication = x)).
        text ("keyspace replication if the Cassandra keyspace needs creation [%s]".format (default.replication))

    opt [Formats.Value]("format").
        action ((x, c) ⇒ c.copy (format = x)).
        text ("format of the data files, one of " + Formats.values.iterator.mkString (",") + " [%s]".format (default.format))

    arg [String]("<ZIP> or <CSV>...").optional ().unbounded ().
        action ((x, c) ⇒
        {
            try
            {
                val sep = System.getProperty ("file.separator")
                val file = if (x.startsWith (sep)) x else new java.io.File (".").getCanonicalPath + sep + x
                c.copy (datafiles = c.datafiles :+ file.toString)
            }
            catch
            {
                case e: Exception =>
                    println (e.getMessage)
                    throw new Exception ("bad input file name")
            }
        }).
        text ("data files to process")

    help ("help").
        hidden ().
        validate (Unit => { helpout = true; Right (Unit) })

    version ("version").
        validate (Unit => { versionout = true; Right (Unit) }).
            text ("Scala: %s, Spark: %s, %s: %s".format (
                APPLICATION_VERSION.split ("-")(0),
                APPLICATION_VERSION.split ("-")(1),
                APPLICATION_NAME,
                APPLICATION_VERSION.split ("-")(2)
            )
        )

    checkConfig (o => { o.valid = !(helpout || versionout); Right (Unit) })

    note (
        """
Generates CIM class files.

Reads CIM UML model files available from the CIM Users Group (https://cimug.ucaiug.org/)
in Enterprise Architect (SparX Systems https://www.sparxsystems.com/) .eap format
and creates source code class files for either Scala or Javascript.
"""
    )
}
