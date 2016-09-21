package ch.ninecode.sp

import java.util.HashMap
import java.util.Map

import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import org.apache.spark.sql.execution.QueryExecution
import org.apache.spark.sql.DataFrame
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
        configuration.registerKryoClasses (Array (classOf[PreEdge], classOf[Extremum], classOf[Edge]))

        val context = new SparkContext (configuration)
        context.setLogLevel ("INFO") // Valid log levels include: ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN
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
        val options = new HashMap[String, String] ().asInstanceOf[Map[String,String]]
        options.put ("StorageLevel", "MEMORY_AND_DISK_SER");
        options.put ("ch.ninecode.cim.make_edges", "false");
        options.put ("ch.ninecode.cim.do_join", "true");
        val element = context.read.format ("ch.ninecode.cim").options (options).load (files:_*)
        val plan = element.queryExecution
        val test = plan.toString ()
        if (!test.contains ("InputPaths"))
            throw new Exception ("input file not found: " + filename)

        return (element)
    }

    test ("Basic")
    {
        a: ContextPair ⇒

        val start = System.nanoTime ()

        val context: SparkContext = a._SparkContext
        val sql_context: SQLContext = a._SQLContext

        val filename =
        FILE_DEPOT + "NIS_CIM_Export_sias_current_20160816_V8_Bruegg" + ".rdf" +
        "," +
        FILE_DEPOT + "ISU_CIM_Export_20160505" + ".rdf"
        val elements = readFile (sql_context, filename)

        val read = System.nanoTime ()

        val spatial = new ch.ninecode.sp.SpatialOperations ()
        spatial._StorageLevel = StorageLevel.MEMORY_AND_DISK_SER
        val results = spatial.nearest (context, sql_context, "psr=EnergyConsumer,lon=7.281558,lat=47.124142,n=5")
        val array = results.collect ()

        val process1 = System.nanoTime ()

        val results2 = spatial.nearest (context, sql_context, "psr=EnergyConsumer,lon=7.301368,lat=47.104892,n=5")
        val array2 = results2.collect ()

        val process2 = System.nanoTime ()

        var text = array (0).toString ()
        println (text)
        // [[[[[[null,HAS164036],269859107:nis_el_house_service,null,HAS164036,HAS164036],null,_location_654219_565962113_269859109,PSRType_Unknown],false,false,_line_ABG23661|HAS164036|KLE457618],BaseVoltage_400,null,null,null],0,false,0.0,0.0,0.0,null,0.0,0.0,0.0,null,null,null]
        assert (text.contains ("HAS164036"))

        text = array2 (0).toString ()
        println (text)
        // [[[[[[null,HAS42693],209444066:nis_el_house_service,null,HAS42693,HAS42693],null,_location_655173_976061239_209444068,PSRType_Unknown],false,false,_line_ABG159742|HAS42693|KLE97274],BaseVoltage_400,null,null,null],0,false,0.0,0.0,0.0,null,0.0,0.0,0.0,null,null,null]
        assert (text.contains ("HAS42693"))

        println ("read : " + (read - start) / 1e9 + " seconds")
        println ("process first location: " + (process1 - read) / 1e9 + " seconds")
        println ("process second location: " + (process2 - process1) / 1e9 + " seconds")
        println ()

        // setup : 5.444885289 seconds
        // read : 3.575346462 seconds
        // process first location: 22.219852666 seconds
        // process second location: 2.911512529 seconds
    }

}
