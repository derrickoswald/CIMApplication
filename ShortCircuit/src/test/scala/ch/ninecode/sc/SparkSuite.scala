package ch.ninecode.sc

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util
import java.util.zip.ZipInputStream

import scala.collection.JavaConverters._
import org.scalatest.fixture.FunSuite

import org.apache.spark.SparkConf
import org.apache.spark.graphx.GraphXUtils
import org.apache.spark.sql.SparkSession

import ch.ninecode.cim.CIMClasses
import ch.ninecode.gl.GridLABD

class SparkSuite extends FunSuite
{
    type FixtureParam = SparkSession

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
            val zip = new ZipInputStream (new FileInputStream (file))
            var entry = zip.getNextEntry
            // iterates over entries in the zip file
            while (null != entry)
            {
                val path = directory + entry.getName
                if (!entry.isDirectory)
                // if the entry is a file, extracts it
                    extractFile (zip, path)
                else
                // if the entry is a directory, make the directory
                    new File (path).mkdir
                zip.closeEntry ()
                entry = zip.getNextEntry
            }
            zip.close ()
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
            val bos = new BufferedOutputStream (new FileOutputStream (path))
            val bytesIn = new Array[Byte](4096)
            var read = -1
            while (
            {
                read = zip.read (bytesIn); read != -1
            })
                bos.write (bytesIn, 0, read)
            bos.close ()
        }
    }

    def withFixture (test: OneArgTest): org.scalatest.Outcome =
    {
        org.apache.log4j.LogManager.getLogger ("ch.ninecode.sc.Main$").setLevel (org.apache.log4j.Level.INFO)
        org.apache.log4j.LogManager.getLogger ("ch.ninecode.sc.ShortCircuit").setLevel (org.apache.log4j.Level.INFO)
        org.apache.log4j.LogManager.getLogger ("ch.ninecode.sc.Database").setLevel (org.apache.log4j.Level.INFO)

        // create the configuration
        val configuration = new SparkConf (false)
        configuration.setAppName ("ShortCircuitSuite")
        configuration.setMaster ("local[2]")
        configuration.set ("spark.driver.memory", "1g")
        configuration.set ("spark.executor.memory", "2g")
        configuration.set ("spark.ui.port", "4041")
        configuration.set ("spark.ui.showConsoleProgress", "false")
        configuration.set ("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
        configuration.set ("spark.sql.warehouse.dir", "file:///tmp/")

        // register CIMReader classes
        configuration.registerKryoClasses (CIMClasses.list)
        // register GridLAB-D classes
        configuration.registerKryoClasses (GridLABD.classes)
        // register ShortCircuit analysis classes
        configuration.registerKryoClasses (ShortCircuit.classes)
        // register GraphX classes
        GraphXUtils.registerKryoClasses (configuration)

        // create the fixture
        val session = SparkSession.builder.config (configuration).getOrCreate // create the fixture
        try
        {
            withFixture (test.toNoArgTest (session)) // "loan" the fixture to the test
        }
        finally session.stop // clean up the fixture
    }
}
