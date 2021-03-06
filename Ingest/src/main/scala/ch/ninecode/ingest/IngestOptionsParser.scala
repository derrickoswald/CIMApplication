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
    var job: IngestJob = IngestJob()

    implicit val FormatsRead: scopt.Read[Formats.Value] = scopt.Read.reads(Formats.withName)
    implicit val ModesRead: scopt.Read[Modes.Value] = scopt.Read.reads(Modes.withName)

    def measurementTimestampFormat: SimpleDateFormat =
    {
        val zone = TimeZone.getTimeZone(job.timezone)
        val calendar = Calendar.getInstance()
        calendar.setTimeZone(zone)
        val ret = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        ret.setCalendar(calendar)
        ret
    }

    def parseTime (time: String): Long =
    {
        measurementTimestampFormat.parse(time).getTime
    }

    def formatTime (time: Long): String =
    {
        measurementTimestampFormat.format(time)
    }

    def updateJson (options: IngestOptions): IngestOptions = options.copy(ingestions = Seq(job.asJson))

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
            job = job.copy(mapping = x)
            updateJson(c)
        })
        .text(s"file name of mapping CSV or RDF [${job.mapping}] (required)")

    opt[String]("metercol")
        .action((x, c) =>
        {
            job = job.copy(metercol = x)
            updateJson(c)
        })
        .text(s"column name of meter id in mapping CSV [${job.metercol}]")

    opt[String]("mridcol")
        .action((x, c) =>
        {
            job = job.copy(mridcol = x)
            updateJson(c)
        })
        .text(s"column name of CIM mRID in mapping CSV [${job.mridcol}]")

    opt[String]("timezone")
        .action((x, c) =>
        {
            job = job.copy(timezone = x)
            updateJson(c)
        })
        .text(s"measurement time zone for measurements [${job.timezone}]")

    opt[String]("mintime")
        .action((x, c) =>
        {
            job = job.copy(mintime = parseTime(x))
            updateJson(c)
        })
        .text(s"minimum time for ingestion timespan [${formatTime(job.mintime)}]")

    opt[String]("maxtime")
        .action((x, c) =>
        {
            job = job.copy(maxtime = parseTime(x))
            updateJson(c)
        })
        .text(s"maximum time for ingestion timespan [${formatTime(job.maxtime)}]")

    opt[String]("keyspace")
        .action((x, c) =>
        {
            job = job.copy(keyspace = x)
            updateJson(c)
        })
        .text(s"target Cassandra keyspace [${job.keyspace}]")

    opt[Int]("replication")
        .action((x, c) =>
        {
            job = job.copy(replication = x)
            updateJson(c)
        })
        .text(s"keyspace replication if the Cassandra keyspace needs creation [${job.replication}]")

    opt[Formats.Value]("format")
        .action((x, c) =>
        {
            job = job.copy(format = x)
            updateJson(c)
        })
        .text(s"format of the data files, one of ${Formats.values.iterator.mkString(",")} [${job.format}]")

    opt[Modes.Value]("mode")
        .action((x, c) =>
        {
            job = job.copy(mode = x)
            updateJson(c)
        })
        .text(s"ingest mode, one of ${Modes.values.iterator.mkString(",")} [${job.mode}]")

    opt[Unit]("nocopy")
        .action((_, c) =>
        {
            job = job.copy(nocopy = true)
            updateJson(c)
        })
        .text(s"use files 'as is' without unzipping and copying to HDFS [${job.nocopy}]")

    opt[String]("aws_s3a_access_key")
        .action((x, c) =>
        {
            job = job.copy(aws_s3a_access_key = x)
            updateJson(c)
        })
        .text(s"aws access key [${job.aws_s3a_access_key}]")

    opt[String]("aws_s3a_secret_key")
        .action((x, c) =>
        {
            job = job.copy(aws_s3a_secret_key = x)
            updateJson(c)
        })
        .text(s"aws seceret key [${job.aws_s3a_secret_key}]")

    arg[String]("<ZIP> or <CSV>...")
        .optional()
        .unbounded()
        .action((x, c) =>
        {
            try
            {
                val sep = System.getProperty("file.separator")
                val file = if (x.startsWith(sep) || x.startsWith("s3a:"))
                {
                    x
                } else
                {
                    s"${new java.io.File(".").getCanonicalPath}$sep$x"
                }
                job = job.copy(datafiles = job.datafiles :+ file)
                updateJson(c)
            }
            catch
            {
                case e: Exception =>
                    val log = LoggerFactory.getLogger(getClass.getName)
                    log.error("bad input file name", e)
                    helpout = true
                    job = job.copy(datafiles = Seq())
                    updateJson(c)
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
