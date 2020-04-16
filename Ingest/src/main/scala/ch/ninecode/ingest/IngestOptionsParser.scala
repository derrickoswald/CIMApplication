package ch.ninecode.ingest

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

import scopt.OptionParser

/**
 * Parser for command line operation.
 *
 * @param APPLICATION_NAME the name of the program
 * @param APPLICATION_VERSION the version of the program.
 */
@SuppressWarnings (Array ("org.wartremover.warts.NonUnitStatements"))
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

    def measurementTimestampFormat (options: IngestOptions): SimpleDateFormat =
    {
        val zone = TimeZone.getTimeZone (options.timezone)
        val calendar = Calendar.getInstance ()
        calendar.setTimeZone (zone)
        val ret = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss")
        ret.setCalendar (calendar)
        ret
    }

    def parseTime (options: IngestOptions, time: String): Long =
    {
        measurementTimestampFormat (options).parse (time).getTime
    }

    def formatTime (options: IngestOptions, time: Long): String =
    {
        measurementTimestampFormat (options).format (time)
    }

    opt[Unit]("unittest")
        .hidden ()
        .action ((_, c) => { unittest = true; c.copy (unittest = true) })
        .text (s"unit testing - don't call sys.exit() [${default.unittest}]")

    opt[LogLevels.Value]("log")
        .action ((x, c) => c.copy (log_level = x))
        .text (s"log level, one of ${LogLevels.values.mkString (",")} [${default.log_level}]")

    opt[Unit]("verbose")
        .action ((_, c) => c.copy (verbose = true))
        .text (s"emit progress messages [${default.verbose}]")

    opt[String]("master").valueName ("MASTER_URL")
        .action ((x, c) => c.copy (master = x))
        .text (s"local[*], spark://host:port, mesos://host:port or yarn [${default.master}]")

    opt[String]("host").valueName ("Cassandra")
        .action ((x, c) => c.copy (host = x))
        .text (s"Cassandra connection host (listen_address or seed in cassandra.yaml) [${default.host}]")

    opt[Int]("port").valueName ("<port_number>")
        .action ((x, c) => c.copy (port = x))
        .text (s"Cassandra connection port [${default.port}]")

    opt[String]("storage")
        .action ((x, c) => c.copy (storage = x))
        .text (s"storage level for RDD serialization [${default.storage}]")

    opt[Unit]("nocopy")
        .action ((_, c) => c.copy (nocopy = true))
        .text (s"use files 'as is' without unzipping and copying to HDFS [${default.nocopy}]")

    opt[String]("workdir")
        .action ((x, c) => c.copy (workdir = if (x.endsWith ("/")) x else s"$x/"))
        .text (s"working directory for unzip and copy [${default.workdir}]")

    opt[String]("mapping")
        .action ((x, c) => c.copy (mapping = x))
        .text (s"file name of mapping CSV [${default.mapping}] (required)")

    opt[String]("metercol")
        .action ((x, c) => c.copy (metercol = x))
        .text (s"column name of meter id in mapping CSV [${default.metercol}]")

    opt[String]("mridcol")
        .action ((x, c) => c.copy (mridcol = x))
        .text (s"col.umn name of CIM mRID in mapping CSV [${default.mridcol}]")

    opt[String]("timezone")
        .action ((x, c) => c.copy (timezone = x))
        .text (s"measurement time zone for measurements [${default.timezone}]")

    opt[String]("mintime")
        .action ((x, c) => c.copy (mintime = parseTime (c, x)))
        .text (s"minimum time for ingestion timespan [${formatTime (default, default.mintime)}]")

    opt[String]("maxtime")
        .action ((x, c) => c.copy (maxtime = parseTime (c, x)))
        .text (s"maximum time for ingestion timespan [${formatTime (default, default.maxtime)}]")

    opt[String]("keyspace")
        .action ((x, c) => c.copy (keyspace = x))
        .text (s"target Cassandra keyspace [${default.keyspace}]")

    opt[Int]("replication")
        .action ((x, c) => c.copy (replication = x))
        .text (s"keyspace replication if the Cassandra keyspace needs creation [${default.replication}]")

    opt[Formats.Value]("format")
        .action ((x, c) => c.copy (format = x))
        .text (s"format of the data files, one of ${Formats.values.iterator.mkString (",")} [${default.format}]")

    arg[String]("<ZIP> or <CSV>...")
        .optional ()
        .unbounded ()
        .action ((x, c) =>
        {
            try
            {
                val sep = System.getProperty ("file.separator")
                val file = if (x.startsWith (sep)) x else new java.io.File (".").getCanonicalPath + sep + x
                c.copy (datafiles = c.datafiles :+ file)
            }
            catch
            {
                case e: Exception =>
                    val log = org.apache.log4j.LogManager.getLogger (getClass.getName)
                    log.error ("bad input file name", e)
                    helpout = true
                    c.copy (datafiles = Seq ())
            }
        })
        .text ("data files to process")

    help ("help")
        .hidden ()
        .validate (Unit => { helpout = true; Right (Unit) })

    version ("version")
        .validate (Unit => { versionout = true; Right (Unit) })
        .text (
            {
                val version = APPLICATION_VERSION.split ("-")
                s"Scala: ${version(0)}, Spark: ${version(1)}, $APPLICATION_NAME: ${version(2)}"
            }
        )

    checkConfig (o => { o.valid = !(helpout || versionout); Right (Unit) })

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
