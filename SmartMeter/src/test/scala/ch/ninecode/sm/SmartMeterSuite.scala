package ch.ninecode.sm

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.util.HashMap

import org.apache.spark.SparkConf

import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.SQLContext
import org.apache.spark.sql.SparkSession

import org.scalatest.fixture.FunSuite

import ch.ninecode.cim.CIMClasses

class SmartMeterSuite extends FunSuite
{
    val FILE_DEPOT = "private_data/"

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
        configuration.set ("spark.executor.memory", "4g")
        //configuration.set ("spark.executor.extraJavaOptions", "-XX:+UseCompressedOops")
        configuration.set ("spark.executor.extraJavaOptions", "-XX:+UseCompressedOops -XX:+PrintGCDetails -XX:+PrintGCTimeStamps")

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
        val options = new HashMap[String, String] ()
        options.put ("path", filename)
        options.put ("StorageLevel", "MEMORY_AND_DISK_SER")
        options.put ("ch.ninecode.cim.make_edges", "false")
        options.put ("ch.ninecode.cim.do_join", "false")
        options.put ("ch.ninecode.cim.do_topo", use_topolocial_nodes.toString)
        options.put ("ch.ninecode.cim.do_topo_islands", "false")
        context.read.format ("ch.ninecode.cim").options (options).load (files:_*)
    }

    test ("Basic")
    {
        session: SparkSession ⇒

        val start = System.nanoTime ()

        val filename = FILE_DEPOT + "NIS_CIM_Export_sias_current_20160703_Hirzel_Kirche_V9_dummyDaten.rdf"
        val use_topological_nodes = true
        val starting_node = "SAM10106"
            
        val elements = readFile (session.sqlContext, filename, use_topological_nodes)
        println (elements.count () + " elements")
        val read = System.nanoTime ()

        val smart = new SmartMeter ()
        val text = smart.run (session.sparkContext, session.sqlContext, starting_node, use_topological_nodes)
        
        val process = System.nanoTime ()
        
        println ("traced nodes: " )
        println (text)
        
        val out_content = "smartmeter_tree = " + text
        Files.write (Paths.get (FILE_DEPOT, "smartmeter_tree.js"), out_content.getBytes(StandardCharsets.UTF_8))

        val write = System.nanoTime ()

        println ("read : " + (read - start) / 1e9 + " seconds")
        println ("process: " + (process - read) / 1e9 + " seconds")
        println ("write: " + (write - process) / 1e9 + " seconds")
        println ()
    }

}
