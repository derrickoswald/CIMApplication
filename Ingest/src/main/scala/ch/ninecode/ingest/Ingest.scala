package ch.ninecode.ingest

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import java.util.zip.ZipInputStream

import scala.collection._

import com.datastax.spark.connector.SomeColumns
import com.datastax.spark.connector._
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path
import org.apache.hadoop.fs.permission.FsPermission
import org.apache.spark.sql.Row
import org.apache.spark.sql.SparkSession
import org.slf4j.Logger
import org.slf4j.LoggerFactory

case class Ingest (spark: SparkSession, options: IngestOptions)
{
    if (options.verbose) org.apache.log4j.LogManager.getLogger (getClass.getName).setLevel (org.apache.log4j.Level.INFO)
    val log: Logger = LoggerFactory.getLogger (getClass)

    val MeasurementTimeZone: TimeZone = TimeZone.getTimeZone (options.timezone)
    val MeasurementCalendar: Calendar = Calendar.getInstance ()
    MeasurementCalendar.setTimeZone (MeasurementTimeZone)
    val MeasurementTimestampFormat: SimpleDateFormat = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss.SSS")
    MeasurementTimestampFormat.setCalendar (MeasurementCalendar)

    val ZuluTimeZone: TimeZone = TimeZone.getTimeZone ("GMT")
    val ZuluTimeCalendar: Calendar = Calendar.getInstance ()
    ZuluTimeCalendar.setTimeZone (ZuluTimeZone)
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
     * ("mrid", "type", "time", "interval", "real_a", "imag_a", "units")
     *
     * @param reading the reading from the csv
     * @return the list of time series records
     */
    def to_timeseries (reading: Reading): IndexedSeq[(String, String, String, String, Double, Double, String)] =
    {
        // Note: reading.interval is in seconds and we need milliseconds for Cassandra

        // reading.time thinks it's in GMT but it's not
        // so use the timezone to convert it to GMT
        val timestamp = MeasurementTimestampFormat.parse (reading.time.toString)
        val half_interval = reading.interval * 1000 / 2
        def inrange (i: Int): Boolean =
        {
            val offset = (reading.interval * i) * 1000
            val date_time = new Date (timestamp.getTime + offset - half_interval)
            val measurement_time = new Date (timestamp.getTime + offset).getTime
            (measurement_time >= options.mintime) && (measurement_time < options.maxtime)
        }
        for (
            i <- 0 until 96
            if inrange (i)
        )
        yield
        {
            val offset = (reading.interval * i) * 1000
            val date_time = new Date (timestamp.getTime + offset - half_interval)
            val measurement_time = new Date (timestamp.getTime + offset)
            val time = ZuluTimestampFormat.format (measurement_time)
            (reading.mRID, "energy", time, (reading.interval * 1000).toString, 1000.0 * reading.values (i), 0.0, "Wh")
        }
    }

    // build a file system configuration, including core-site.xml
    def hdfs_configuration: Configuration =
    {
        val configuration = new Configuration ()
        if (null == configuration.getResource ("core-site.xml"))
        {
            val hadoop_conf: String = System.getenv ("HADOOP_CONF_DIR")
            if (null != hadoop_conf)
            {
                val site: Path = new Path (hadoop_conf, "core-site.xml")
                val f: File = new File (site.toString)
                if (f.exists && !f.isDirectory)
                    configuration.addResource (site)
            }
        }
        configuration
    }

    // get the file system
    def uri: URI = FileSystem.getDefaultUri (hdfs_configuration)
    // or: val uri: URI = URI.create (hdfs_configuration.get (FileSystem.FS_DEFAULT_NAME_KEY))

    def hdfs: FileSystem = FileSystem.get (uri, hdfs_configuration)

    def base_name (path: String): String =
    {
        val sep = System.getProperty ("file.separator")
        val index = path.lastIndexOf (sep)
        if (-1 != index)
            path.substring (index + 1)
        else
            path
    }

    def putFile (spark: SparkSession, path: String, data: Array[Byte], unzip: Boolean = false): Seq[String] =
    {
        var ret = Seq[String]()

        val file: Path = new Path (hdfs.getUri.toString, path)
        // write the file
        try
        {
            val parent = if (path.endsWith ("/")) file else file.getParent
            hdfs.mkdirs (parent, new FsPermission("ugoa-rwx"))
            hdfs.setPermission (parent, new FsPermission("ugoa-rwx"))

            if (0 != data.length && !path.endsWith ("/"))
            {
                if (unzip)
                {
                    val zip = new ZipInputStream (new ByteArrayInputStream (data))
                    val buffer = new Array[Byte](1024)
                    var more = true
                    do
                    {
                        val entry = zip.getNextEntry
                        if (null != entry)
                        {
                            if (entry.isDirectory)
                            {
                                val path = new Path (parent, entry.getName)
                                hdfs.mkdirs (path, new FsPermission("ugoa-rwx"))
                                hdfs.setPermission (path, new FsPermission("ugoa-rwx"))
                            }
                            else
                            {
                                val baos = new ByteArrayOutputStream ()
                                var eof = false
                                do
                                {
                                    val len = zip.read (buffer, 0, buffer.length)
                                    if (-1 == len)
                                        eof = true
                                    else
                                        baos.write (buffer, 0, len)
                                }
                                while (!eof)
                                baos.close ()
                                val f = new Path (parent, entry.getName)
                                val out = hdfs.create (f)
                                out.write (baos.toByteArray)
                                out.close ()
                                ret = ret :+ f.toString
                            }
                            zip.closeEntry ()
                        }
                        else
                            more = false
                    }
                    while (more)
                    zip.close ()
                }
                else
                {
                    val out = hdfs.create (file)
                    out.write (data)
                    out.close ()
                    ret = ret :+ file.toString
                }
            }
            else
                log.error ("""putFile could not store %d bytes for path "%s"""".format (data.length, path))
        }
        catch
        {
            case e: Exception =>
                log.error ("""putFile failed for path "%s" with unzip=%s""".format (path, unzip), e)
        }

        ret
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
        val ok = readings.filter (_._1 != null)
        ok.saveToCassandra ("cimapplication", "measured_value_by_day", SomeColumns ("mrid", "type", "time", "interval", "real_a", "imag_a", "units"))
    }

    def run (): Unit =
    {
        val begin = System.nanoTime ()

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

        val mapping_options = new mutable.HashMap[String, String] ()

        mapping_options.put ("header", header)
        mapping_options.put ("ignoreLeadingWhiteSpace", ignoreLeadingWhiteSpace)
        mapping_options.put ("ignoreTrailingWhiteSpace", ignoreTrailingWhiteSpace)
        mapping_options.put ("sep", sep)
        mapping_options.put ("quote", quote)
        mapping_options.put ("escape", escape)
        mapping_options.put ("encoding", encoding)
        mapping_options.put ("comment", comment)
        mapping_options.put ("nullValue", nullValue)
        mapping_options.put ("nanValue", nanValue)
        mapping_options.put ("positiveInf", positiveInf)
        mapping_options.put ("negativeInf", negativeInf)
        mapping_options.put ("dateFormat", dateFormat)
        mapping_options.put ("timestampFormat", timestampFormat)
        mapping_options.put ("mode", mode)
        mapping_options.put ("inferSchema", inferSchema)

        val bytes = Files.readAllBytes (Paths.get (options.mapping))
        val mapping_files = putFile (spark, "/" + base_name (options.mapping), bytes, options.mapping.toLowerCase.endsWith (".zip"))
        val filename = mapping_files.head // "hdfs://sandbox:8020/Stoerung_Messstellen2.csv"
        val dataframe = spark.sqlContext.read.format ("csv").options (mapping_options).csv (filename)

        val read = System.nanoTime ()
        log.info ("read %s: %s seconds".format (filename, (read - begin) / 1e9))

        val ch_number = dataframe.schema.fieldIndex (options.metercol)
        val nis_number = dataframe.schema.fieldIndex (options.mridcol)
        val join_table = dataframe.rdd.cache.map (row ⇒ (row.getString (ch_number), row.getString (nis_number))).filter (_._2 != null).collect.toMap

        val map = System.nanoTime ()
        log.info ("map: %s seconds".format ((map - read) / 1e9))

        for (file ← options.belvis)
        {
            try
            {
                var start = System.nanoTime ()
                val bytes = Files.readAllBytes (Paths.get (file))
                val belvis_files = putFile (spark, "/" + base_name (file), bytes, file.toLowerCase.endsWith (".zip"))
                for (filename ← belvis_files) // "hdfs://sandbox:8020/20180412_080258_Belvis_manuell_TS Amalerven.csv"
                {
                    sub (filename, join_table)
                    hdfs.delete (new Path (filename), false)
                    val end = System.nanoTime ()
                    log.info ("process %s: %s seconds".format (filename, (end - start) / 1e9))
                    start = end
                }
            }
            catch
            {
                case e: Exception =>
                    log.error ("""ingest failed for file "%s"""".format (file), e)
            }
        }
        hdfs.delete (new Path (filename), false)
    }
}
