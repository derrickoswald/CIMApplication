package ch.ninecode.mscons

import java.io.ByteArrayOutputStream
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream

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
        val out = new ByteArrayOutputStream
        System.setOut (new PrintStream (out))
        main (Array ("--unittest", "--verbose",
            s"${FILE_DEPOT}${MSCONS_FILE1}"))
        System.setOut (new PrintStream (new FileOutputStream (FileDescriptor.out)))
        val text = out.toString.split ("\\n")
        assert (text.length == 96)
        assert (text(0)  == "CH1008801234500000000000000113813 energy 2019-12-14 00:00:00 CET 900000 36.3+0.0j Wh")
        assert (text(95) == "CH1008801234500000000000000113813 energy 2019-12-14 23:45:00 CET 900000 51.6+0.0j Wh")
    }

    @Test def NoRead ()
    {
        val err = new ByteArrayOutputStream
        System.setErr (new PrintStream (err))
        main (Array ("--unittest", "--verbose",
            s"${FILE_DEPOT}${OLD_MSCONS_FILE}"))
        System.setErr (new PrintStream (new FileOutputStream (FileDescriptor.err)))
        val text = err.toString.split ("\\n")
        assert (text.length == 1)
        assert (text(0)  == "[main] ERROR ch.ninecode.mscons.MSCONSParser - MSCONS version D release 99A is not supported")
    }

    @Test def ReadMultiple ()
    {
        val out = new ByteArrayOutputStream
        System.setOut (new PrintStream (out))
        main (Array ("--unittest", "--verbose",
            s"${FILE_DEPOT}${MSCONS_FILE1}",
            s"${FILE_DEPOT}${MSCONS_FILE2}",
            s"${FILE_DEPOT}${MSCONS_FILE3}",
            s"${FILE_DEPOT}${MSCONS_FILE4}",
            s"${FILE_DEPOT}${MSCONS_FILE5}",
            s"${FILE_DEPOT}${MSCONS_FILE6}"
        ))
        System.setOut (new PrintStream (new FileOutputStream (FileDescriptor.out)))
        val text = out.toString.split ("\\n")
        assert (text.length == 96 * 6)
        assert (text(0)  == "CH1008801234500000000000000113813 energy 2019-12-14 00:00:00 CET 900000 36.3+0.0j Wh")
        assert (text(96 * 6 - 1) == "CH1008801234500000000000000113813 energy 2019-12-14 23:45:00 CET 900000 0.0+0.0j Wh")
    }
}
