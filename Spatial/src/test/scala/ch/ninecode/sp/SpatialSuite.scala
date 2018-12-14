package ch.ninecode.sp

import org.apache.spark.SparkConf
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.SQLContext
import org.apache.spark.sql.SparkSession
import ch.ninecode.cim.CIMClasses

class SpatialSuite extends org.scalatest.fixture.FunSuite
{
    val FILE_DEPOT = "data/"
    val FILENAME1 = "DemoData.rdf"

    type FixtureParam = SparkSession

    def withFixture (test: OneArgTest): org.scalatest.Outcome =
    {
        // create the fixture
        val start = System.nanoTime ()

        // create the configuration
        val configuration = new SparkConf (false)
        configuration.setAppName ("SpatialOperationsSuite")
        configuration.setMaster ("local[2]")
        configuration.set ("spark.driver.memory", "1g")
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

    test ("Basic")
    {
        session: SparkSession ⇒

            val start = System.nanoTime ()

            val filename = FILE_DEPOT + FILENAME1
            val elements = readFile (session.sqlContext, filename)
            println (elements.count () + " elements")
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
            println ("process first location: " + (process1 - read) / 1e9 + " seconds")
            println ("process second location: " + (process2 - process1) / 1e9 + " seconds")
    }
}
