package ch.ninecode.sim

import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

import com.datastax.spark.connector.SomeColumns
import com.datastax.spark.connector._
import org.apache.spark.sql.Row
import org.apache.spark.sql.SparkSession

import scala.collection._

case class Ingest (spark: SparkSession)
{
    val MeasurementTimeZone: TimeZone = TimeZone.getTimeZone ("Europe/Berlin")
    val MeasurementCalendar: Calendar = Calendar.getInstance ()
    MeasurementCalendar.setTimeZone (MeasurementTimeZone)
    val MeasurementTimestampFormat: SimpleDateFormat = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss.SSS")
    MeasurementTimestampFormat.setCalendar (MeasurementCalendar)

    val ZuluTimeZone: TimeZone = TimeZone.getTimeZone ("GMT")
    val ZuluTimeCalendar: Calendar = Calendar.getInstance ()
    ZuluTimeCalendar.setTimeZone (ZuluTimeZone)
    val ZuluDateFormat: SimpleDateFormat = new SimpleDateFormat ("yyyy-MM-dd")
    ZuluDateFormat.setCalendar (ZuluTimeCalendar)
    val ZuluTimestampFormat: SimpleDateFormat = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss.SSS")
    ZuluTimestampFormat.setCalendar (ZuluTimeCalendar)

    case class Reading (mRID: String, time: Timestamp, interval: Int, values: Array[Double])

    def not_all_null (row: Row): Boolean =
    {
        (for (i <- 0 until 96) yield row.isNullAt (7 + (2 * i))).exists (!_)
    }

    def to_reading (s: (String, Row)): (String, Reading) =
    {
        val time = s._2.getTimestamp (0)
        (s._1 + time.toString, Reading (s._1, time, s._2.getInt (6) * 60, (for (i <- 0 until 96) yield { if (s._2.isNullAt (7 + (2 * i))) 0.0 else s._2.getDouble (7 + (2 * i)) } ).toArray))
    }

    def sum (a: Reading, b:Reading): Reading =
    {
        Reading (a.mRID, a.time, a.interval, (for (i <- 0 until 96) yield a.values(i) + b.values(i)).toArray)
    }

    /**
     * Make tuples suitable for Cassandra:
     * ("mrid", "type", "date", "time", "interval", "real_a", "imag_a", "units")
     *
     * @param reading the reading from the csv
     * @return the list of time series records
     */
    def to_timeseries (reading: Reading): IndexedSeq[(String, String, String, String, String, Double, Double, String)] =
    {
        // reading.time thinks it's in GMT but it's not
        // so use the timezone to convert it to GMT
        val timestamp = MeasurementTimestampFormat.parse (reading.time.toString)
        for (i <- 0 until 96) yield
        {
            val offset = (reading.interval * i) * 1000
            val half_interval = reading.interval * 1000 / 2
            val date_time = new Date (timestamp.getTime + offset - half_interval)
            val date = ZuluDateFormat.format (date_time)
            val measurement_time = new Date (timestamp.getTime + offset)
            val time = ZuluTimestampFormat.format (measurement_time)
            (reading.mRID, "energy", date, time, reading.interval.toString, reading.values (i), 0.0, "kWh")
        }
    }

    def sub (filename: String, join_table: Map[String, String]): Unit =
    {
        val options = new mutable.HashMap[String,String]

        val header = "false"
        val ignoreLeadingWhiteSpace = "false"
        val ignoreTrailingWhiteSpace = "false"
        val sep = ";"
        val quote = "\""
        val escape = "\\"
        val encoding = "UTF-8"
        val comment = "#"
        val nullValue = ""
        val nanValue = "NaN"
        val positiveInf = "Inf"
        val negativeInf = "-Inf"
        val dateFormat = "yyyy-MM-dd"
        val timestampFormat = "dd.MM.yyyy HH:mm"
        val mode = "DROPMALFORMED"
        val inferSchema = "true"

        options.put ("header", header)
        options.put ("ignoreLeadingWhiteSpace", ignoreLeadingWhiteSpace)
        options.put ("ignoreTrailingWhiteSpace", ignoreTrailingWhiteSpace)
        options.put ("sep", sep)
        options.put ("quote", quote)
        options.put ("escape", escape)
        options.put ("encoding", encoding)
        options.put ("comment", comment)
        options.put ("nullValue", nullValue)
        options.put ("nanValue", nanValue)
        options.put ("positiveInf", positiveInf)
        options.put ("negativeInf", negativeInf)
        options.put ("dateFormat", dateFormat)
        options.put ("timestampFormat", timestampFormat)
        options.put ("mode", mode)
        options.put ("inferSchema", inferSchema)

        // we assume a very specific format since there is no header
        val rdd = spark.sqlContext.read.format ("csv").options (options).csv (filename).rdd
        val raw = rdd.filter (not_all_null).keyBy (row ⇒ join_table.getOrElse (row.getString (1), "")).filter (_._1 != "").map (to_reading)
        val readings = raw.reduceByKey (sum).values.flatMap (to_timeseries)
        readings.saveToCassandra ("cimapplication", "measured_value_by_day", SomeColumns ("mrid", "type", "date", "time", "interval", "real_a", "imag_a", "units"))
    }

    def main (): Unit =
    {
        val header = "true"
        val ignoreLeadingWhiteSpace = "false"
        val ignoreTrailingWhiteSpace = "false"
        val sep = ";"
        val quote = "\""
        val escape = "\\"
        val encoding = "UTF-8"
        val comment = "#"
        val nullValue = ""
        val nanValue = "NaN"
        val positiveInf = "Inf"
        val negativeInf = "-Inf"
        val dateFormat = "yyyy-MM-dd"
        val timestampFormat = "dd.MM.yyyy HH:mm"
        val mode = "PERMISSIVE"
        val inferSchema = "true"

        val options = new mutable.HashMap[String, String] ()

        options.put ("header", header)
        options.put ("ignoreLeadingWhiteSpace", ignoreLeadingWhiteSpace)
        options.put ("ignoreTrailingWhiteSpace", ignoreTrailingWhiteSpace)
        options.put ("sep", sep)
        options.put ("quote", quote)
        options.put ("escape", escape)
        options.put ("encoding", encoding)
        options.put ("comment", comment)
        options.put ("nullValue", nullValue)
        options.put ("nanValue", nanValue)
        options.put ("positiveInf", positiveInf)
        options.put ("negativeInf", negativeInf)
        options.put ("dateFormat", dateFormat)
        options.put ("timestampFormat", timestampFormat)
        options.put ("mode", mode)
        options.put ("inferSchema", inferSchema)

        val filename = "hdfs://sandbox:8020/Stoerung_Messstellen2.csv"
        val dataframe = spark.sqlContext.read.format ("csv").options (options).csv (filename)

        val ch_number = dataframe.schema.fieldIndex ("Messpunktbezeichnung")
        val nis_number = dataframe.schema.fieldIndex ("nis_number")
        val join_table = dataframe.rdd.cache.map (row ⇒ (row.getString (ch_number), row.getString (nis_number))).collect.toMap
        sub ("hdfs://sandbox:8020/20180419_111102_Belvis_manuell_Bad_Ragaz1.csv", join_table)
    }
}
