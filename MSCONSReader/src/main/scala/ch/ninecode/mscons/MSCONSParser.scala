package ch.ninecode.mscons

import java.io.File
import java.net.URI
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Calendar

import ch.ninecode.edifact.Segment
import ch.ninecode.edifact.SegmentParser
import ch.ninecode.edifact.SegmentScanner
import ch.ninecode.edifact.ServiceSegmentParser
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FSDataInputStream
import org.apache.hadoop.fs.Path
import org.slf4j.Logger
import org.slf4j.LoggerFactory

case class MSCONSParser (options: MSCONSOptions)
{
    type ID = String
    type Quantity = String
    type Time = Calendar
    type Period = Int
    type Real = Double
    type Imaginary = Double
    type Units = String
    val log: Logger = LoggerFactory.getLogger (getClass)

    def parse (name: String): List[(ID, Quantity, Time, Period, Real, Imaginary, Units)] =
    {
        val buffer: ByteBuffer = getFileBuffer (name)
        if (null != buffer)
        {
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
                            if ((r.unh.Type == "MSCONS")
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
        else
            List ()
    }

    def getFileBuffer (name: String): ByteBuffer =
    {
        val f: File = new File (name)
        val isLocalFile = f.exists ()
        val uri: URI = if (isLocalFile)
        {
            f.toURI
        } else
        {
            new URI (name)
        }
        val file: Path = new Path (uri)
        val scheme: String = uri.getScheme

        // read the file
        var buffer: ByteBuffer = null
        var data: FSDataInputStream = null
        if ((null == scheme) || ("file" == scheme))
        {
            try
            {
                val f = Paths.get (file.toUri)
                val bytes = Files.readAllBytes (f)
                buffer = ByteBuffer.wrap (bytes)
            }
            catch
            {
                case e: Exception =>
                    log.error (s"failed to read file($file) with error:", e)
            }
        }
        else
            try
            {
                val fs = file.getFileSystem (getHadoopConfiguration)
                log.info (s"file: $file")
                data = fs.open (file)
                // ToDo: handle files bigger than 2GB
                val size = fs.getFileStatus (file).getLen.toInt
                val bytes = new Array[Byte](size)
                data.readFully (0, bytes)
                buffer = ByteBuffer.wrap (bytes)
            }
            catch
            {
                case e: Exception =>
                    log.error (s"failed to read file($file) with error:", e)
            }
            finally
            {
                if (data != null)
                    data.close ()
            }
        buffer
    }

    def getHadoopConfiguration: Configuration =
    {
        // build a file system configuration, including core-site.xml
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
}
