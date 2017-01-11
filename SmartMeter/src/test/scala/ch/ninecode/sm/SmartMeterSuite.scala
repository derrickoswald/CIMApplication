package ch.ninecode.sm

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Calendar
import java.util.HashMap
import java.util.Map
import javax.xml.bind.DatatypeConverter
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Types
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.sql.Timestamp
import java.util.Random

import org.apache.commons.io.FileUtils
import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.SQLContext
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel

import org.scalatest.fixture

import ch.ninecode.cim._
import ch.ninecode.model._

class SmartMeterSuite extends fixture.FunSuite
{
    val FILE_DEPOT = "src/test/resources/"

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

        // register low level classes
        configuration.registerKryoClasses (Array (classOf[Element], classOf[BasicElement], classOf[Unknown]))
        // register CIM case classes
        CHIM.apply_to_all_classes { x => configuration.registerKryoClasses (Array (x.runtime_class)) }
        // register edge related classes
        configuration.registerKryoClasses (Array (classOf[PreEdge], classOf[Extremum], classOf[Edge]))
        // register topological classes
        configuration.registerKryoClasses (Array (classOf[CuttingEdge], classOf[TopologicalData]))

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
        val element = context.read.format ("ch.ninecode.cim").options (options).load (files:_*)

        return (element)
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
        println ("traced nodes: " )
        println (text)

        val process = System.nanoTime ()

        val write = System.nanoTime ()

        println ("read : " + (read - start) / 1e9 + " seconds")
        println ("process: " + (process - read) / 1e9 + " seconds")
        println ("write: " + (write - process) / 1e9 + " seconds")
        println ()
    }

}
