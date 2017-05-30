package ch.ninecode.sc

import java.util.HashMap
import java.util.Map

import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import org.apache.spark.sql.SQLContext
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel

import org.scalatest.fixture.FunSuite

import ch.ninecode.cim._
import ch.ninecode.model._

class ShortCircuitSuite extends FunSuite
{
    val FILE_DEPOT = "private_data/"

    type FixtureParam = SparkSession

    def withFixture (test: OneArgTest): org.scalatest.Outcome =
    {
        // create the fixture

        // create the configuration
        val configuration = new SparkConf (false)
        configuration.setAppName ("ShortCircuitSuite")
        configuration.setMaster ("local[2]")
        configuration.set ("spark.driver.memory", "1g")
        configuration.set ("spark.executor.memory", "4g")
        configuration.set ("spark.ui.showConsoleProgress", "false")

        // register low level classes
        configuration.registerKryoClasses (Array (classOf[Element], classOf[BasicElement], classOf[Unknown]))
        // register CIM case classes
        CHIM.apply_to_all_classes { x => configuration.registerKryoClasses (Array (x.runtime_class)) }
        // register edge related classes
        configuration.registerKryoClasses (Array (classOf[PreEdge], classOf[Extremum], classOf[PostEdge]))
        // register short circuit classes
        configuration.registerKryoClasses (Array (classOf[ShortCircuitData], classOf[TData], classOf[Message], classOf[VertexData]))
        // register short circuit inner classes
        configuration.registerKryoClasses (Array (classOf[EdgePlus], classOf[TransformerName], classOf[HouseConnection], classOf[Result]))

        val session = SparkSession.builder.config (configuration).getOrCreate // create the fixture
        session.sparkContext.setLogLevel ("OFF") // Valid log levels include: ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN
        try
        {
            withFixture (test.toNoArgTest (session)) // "loan" the fixture to the test
        }
        finally session.stop // clean up the fixture
    }

    test ("Basic")
    {
        session: SparkSession ⇒

        val filename = FILE_DEPOT + "bkw_cim_export_sias_current_20161220_Haelig_no_EEA7355_or_EEA5287" + ".rdf"

        val start = System.nanoTime
        val files = filename.split (",")
        val options = new HashMap[String, String] ().asInstanceOf[Map[String,String]]
        options.put ("path", filename)
        options.put ("StorageLevel", "MEMORY_AND_DISK_SER");
        options.put ("ch.ninecode.cim.make_edges", "true");
        val elements = session.sqlContext.read.format ("ch.ninecode.cim").options (options).load (files:_*)
        val count = elements.count

        val read = System.nanoTime

        val sc_options = ShortCircuitOptions (csv_file = FILE_DEPOT + "KS_Leistungen.csv", transformer="all")
        val shortcircuit = ShortCircuit (session, StorageLevel.MEMORY_AND_DISK_SER, sc_options)

        val prep = System.nanoTime

        val rdd = shortcircuit.run

        val graph = System.nanoTime

        val results = rdd.collect

        val fetch = System.nanoTime

        println (s"""id,Name,ik,ik3pol,ip,Transformer,r,x,r0,x0,wires_valid,trafo_valid,fuse_valid,x,y,fuses""")
        for (i <- 0 until results.length)
        {
            val h = results (i)
            println (h.getString(0) + "," + h.getString(1) + "," + h.getDouble(8) + "," + h.getDouble(9) + "," + h.getDouble(10) + "," + h.getString(2) + "," + h.getDouble(3) + "," + h.getDouble(4) + "," + h.getDouble(5) + "," + h.getDouble(6) + "," + h.getBoolean(11) + "," + h.getBoolean(12) + "," + h.getBoolean(13) + "," + h.getString(14) + "," + h.getString(15) + "," + h.getString(7))
        }

        println ("" + count + " elements")
        println ("read : " + (read - start) / 1e9 + " seconds")
        println ("prep : " + (prep - read) / 1e9 + " seconds")
        println ("graph: " + (graph - prep) / 1e9 + " seconds")
        println ("fetch: " + (fetch - graph) / 1e9 + " seconds")
        println ("print: " + (System.nanoTime - fetch) / 1e9 + " seconds")
        println;

    }

}
