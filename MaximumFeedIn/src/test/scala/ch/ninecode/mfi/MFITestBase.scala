package ch.ninecode.mfi

import ch.ninecode.cim.CIMClasses
import ch.ninecode.gl.GridLABD
import ch.ninecode.util.Sqlite
import ch.ninecode.util.Unzip
import com.sun.rowset.CachedRowSetImpl
import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import org.scalatest.fixture

class MFITestBase extends fixture.FunSuite with Sqlite with Unzip
{
    type FixtureParam = SparkSession
    val FILE_DEPOT = "data/"

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
}
