package ch.ninecode.ingest

import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.regex.Pattern
import java.util.zip.ZipInputStream

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path
import org.apache.hadoop.fs.permission.FsAction
import org.apache.hadoop.fs.permission.FsPermission
import org.apache.spark.sql.Row
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.types.DataType
import org.slf4j.Logger
import org.slf4j.LoggerFactory

trait IngestProcessor
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

    lazy val log: Logger = LoggerFactory.getLogger (getClass)
    lazy val obis: Pattern = java.util.regex.Pattern.compile ("""^((\d+)-)*((\d+):)*(\d+)\.(\d+)(\.(\d+))*(\*(\d+))*$""")

    def map_csv_options: Map[String, String] =
    {
        Map [String, String](
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
        val t0 = System.nanoTime ()
        val ret = block
        val t1 = System.nanoTime ()
        LoggerFactory.getLogger (getClass).info (template.format ((t1 - t0) / 1e9), None)
        ret
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

    lazy val wideOpen = new FsPermission (FsAction.ALL, FsAction.ALL, FsAction.ALL)

    /**
     * Put a file on HDFS
     *
     * @param dst   the path to save the file
     * @param src   the file to save
     * @param unzip flag indicating the stream is a ZIP file that needs to be expanded
     * @return
     */
    def putFile (dst: String, src: String, unzip: Boolean = false): Seq[String] =
    {
        var ret = Seq [String]()
        val fs = hdfs
        val file = new Path (fs.getUri.toString, dst)
        // write the file
        try
        {
            val parent = if (dst.endsWith ("/")) file else file.getParent
            if (!fs.exists (parent))
            {
                val _ = fs.mkdirs (parent, wideOpen)
                if (!parent.isRoot)
                    fs.setPermission (parent, wideOpen)
            }

            if (unzip)
            {
                val in =
                    try
                    Files.newInputStream (Paths.get (src))
                    catch
                    {
                        case e: Exception =>
                            log.error (s"""ingest failed for file "$file"""", e)
                            new ByteArrayInputStream (Array [Byte]())
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
                            val _ = fs.mkdirs (path, wideOpen)
                            fs.setPermission (path, wideOpen)
                        }
                        else
                        {
                            val tmp = File.createTempFile ("ingest", ".tmp")
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

    def getFiles (job: IngestJob, workdir: String)(file: String): Seq[String] =
    {
        if (job.nocopy)
            Seq (file)
        else
            time (s"copy $file: %s seconds")
            {
                val name = base_name (file)
                putFile (s"${workdir}$name", file, file.toLowerCase.endsWith (".zip"))
            }
    }


    def cleanUp (job: IngestJob, filename: String): Unit =
    {
        if (!job.nocopy)
        {
            val _ = hdfs.delete (new Path (filename), false)
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
        val matcher = obis.matcher (code)
        if (matcher.find)
        {
            if (1 == matcher.group (2).toInt)
            {
                val quantity = matcher.group (5).toInt
                val what = matcher.group (6).toInt
                val (typ, real, imag, factor, unit) = quantity match
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
            case Some ((mrid, typ, time, period, _, _, units)) =>
                Some ((mrid, typ, time, period, measurements.map (_._5).sum, measurements.map (_._6).sum, units))
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
                (row: Row, column: Int) => row.getDouble (column).toString
            case "string" =>
                (row: Row, column: Int) => row.getString (column)
            case "integer" | "int" | "short" | "smallint" =>
                (row: Row, column: Int) => row.getInt (column).toString
            case "long" =>
                (row: Row, column: Int) => row.getLong (column).toString
            case _ =>
                (_: Row, _: Int) => s"unsupported datatype ${datatype.toString}"
        }
    }

    def loadCsvMapping (session: SparkSession, filename: String, job: IngestJob): Map[String, String] =
    {
        val dataframe = time (s"read $filename: %s seconds")
        {
            session.sqlContext.read.format ("csv").options (map_csv_options).csv (filename)
        }
        val join_table = time ("map: %s seconds")
        {
            val ch_number = dataframe.schema.fieldIndex (job.metercol)
            val nis_number = dataframe.schema.fieldIndex (job.mridcol)
            val extract = extractor (dataframe.schema.fields (ch_number).dataType)
            dataframe.rdd.map (row => (extract (row, ch_number), row.getString (nis_number))).filter (_._2 != null).collect.toMap
        }
        join_table
    }

    def process (filename: String, job: IngestJob): Unit
}
