package ch.ninecode.spatial

import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import org.apache.spark.sql.SQLContext
import org.apache.spark.storage.StorageLevel

import org.scalatest.fixture

import ch.ninecode._
import ch.ninecode.cim._
import ch.ninecode.model._

class SpatialSuite extends fixture.FunSuite
{
    case class ContextPair (_SparkContext: SparkContext, _SQLContext: SQLContext)

    val FILE_DEPOT = "/home/derrick/Documents/9code/nis/cim/cim_export/"

    type FixtureParam = ContextPair

    def withFixture (test: OneArgTest): org.scalatest.Outcome =
    {
        // create the fixture

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
        configuration.registerKryoClasses (Array (classOf[PreEdge], classOf[Extremum], classOf[Edge]))

        val context = new SparkContext (configuration)
        context.setLogLevel ("INFO") // Valid log levels include: ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN
        val sql_context = new SQLContext (context)
        try
        {
            withFixture (test.toNoArgTest (ContextPair (context, sql_context))) // "loan" the fixture to the test
        }
        finally context.stop () // clean up the fixture
    }

    test ("Basic")
    {
        a: ContextPair ⇒

        val context: SparkContext = a._SparkContext
        val sql_context: SQLContext = a._SQLContext

        val filename = FILE_DEPOT + "NIS_CIM_Export_sias_current_20160816_V8_Bruegg" + ".rdf"

        val start = System.nanoTime ()

        val elements = sql_context.read.format ("ch.ninecode.cim").option ("StorageLevel", "MEMORY_AND_DISK_SER").load (filename)
        val count = elements.count

        val read = System.nanoTime ()

        val spatial = new ch.ninecode.spatial.SpatialOperations ()
        spatial._StorageLevel = StorageLevel.MEMORY_AND_DISK_SER
        val results = spatial.nearest (context, sql_context, "psr=EnergyConsumer,lon=7.281558,lat=47.124142,n=5")
        val array = results.collect ()
        println (array (0))

        println ("" + count + " elements")
        println ("read : " + (read - start) / 1e9 + " seconds")
        println ("print: " + (System.nanoTime () - read) / 1e9 + " seconds")
        println ();

    }

}
