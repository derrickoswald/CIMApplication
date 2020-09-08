package ch.ninecode.ingest

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

import scala.collection.Map
import scala.collection.Seq

import com.datastax.spark.connector.SomeColumns
import com.datastax.spark.connector._
import com.datastax.spark.connector.rdd.ReadConf
import org.apache.log4j.Level
import org.apache.log4j.LogManager
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession

case class IngestNyquist (session: SparkSession, options: IngestOptions) extends IngestProcessor
{
    if (options.verbose) LogManager.getLogger (getClass).setLevel (Level.INFO)
    implicit val spark: SparkSession = session

    def isNumber (s: String): Boolean = s forall Character.isDigit

    def process (filename: String, job: IngestJob): Unit =
    {
        val join_table = loadCsvMapping (session, filename, job)
        job.datafiles.foreach (
            file =>
                for (filename <- getFiles (job, options.workdir)(file))
                    time (s"process $filename: %s seconds")
                    {
                        sub_nyquist (filename, join_table, job)
                        cleanUp (job, filename)
                    }
        )
    }


    def sub_nyquist (filename: String, join_table: Map[String, String], job: IngestJob): Unit =
    {
        val lines: RDD[String] = session.sparkContext.textFile (filename)
        val measurementTimeZone: TimeZone = TimeZone.getTimeZone (job.timezone)
        val measurementCalendar: Calendar = Calendar.getInstance ()
        measurementCalendar.setTimeZone (measurementTimeZone)
        val nyquistDateTimeFormat: SimpleDateFormat = new SimpleDateFormat ("dd.MM.yyyy HH:mm")
        nyquistDateTimeFormat.setCalendar (measurementCalendar)
        val rdd = lines.flatMap (line_nyquist (join_table, nyquistDateTimeFormat))
        // combine real and imaginary parts
        if (job.mode == Modes.Append)
        {
            val executors = session.sparkContext.getExecutorMemoryStatus.keys.size - 1
            implicit val configuration: ReadConf =
                ReadConf
                    .fromSparkConf (session.sparkContext.getConf)
                    .copy (splitCount = Some (executors))
            val df =
                session
                    .sparkContext
                    .cassandraTable[MeasuredValue](job.keyspace, "measured_value")
                    .select ("mrid", "type", "time", "period", "real_a", "imag_a", "units")
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

    def line_nyquist (join_table: Map[String, String], nyquistDateTimeFormat: SimpleDateFormat)(line: String): Seq[MeasuredValue] =
    {
        val fields: Array[String] = line.split (";")
        val mrid = join_table.getOrElse (fields (0), null)
        if (null != mrid)
        {
            //val (typ, real, imag, units, factor) = decode_obis (fields (3), fields (5), "1.0") // TODO: don't hardcode OBIS
            val (typ, real, imag, units, factor) = if (fields (2).equals ("1.1.1.8.0.255"))
                ("energy", true, false, "Wh", 1.0)
            else
                if (fields (2).equals ("1.1.2.8.0.255"))
                    ("energy", true, false, "Wh", -1.0)
                else
                    ("", false, false, "OBIS code format error", 0.0)

            val timestamp = nyquistDateTimeFormat.parse (fields (1))
            val value = asDouble (fields (3)) * factor
            if (real)
                Seq [MeasuredValue]((mrid, typ, timestamp.getTime, 900000, value, 0.0, units))
            else
                Seq [MeasuredValue]((mrid, typ, timestamp.getTime, 900000, 0.0, value, units))
        } else
        {
            List ()
        }
    }
}
