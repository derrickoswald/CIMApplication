package ch.ninecode.ingest

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

import org.apache.log4j.Level
import org.apache.log4j.LogManager
import com.datastax.spark.connector._
import org.apache.spark.rdd.RDD

import org.apache.spark.sql.SparkSession
import com.datastax.spark.connector.SomeColumns


case class IngestLPEx (session: SparkSession, options: IngestOptions) extends IngestProcessor
{
    if (options.verbose) LogManager.getLogger (getClass).setLevel (Level.INFO)

    /**
     * Make tuples suitable for Cassandra:
     * ("mrid", "type", "time", "period", "real_a", "imag_a", "units")
     *
     * @param line one line from the LPEx file
     */
    def parse_lpex_line (join_table: Map[String, String], job: IngestJob, measurementDateTimeFormat: SimpleDateFormat)
        (line: String): Seq[MeasuredValue] =
    {
        val ONE_MINUTE_IN_MILLIS = 60000

        // described in GoerlitzExportImport_V131I04_FBe_DE.pdf
        val fields = line.split (";")
        // eliminate the version line and header line
        if (fields.length > 15 && fields (0) != "Datum")
        {
            val datetime = measurementDateTimeFormat.parse (fields (0) + " " + fields (1))
            join_table.get (fields (10)) match
            {
                case Some (mrid) =>
                    val (typ, real, imag, units, factor) = decode_obis (fields (11), fields (12), fields (13))
                    val time = datetime.getTime
                    val period = fields (14).toInt
                    val interval = period * ONE_MINUTE_IN_MILLIS
                    val list = for
                        {
                        i <- 15 until fields.length by 2
                        flags = fields (i + 1)
                        if flags == "W"
                        value = asDouble (fields (i)) * factor
                        slot = (i - 15) / 2
                        timestamp = time + (interval * slot)
                        if (timestamp >= job.mintime) && (timestamp <= job.maxtime)
                    }
                        yield
                            (mrid, typ, timestamp, interval, if (real) value else 0.0, if (imag) value else 0.0, units)
                    // discard all zero records
                    if (list.exists (x => x._5 != 0.0 || x._6 != 0.0))
                        list
                    else
                        List ()
                case _ =>
                    List ()
            }
        }
        else
            List ()
    }

    def sub_lpex (filename: String, join_table: Map[String, String], job: IngestJob): Unit =
    {
        val measurementTimeZone: TimeZone = TimeZone.getTimeZone (job.timezone)
        val measurementCalendar: Calendar = Calendar.getInstance ()
        measurementCalendar.setTimeZone (measurementTimeZone)
        val measurementDateTimeFormat: SimpleDateFormat = new SimpleDateFormat ("dd.MM.yy HH:mm:ss")
        measurementDateTimeFormat.setCalendar (measurementCalendar)

        // it's almost a CSV file but they screwed up and gave it a version line
        // and for daylight savings time changes, not all lines have the same number of columns
        val lines = session.sparkContext.textFile (filename)
        if (lines.first.startsWith ("LPEX V3.0"))
        {
            val rdd = lines.flatMap (parse_lpex_line (join_table, job, measurementDateTimeFormat))
            // combine real and imaginary parts
            val grouped: RDD[MeasuredValue] = rdd.groupBy (x => (x._1, x._2, x._3)).values.flatMap (complex)
            grouped.saveToCassandra (job.keyspace, "measured_value", SomeColumns ("mrid", "type", "time", "period", "real_a", "imag_a", "units"))
        }
    }

    def process (join_table: Map[String, String], job: IngestJob): Unit =
    {
        job.datafiles.foreach (
            file =>
                for (filename <- getFiles (job, options.workdir) (file))
                    time (s"process $filename: %s seconds")
                    {
                        sub_lpex (filename, join_table, job)
                        cleanUp (job, filename)
                    }
        )
    }
}
