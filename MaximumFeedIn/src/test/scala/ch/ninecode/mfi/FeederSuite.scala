package ch.ninecode.mfi

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream

import ch.ninecode.cim.CIMClasses
import ch.ninecode.model.Connector
import ch.ninecode.model.Element
import ch.ninecode.model.Terminal
import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import org.scalatest._


case class MyFeeders
(
    session: SparkSession
)
    extends Feeders (session)
{
    override def isFeeder (element: Element, terminal: Terminal): Boolean =
    {
        element match
        {
            case c: Connector ⇒
                val equipment = c.ConductingEquipment
                (equipment.BaseVoltage == "BaseVoltage_400") &&
                    equipment.Equipment.EquipmentContainer.startsWith ("STA")
            case _ ⇒ false
        }
    }
}

class FeederSuite extends fixture.FunSuite with BeforeAndAfter
{
    val FILE_DEPOT = "data/"
    val FILENAME = "DemoData"

    type FixtureParam = SparkSession

    def using[T <: AutoCloseable, R] (resource: T)(block: T => R): R =
    {
        try
        {
            block (resource)
        }
        finally
        {
            resource.close ()
        }
    }

    /**
     * This utility extracts files and directories of a standard zip file to
     * a destination directory.
     *
     * @author www.codejava.net
     *
     */
    class Unzip
    {
        /**
         * Extracts a zip file specified by the file to a directory.
         *
         * The directory will be created if does not exist.
         *
         * @param file      The Zip file.
         * @param directory The directory to extract it to
         * @throws IOException If there is a problem with the zip extraction
         */
        @throws[IOException]
        def unzip (file: String, directory: String): Unit =
        {
            val dir = new File (directory)
            if (!dir.exists)
                dir.mkdir
            using (new ZipInputStream (new FileInputStream (file)))
            {
                zip =>
                    var entry = zip.getNextEntry
                    // iterates over entries in the zip file
                    while (null != entry)
                    {
                        val path = directory + entry.getName
                        if (!entry.isDirectory)
                            // if the entry is a file, extract it
                            extractFile (zip, path)
                        else
                            // if the entry is a directory, make the directory
                            new File (path).mkdir
                        zip.closeEntry ()
                        entry = zip.getNextEntry
                    }
            }
        }

        /**
         * Extracts a zip entry (file entry).
         *
         * @param zip  The Zip input stream for the file.
         * @param path The path to extract he file to.
         * @throws IOException If there is a problem with the zip extraction
         */
        @throws[IOException]
        private def extractFile (zip: ZipInputStream, path: String): Unit =
        {
            val bytesIn = new Array[Byte](4096)
            using (new BufferedOutputStream (new FileOutputStream (path)))
            {
                bos =>
                    var read = -1
                    while (
                    {
                        read = zip.read (bytesIn)
                        read != -1
                    })
                    bos.write (bytesIn, 0, read)
            }
        }
    }

    before
    {
        // unpack the zip files
        if (!new File (s"$FILE_DEPOT$FILENAME.rdf").exists)
            new Unzip ().unzip (s"$FILE_DEPOT$FILENAME.zip", FILE_DEPOT)
    }

    after
    {
        new File (s"$FILE_DEPOT$FILENAME.rdf").delete
    }

    def withFixture (test: OneArgTest): org.scalatest.Outcome =
    {
        // create the fixture
        val start = System.nanoTime ()

        // create the configuration
        val configuration = new SparkConf (false)
        configuration.setAppName ("GridLABDSuite")
        configuration.setMaster ("local[2]")
        configuration.set ("spark.driver.memory", "2g")
        configuration.set ("spark.executor.memory", "2g")
        configuration.set ("spark.sql.warehouse.dir", "file:///tmp/")
        configuration.set ("spark.ui.showConsoleProgress", "false")

        // register CIMReader classes
        configuration.registerKryoClasses (CIMClasses.list)
        // register Einspeiseleistung classes
        configuration.registerKryoClasses (Einspeiseleistung.classes)

        val session = SparkSession.builder ().config (configuration).getOrCreate () // create the fixture
        session.sparkContext.setLogLevel ("WARN") // Valid log levels include: ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN

        val end = System.nanoTime ()
        println ("setup : " + (end - start) / 1e9 + " seconds")
        try
        {
            withFixture (test.toNoArgTest (session)) // "loan" the fixture to the test
        }
        finally session.stop () // clean up the fixture
    }

    /**
     * Test getFeeders.
     */
    test ("getFeeders")
    {
        session: SparkSession ⇒

            val begin = System.nanoTime ()

            val filename = s"$FILE_DEPOT$FILENAME.rdf"
            val opts = Map[String,String](
                "ch.ninecode.cim.do_topo_islands" -> "true",
                "ch.ninecode.cim.force_retain_switches" -> "ForceTrue",
                "ch.ninecode.cim.force_retain_fuses" -> "ForceTrue")
            val elements = session.read.format ("ch.ninecode.cim").options (opts).load (filename)
            val count = elements.count
            val read = System.nanoTime ()
            println (s"read ${count} elements in ${(read - begin) / 1e9} seconds")

            val feeders = MyFeeders (session).getFeeders ()
            val nfeeders = feeders.collect.length
            println (s"found $nfeeders feeders in ${(System.nanoTime () - read) / 1e9} seconds")

            assert (nfeeders == 62, "feeder count")
            val bad = feeders.filter (_.control == null)
            assert (bad.count == 0, "bad feeder count")
    }
}
