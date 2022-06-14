package ch.ninecode.ingest

import ch.ninecode.util.HDFS

import java.util.regex.Pattern
import com.datastax.spark.connector._
import com.datastax.spark.connector.rdd.ReadConf
import org.apache.hadoop.fs.Path
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.Row
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.types.DataType
import org.slf4j.Logger
import org.slf4j.LoggerFactory

trait IngestProcessor extends HDFS
{
    type AOID = String
    type MstID = String
    type Mrid = String
    type Type = String
    type Time = Long
    type Period = Int
    type Real_a = Double
    type Imag_a = Double
    type Units = String
    type MeasuredValue = (Mrid, Type, Time, Period, Real_a, Imag_a, Units)

    lazy val log: Logger = LoggerFactory.getLogger(getClass)
    lazy val obis: Pattern = java.util.regex.Pattern.compile("""^((\d+)-)*((\d+):)*(\d+)\.(\d+)(\.(\d+))*(\*(\d+))*$""")

    def map_csv_options: Map[String, String] =
    {
        Map[String, String](
            "header" -> "true",
            "ignoreLeadingWhiteSpace" -> "false",
            "ignoreTrailingWhiteSpace" -> "false",
            "sep" -> ";",
            "quote" -> "\"",
            "escape" -> "\\",
            "encoding" -> "UTF-8",
            "comment" -> "#",
            "nullValue" -> "",
            "nanValue" -> "NaN",
            "positiveInf" -> "Inf",
            "negativeInf" -> "-Inf",
            "dateFormat" -> "yyyy-MM-dd",
            "timestampFormat" -> "dd.MM.yyyy HH:mm",
            "mode" -> "PERMISSIVE",
            "inferSchema" -> "true")
    }

    def time[R] (template: String)(block: => R): R =
    {
        val t0 = System.nanoTime()
        val ret = block
        val t1 = System.nanoTime()
        LoggerFactory.getLogger(getClass).info(template.format((t1 - t0) / 1e9), None)
        ret
    }

    def getFiles (job: IngestJob, workdir: String)(file: String): Seq[String] =
    {
        if (job.nocopy)
            Seq(file)
        else
            time(s"copy $file: %s seconds")
            {
                val name = base_name(file)
                putFile(s"${workdir}$name", file, file.toLowerCase.endsWith(".zip"))
            }
    }


    def cleanUp (job: IngestJob, filename: String): Unit =
    {
        if (!job.nocopy)
        {
            val _ = hdfs.delete(new Path(filename), false)
        }
    }

    /**
     * Decode an OBIS code into actionable values.
     *
     * @param code  the OBIS code to deconstruct
     * @param units the original units provided for the values
     * @param scale the original scaling factor for the values
     * @return (type: e.g. energy or power, real: true if active, imag: true if reactive, units: e.g. Wh, factor: to multiply the values by)
     */
    def decode_obis (code: String, units: String, scale: String): (String, Boolean, Boolean, String, Double) =
    {
        val matcher = obis.matcher(code)
        if (matcher.find)
        {
            if (1 == matcher.group(2).toInt)
            {
                val quantity = matcher.group(5).toInt
                val what = matcher.group(6).toInt
                val (typ, real, imag, factor, unit) = quantity match
                {
                    // active power p+
                    case 1 => ("power", true, false, 1.0, "W")
                    // active power p-
                    case 2 => ("power", true, false, -1.0, "W")
                    // reactive power q+
                    case 3 => ("power", false, true, 1.0, "W")
                    // reactive power q-
                    case 4 => ("power", false, true, -1.0, "W")
                    // reactive power Q I
                    case 5 => ("power", false, true, 1.0, "W")
                    // reactive power Q II
                    case 6 => ("power", false, true, 1.0, "W")
                    // reactive power Q III
                    case 7 => ("power", false, true, -1.0, "W")
                    // reactive power Q IV
                    case 8 => ("power", false, true, -1.0, "W")
                    // error
                    case _ => ("", false, false, 0.0, "")
                }
                if (factor != 0.0)
                {
                    val (_type, _unit) = what match
                    {
                        // last average
                        case 5 => (typ, unit)
                        // time integral 1
                        case 8 => ("energy", "Wh")
                        // time integral
                        case 29 => ("energy", "Wh")
                    }
                    val _factor = units match
                    {
                        case "kWh" => factor * 1000.0 * scale.toDouble;
                        case "kvarh" => factor * 1000.0 * scale.toDouble;
                        case "kVArh" => factor * 1000.0 * scale.toDouble;
                        case _ => factor
                    }
                    (_type, real, imag, _unit, _factor)
                }
                else
                    ("", false, false, s"OBIS code '$code' has unrecognized quantity type $quantity", 0.0)
            }
            else
                ("", false, false, s"'$code' is not an electric OBIS code", 0.0)
        }
        else
            ("", false, false, s"'$code' has an OBIS code format error", 0.0)
    }

    def complex (measurements: Iterable[MeasuredValue]): Option[MeasuredValue] =
    {
        measurements.headOption match
        {
            case Some((mrid, typ, time, period, _, _, units)) =>
                Some((mrid, typ, time, period, measurements.map(_._5).sum, measurements.map(_._6).sum, units))
            case _ =>
                None
        }
    }

    def asDouble (s: String): Double =
        try
        {
            s.toDouble
        }
        catch
        {
            case _: Throwable => 0.0
        }

    def extractor (datatype: DataType): (Row, Int) => String =
    {
        datatype.simpleString match
        {
            case "decimal" | "double" | "float" =>
                (row: Row, column: Int) => row.getDouble(column).toString
            case "string" =>
                (row: Row, column: Int) => row.getString(column)
            case "integer" | "int" | "short" | "smallint" =>
                (row: Row, column: Int) => row.getInt(column).toString
            case "long" =>
                (row: Row, column: Int) => row.getLong(column).toString
            case _ =>
                (_: Row, _: Int) => s"unsupported datatype ${datatype.toString}"
        }
    }

    def loadCsvMapping (session: SparkSession, filename: String, job: IngestJob): Map[String, String] =
    {
        val dataframe = time(s"read $filename: %s seconds")
        {
            session.sqlContext.read.format("csv").options(map_csv_options).csv(filename)
        }
        val join_table = time("map: %s seconds")
        {
            val ch_number = dataframe.schema.fieldIndex(job.metercol)
            val nis_number = dataframe.schema.fieldIndex(job.mridcol)
            val extract = extractor(dataframe.schema.fields(ch_number).dataType)
            dataframe.rdd.map(row => (extract(row, ch_number), row.getString(nis_number))).filter(_._2 != null).collect.toMap
        }
        join_table
    }

    def get_existing_data_from_database (session: SparkSession, job: IngestJob): RDD[MeasuredValue] =
    {
        val executors = session.sparkContext.getExecutorMemoryStatus.keys.size - 1
        implicit val configuration: ReadConf =
            ReadConf
                .fromSparkConf(session.sparkContext.getConf)
                .copy(splitCount = Some(executors))
        val df = session.sparkContext.cassandraTable[MeasuredValue](job.keyspace, "measured_value")
            .select("mrid", "type", "time", "period", "real_a", "imag_a", "units")
        df
    }

    def store_data (session: SparkSession, job: IngestJob, rdd: RDD[MeasuredValue]): Unit =
    {
        val data = if (job.mode == Modes.Append)
        {
            val existingData: RDD[MeasuredValue] = get_existing_data_from_database(session, job)
            rdd.union(existingData)
        }
        else
        {
            rdd
        }
        save_to_cassandra(job, data)
    }

    def save_to_cassandra (job: IngestJob, data: RDD[MeasuredValue]): Unit =
    {
        val grouped = data.groupBy(x => (x._1, x._2, x._3)).values.flatMap(complex)
        grouped.saveToCassandra(job.keyspace, "measured_value", SomeColumns("mrid", "type", "time", "period", "real_a", "imag_a", "units"))
    }

    def process (filename: String, job: IngestJob): Unit
}
