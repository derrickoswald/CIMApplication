package ch.ninecode.ingest

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

import org.apache.log4j.Level
import org.apache.log4j.LogManager
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import com.datastax.spark.connector.SomeColumns
import com.datastax.spark.connector._
import com.datastax.spark.connector.rdd.ReadConf

case class IngestCustom (session: SparkSession, options: IngestOptions) extends IngestProcessor
{
    if (options.verbose) LogManager.getLogger (getClass).setLevel (Level.INFO)
    implicit val spark: SparkSession = session

    def isNumber (s: String): Boolean = s forall Character.isDigit

    /**
     * Make tuples suitable for Cassandra:
     * ("mrid", "type", "time", "period", "real_a", "imag_a", "units")
     *
     * @param line one line from the data file
     */
    def line_custom (join_table: Map[String, String], measurementDateTimeFormat2: SimpleDateFormat, measurementDateTimeFormat3: SimpleDateFormat)
        (line: String): Seq[MeasuredValue] =
    {
        // LDN-Messpunkt;Einheitennummer...
        // 730154;39580894;Wirkenergie A+ 15;1-1:1.8.0*255;15;kWh;2019.08.24;24.08.2019 00:00;24.08.2019 00:15;0.038;...
        val fields: Array[String] = line.split (";")
        // eliminate the header line
        if (isNumber (fields (0)))
        {
            join_table.get (fields (0)) match
            {
                case Some (mrid) =>
                    val (typ, real, imag, units, factor) = decode_obis (fields (3), fields (5), "1.0")
                    val date = fields (6)
                    if (real || imag)
                        for (
                            index <- 7 until fields.length
                            if 0 == (index - 7) % 3;
                            start = fields (index);
                            end = fields (index + 1);
                            datetime1 = if (start.length == 8) measurementDateTimeFormat3.parse (s"$date $start") else measurementDateTimeFormat2.parse (start);
                            timestamp = if (end.length == 8) measurementDateTimeFormat3.parse (s"$date $end") else measurementDateTimeFormat2.parse (end);
                            interval = (timestamp.getTime - datetime1.getTime).toInt;
                            value = asDouble (fields (index + 2)) * factor
                        )
                            yield
                                if (real)
                                    (mrid, typ, timestamp.getTime, interval, value, 0.0, units)
                                else
                                    (mrid, typ, timestamp.getTime, interval, 0.0, value, units)
                    else
                        List ()
                case _ =>
                    List ()
            }
        }
        else
            List ()
    }


    def sub_custom (filename: String, join_table: Map[String, String], job: IngestJob): Unit =
    {
        val measurementTimeZone: TimeZone = TimeZone.getTimeZone (job.timezone)
        val measurementCalendar: Calendar = Calendar.getInstance ()
        measurementCalendar.setTimeZone (measurementTimeZone)
        val measurementDateTimeFormat2: SimpleDateFormat = new SimpleDateFormat ("dd.MM.yyyy HH:mm")
        measurementDateTimeFormat2.setCalendar (measurementCalendar)
        val measurementDateTimeFormat3: SimpleDateFormat = new SimpleDateFormat ("dd.MM.yyyy HH:mm:ss")
        measurementDateTimeFormat3.setCalendar (measurementCalendar)

        val lines: RDD[String] = session.sparkContext.textFile (filename)
        val rdd = lines.flatMap (line_custom (join_table, measurementDateTimeFormat2, measurementDateTimeFormat3))
        // combine real and imaginary parts
        if (job.mode == Modes.Append)
        {
            val executors = session.sparkContext.getExecutorMemoryStatus.keys.size - 1
            implicit val configuration: ReadConf =
                ReadConf
                    .fromSparkConf (session.sparkContext.getConf)
                    .copy (splitCount = Some (executors))
            val df = session.sparkContext.cassandraTable[MeasuredValue](job.keyspace, "measured_value").select ("mrid", "type", "time", "period", "real_a", "imag_a", "units")
            val unioned = rdd.union (df)
            val grouped = unioned.groupBy (x => (x._1, x._2, x._3)).values.flatMap (complex)
            grouped.saveToCassandra (job.keyspace, "measured_value", SomeColumns ("mrid", "type", "time", "period", "real_a", "imag_a", "units"))
        }
        else
        {
            val grouped: RDD[MeasuredValue] = rdd.groupBy (x => (x._1, x._2, x._3)).values.flatMap (complex)
            grouped.saveToCassandra (job.keyspace, "measured_value", SomeColumns ("mrid", "type", "time", "period", "real_a", "imag_a", "units"))
        }
    }

    def process (filename: String, job: IngestJob): Unit =
    {
        val join_table = loadCsvMapping (session, filename, job)
        job.datafiles.foreach (
            file =>
                for (filename <- getFiles (job, options.workdir)(file))
                    time (s"process $filename: %s seconds")
                    {
                        sub_custom (filename, join_table, job)
                        cleanUp (job, filename)
                    }
        )
    }
}
