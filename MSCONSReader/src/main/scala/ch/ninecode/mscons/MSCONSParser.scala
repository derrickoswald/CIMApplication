package ch.ninecode.mscons

import java.nio.channels.FileChannel
import java.nio.file.FileSystems
import java.nio.file.StandardOpenOption
import java.text.SimpleDateFormat
import java.util.Calendar

import org.slf4j.LoggerFactory
import org.slf4j.Logger

import ch.ninecode.edifact.ServiceSegmentParser
import ch.ninecode.edifact.Segment
import ch.ninecode.edifact.SegmentParser
import ch.ninecode.edifact.SegmentScanner

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
        val path = FileSystems.getDefault.getPath (name)
        val file = FileChannel.open (path, StandardOpenOption.READ)
        val buffer = file.map (FileChannel.MapMode.READ_ONLY, 0L, file.size ())
        file.close ()

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
