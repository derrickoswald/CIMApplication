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
    implicit val ModesRead: scopt.Read[Modes.Value] = scopt.Read.reads (Modes.withName)

    override def terminate (exitState: Either[String, Unit]): Unit =
    {
        if ((helpout || versionout) && !unittest)
            exitState match
            {
                case Left (_) => sys.exit (1)
                case Right (_) => sys.exit (0)
            }
    }

    def MeasurementTimestampFormat (options: IngestOptions): SimpleDateFormat =
    {
        val MeasurementTimeZone: TimeZone = TimeZone.getTimeZone (options.timezone)
        val MeasurementCalendar: Calendar = Calendar.getInstance ()
        MeasurementCalendar.setTimeZone (MeasurementTimeZone)
        val ret: SimpleDateFormat = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss")
        ret.setCalendar (MeasurementCalendar)
        ret
    }

    def parseTime (options: IngestOptions, time: String): Long =
    {
        MeasurementTimestampFormat (options).parse (time).getTime
    }

    def formatTime (options: IngestOptions, time: Long): String =
    {
        MeasurementTimestampFormat (options).format (time)
    }

    opt [Unit]("unittest").
        hidden ().
        action ((_, c) =>
        {
            unittest = true;
            c.copy (unittest = true)
        }).
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

    opt [String]("workdir").
        action ((x, c) ⇒ c.copy (workdir = if (x.endsWith ("/")) x else s"$x/")).
        text ("working directory for unzip and copy [%s]".format (default.workdir))

    opt [String]("mapping").
        action ((x, c) ⇒ c.copy (mapping = x)).
        text ("file name of mapping CSV or RDF [%s] (required)".format (default.mapping))

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

    opt [Modes.Value]("mode").
        action ((x, c) ⇒ c.copy (mode = x)).
        text ("ingest mode, one of " + Modes.values.iterator.mkString (",") + " [%s]".format (default.mode))

    opt [String]("aws_s3a_access_key").
        action ((x, c) ⇒ c.copy (aws_s3a_access_key = x)).
        text ("aws access key [%s]".format (default.aws_s3a_access_key))

    opt [String]("aws_s3a_secret_key").
        action ((x, c) ⇒ c.copy (aws_s3a_secret_key = x)).
        text ("aws secret key [%s]".format (default.aws_s3a_secret_key))

    arg [String]("<ZIP> or <CSV>...").optional ().unbounded ().
        action ((x, c) ⇒
        {
            try
            {
                val sep = System.getProperty ("file.separator")
                val file = if (x.startsWith (sep) || x.startsWith ("s3a:"))
                {
                    x
                } else
                {
                    new java.io.File (".").getCanonicalPath + sep + x
                }
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
        validate (Unit =>
        {
            helpout = true;
            Right (Unit)
        })

    version ("version").
        validate (Unit =>
        {
            versionout = true;
            Right (Unit)
        }).
        text ("Scala: %s, Spark: %s, %s: %s".format (
            APPLICATION_VERSION.split ("-")(0),
            APPLICATION_VERSION.split ("-")(1),
            APPLICATION_NAME,
            APPLICATION_VERSION.split ("-")(2)
        )
        )

    checkConfig (o =>
    {
        o.valid = !(helpout || versionout);
        Right (Unit)
    })

    note (
        """
Ingests smart meter data into Cassandra.

Reads smart meter files in LPEx, BelVis or MSCONS format and matches the meter ID with a CIM mRID
based on a mapping CSV file, and inserts 'raw meter data' into the Cassandra measured_values table.
The program normally copies and unzips (based on file extension) files to HDFS (unless --nocopy is specified) and
reads the mapping file and meter data files (demultiplexed to individual readings) into Spark RDD.
These are then rearranged and augmented to match the measured_value schema and inserted into Cassandra in bulk.
Operation are performed with complex numbers per the measured_value schema,
but so far deal only with one phase (phase A in North America or phase R in the rest of the world).
Where multiple meter IDs map to one CIM mRID, the values are added together, providing the capability to
have an apartment block merged or active and reactive measurements from the same meter merged.
This merging is done per file for LPEx and BelVis, but per command line for MSCONS.
"""
    )
}
