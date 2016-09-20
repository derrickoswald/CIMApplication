package ch.ninecode.gl

import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import org.apache.spark.sql.SQLContext
import org.apache.spark.storage.StorageLevel

import org.scalatest.fixture

import ch.ninecode._
import ch.ninecode.cim._
import ch.ninecode.model._

class GridLABDSuite extends fixture.FunSuite
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
        // ToDo: register GridLAB-D classes

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

        assert(1==1)
    }
}
