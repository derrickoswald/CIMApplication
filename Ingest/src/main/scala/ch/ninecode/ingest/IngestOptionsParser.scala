package ch.ninecode.ingest

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

import org.slf4j.LoggerFactory

import ch.ninecode.util.CassandraOptionsParser
import ch.ninecode.util.MainOptionsParser
import ch.ninecode.util.SparkOptionsParser

/**
 * Parser for command line operation.
 */
@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
class IngestOptionsParser (options: IngestOptions)
    extends MainOptionsParser[IngestOptions](options)
    with SparkOptionsParser[IngestOptions]
    with CassandraOptionsParser[IngestOptions]
{
    implicit val FormatsRead: scopt.Read[Formats.Value] = scopt.Read.reads(Formats.withName)
    implicit val ModesRead: scopt.Read[Modes.Value] = scopt.Read.reads(Modes.withName)

    def measurementTimestampFormat (timezone: String): SimpleDateFormat =
    {
        val zone = TimeZone.getTimeZone(timezone)
        val calendar = Calendar.getInstance()
        calendar.setTimeZone(zone)
        val ret = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        ret.setCalendar(calendar)
        ret
    }

    def parseTime (time: String, timezone: String): Long =
    {
        measurementTimestampFormat(timezone).parse(time).getTime
    }

    def formatTime (time: Long, timezone: String): String =
    {
        measurementTimestampFormat(timezone).format(time)
    }

    opt[Unit]("verbose")
        .action((_, c) => c.copy(verbose = true))
        .text(s"emit progress messages [${options.verbose}]")

    opt[String]("workdir")
        .action(
            (x, c) =>
            {
                val sep = System.getProperty("file.separator")
                c.copy(workdir = if (x.endsWith(sep)) x else s"$x$sep")
            }
        )
        .text(s"working directory for unzip and copy [${options.workdir}]")

    opt[String]("mapping")
        .action((x, c) =>
        {
            val v = c.copy (ingestions = c.ingestions.map(_.copy(mapping = x)))
            v
        })
        .text(s"file name of mapping CSV or RDF [${options.ingestions.map(_.mapping).mkString(",")}] (required)")

    opt[String]("metercol")
        .action((x, c) =>
        {
            c.copy (ingestions = c.ingestions.map(_.copy(metercol = x)))
        })
        .text(s"column name of meter id in mapping CSV [${options.ingestions.map(_.metercol).mkString(",")}]")

    opt[String]("mridcol")
        .action((x, c) =>
        {
            c.copy (ingestions = c.ingestions.map(_.copy(mridcol = x)))
        })
        .text(s"column name of CIM mRID in mapping CSV [${options.ingestions.map(_.mridcol).mkString(",")}]")

    opt[String]("timezone")
        .action((x, c) =>
        {
            c.copy (ingestions = c.ingestions.map(_.copy(timezone = x)))
        })
        .text(s"measurement time zone for measurements [${options.ingestions.map(_.timezone).mkString(",")}]")

    opt[String]("mintime")
        .action((x, c) =>
        {
            c.copy (ingestions = c.ingestions.map(y => y.copy(mintime = parseTime(x, y.timezone))))
        })
        .text(s"minimum time for ingestion timespan [${options.ingestions.map(x => formatTime(x.mintime, x.timezone)).mkString(",")}]")

    opt[String]("maxtime")
        .action((x, c) =>
        {
            c.copy (ingestions = c.ingestions.map(y => y.copy(maxtime = parseTime(x, y.timezone))))
        })
        .text(s"maximum time for ingestion timespan [${options.ingestions.map(x => formatTime(x.maxtime, x.timezone)).mkString(",")}]")

    opt[String]("keyspace")
        .action((x, c) =>
        {
            c.copy (ingestions = c.ingestions.map(_.copy(keyspace = x)))
        })
        .text(s"target Cassandra keyspace [${options.ingestions.map(_.keyspace).mkString(",")}]")

    opt[Int]("replication")
        .action((x, c) =>
        {
            c.copy (ingestions = c.ingestions.map(_.copy(replication = x)))
        })
        .text(s"keyspace replication if the Cassandra keyspace needs creation [${options.ingestions.map(_.replication.toString).mkString(",")}]")

    opt[Formats.Value]("format")
        .action((x, c) =>
        {
            c.copy (ingestions = c.ingestions.map(_.copy(format = x)))
        })
        .text(s"format of the data files, one of ${Formats.values.iterator.mkString(",")} [${options.ingestions.map(_.format.toString).mkString(",")}]")

    opt[Modes.Value]("mode")
        .action((x, c) =>
        {
            c.copy (ingestions = c.ingestions.map(_.copy(mode = x)))
        })
        .text(s"ingest mode, one of ${Modes.values.iterator.mkString(",")} [${options.ingestions.map(_.mode.toString).mkString(",")}]")

    opt[Unit]("nocopy")
        .action((_, c) =>
        {
            c.copy (ingestions = c.ingestions.map(_.copy(nocopy = true)))
        })
        .text(s"use files 'as is' without unzipping and copying to HDFS [${options.ingestions.map(_.nocopy.toString).mkString(",")}]")

    opt[String]("aws_s3a_access_key")
        .action((x, c) =>
        {
            c.copy (ingestions = c.ingestions.map(_.copy(aws_s3a_access_key = x)))
        })
        .text(s"aws access key [${options.ingestions.map(_.aws_s3a_access_key).mkString(",")}]")

    opt[String]("aws_s3a_secret_key")
        .action((x, c) =>
        {
            c.copy (ingestions = c.ingestions.map(_.copy(aws_s3a_secret_key = x)))
        })
        .text(s"aws seceret key [${options.ingestions.map(_.aws_s3a_secret_key).mkString(",")}]")

    arg[String]("<ZIP> or <CSV>...")
        .optional()
        .unbounded()
        .action((x, c) =>
        {
            try
            {
                val sep = System.getProperty("file.separator")
                val file = if (x.startsWith(sep) || x.startsWith("s3a:"))
                    x
                else
                    s"${new java.io.File(".").getCanonicalPath}$sep$x"
                c.copy (ingestions = c.ingestions.map(x => x.copy(datafiles = x.datafiles :+ file)))
            }
            catch
            {
                case e: Exception =>
                    val log = LoggerFactory.getLogger(getClass.getName)
                    log.error("bad input file name", e)
                    helpout = true
                    c.copy (ingestions = c.ingestions.map(x => x.copy(datafiles = Seq())))
            }
        })
        .text("data files to process")

    note(
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
