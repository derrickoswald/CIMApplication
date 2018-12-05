package ch.ninecode.sp

import java.util.HashMap
import java.util.Map

import org.apache.spark.SparkConf
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.SQLContext
import org.apache.spark.sql.SparkSession
import org.scalatest.fixture.FunSuite

import ch.ninecode.cim.CIMClasses

class SpatialSuite extends FunSuite
{
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
        val options = new HashMap[String, String]().asInstanceOf [Map[String, String]]
        options.put ("path", filename)
        options.put ("StorageLevel", "MEMORY_AND_DISK_SER")
        context.read.format ("ch.ninecode.cim").options (options).load (files: _*)
    }

    test ("Basic")
    {
        session: SparkSession ⇒
            println ("placeholder")
    }

}
