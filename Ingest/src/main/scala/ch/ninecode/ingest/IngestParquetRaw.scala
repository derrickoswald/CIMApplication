package ch.ninecode.ingest

import java.sql.Timestamp
import java.text.SimpleDateFormat

import com.datastax.spark.connector.SomeColumns
import com.datastax.spark.connector.toRDDFunctions
import org.apache.log4j.Level
import org.apache.log4j.LogManager
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.Row
import org.apache.spark.sql.SparkSession

import ch.ninecode.cim.CIMRDD

case class IngestParquetRaw (session: SparkSession, options: IngestOptions) extends IngestProcessor with CIMRDD
{
    if (options.verbose) LogManager.getLogger(getClass).setLevel(Level.INFO)
    implicit val spark: SparkSession = session

    def process (filename: String, job: IngestJob): Unit =
    {
        val synthLoadProfile: RDD[MeasuredValue] = import_parquet(job)
        synthLoadProfile.saveToCassandra(job.keyspace, "measured_value",
            SomeColumns("mrid", "type", "time", "period", "real_a", "imag_a", "units")
        )
    }

    def timeStringParser (timeString: String): Option[SimpleDateFormat] =
    {
        val knownDateFormats: Array[SimpleDateFormat] = Array(
            new SimpleDateFormat("yyyy-MM-dd HH:mm"),
            new SimpleDateFormat("dd.MM.yyyy HH:mm"),
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ssXXX"),
            new SimpleDateFormat("dd.MM.yyyy HH:mm:ssXXX"),
        )
        knownDateFormats.flatMap((format) =>
        {
            try
            {
                val _ = format.parse(timeString).getTime
                Option(format)
            } catch
            {
                case _: Exception => None
            }
        }).headOption
    }

    def detectTimeFormat (row: Row): SimpleDateFormat =
    {
        val time = row.getAs[Any]("time").toString
        timeStringParser(time) match
        {
            case Some(format: SimpleDateFormat) => format
            case _ => new SimpleDateFormat("yyyy-MM-dd HH:mm")
        }
    }

    @SuppressWarnings(Array("org.wartremover.warts.Throw"))
    def import_parquet (job: IngestJob): RDD[MeasuredValue] =
    {
        def parquetMapping (timeFormat: SimpleDateFormat)(row: Row): MeasuredValue =
        {
            val has_id = row.getAs[String]("mrid")
            val value_type = row.getAs[String]("type")
            val time = row.getAs[Any]("time")
            val timestamp: Time = time match
            {
                case timeString: String => timeFormat.parse(timeString).getTime
                case timestamp: Timestamp => timestamp.getTime
                case _ => throw new Exception("invalid timestamp format")
            }
            val real_a = row.getAs[Number]("real_a").doubleValue()
            val imag_a = row.getAs[Number]("imag_a").doubleValue()
            val period = row.getAs[Number]("period").intValue()
            val units = row.getAs[String]("units")
            (has_id, value_type, timestamp, period, real_a, imag_a, units)
        }

        val parquetFileDF = session.read.load(job.datafiles: _*)
        val timeFormat = detectTimeFormat(parquetFileDF.rdd.take(1).head)
        parquetFileDF.rdd.map(parquetMapping(timeFormat))
    }

}
