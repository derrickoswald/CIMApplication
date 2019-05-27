package ch.ninecode.ingest

import java.io.ByteArrayInputStream
import java.io.BufferedWriter
import java.io.FileOutputStream
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import java.util.regex.Pattern
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

/**
 * Import measured data into Cassandra.
 *
 * Copies files to HDFS, reads them into Spark, executes a join across a CH### to mRID mapping table and stores them in Cassandra.
 *
 * @param session The Spark session to use.
 * @param options Options regarding Cassandra master, files to process etc.
 */
case class Ingest (session: SparkSession, options: IngestOptions)
{
    if (options.verbose) org.apache.log4j.LogManager.getLogger (getClass.getName).setLevel (org.apache.log4j.Level.INFO)
    val log: Logger = LoggerFactory.getLogger (getClass)

    val MeasurementTimeZone: TimeZone = TimeZone.getTimeZone (options.timezone)
    val MeasurementCalendar: Calendar = Calendar.getInstance ()
    MeasurementCalendar.setTimeZone (MeasurementTimeZone)
    val MeasurementTimestampFormat: SimpleDateFormat = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss.SSS")
    MeasurementTimestampFormat.setCalendar (MeasurementCalendar)
    val MeasurementDateTimeFormat: SimpleDateFormat = new SimpleDateFormat ("dd.MM.yy HH:mm:ss")
    MeasurementDateTimeFormat.setCalendar (MeasurementCalendar)

    val ZuluTimeZone: TimeZone = TimeZone.getTimeZone ("GMT")
    val ZuluTimeCalendar: Calendar = Calendar.getInstance ()
    ZuluTimeCalendar.setTimeZone (ZuluTimeZone)
    val ZuluTimestampFormat: SimpleDateFormat = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss.SSS")
    ZuluTimestampFormat.setCalendar (ZuluTimeCalendar)

    case class Reading (mRID: String, time: Timestamp, period: Int, values: Array[Double])

    //    def dumpHeap (): Unit =
    //    {
    //        import java.lang.management.ManagementFactory
    //        import java.lang.management.MemoryType
    //        import scala.collection.JavaConversions._
    //        // System.gc()
    //        for (mpBean ← ManagementFactory.getMemoryPoolMXBeans)
    //        {
    //            if (mpBean.getType eq MemoryType.HEAP)
    //            {
    //                val usage = mpBean.getUsage
    //                log.info ("""  %s: %s%% (%s/%s)""".format (mpBean.getName, (1000.0 * usage.getUsed / usage.getMax / 10.0).asInstanceOf[Int], usage.getUsed, usage.getMax))
    //            }
    //        }
    //    }

    def map_csv_options: mutable.HashMap[String, String] =
    {
        val mapping_options = new mutable.HashMap[String, String]()

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

        mapping_options
    }

    def not_all_null (row: Row): Boolean =
    {
        (for (i <- 0 until 96) yield row.isNullAt (7 + (2 * i))).exists (!_)
    }

    def to_reading (s: (String, Row)): (String, Reading) =
    {
        val time = s._2.getTimestamp (0)
        (s._1 + time.toString, Reading (s._1, time, s._2.getInt (6) * 60, (for (i <- 0 until 96) yield
            {
                if (s._2.isNullAt (7 + (2 * i))) 0.0 else s._2.getDouble (7 + (2 * i))
            }).toArray))
    }

    def sum (a: Reading, b: Reading): Reading =
    {
        Reading (a.mRID, a.time, a.period, (for (i <- 0 until 96) yield a.values (i) + b.values (i)).toArray)
    }

    /**
     * Make tuples suitable for Cassandra:
     * ("mrid", "type", "time", "period", "real_a", "imag_a", "units")
     *
     * @param reading the reading from the csv
     * @return the list of time series records
     */
    def to_timeseries (reading: Reading): IndexedSeq[(String, String, String, String, Double, Double, String)] =
    {
        // Note: reading.period is in seconds and we need milliseconds for Cassandra

        // reading.time thinks it's in GMT but it's not
        // so use the timezone to convert it to GMT
        val timestamp = MeasurementTimestampFormat.parse (reading.time.toString)

        def inrange (i: Int): Boolean =
        {
            val offset = (reading.period * i) * 1000
            val measurement_time = new Date (timestamp.getTime + offset).getTime
            (measurement_time >= options.mintime) && (measurement_time < options.maxtime)
        }

        for (
            i <- 0 until 96
            if inrange (i)
        )
            yield
                {
                    val offset = (reading.period * i) * 1000
                    val measurement_time = new Date (timestamp.getTime + offset)
                    val time = ZuluTimestampFormat.format (measurement_time)
                    (reading.mRID, "energy", time, (reading.period * 1000).toString, 1000.0 * reading.values (i), 0.0, "Wh")
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

    def readFile (file: String): Array[Byte] =
    {
        try
            Files.readAllBytes (Paths.get (file))
        catch
        {
            case e: Exception =>
                log.error ("""ingest failed for file "%s"""".format (file), e)
                Array ()
        }
    }

    def putFile (spark: SparkSession, path: String, data: Array[Byte], unzip: Boolean = false): Seq[String] =
    {
        var ret = Seq [String]()

        val file = new Path (hdfs.getUri.toString, path)
        // write the file
        try
        {
            val parent = if (path.endsWith ("/")) file else file.getParent
            hdfs.mkdirs (parent, new FsPermission ("ugoa-rwx"))
            if (!parent.isRoot)
                hdfs.setPermission (parent, new FsPermission ("ugoa-rwx"))

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
                                hdfs.mkdirs (path, new FsPermission ("ugoa-rwx"))
                                hdfs.setPermission (path, new FsPermission ("ugoa-rwx"))
                            }
                            else
                            {
                                val tmp = File.createTempFile ("ingest", null, null)
                                val stream = new FileOutputStream (tmp)
                                var eof = false
                                do
                                {
                                    val len = zip.read (buffer, 0, buffer.length)
                                    if (-1 == len)
                                        eof = true
                                    else
                                        stream.write (buffer, 0, len)
                                }
                                while (!eof)
                                stream.close ()
                                val f = new Path (parent, entry.getName)
                                hdfs.copyFromLocalFile (true, true, new Path (tmp.getAbsolutePath), f)
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

    def sub_belvis (filename: String, measurement_options: Map[String, String], join_table: Map[String, String]): Unit =
    {
        // we assume a very specific format since there is no header
        val df = session.sqlContext.read.format ("csv").options (measurement_options).csv (filename)
        val rdd = df.rdd
        val raw = rdd.filter (not_all_null).keyBy (row ⇒ join_table.getOrElse (row.getString (1), "")).filter (_._1 != "").map (to_reading)
        val readings = raw.reduceByKey (sum).values.flatMap (to_timeseries)
        val ok = readings.filter (_._1 != null)
        ok.saveToCassandra (options.keyspace, "measured_value", SomeColumns ("mrid", "type", "time", "period", "real_a", "imag_a", "units"))
        df.unpersist (false)
        rdd.unpersist (false)
        raw.unpersist (false)
        readings.unpersist (false)
        ok.unpersist (false)
    }

    def process_belvis (join_table: Map[String, String])(file: String): Unit =
    {
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

        val measurement_csv_options = immutable.HashMap (
            "header" → header,
            "ignoreLeadingWhiteSpace" → ignoreLeadingWhiteSpace,
            "ignoreTrailingWhiteSpace" → ignoreTrailingWhiteSpace,
            "sep" → sep,
            "quote" → quote,
            "escape" → escape,
            "encoding" → encoding,
            "comment" → comment,
            "nullValue" → nullValue,
            "nanValue" → nanValue,
            "positiveInf" → positiveInf,
            "negativeInf" → negativeInf,
            "dateFormat" → dateFormat,
            "timestampFormat" → timestampFormat,
            "mode" → mode,
            "inferSchema" → inferSchema
        )

        val belvis_files =
        {
            val start = System.nanoTime ()
            val files = putFile (session, "/" + base_name (file), readFile (file), file.toLowerCase.endsWith (".zip"))
            val end = System.nanoTime ()
            log.info ("copy %s: %s seconds".format (new File (file).getName, (end - start) / 1e9))
            files
        }
        // dumpHeap ()
        for (filename ← belvis_files) // e.g. "hdfs://sandbox:8020/20180412_080258_Belvis_manuell_TS Amalerven.csv"
        {
            val start = System.nanoTime ()
            sub_belvis (filename, measurement_csv_options, join_table)
            hdfs.delete (new Path (filename), false)
            val end = System.nanoTime ()
            log.info ("process %s: %s seconds".format (filename, (end - start) / 1e9))
        }
        // dumpHeap ()
    }

    val obis: Pattern = java.util.regex.Pattern.compile ("""^((\d+)-)*((\d+):)*(\d+)\.(\d+)(\.(\d+))*(\*(\d+))*$""")

    /**
     * Decode an OBIS code into actionable values.
     *
     * @param code the OBIS code to deconstruct
     * @param units the original units provided for the values
     * @param factor the original scaling factor for the values
     * @return (type: e.g. energy or power, real: true if active, imag: true if reactive, units: e.g. Wh, factor: to multiply the values by)
     */
    def decode_obis (code: String, units: String, factor: String): (String, Boolean, Boolean, String, Double) =
    {
        val matcher = obis.matcher (code)
        if (matcher.find)
        {
            if (1 == matcher.group (2).toInt)
            {
                val channel = matcher.group (4).toInt
                val quantity = matcher.group (5).toInt
                val what = matcher.group (6).toInt
                var (typ, real, imag, factor, unit) = quantity match
                {
                    // active power +
                    case 1 ⇒ ("power", true, false, 1.0, "W")
                    // active power -
                    case 2 ⇒ ("power", true, false, -1.0, "W")
                    // reactive power Q I
                    case 5 ⇒ ("power", false, true, 1.0, "W")
                    // reactive power Q IV
                    case 8 ⇒ ("power", false, true, -1.0, "W")
                    // error
                    case _ ⇒ ("", false, false, 0.0, "")
                }
                if (factor != 0.0)
                {
                    what match
                    {
                        // last average
                        case 5 ⇒
                        // time integral
                        case 29 ⇒ typ = "energy"; unit = "Wh"
                    }
                    units match
                    {
                        case "kWh" ⇒ factor = factor * 1000.0;
                        case "kvarh" ⇒ factor = factor * 1000.0;
                        case _ ⇒
                    }
                    (typ, real, imag, unit, factor)
                }
                else
                    ("", false, false, "OBIS code '%s' has unrecognized quantity type %s".format (code, quantity), 0.0)
            }
            else
                ("", false, false, "'%s' is not an electric OBIS code".format (code), 0.0)
        }
        else
            ("", false, false, "'%s' has an OBIS code format error".format (code), 0.0)
    }

    /**
     * Make tuples suitable for Cassandra:
     * ("mrid", "type", "time", "period", "real_a", "imag_a", "units")
     * @param line one line from the PEx file
     */
    def to_tuples (join_table: Map[String, String]) (line: String): Seq[(String, String, Long, Int, Double, Double, String)] =
    {
        val ONE_MINUTE_IN_MILLIS = 60000

        // described in GoerlitzExportImport_V131I04_FBe_DE.pdf
        val fields = line.split (";")
        // eliminate the version line and header line
        if (fields.length > 15 && fields(0) != "Datum")
        {
            val datetime = MeasurementDateTimeFormat.parse (fields(0) + " " + fields(1))
            val mrid = join_table.getOrElse (fields(10), null)
            if (null != mrid)
            {
                val (typ, real, imag, units, factor) = decode_obis (fields(11), fields(12), fields(13))
                val time = datetime.getTime
                val period = fields(14).toInt
                val interval = period * ONE_MINUTE_IN_MILLIS
                val list = for {
                        i ← 15 until fields.length by 2
                        flags = fields(i + 1)
                        if flags == "W"
                        value = fields(i).toDouble * factor
                        slot = (i - 15) / 2
                        timestamp = time + (interval * slot)
                    }
                    yield
                        (mrid, typ, timestamp, interval, if (real) value else 0.0, if (imag) value else 0.0, units)
                // discard all zero records
                if (list.exists (x ⇒ x._5 != 0.0 || x._6 != 0.0))
                    list
                else
                    List ()
            }
            else
                List ()
        }
        else
            List ()
    }

    def complex (measurements: Iterable[(String, String, Long, Int, Double, Double, String)]) : (String, String, Long, Int, Double, Double, String) =
    {
        val a = measurements.head
        (a._1, a._2, a._3, a._4, measurements.map (_._5).sum, measurements.map (_._6).sum, a._7)
    }

    def sub_lpex (filename: String, join_table: Map[String, String]): Unit =
    {
        // it's almost a CSV file but they screwed up and gave it a version line
        // and for daylight savings time changes, not all lines have the same number of columns
        val lines = session.sparkContext.textFile (filename)
        if (lines.first.startsWith ("LPEX V3.0"))
        {
            val rdd = lines.flatMap (to_tuples (join_table))
            // combine real and imaginary parts
            val grouped = rdd.groupBy (x ⇒ (x._1, x._2, x._3)).values.map (complex)
            grouped.saveToCassandra (options.keyspace, "measured_value", SomeColumns ("mrid", "type", "time", "period", "real_a", "imag_a", "units"))
        }
    }

    def process_lpex (join_table: Map[String, String])(file: String): Unit =
    {
        val lpex_files =
        {
            val start = System.nanoTime ()
            val files = putFile (session, "/" + base_name (file), readFile (file), file.toLowerCase.endsWith (".zip"))
            val end = System.nanoTime ()
            log.info ("copy %s: %s seconds".format (new File (file).getName, (end - start) / 1e9))
            files
        }
        for (filename ← lpex_files) // e.g. "hdfs://sandbox:8020/2000004515773_Lastprofil_Fremdgeräte_EDM_20190304031000.txt"
        {
            val start = System.nanoTime ()
            sub_lpex (filename, join_table)
            hdfs.delete (new Path (filename), false)
            val end = System.nanoTime ()
            log.info ("process %s: %s seconds".format (filename, (end - start) / 1e9))
        }
    }

    def run (): Unit =
    {
        val begin = System.nanoTime ()

        val schema = Schema (session, options.keyspace, options.replication, true)
        if (schema.make)
        {

            val mapping_files = putFile (session, "/" + base_name (options.mapping), readFile (options.mapping), options.mapping.toLowerCase.endsWith (".zip"))
            if (mapping_files.nonEmpty) // e.g. "hdfs://sandbox:8020/Stoerung_Messstellen2.csv"
            {
                val filename = mapping_files.head
                val dataframe = session.sqlContext.read.format ("csv").options (map_csv_options).csv (filename)

                val read = System.nanoTime ()
                log.info ("read %s: %s seconds".format (filename, (read - begin) / 1e9))

                val ch_number = dataframe.schema.fieldIndex (options.metercol)
                val nis_number = dataframe.schema.fieldIndex (options.mridcol)
                val join_table = dataframe.rdd.map (row ⇒ (row.getString (ch_number), row.getString (nis_number))).filter (_._2 != null).collect.toMap

                val map = System.nanoTime ()
                log.info ("map: %s seconds".format ((map - read) / 1e9))

                // dumpHeap ()
                options.format.toString match
                {
                    case "Belvis" ⇒ options.datafiles.foreach (process_belvis (join_table))
                    case "LPEx" ⇒ options.datafiles.foreach (process_lpex (join_table))
                }

                hdfs.delete (new Path (filename), false)
            }
        }
    }
}
