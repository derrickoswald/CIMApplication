package ch.ninecode.sp

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream

import org.apache.spark.SparkConf
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.SQLContext
import org.apache.spark.sql.SparkSession

import org.scalatest._

import ch.ninecode.cim.CIMClasses

class SpatialSuite extends fixture.FunSuite with BeforeAndAfter
{
    val FILE_DEPOT = "data/"
    val FILENAME1 = "DemoData"

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

    def withFixture (test: OneArgTest): Outcome =
    {
        // create the fixture
        val start = System.nanoTime ()

        // create the configuration
        val configuration = new SparkConf (false)
        configuration.setAppName ("SpatialOperationsSuite")
        configuration.setMaster ("local[2]")
        configuration.set ("spark.driver.memory", "2g")
        configuration.set ("spark.executor.memory", "2g")
        configuration.set ("spark.sql.warehouse.dir", "file:///tmp/")

        // register CIMReader classes
        configuration.registerKryoClasses (CIMClasses.list)

        val session = SparkSession.builder ().config (configuration).getOrCreate () // create the fixture
        session.sparkContext.setLogLevel ("OFF") // Valid log levels include: ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN

        val end = System.nanoTime ()
        println ("setup : " + (end - start) / 1e9 + " seconds")
        try
        {
            withFixture (test.toNoArgTest (session)) // "loan" the fixture to the test
        }
        finally session.stop () // clean up the fixture
    }

    def readFile (context: SQLContext, filename: String): DataFrame =
    {
        val files = filename.split (",")
        val options = new java.util.HashMap[String, String] ().asInstanceOf[java.util.Map[String,String]]
        options.put ("path", filename)
        options.put ("StorageLevel", "MEMORY_AND_DISK_SER")
        context.read.format ("ch.ninecode.cim").options (options).load (files:_*)
    }

    before
    {
        // unpack the zip files
        if (!new File (s"$FILE_DEPOT$FILENAME1.rdf").exists)
            new Unzip ().unzip (s"$FILE_DEPOT$FILENAME1.zip", FILE_DEPOT)
    }

    after
    {
        new File (FILE_DEPOT + FILENAME1).delete
    }

    test ("Basic")
    {
        session: SparkSession ⇒

            val start = System.nanoTime ()

            val filename = s"$FILE_DEPOT$FILENAME1.rdf"
            val elements = readFile (session.sqlContext, filename)
            println (s"${elements.count} elements")
            val read = System.nanoTime ()

            val spatial = new ch.ninecode.sp.SpatialOperations (session)

            val results1 = spatial.nearest (SpatialOperationParameters (lon = 5.271720, lat = 51.47120))
            val process1 = System.nanoTime ()

            val results2 = spatial.nearest (SpatialOperationParameters (lon = 5.272310, lat = 51.47024))
            val process2 = System.nanoTime ()

            results1.show
            val array1 = results1.collect ()
            var text = array1 (0).toString ()
            assert (text.contains ("USR0019"))

            results2.show
            val array2 = results2.collect ()
            text = array2 (0).toString ()
            assert (text.contains ("USR0023"))

            println ("read : " + (read - start) / 1e9 + " seconds")
            println (s"process first location: ${(process1 - read) / 1e9} seconds")
            println (s"process second location: ${(process2 - process1) / 1e9} seconds")
    }
}
