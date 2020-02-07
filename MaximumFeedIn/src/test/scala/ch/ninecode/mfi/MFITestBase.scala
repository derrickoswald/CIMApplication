package ch.ninecode.mfi

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.sql.DriverManager
import java.util.zip.ZipInputStream

import ch.ninecode.cim.CIMClasses
import ch.ninecode.gl.GridLABD
import com.sun.rowset.CachedRowSetImpl
import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import org.scalatest.fixture

class MFITestBase extends fixture.FunSuite
{

    type FixtureParam = SparkSession
    val FILE_DEPOT = "data/"
    val PRIVATE_FILE_DEPOT = "private_data/"

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

    def withFixture (test: OneArgTest): org.scalatest.Outcome =
    {
        // create the fixture
        val start = System.nanoTime ()

        // create the configuration
        val configuration = new SparkConf (false)
        configuration.setAppName (this.getClass.getSimpleName)
        configuration.setMaster ("local[2]")
        configuration.set ("spark.driver.memory", "2g")
        configuration.set ("spark.executor.memory", "2g")
        configuration.set ("spark.sql.warehouse.dir", "file:///tmp/")
        configuration.set ("spark.ui.showConsoleProgress", "false")

        // register CIMReader classes
        configuration.registerKryoClasses (CIMClasses.list)
        // register GridLAB-D classes
        configuration.registerKryoClasses (GridLABD.classes)
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

    def readFile (session: SparkSession, filename: String): Unit =
    {
        val files = filename.split (",")
        val options = new java.util.HashMap[String, String]()
        options.put ("path", filename)
        options.put ("StorageLevel", "MEMORY_AND_DISK_SER")
        options.put ("ch.ninecode.cim.make_edges", "false")
        options.put ("ch.ninecode.cim.do_join", "false")
        options.put ("ch.ninecode.cim.do_topo", "true")
        options.put ("ch.ninecode.cim.do_topo_islands", "true")
        val elements = session.read.format ("ch.ninecode.cim").options (options).load (files: _*)
        println (elements.count () + " elements")
    }

    def getMaxSimulation (databasePath: String): String =
    {
        val query = "select max(simulation) from results"
        val result = querySQLite (databasePath, query)
        assert (result.next, "no results found")
        result.getString (1)
    }

    def querySQLite (databasePath: String, sqlStatement: String): CachedRowSetImpl =
    {
        // load the sqlite-JDBC driver using the current class loader
        Class.forName ("org.sqlite.JDBC")
        // create a database connection
        val connection = DriverManager.getConnection (s"jdbc:sqlite:$databasePath")

        val statement = connection.createStatement ()
        val resultset = statement.executeQuery (sqlStatement)
        val crs = new CachedRowSetImpl
        crs.populate (resultset)
        resultset.close ()
        statement.close ()
        connection.close ()
        crs
    }

    def near (number: Double, reference: Double, epsilon: Double = 1.0e-3): Boolean =
    {
        val diff = number - reference
        val ret = Math.abs (diff) < epsilon
        if (!ret)
            println ("""%s vs. reference %s differs by more than %s (%s)""".format (number, reference, epsilon, diff))
        ret
    }

    def checkResults (result: CachedRowSetImpl, max: Double, reason: String, details: String): Unit =
    {
        val house = result.getString ("House")
        val maximum = result.getDouble ("Maximum")
        assert (Math.abs (maximum - max) <= 1000, s"maximum for $house is $maximum instead of $max")
        assert (result.getString ("Reason") == reason, s"reason for $house")
        assert (result.getString ("Details") == details, s"details for $house")
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

}
