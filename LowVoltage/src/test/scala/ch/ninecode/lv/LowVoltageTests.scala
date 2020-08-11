package ch.ninecode.lv

import java.io.File

import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test

import ch.ninecode.lv.LowVoltage.main
import ch.ninecode.testutil.Unzip

class LowVoltageTests
{

    import LowVoltageTests.FILE_DEPOT
    import LowVoltageTests.FILENAME

    @Test def doBasic ()
    {
        val begin = System.nanoTime ()

        val filename = s"$FILE_DEPOT$FILENAME.rdf"

        main (Array ("--unittest", "--verbose", "--workdir", "./target/", "--cache", s"./target/$FILENAME", filename))

        val total = System.nanoTime ()
        println (s"total: ${(total - begin) / 1e9} seconds")

        assert (new File ("./target/TX0001/TX0001.glm").exists, "TX0001")
        assert (new File ("./target/TX0001/TX0001.glm").exists, "TX0001")
    }
}

object LowVoltageTests extends Unzip
{
    val FILE_DEPOT = "data/"
    val FILENAME = "DemoData"

    @BeforeClass ()
    def unzip (): Unit =
    {
        if (!new File (s"$FILE_DEPOT$FILENAME.rdf").exists)
            new Unzip ().unzip (s"$FILE_DEPOT$FILENAME.zip", FILE_DEPOT)
    }

    @AfterClass ()
    def delete (): Unit =
    {
        val _ = new File (s"$FILE_DEPOT$FILENAME.rdf").delete
    }
}