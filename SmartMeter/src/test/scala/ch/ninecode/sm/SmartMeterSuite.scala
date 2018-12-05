package ch.ninecode.sm

import java.util.HashMap

import org.apache.spark.SparkConf
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.SQLContext
import org.apache.spark.sql.SparkSession

import org.scalatest.fixture.FunSuite

import ch.ninecode.cim.CIMClasses

class SmartMeterSuite extends FunSuite
{
    type FixtureParam = SparkSession

    def withFixture (test: OneArgTest): org.scalatest.Outcome =
    {
        // create the fixture
        val start = System.nanoTime ()

        // create the configuration
        val configuration = new SparkConf (false)
        configuration.setAppName ("SmartMeterSuite")
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

    def readFile (context: SQLContext, filename: String, use_topolocial_nodes: Boolean): DataFrame =
    {
        val files = filename.split (",")
        val options = new HashMap[String, String]()
        options.put ("path", filename)
        options.put ("StorageLevel", "MEMORY_AND_DISK_SER")
        options.put ("ch.ninecode.cim.make_edges", "false")
        options.put ("ch.ninecode.cim.do_join", "false")
        options.put ("ch.ninecode.cim.do_topo", use_topolocial_nodes.toString)
        options.put ("ch.ninecode.cim.do_topo_islands", "false")
        context.read.format ("ch.ninecode.cim").options (options).load (files: _*)
    }

    test ("Basic")
    {
        session: SparkSession ⇒
            println ("placeholder")
    }
}
