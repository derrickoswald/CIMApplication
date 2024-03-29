package ch.ninecode.ingest

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

import scala.collection.Map
import scala.collection.Seq

import org.apache.log4j.Level
import org.apache.log4j.LogManager
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession

case class IngestNyquist (session: SparkSession, options: IngestOptions) extends IngestProcessor
{
    private val NYQUIST_TIMEZONE = "GMT+1"

    if (options.verbose) LogManager.getLogger(getClass).setLevel(Level.INFO)
    implicit val spark: SparkSession = session

    def isNumber (s: String): Boolean = s forall Character.isDigit

    def process (filename: String, job: IngestJob): Unit =
    {
        val join_table = loadCsvMapping(session, filename, job)
        job.datafiles.foreach(
            file =>
                for (filename <- getFiles(job, options.workdir)(file))
                    time(s"process $filename: %s seconds")
                    {
                        sub_nyquist(filename, join_table, job)
                        cleanUp(job, filename)
                    }
        )
    }


    def sub_nyquist (filename: String, join_table: Map[String, String], job: IngestJob): Unit =
    {
        val lines: RDD[String] = session.sparkContext.textFile(filename)
        val measurementTimeZone: TimeZone = TimeZone.getTimeZone(NYQUIST_TIMEZONE)
        val measurementCalendar: Calendar = Calendar.getInstance()
        measurementCalendar.setTimeZone(measurementTimeZone)
        val nyquistDateTimeFormat: SimpleDateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm")
        nyquistDateTimeFormat.setCalendar(measurementCalendar)
        val rdd = lines.flatMap(line_nyquist(join_table, nyquistDateTimeFormat))
        store_data(session, job, rdd)
    }

    def line_nyquist (join_table: Map[String, String], nyquistDateTimeFormat: SimpleDateFormat)(line: String): Seq[MeasuredValue] =
    {
        val fields: Array[String] = line.split(";")
        join_table.get(fields(0)) match
        {
            case Some(mrid) =>
                val (typ, real, imag, units, factor) = decode_obis(fields(1), fields(5), "1.0")
                if (real || imag)
                {
                    val timestamp = nyquistDateTimeFormat.parse(fields(2))
                    val value = asDouble(fields(3)) * factor
                    if (real)
                        Seq[MeasuredValue]((mrid, typ, timestamp.getTime, 900000, value, 0.0, units))
                    else
                        Seq[MeasuredValue]((mrid, typ, timestamp.getTime, 900000, 0.0, value, units))
                } else
                {
                    List()
                }
            case None =>
                List()
        }
    }
}
