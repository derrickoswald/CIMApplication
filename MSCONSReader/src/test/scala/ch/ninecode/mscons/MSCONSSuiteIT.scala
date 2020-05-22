package ch.ninecode.mscons

import java.io.File
import java.util.Locale

import scala.io.Source

import org.junit.Test

import ch.ninecode.mscons.MSCONS.main

class MSCONSSuiteIT
{
    val FILE_DEPOT = "data/"

    val MSCONS_FILE1 = "20191215_045127_12X-SAK-N------6_E66_12X-SAK-N------6_ESLEVU14572840_-566879393.txt"
    val MSCONS_FILE2 = "20191215_045127_12X-SAK-N------6_E66_12X-SAK-N------6_ESLEVU14572841_-1811850990.txt"
    val MSCONS_FILE3 = "20191215_045128_12X-SAK-N------6_E66_12X-SAK-N------6_ESLEVU14572842_-1470816376.txt"
    val MSCONS_FILE4 = "20191215_045128_12X-SAK-N------6_E66_12X-SAK-N------6_ESLEVU14572843_-1813073308.txt"
    val MSCONS_FILE5 = "20191215_045129_12X-SAK-N------6_E66_12X-SAK-N------6_ESLEVU14572844_-1411967842.txt"
    val MSCONS_FILE6 = "20191215_045129_12X-SAK-N------6_E66_12X-SAK-N------6_ESLEVU14572845_1003992095.txt"

    val OLD_MSCONS_FILE = "MSCONS_LG_12X-0000000858-F_12X-0000000858-F_20140314_1407300597853.txt"

    @Test def Read ()
    {
        Locale.setDefault (new Locale ("en", "US"))
        main (Array ("--unittest", "--verbose", "--log", "WARN",
            "--output_file", s"${FILE_DEPOT}test.out",
            s"${FILE_DEPOT}${MSCONS_FILE1}"))

        val file = new File (s"${FILE_DEPOT}test.out")
        val source = Source.fromFile (file, "UTF-8")
        val text = source.getLines.toArray
        source.close
        file.delete

        assert (text.length == 96, text)
        assert (text(0)  == "CH1008801234500000000000000113813 energy 2019-12-14 00:15:00 CET 900000 36300.0+0.0j Wh")
        assert (text(95) == "CH1008801234500000000000000113813 energy 2019-12-15 00:00:00 CET 900000 51600.0+0.0j Wh")
    }


    @Test def GermanRead ()
    {
        Locale.setDefault (new Locale ("de", "CH"))
        main (Array ("--unittest", "--verbose", "--log", "WARN",
            "--output_file", s"${FILE_DEPOT}test.out",
            s"${FILE_DEPOT}${MSCONS_FILE1}"))

        val file = new File (s"${FILE_DEPOT}test.out")
        val source = Source.fromFile (file, "UTF-8")
        val text = source.getLines.toArray
        source.close
        file.delete

        assert (text.length == 96, text)
        assert (text(0)  == "CH1008801234500000000000000113813 energy 2019-12-14 00:15:00 MEZ 900000 36300.0+0.0j Wh")
        assert (text(95) == "CH1008801234500000000000000113813 energy 2019-12-15 00:00:00 MEZ 900000 51600.0+0.0j Wh")
    }

    // this test works in IntelliJ but doesn't work in failsafe because stderr is captured by failsafe
//    @Test def NoRead ()
//    {
//        val stderr = System.err
//        val err = new ByteArrayOutputStream
//        System.setErr (new PrintStream (err))
//        main (Array ("--unittest", "--verbose",
//            s"${FILE_DEPOT}${OLD_MSCONS_FILE}"))
//        System.setErr (stderr)
//        val text = err.toString.split ("\\n")
//
//        printf (text.mkString ("\n"))
//
//        assert (text.length == 1)
//        assert (text(0).contains ("MSCONS version D release 99A is not supported"))
//    }

    @Test def ReadMultiple ()
    {
        Locale.setDefault (new Locale ("en", "US"))
        main (Array ("--unittest", "--verbose", "--log", "WARN",
            "--output_file", s"${FILE_DEPOT}test.out",
            s"${FILE_DEPOT}${MSCONS_FILE1}",
            s"${FILE_DEPOT}${MSCONS_FILE2}",
            s"${FILE_DEPOT}${MSCONS_FILE3}",
            s"${FILE_DEPOT}${MSCONS_FILE4}",
            s"${FILE_DEPOT}${MSCONS_FILE5}",
            s"${FILE_DEPOT}${MSCONS_FILE6}"
        ))
        val file = new File (s"${FILE_DEPOT}test.out")
        val source = Source.fromFile (file, "UTF-8")
        val text = source.getLines.toArray
        source.close
        file.delete

        assert (text.length == 96 * 6)
        assert (text(0)  == "CH1008801234500000000000000113813 energy 2019-12-14 00:15:00 CET 900000 36300.0+0.0j Wh")
        assert (text(96 * 6 - 1) == "CH1008801234500000000000000113813 energy 2019-12-15 00:00:00 CET 900000 0.0+0.0j Wh")
    }
}
