package ch.ninecode.ingest

import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import java.util.regex.Pattern
import java.util.zip.ZipInputStream

import ch.ninecode.cim.CIMRDD
import ch.ninecode.gl.Complex
import ch.ninecode.gl.ThreePhaseComplexDataElement
import ch.ninecode.model.Name
import ch.ninecode.model.ServiceLocation
import ch.ninecode.model.StringQuantity
import ch.ninecode.model.UserAttribute
import ch.ninecode.mscons._
import com.datastax.spark.connector.SomeColumns
import com.datastax.spark.connector._
import com.datastax.spark.connector.rdd.ReadConf
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path
import org.apache.hadoop.fs.permission.FsPermission
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.Row
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.types.DataType
import org.apache.spark.storage.StorageLevel
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import scala.collection._

/**
 * Import measured data into Cassandra.
 *
 * Copies files to HDFS, reads them into Spark, executes a join across a CH### to mRID mapping table and stores them in Cassandra.
 *
 * @param session The Spark session to use.
 * @param options Options regarding Cassandra master, files to process etc.
 */
case class Ingest (session: SparkSession, options: IngestOptions) extends CIMRDD
{

    type Mrid = String
    type Type = String
    type Time = Long
    type Period = Int
    type Real_a = Double
    type Imag_a = Double
    type Units = String
    type MeasuredValue = (Mrid, Type, Time, Period, Real_a, Imag_a, Units)

    if (options.verbose) org.apache.log4j.LogManager.getLogger (getClass.getName).setLevel (org.apache.log4j.Level.INFO)
    if (options.verbose) org.apache.log4j.LogManager.getLogger ("ch.ninecode.mscons.MSCONSParser").setLevel (org.apache.log4j.Level.INFO)

    implicit val spark: SparkSession = session
    implicit val log: Logger = LoggerFactory.getLogger (getClass)

    val MeasurementTimeZone: TimeZone = TimeZone.getTimeZone (options.timezone)
    val MeasurementCalendar: Calendar = Calendar.getInstance ()
    MeasurementCalendar.setTimeZone (MeasurementTimeZone)
    val MeasurementTimestampFormat: SimpleDateFormat = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss.SSS")
    MeasurementTimestampFormat.setCalendar (MeasurementCalendar)
    val MeasurementDateTimeFormat: SimpleDateFormat = new SimpleDateFormat ("dd.MM.yy HH:mm:ss")
    MeasurementDateTimeFormat.setCalendar (MeasurementCalendar)
    val MeasurementDateTimeFormat2: SimpleDateFormat = new SimpleDateFormat ("dd.MM.yyyy HH:mm")
    MeasurementDateTimeFormat2.setCalendar (MeasurementCalendar)
    val MeasurementDateTimeFormat3: SimpleDateFormat = new SimpleDateFormat ("dd.MM.yyyy HH:mm:ss")
    MeasurementDateTimeFormat3.setCalendar (MeasurementCalendar)

    val ZuluTimeZone: TimeZone = TimeZone.getTimeZone ("GMT")
    val ZuluTimeCalendar: Calendar = Calendar.getInstance ()
    ZuluTimeCalendar.setTimeZone (ZuluTimeZone)
    val ZuluTimestampFormat: SimpleDateFormat = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss.SSS")
    ZuluTimestampFormat.setCalendar (ZuluTimeCalendar)

    lazy val obis: Pattern = java.util.regex.Pattern.compile ("""^((\d+)-)*((\d+):)*(\d+)\.(\d+)(\.(\d+))*(\*(\d+))*$""")

    case class Reading (mRID: String, time: Timestamp, period: Int, values: Array[Double])

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
        val values: Array[Double] = new Array[Double](math.max (a.values.length, b.values.length))
        for (i ← a.values.indices)
            values (i) = a.values (i)
        for (i ← b.values.indices)
            values (i) = values (i) + b.values (i)
        Reading (a.mRID, a.time, a.period, values)
    }

    /**
     * Make tuples suitable for Cassandra:
     * ("mrid", "type", "time", "period", "real_a", "imag_a", "units")
     *
     * @param reading the reading from the csv
     * @return the list of time series records
     */
    def to_timeseries (reading: Reading): IndexedSeq[MeasuredValue] =
    {
        // Note: reading.period is in seconds and we need milliseconds for Cassandra
        val period = 1000 * reading.period

        // reading.time thinks it's in GMT but it's not
        // so use the timezone to convert it to GMT
        val timestamp = MeasurementTimestampFormat.parse (reading.time.toString)
        val measurement_time = new Date (timestamp.getTime).getTime
        for
        {
            i <- reading.values.indices
            time = measurement_time + period * i
            if (time >= options.mintime) && (time <= options.maxtime)
        }
            yield
                (reading.mRID, "energy", time, period, 1000.0 * reading.values (i), 0.0, "Wh")
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
            else
                log.error ("HADOOP_CONF_DIR environment variable not found")
        }
        configuration
    }

    def hdfs: FileSystem =
    {
        // get the configuration
        val conf = hdfs_configuration
        // get the file system
        FileSystem.get (FileSystem.getDefaultUri (conf), conf)
    }

    def base_name (path: String): String =
    {
        val sep = System.getProperty ("file.separator")
        val index = path.lastIndexOf (sep)
        if (-1 != index)
            path.substring (index + 1)
        else
            path
    }

    def getFiles (file: String): Seq[String] =
    {
        if (options.nocopy)
            Seq (file)
        else
        {
            val start = System.nanoTime ()
            val name = base_name (file)
            val files = putFile (session, name, file, file.toLowerCase.endsWith (".zip"))
            val end = System.nanoTime ()
            log.info (s"copy $file: ${(end - start) / 1e9} seconds")
            files
        }
    }

    def readFile (file: String): Array[Byte] =
    {
        try
            Files.readAllBytes (Paths.get (file))
        catch
        {
            case e: Exception =>
                log.error (s"""ingest failed for file "$file"""", e)
                Array ()
        }
    }

    /**
     * Put a file on HDFS
     * @param spark the Spark session
     * @param dst the path to save the file
     * @param src the file to save
     * @param unzip flag indicating the stream is a ZIP file that needs to be expanded
     * @return
     */
    def putFile (spark: SparkSession, dst: String, src: String, unzip: Boolean = false): Seq[String] =
    {
        var ret = Seq[String]()
        val fs = hdfs
        val file = new Path (fs.getUri.toString, s"${options.workdir}$dst")
        // write the file
        try
        {
            val parent = if (dst.endsWith ("/")) file else file.getParent
            if (!fs.exists (parent))
            {
                fs.mkdirs (parent, new FsPermission ("ugoa-rwx"))
                if (!parent.isRoot)
                    fs.setPermission (parent, new FsPermission ("ugoa-rwx"))
            }

            if (unzip)
            {
                val in =
                try
                    Files.newInputStream(Paths.get (src))
                catch
                {
                    case e: Exception =>
                        log.error (s"""ingest failed for file "$file"""", e)
                        new ByteArrayInputStream (Array[Byte]())
                }
                val zip = new ZipInputStream (in)
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
                            fs.mkdirs (path, new FsPermission ("ugoa-rwx"))
                            fs.setPermission (path, new FsPermission ("ugoa-rwx"))
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
                            fs.copyFromLocalFile (true, true, new Path (tmp.getAbsolutePath), f)
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
                val f = new Path (parent, dst)
                fs.copyFromLocalFile (false, true, new Path (src), f)
                ret = ret :+ file.toString
            }
        }
        catch
        {
            case e: Exception =>
                log.error (s"""putFile failed for "$src" to "$dst" with unzip=$unzip""", e)
        }

        ret
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
                    case 1 => ("power", true, false, 1.0, "W")
                    // active power -
                    case 2 => ("power", true, false, -1.0, "W")
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
                    what match
                    {
                        // last average
                        case 5 =>
                        // time integral 1
                        case 8 => typ = "energy"; unit = "Wh"
                        // time integral
                        case 29 => typ = "energy"; unit = "Wh"
                    }
                    units match
                    {
                        case "kWh" => factor = factor * 1000.0 * scale.toDouble;
                        case "kvarh" => factor = factor * 1000.0 * scale.toDouble;
                        case _ =>
                    }
                    (typ, real, imag, unit, factor)
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

    def complex (measurements: Iterable[MeasuredValue]): MeasuredValue =
    {
        val a = measurements.head
        (a._1, a._2, a._3, a._4, measurements.map (_._5).sum, measurements.map (_._6).sum, a._7)
    }

    /**
     * Make tuples suitable for Cassandra:
     * ("mrid", "type", "time", "period", "real_a", "imag_a", "units")
     *
     * @param line one line from the BelVis file
     */
    def parse_belvis_line (join_table: Map[String, String])(line: String): Seq[MeasuredValue] =
    {
        val ONE_MINUTE_IN_MILLIS = 60000

        val fields = line.split (";")
        // eliminate blank lines at the end
        if (fields.length > 1)
        {
            val datetime = MeasurementDateTimeFormat2.parse (fields (0))
            val mrid = join_table.getOrElse (fields (1), null)
            if (null != mrid)
            {
                val (typ, real, imag, units, factor) = decode_obis (fields (2), fields (3), "1.0")
                val time = datetime.getTime
                val period = fields (6).toInt
                val interval = period * ONE_MINUTE_IN_MILLIS
                val list = for
                {
                    i ← 7 until fields.length by 2
                    reading = fields (i)
                    value = if ("" != reading) asDouble (reading) * factor else 0.0
                    slot = (i - 7) / 2
                    timestamp = time + (interval * slot)
                    if (timestamp >= options.mintime) && (timestamp <= options.maxtime)
                }
                    yield
                        (mrid, typ, timestamp, interval, if (real) value else 0.0, if (imag) value else 0.0, units)
                list
            }
            else
                List ()
        }
        else
            List ()
    }

    def sub_belvis (filename: String, join_table: Map[String, String]): Unit =
    {
        // it's almost a CSV file
        // for daylight savings time changes, not all lines have the same number of columns
        val lines = session.sparkContext.textFile (filename)
        val rdd = lines.flatMap (parse_belvis_line (join_table))
        // combine real and imaginary parts
        if (options.mode == Modes.Append)
        {
            val executors = session.sparkContext.getExecutorMemoryStatus.keys.size - 1
            implicit val configuration: ReadConf =
                ReadConf
                .fromSparkConf (session.sparkContext.getConf)
                .copy (splitCount = Some (executors))
            val df =
                session
                .sparkContext
                .cassandraTable[(Mrid, Type, Time, Period, Real_a, Imag_a, Units)](options.keyspace, "measured_value")
                .select("mrid", "type", "time", "period", "real_a", "imag_a", "units")
            val unioned = rdd.union(df)
            val grouped = unioned.groupBy(x => (x._1, x._2, x._3)).values.map(complex)
            grouped.saveToCassandra (options.keyspace, "measured_value", SomeColumns ("mrid", "type", "time", "period", "real_a", "imag_a", "units"))
        }
        else
        {
            val grouped = rdd.groupBy (x => (x._1, x._2, x._3)).values.map (complex)
            grouped.saveToCassandra (options.keyspace, "measured_value", SomeColumns ("mrid", "type", "time", "period", "real_a", "imag_a", "units"))
        }
    }

    def process_belvis (join_table: Map[String, String])(file: String): Unit =
    {
        val belvis_files: Seq[String] = getFiles (file)
        for (filename ← belvis_files)
        {
            val start = System.nanoTime ()
            sub_belvis (filename, join_table)
            if (!options.nocopy)
                hdfs.delete (new Path (filename), false)
            val end = System.nanoTime ()
            log.info (s"process $filename: ${(end - start) / 1e9} seconds")
        }
    }

    /**
     * Make tuples suitable for Cassandra:
     * ("mrid", "type", "time", "period", "real_a", "imag_a", "units")
     *
     * @param line one line from the LPEx file
     */
    def parse_lpex_line (join_table: Map[String, String])(line: String): Seq[MeasuredValue] =
    {
        val ONE_MINUTE_IN_MILLIS = 60000

        // described in GoerlitzExportImport_V131I04_FBe_DE.pdf
        val fields = line.split (";")
        // eliminate the version line and header line
        if (fields.length > 15 && fields (0) != "Datum")
        {
            val datetime = MeasurementDateTimeFormat.parse (fields (0) + " " + fields (1))
            val mrid = join_table.getOrElse (fields (10), null)
            if (null != mrid)
            {
                val (typ, real, imag, units, factor) = decode_obis (fields (11), fields (12), fields (13))
                val time = datetime.getTime
                val period = fields (14).toInt
                val interval = period * ONE_MINUTE_IN_MILLIS
                val list = for
                {
                    i ← 15 until fields.length by 2
                    flags = fields (i + 1)
                    if flags == "W"
                    value = asDouble (fields (i)) * factor
                    slot = (i - 15) / 2
                    timestamp = time + (interval * slot)
                    if (timestamp >= options.mintime) && (timestamp <= options.maxtime)
                }
                    yield
                        (mrid, typ, timestamp, interval, if (real) value else 0.0, if (imag) value else 0.0, units)
                // discard all zero records
                if (list.exists (x => x._5 != 0.0 || x._6 != 0.0))
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

    def sub_lpex (filename: String, join_table: Map[String, String]): Unit =
    {
        // it's almost a CSV file but they screwed up and gave it a version line
        // and for daylight savings time changes, not all lines have the same number of columns
        val lines = session.sparkContext.textFile (filename)
        if (lines.first.startsWith ("LPEX V3.0"))
        {
            val rdd = lines.flatMap (parse_lpex_line (join_table))
            // combine real and imaginary parts
            if (options.mode == Modes.Append)
            {
                val executors = session.sparkContext.getExecutorMemoryStatus.keys.size - 1
                implicit val configuration: ReadConf =
                    ReadConf
                        .fromSparkConf (session.sparkContext.getConf)
                        .copy (splitCount = Some (executors))
                val df =
                    session
                    .sparkContext
                    .cassandraTable[MeasuredValue](options.keyspace, "measured_value")
                    .select("mrid", "type", "time", "period", "real_a", "imag_a", "units")
                val unioned = rdd.union(df)
                val grouped = unioned.groupBy(x => (x._1, x._2, x._3)).values.map(complex)
                grouped.saveToCassandra (options.keyspace, "measured_value", SomeColumns ("mrid", "type", "time", "period", "real_a", "imag_a", "units"))
            }
            else
            {
                val grouped: RDD[MeasuredValue] = rdd.groupBy (x => (x._1, x._2, x._3)).values.map (complex)
                grouped.saveToCassandra (options.keyspace, "measured_value", SomeColumns ("mrid", "type", "time", "period", "real_a", "imag_a", "units"))
            }
        }
    }

    def process_lpex (join_table: Map[String, String])(file: String): Unit =
    {
        val lpex_files: Seq[String] = getFiles (file)
        for (filename ← lpex_files)
        {
            val start = System.nanoTime ()
            sub_lpex (filename, join_table)
            if (!options.nocopy)
                hdfs.delete (new Path (filename), false)
            val end = System.nanoTime ()
            log.info (s"process $filename: ${(end - start) / 1e9} seconds")
        }
    }

    def process_mscons (join_table: Map[String, String])(files: Seq[String]): Unit =
    {
        /**
         * Make a three phase data element from record returned by MSCONS parser.
         *
         * @param join_table the mapping from meter id to mRID
         * @param record a reading from the MSCONS parser
         */
        def to_data_element (join_table: Map[String, String])(record: (String, String, Calendar, Int, Double, Double, String)): Option[ThreePhaseComplexDataElement] =
        {
            val mrid = join_table.getOrElse (record._1, null)
            if (null != mrid)
                Some (ThreePhaseComplexDataElement (mrid, record._3.getTimeInMillis, Complex (record._5, record._6), null, null, record._7))
            else
                None
        }

        /**
         * Apply the MSCONS parser to parse a file.
         *
         * @param filename the file to parse
         * @return the sequence of data elements found in the file
         */
        def processOneFile (filename: String): Seq[ThreePhaseComplexDataElement] =
        {
            val parser = MSCONSParser (MSCONSOptions ())
            parser.parse (filename)
                .flatMap (to_data_element (join_table))
                .filter (x => (x.millis >= options.mintime) && (x.millis <= options.maxtime))
        }

        /**
         * Sum the elements for an mRID.
         *
         * @param m the measurements for an mRID
         * @return the aggregated (sum) of elements
         */
        def complex2 (m: Iterable[ThreePhaseComplexDataElement]): ThreePhaseComplexDataElement =
        {
            val head = m.head
            val sum = Complex (m.map (_.value_a.re).sum, m.map (_.value_a.im).sum)
            ThreePhaseComplexDataElement (head.element, head.millis, sum, null, null, head.units)
        }

        /**
         * Prepare the data element for insertion into Cassandra.
         *
         * @param record the three phase data element
         * @return a tuple suitable for Cassandra:
         *         ("mrid", "type", "time", "period", "real_a", "imag_a", "units")
         */
        def split (record: ThreePhaseComplexDataElement): (String, String, Long, Int, Double, Double, String) =
        {
            (record.element, "energy", record.millis, 900000, record.value_a.re, record.value_a.im, record.units)
        }

        val start = System.nanoTime ()
        val all_files = files.flatMap (getFiles)
        val mscons_files = session.sparkContext.parallelize (all_files)

        // read all files into one RDD
        val raw = mscons_files.flatMap (processOneFile)
        // combine real and imaginary parts
        if (options.mode == Modes.Append)
        {
            val executors = session.sparkContext.getExecutorMemoryStatus.keys.size - 1
            implicit val configuration: ReadConf =
                ReadConf
                    .fromSparkConf (session.sparkContext.getConf)
                    .copy (splitCount = Some (executors))
            val df =
                session
                .sparkContext
                .cassandraTable(options.keyspace, "measured_value")
                .select("mrid", "time", "period", "real_a", "imag_a", "units")
                .map((row) => {
                    ThreePhaseComplexDataElement(
                        row.getString("mrid"),
                        row.getLong("time"),
                        Complex(row.getDouble("real_a"),row.getDouble("imag_a")),
                        null,
                        null,
                        row.getString("units"))
                })
            val unioned= raw.union(df)
            val grouped = unioned.groupBy (x => (x.element, x.millis)).values.map (complex2).map (split)
            grouped.saveToCassandra (options.keyspace, "measured_value", SomeColumns ("mrid", "type", "time", "period", "real_a", "imag_a", "units"))
        } else {
            val grouped = raw.groupBy (x => (x.element, x.millis)).values.map (complex2).map (split)
            grouped.saveToCassandra (options.keyspace, "measured_value", SomeColumns ("mrid", "type", "time", "period", "real_a", "imag_a", "units"))
        }
        val end = System.nanoTime ()
        if (!options.nocopy)
            all_files.foreach (x => hdfs.delete (new Path (x), false))
        val some_files = all_files.take (6).mkString (",")
        val more_files = all_files.length > 6
        log.info (s"processed files [${some_files}${if (more_files) "..." else ""}]: ${(end - start) / 1e9} seconds")
    }

    def isNumber (s: String): Boolean = s forall Character.isDigit

    def asDouble (s: String): Double =
        try
        {
            s.toDouble
        }
        catch
        {
            case _: Throwable => 0.0
        }

    /**
     * Make tuples suitable for Cassandra:
     * ("mrid", "type", "time", "period", "real_a", "imag_a", "units")
     *
     * @param line one line from the data file
     */
    def line_custom (join_table: Map[String, String])(line: String): Seq[MeasuredValue] =
    {
        // LDN-Messpunkt;Einheitennummer...
        // 730154;39580894;Wirkenergie A+ 15;1-1:1.8.0*255;15;kWh;2019.08.24;24.08.2019 00:00;24.08.2019 00:15;0.038;...
        val fields: Array[String] = line.split (";")
        // eliminate the header line
        if (isNumber (fields (0)))
        {
            val mrid = join_table.getOrElse (fields (0), null)
            if (null != mrid)
            {
                val (typ, real, imag, units, factor) = decode_obis (fields (3), fields (5), "1.0")
                val date = fields (6)
                if (real || imag)
                    for (
                        index <- 7 until fields.length
                        if 0 == (index - 7) % 3;
                        start = fields (index);
                        end = fields (index + 1);
                        datetime1 = if (start.length == 8) MeasurementDateTimeFormat3.parse (s"$date $start") else MeasurementDateTimeFormat2.parse (start);
                        timestamp = if (end.length == 8) MeasurementDateTimeFormat3.parse (s"$date $end") else MeasurementDateTimeFormat2.parse (end);
                        interval = (timestamp.getTime - datetime1.getTime).toInt;
                        value = asDouble (fields (index + 2)) * factor
                    )
                        yield
                            if (real)
                                (mrid, typ, timestamp.getTime, interval, value, 0.0, units)
                            else
                                (mrid, typ, timestamp.getTime, interval, 0.0, value, units)
                else
                    List ()
            }
            else
                List ()
        }
        else
            List ()
    }


    def sub_custom (filename: String, join_table: Map[String, String]): Unit =
    {
        val lines: RDD[String] = session.sparkContext.textFile (filename)
        val rdd = lines.flatMap (line_custom (join_table))
        // combine real and imaginary parts
        if (options.mode == Modes.Append)
        {
            val executors = session.sparkContext.getExecutorMemoryStatus.keys.size - 1
            implicit val configuration: ReadConf =
                ReadConf
                    .fromSparkConf (session.sparkContext.getConf)
                    .copy (splitCount = Some (executors))
            val df =
                session
                .sparkContext
                .cassandraTable[MeasuredValue](options.keyspace, "measured_value")
                .select("mrid", "type", "time", "period", "real_a", "imag_a", "units")
            val unioned = rdd.union(df)
            val grouped = unioned.groupBy(x => (x._1, x._2, x._3)).values.map(complex)
            grouped.saveToCassandra (options.keyspace, "measured_value", SomeColumns ("mrid", "type", "time", "period", "real_a", "imag_a", "units"))
        }
        else
        {
            val grouped: RDD[MeasuredValue] = rdd.groupBy (x => (x._1, x._2, x._3)).values.map (complex)
            grouped.saveToCassandra (options.keyspace, "measured_value", SomeColumns ("mrid", "type", "time", "period", "real_a", "imag_a", "units"))
        }
    }

    def process_custom (join_table: Map[String, String])(file: String): Unit =
    {
        val files: Seq[String] = getFiles (file)
        for (filename ← files)
        {
            val start = System.nanoTime ()
            sub_custom (filename, join_table)
            if (!options.nocopy)
                hdfs.delete (new Path (filename), false)
            val end = System.nanoTime ()
            log.info (s"process $filename: ${(end - start) / 1e9} seconds")
        }
    }

    def process_parquet(): Unit = {
        readCIM()
        val synthLoadProfile: RDD[MeasuredValue] = import_parquet()
        val mapping: RDD[(String, String)] = getMappingAoHas()
        val joinedData: RDD[MeasuredValue] = synthLoadProfile.keyBy(_._1).join(mapping).values.map(v => v._1.copy(_1 = v._2))

        def aggregateData(data: Iterable[MeasuredValue]): MeasuredValue = {
            val real_a = data.map(_._5).sum
            val imag_a = data.map(_._6).sum
            data.head.copy(_5 = real_a, _6 = imag_a)
        }
        val aggregatedData: RDD[MeasuredValue] = joinedData.groupBy(k => (k._1, k._3)).values.map(aggregateData)
        aggregatedData.saveToCassandra(options.keyspace, "measured_value", SomeColumns("mrid", "type", "time", "period", "real_a", "imag_a", "units"))
    }

    def getMappingAoHas(): RDD[(String, String)] = {
        val name: RDD[Name] = getOrElse[Name]
        val serviceLocation: RDD[ServiceLocation] = getOrElse[ServiceLocation]
        val userAttribute: RDD[UserAttribute] = getOrElse[UserAttribute]
        val stringQuantity: RDD[StringQuantity] = getOrElse[StringQuantity]

        val MstHasMapping: RDD[(String, String)] = userAttribute.keyBy(_.value).join(stringQuantity.keyBy(_.id)).values.map(x => (x._1.name, x._2.value))
        val MstAoMapping: RDD[(String, String)] = serviceLocation.keyBy(_.id).join(name.keyBy(_.IdentifiedObject)).values.map(x => (x._1.WorkLocation.Location.IdentifiedObject.name, x._2.name))
        MstAoMapping.join(MstHasMapping).values
    }

    def import_parquet(): RDD[MeasuredValue] = {
        val dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ssXXX")
        def parquetMapping(row: Row): MeasuredValue = {
            val ao_id = row.getLong(0).toString
            val timestamp = dateFormat.parse(row.getString(1)).getTime
            val real_a = row.getDouble(2)
            val imag_a = row.getDouble(3)
            (ao_id, "energy", timestamp, 900000, real_a, imag_a, "Wh")
        }
        val parquetFileDF = session.read.parquet(options.datafiles: _*)
        parquetFileDF.rdd.map(parquetMapping)
    }

    def readCIM(): Unit = {
        val start = System.nanoTime
        val thisFiles = options.mapping.split(",")
        val readOptions = Map[String, String](
            "path" -> options.mapping,
            "StorageLevel" -> "MEMORY_AND_DISK_SER",
            "ch.ninecode.cim.do_topo_islands" -> "false",
            "ch.ninecode.cim.debug" -> "true",
            "ch.ninecode.cim.do_deduplication" -> "true"
        )
        val elements = session.sqlContext.read.format("ch.ninecode.cim")
            .options(readOptions)
            .load(thisFiles: _*)
            .persist(StorageLevel.MEMORY_AND_DISK_SER)
        println(elements.count + " elements")
        val read = System.nanoTime
        println("read: " + (read - start) / 1e9 + " seconds")
    }

    def extractor (datatype: DataType): (Row, Int) => String =
    {
        datatype.simpleString match
        {
            case "decimal" | "double" | "float" =>
                (row: Row, column: Int) => row.getDouble (column).toString
            case "string" =>
                (row: Row, column: Int) => row.getString (column)
            case "integer" | "int" | "short" | "smallint" =>
                (row: Row, column: Int) => row.getInt (column).toString
            case "long" =>
                (row: Row, column: Int) => row.getLong (column).toString
            case _ =>
                throw new Exception (s"unsupported datatype as key value ${datatype.toString}")
        }
    }

    def run (): Unit =
    {
        val begin = System.nanoTime ()

        val schema = Schema (session, options.keyspace, options.replication, verbose = options.verbose)
        if (schema.make)
        {
            val db = System.nanoTime ()
            log.info (s"schema: ${(db - begin) / 1e9} seconds")
            val mapping_files =
                if (options.nocopy)
                    Seq(options.mapping)
                else
                    putFile(session, base_name(options.mapping), options.mapping, options.mapping.toLowerCase.endsWith(".zip"))

            var join_table: Map[String, String] = Map.empty
            if (options.format != Formats.Parquet && mapping_files.nonEmpty) {
                val filename = mapping_files.head
                val dataframe = session.sqlContext.read.format("csv").options(map_csv_options).csv(filename)

                val read = System.nanoTime()
                log.info(s"read $filename: ${(read - db) / 1e9} seconds")

                val ch_number = dataframe.schema.fieldIndex(options.metercol)
                val nis_number = dataframe.schema.fieldIndex(options.mridcol)
                val extract = extractor(dataframe.schema.fields(ch_number).dataType)
                join_table = dataframe.rdd.map(row => (extract(row, ch_number), row.getString(nis_number))).filter(_._2 != null).collect.toMap

                val map = System.nanoTime()
                log.info(s"map: ${(map - read) / 1e9} seconds")
            }

            options.format.toString match {
                case "Belvis" => options.datafiles.foreach(process_belvis(join_table))
                case "LPEx" => options.datafiles.foreach(process_lpex(join_table))
                case "MSCONS" => process_mscons(join_table)(options.datafiles)
                case "Custom" => options.datafiles.foreach(process_custom(join_table))
                case "Parquet" => process_parquet()
            }

            if (!options.nocopy)
                hdfs.delete(new Path(mapping_files.head), false)
        }
    }
}
