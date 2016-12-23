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
import org.apache.spark.storage.StorageLevel

import org.scalatest.fixture

import ch.ninecode.cim._
import ch.ninecode.model._

class SmartMeterSuite extends fixture.FunSuite
{
    case class ContextPair (_SparkContext: SparkContext, _SQLContext: SQLContext)

    val FILE_DEPOT = "/home/derrick/Documents/9code/nis/cim/cim_export/"

    type FixtureParam = ContextPair

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

        val context = new SparkContext (configuration)
        context.setLogLevel ("WARN") // Valid log levels include: ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN
        val sql_context = new SQLContext (context)

        val end = System.nanoTime ()
        println ("setup : " + (end - start) / 1e9 + " seconds")
        try
        {
            withFixture (test.toNoArgTest (ContextPair (context, sql_context))) // "loan" the fixture to the test
        }
        finally context.stop () // clean up the fixture
    }

    def readFile (context: SQLContext, filename: String): DataFrame =
    {
        val files = filename.split (",")
        val options = new HashMap[String, String] ()
        options.put ("StorageLevel", "MEMORY_AND_DISK_SER")
        options.put ("ch.ninecode.cim.make_edges", "false")
        options.put ("ch.ninecode.cim.do_join", "true")
        options.put ("ch.ninecode.cim.do_topo", "true")
        options.put ("ch.ninecode.cim.do_topo_islands", "true")
        val element = context.read.format ("ch.ninecode.cim").options (options).load (files:_*)
        val plan = element.queryExecution
        val test = plan.toString ()
        if (!test.contains ("InputPaths"))
            throw new Exception ("input file not found: " + filename + "\n" + test)

        return (element)
    }

    test ("Basic")
    {
        a: ContextPair ⇒

        val start = System.nanoTime ()

        val context: SparkContext = a._SparkContext
        val sql_context: SQLContext = a._SQLContext

        val filename =
            FILE_DEPOT + "NIS_CIM_Export_sias_current_20160816_Wildenrueti_V9" + ".rdf"

        val elements = readFile (sql_context, filename)

        val read = System.nanoTime ()

        val smart = SmartMeter ()
        val text = smart.run (context, sql_context, "ABG91246")
        println (text)

        val process = System.nanoTime ()

        val write = System.nanoTime ()

        println ("read : " + (read - start) / 1e9 + " seconds")
        println ("process: " + (process - read) / 1e9 + " seconds")
        println ("write: " + (write - process) / 1e9 + " seconds")
        println ()
    }

}
