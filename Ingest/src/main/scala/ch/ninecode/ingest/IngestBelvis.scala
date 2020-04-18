package ch.ninecode.ingest

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

import org.apache.log4j.Level
import org.apache.log4j.LogManager
import org.apache.spark.sql.SparkSession

import com.datastax.spark.connector.SomeColumns
import com.datastax.spark.connector._

case class IngestBelvis (session: SparkSession, options: IngestOptions) extends IngestProcessor
{
    if (options.verbose) LogManager.getLogger (getClass).setLevel (Level.INFO)

    /**
     * Make tuples suitable for Cassandra:
     * ("mrid", "type", "time", "period", "real_a", "imag_a", "units")
     *
     * @param line one line from the BelVis file
     */
    def parse_belvis_line (join_table: Map[String, String], job: IngestJob, measurementDateTimeFormat: SimpleDateFormat)
        (line: String): Seq[MeasuredValue] =
    {
        val ONE_MINUTE_IN_MILLIS = 60000

        val fields = line.split (";")
        // eliminate blank lines at the end
        if (fields.length > 1)
        {
            val datetime = measurementDateTimeFormat.parse (fields (0))
            join_table.get (fields (1)) match
            {
                case Some (mrid) =>
                    val (typ, real, imag, units, factor) = decode_obis (fields (2), fields (3), "1.0")
                    val time = datetime.getTime
                    val period = fields (6).toInt
                    val interval = period * ONE_MINUTE_IN_MILLIS
                    val list = for
                        {
                        i <- 7 until fields.length by 2
                        reading = fields (i)
                        value = if ("" != reading) asDouble (reading) * factor else 0.0
                        slot = (i - 7) / 2
                        timestamp = time + (interval * slot)
                        if (timestamp >= job.mintime) && (timestamp <= job.maxtime)
                    }
                        yield
                            (mrid, typ, timestamp, interval, if (real) value else 0.0, if (imag) value else 0.0, units)
                    list
                case _ =>
                    List ()
            }
        }
        else
            List ()
    }

    def sub_belvis (filename: String, join_table: Map[String, String], job: IngestJob): Unit =
    {
        val measurementTimeZone: TimeZone = TimeZone.getTimeZone (job.timezone)
        val measurementCalendar: Calendar = Calendar.getInstance ()
        measurementCalendar.setTimeZone (measurementTimeZone)
        val measurementDateTimeFormat: SimpleDateFormat = new SimpleDateFormat ("dd.MM.yyyy HH:mm")
        measurementDateTimeFormat.setCalendar (measurementCalendar)

        // it's almost a CSV file
        // for daylight savings time changes, not all lines have the same number of columns
        val lines = session.sparkContext.textFile (filename)
        val rdd = lines.flatMap (parse_belvis_line (join_table, job, measurementDateTimeFormat))
        // combine real and imaginary parts
        val grouped = rdd.groupBy (x => (x._1, x._2, x._3)).values.flatMap (complex)
        grouped.saveToCassandra (job.keyspace, "measured_value", SomeColumns ("mrid", "type", "time", "period", "real_a", "imag_a", "units"))
    }

    def process (join_table: Map[String, String], job: IngestJob): Unit =
    {
        job.datafiles.foreach (
            file =>
                for (filename <- getFiles (job, options.workdir) (file))
                    time (s"process $filename: %s seconds")
                    {
                        sub_belvis (filename, join_table, job)
                        cleanUp (job, filename)
                    }
        )
    }
}
