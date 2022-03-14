package ch.ninecode.ingest

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

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

    def sub_belvis_plus
    (
        filename: String,
        join_table: Map[String, String],
        job: IngestJob
    ): RDD[(Mrid, Type, Time, Period, Real_a, Imag_a, Units)] =
    {
        val measurementTimeZone: TimeZone = TimeZone.getTimeZone(job.timezone)
        val measurementCalendar: Calendar = Calendar.getInstance()
        measurementCalendar.setTimeZone(measurementTimeZone)
        val measurementDateTimeFormat: SimpleDateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm")
        measurementDateTimeFormat.setCalendar(measurementCalendar)

        val combinedFiles: RDD[(String, String)] = session.sparkContext.wholeTextFiles(filename)
        val readData: RDD[(Mrid, Type, Time, Period, Real_a, Imag_a, Units)] = combinedFiles.flatMap(fileData =>
        {
            val fileContent = fileData._2
            val singeFile: Array[String] = fileContent.split("Date").drop(1).map(x => "Date" + x)
            singeFile.flatMap(fileString =>
            {
                val fileLines = fileString.split("\n")
                val headers = fileLines.take(NR_HEADER_LINES)
                val dataLines = fileLines.drop(NR_HEADER_LINES)
                val headerInfos = parseHeaderLines(headers)
                val rdd = dataLines.flatMap(parse_belvis_plus_line(join_table, measurementDateTimeFormat, headerInfos))
                rdd
            })
        })
        readData
    }

    @SuppressWarnings(Array("org.wartremover.warts.All"))
    def process (filename: String, job: IngestJob): Unit =
    {
        val join_table = loadCsvMapping(session, filename, job)
        val existingData: RDD[MeasuredValue] = get_existing_data_from_database(session, job)

        val files: Seq[String] = job.datafiles.flatMap(file =>
        {
            getFiles(job, options.workdir)(file)
        })

        val dataframes: Seq[RDD[MeasuredValue]] = files.map(filename =>
        {
            sub_belvis_plus(filename, join_table, job)
        })

        val union = time(s"process union: %s seconds")
        {
            if (job.mode == Modes.Append && !existingData.isEmpty())
            {
                spark.sparkContext.union(dataframes).union(existingData)
            } else
            {
                spark.sparkContext.union(dataframes)
            }
        }

        save_to_cassandra(job, union)

        files.foreach(filename =>
        {
            cleanUp(job, filename)
        })
    }
}
