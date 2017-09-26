package ch.ninecode.geo

import java.util.HashMap
import java.util.Map

import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import org.apache.spark.sql.execution.QueryExecution
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.SQLContext
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel

import org.scalatest.fixture.FunSuite

import ch.ninecode._
import ch.ninecode.cim._
import ch.ninecode.model._

class GeoSuite extends FunSuite
{
    val FILE_DEPOT = "private_data/"

    type FixtureParam = SparkSession

    def withFixture (test: OneArgTest): org.scalatest.Outcome =
    {
        // create the fixture
        val start = System.nanoTime ()

        // create the configuration
        val configuration = new SparkConf (false)
        configuration.setAppName ("ShortCircuitSuite")
        configuration.setMaster ("local[2]")
        configuration.set ("spark.driver.memory", "1g")
        configuration.set ("spark.executor.memory", "4g")

        // register low level classes
        configuration.registerKryoClasses (Array (classOf[Element], classOf[BasicElement], classOf[Unknown]))
        // register CIM case classes
        CHIM.apply_to_all_classes { x => configuration.registerKryoClasses (Array (x.runtime_class)) }
        // register edge related classes
        configuration.registerKryoClasses (Array (classOf[PreEdge], classOf[Extremum], classOf[PostEdge]))

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
        val options = new HashMap[String, String] ().asInstanceOf[Map[String,String]]
        options.put ("path", filename)
        options.put ("StorageLevel", "MEMORY_AND_DISK_SER")
        options.put ("ch.ninecode.cim.make_edges", "false")
        options.put ("ch.ninecode.cim.do_join", "false")
        options.put ("ch.ninecode.cim.do_topo", "false")
        options.put ("ch.ninecode.cim.do_topo_islands", "false")
        val element = context.read.format ("ch.ninecode.cim").options (options).load (files:_*)

        return (element)
    }

    test ("Basic")
    {
        session: SparkSession ⇒

        val start = System.nanoTime ()

        val filename =
        FILE_DEPOT + "NIS_CIM_Export_sias_current_20160816_Kiental_V10" + ".rdf"
        val elements = readFile (session.sqlContext, filename)
        println (elements.count () + " elements")
        val read = System.nanoTime ()

        val visualize = new ch.ninecode.geo.GeoVis ()
//        val results = visualize.extract (context, sql_context, "xmin=7.71,ymin=46.57,xmax=7.73,ymax=46.60")
        val results = visualize.extract_json (session.sparkContext, session.sqlContext, "xmin=7.71,ymin=46.57,xmax=7.73,ymax=46.60")
        println (results)
        val process = System.nanoTime ()


//        for (record <- results)
//            println (record)
//
//        println ("read : " + (read - start) / 1e9 + " seconds")
//        println ("process: " + (process - read) / 1e9 + " seconds")
//        println ()
//
//        for (record <- results)
//        {
//            val c = record.getStruct (0)
//            println (c.getString (0) + " = " + c.getString (2) + ": " + c.getString (1) + " @ " + c.getString (4))
//            val d = record.getSeq[String] (1)
//            for (x <- d)
//                println (x)
//        }

    }

}
