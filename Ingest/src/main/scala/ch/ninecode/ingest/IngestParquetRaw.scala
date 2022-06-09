package ch.ninecode.ingest

import ch.ninecode.cim.CIMRDD
import com.datastax.spark.connector.{SomeColumns, toRDDFunctions}
import org.apache.log4j.{Level, LogManager}
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{Row, SparkSession}

import java.sql.Timestamp
import java.text.SimpleDateFormat

case class IngestParquetRaw(session: SparkSession, options: IngestOptions) extends IngestProcessor with CIMRDD
{
    if (options.verbose) LogManager.getLogger(getClass).setLevel(Level.INFO)
    implicit val spark: SparkSession = session

    def process (filename: String, job: IngestJob): Unit = {
        val synthLoadProfile: RDD[MeasuredValue] = import_parquet(job)
        synthLoadProfile.saveToCassandra(job.keyspace, "measured_value",
            SomeColumns("mrid", "type", "time", "period", "real_a", "imag_a", "units")
        )
    }
    @SuppressWarnings(Array("org.wartremover.warts.Throw"))
    def import_parquet (job: IngestJob): RDD[MeasuredValue] =
    {
        val dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ssXXX")

        def getValueAsDouble (row: Row, index: Int): Double = {
            val value = row.getAs[java.math.BigDecimal](index)
            if (value != null) {
                value.doubleValue()
            } else {
                0.0
            }
        }

        def parquetMapping (row: Row): MeasuredValue =
        {
            val has_id = row.getString(0)
            val typ = row.getString(1)
            val time = row.get(2)
            val timestamp: Time = time match {
                case timeString: String => dateFormat.parse(timeString).getTime
                case timestamp: Timestamp => timestamp.getTime
                case _ => throw new Exception("invalid timestamp format")
            }
            val real_a = getValueAsDouble(row, 8)
            val imag_a = getValueAsDouble(row, 4)
            val period = row.getInt(7)
            val unit = row.getString(3)
            (has_id, typ, timestamp ,period,real_a,imag_a,unit)
        }

        val parquetFileDF = session.read.load(job.datafiles: _*)
        parquetFileDF.rdd.map(parquetMapping)
    }

}
