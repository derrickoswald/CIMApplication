package ch.ninecode.mscons

import java.io.File
import java.nio.ByteBuffer
import java.util.Calendar

import ch.ninecode.edifact.Segment
import ch.ninecode.edifact.SegmentParser
import ch.ninecode.edifact.SegmentScanner
import ch.ninecode.edifact.ServiceSegmentParser
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FSDataInputStream
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path
import org.slf4j.Logger
import org.slf4j.LoggerFactory

case class MSCONSParser (options: MSCONSOptions)
{
    val log: Logger = LoggerFactory.getLogger (getClass)

    type ID = String
    type Quantity = String
    type Time = Calendar
    type Period = Int
    type Real = Double
    type Imaginary = Double
    type Units = String

    def parse (name: String): List[(ID, Quantity, Time, Period, Real, Imaginary, Units)] =
    {
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
        val fs = hdfs
        val file: Path = new Path (fs.getUri.toString, name)

        // val fileScheme = file.getFileSystem().getUri.getScheme()
        var buffer: ByteBuffer = null
        // read the file
        var data: FSDataInputStream = null
        try
        {
            data = fs.open (file)
            // ToDo: handle files bigger than 2GB
            val size = fs.getFileStatus (file).getLen.toInt
            val bytes = new Array[Byte] (size)
            data.readFully (0, bytes)
            buffer = ByteBuffer.wrap(bytes)
        }
        catch
        {
            case e: Exception =>
                e.getMessage
        }
        finally {
            if (data != null)
                data.close()
        }

        val scanner = SegmentScanner (buffer)
        val message = SegmentParser (scanner.una)
        val segments = message.segment.*
        segments.apply (scanner) match
        {
            case message.Success (result: List[Segment], _) =>
                // result.foreach (segment => println (segment))
                val x = ServiceSegmentParser.read (result)
                x match
                {
                    case ServiceSegmentParser.Success (r, rest) =>
                        if (   (r.unh.Type == "MSCONS")
                            && (r.unh.Version == "D"))
                        {
                            r.unh.Release match
                            {
                                case "04B" =>
                                    MSCONSMessage04B.phrase (rest) match
                                    {
                                        case MSCONSMessage04B.Success (message, rest) =>
                                            if (!rest.atEnd)
                                                log.warn (s"message incompletely parsed, stopped at ${rest.first}")
                                            message.getReadings
                                        case MSCONSMessage04B.Failure (message, _) =>
                                            log.error (s"parse failed '$message'")
                                            List ()
                                        case MSCONSMessage04B.Error (message, _) =>
                                            log.error (s"parse error '$message'")
                                            List ()
                                    }
                                case _ =>
                                    log.error (s"${r.unh.Type} version ${r.unh.Version} release ${r.unh.Release} is not supported")
                                    List ()
                            }
                        }
                        else
                            List ()

                    case ServiceSegmentParser.Failure (msg, _) =>
                        log.error (s"parse failure: $msg")
                        List ()
                    case ServiceSegmentParser.Error (msg, _) =>
                        log.error (s"parse error: $msg")
                        List ()
                }
            case message.Failure (msg, _) =>
                log.error (s"parse failure: $msg")
                List ()
            case message.Error (msg, _) =>
                log.error (s"parse error: $msg")
                List ()
        }
    }
}
