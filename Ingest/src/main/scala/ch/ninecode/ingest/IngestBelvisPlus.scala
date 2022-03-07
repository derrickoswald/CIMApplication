package ch.ninecode.ingest

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

import com.datastax.spark.connector.SomeColumns
import com.datastax.spark.connector._
import com.datastax.spark.connector.rdd.ReadConf
import org.apache.log4j.Level
import org.apache.log4j.LogManager
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession

case class IngestBelvisPlus (session: SparkSession, options: IngestOptions) extends IngestProcessor
{
    val NR_HEADER_LINES = 2
    if (options.verbose) LogManager.getLogger(getClass).setLevel(Level.INFO)
    implicit val spark: SparkSession = session

    /**
     * Make tuples suitable for Cassandra:
     * ("mrid", "type", "time", "period", "real_a", "imag_a", "units")
     *
     * @param line one line from the BelVis file
     */
    def parse_belvis_plus_line (
        join_table: Map[String, String],
        measurementDateTimeFormat: SimpleDateFormat,
        headerInfos: (Mrid, Units, String))
        (line: String): Seq[MeasuredValue] =
    {
        val ONE_MINUTE_IN_MILLIS = 60000
        val chNr = headerInfos._1
        val (typ, real, imag, units, factor) = decode_obis(headerInfos._3, headerInfos._2, "1.0")

        val fields = line.split(";")
        // eliminate blank lines at the end
        if (fields.length > 1)
        {

            val value = fields(1)
            join_table.get(chNr) match
            {
                case Some(mrid) =>
                    val time = measurementDateTimeFormat.parse(fields(0)).getTime
                    List((mrid, typ, time.toLong, 15 * ONE_MINUTE_IN_MILLIS, if (real) asDouble(value) * factor else 0.0, if (imag) asDouble(value) * factor else 0.0, units))
                case _ =>
                    List()
            }
        }
        else
            List()
    }

    def parseHeaderLines (headers: Array[String]): (String, String, String) =
    {
        val firstLine = headers(0).split(';')
        val chNr = firstLine(1)

        val secondLine = headers(1).split(';')
        val unit = secondLine(0)
        val obis = secondLine(1)

        (chNr, unit, obis)
    }

    def sub_belvis_plus (filename: String, join_table: Map[String, String], job: IngestJob): Unit =
    {
        val measurementTimeZone: TimeZone = TimeZone.getTimeZone(job.timezone)
        val measurementCalendar: Calendar = Calendar.getInstance()
        measurementCalendar.setTimeZone(measurementTimeZone)
        val measurementDateTimeFormat: SimpleDateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm")
        measurementDateTimeFormat.setCalendar(measurementCalendar)

        val lines = session.sparkContext.textFile(filename)
        val headers = lines.take(NR_HEADER_LINES)
        val headerLines = session.sparkContext.parallelize(headers)
        val dataLines = lines.subtract(headerLines)
        val headerInfos = parseHeaderLines(headers)
        val rdd: RDD[(Mrid, Type, Time, Period, Real_a, Imag_a, Units)] =
            dataLines.flatMap(parse_belvis_plus_line(join_table, measurementDateTimeFormat, headerInfos))
        // combine real and imaginary parts
        if (job.mode == Modes.Append)
        {
            val executors = session.sparkContext.getExecutorMemoryStatus.keys.size - 1
            implicit val configuration: ReadConf =
                ReadConf
                    .fromSparkConf(session.sparkContext.getConf)
                    .copy(splitCount = Some(executors))
            val df = session.sparkContext.cassandraTable[MeasuredValue](job.keyspace, "measured_value")
                .select("mrid", "type", "time", "period", "real_a", "imag_a", "units")
            val unioned = rdd.union(df)
            val grouped = unioned.groupBy(x => (x._1, x._2, x._3)).values.flatMap(complex)
            grouped.saveToCassandra(job.keyspace, "measured_value",
                SomeColumns("mrid", "type", "time", "period", "real_a", "imag_a", "units"))
        }
        else
        {
            val grouped = rdd.groupBy(x => (x._1, x._2, x._3)).values.flatMap(complex)
            grouped.saveToCassandra(job.keyspace, "measured_value",
                SomeColumns("mrid", "type", "time", "period", "real_a", "imag_a", "units"))
        }
    }

    def process (filename: String, job: IngestJob): Unit =
    {
        val join_table = loadCsvMapping(session, filename, job)
        job.datafiles.foreach(
            file =>
                for (filename <- getFiles(job, options.workdir)(file))
                    time(s"process $filename: %s seconds")
                    {
                        sub_belvis_plus(filename, join_table, job)
                        cleanUp(job, filename)
                    }
        )
    }
}
