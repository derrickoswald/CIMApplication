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
    val FILE_DEPOT = "/home/derrick/Documents/9code/nis/cim/cim_export/"

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

        // register low level classes
        configuration.registerKryoClasses (Array (classOf[Element], classOf[BasicElement], classOf[Unknown]))
        // register CIM case classes
        CHIM.apply_to_all_classes { x => configuration.registerKryoClasses (Array (x.runtime_class)) }
        // register edge related classes
        configuration.registerKryoClasses (Array (classOf[PreEdge], classOf[Extremum], classOf[PostEdge]))
        // register short circuit classes
        configuration.registerKryoClasses (Array (classOf[ShortCircuitData], classOf[TransformerData], classOf[Message], classOf[VertexData]))
        // register short circuit inner classes
        configuration.registerKryoClasses (Array (classOf[ShortCircuit#EdgePlus], classOf[ShortCircuit#TransformerName], classOf[ShortCircuit#HouseConnection], classOf[ShortCircuit#Result]))

        val session = SparkSession.builder ().config (configuration).getOrCreate () // create the fixture
        session.sparkContext.setLogLevel ("OFF") // Valid log levels include: ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN
        try
        {
            withFixture (test.toNoArgTest (session)) // "loan" the fixture to the test
        }
        finally session.stop () // clean up the fixture
    }

    test ("Basic")
    {
        session: SparkSession ⇒

        // val filename = FILE_DEPOT + "NIS_CIM_Export_sias_current_20160816_V9_Bubenei" + ".rdf"
        val filename = FILE_DEPOT + "NIS_CIM_Export_sias_current_20160608_V9_Preview_CKW_with_filter_EWS_Jessenenstrasse" + ".rdf"

        val start = System.nanoTime ()
        val files = filename.split (",")
        val options = new HashMap[String, String] ().asInstanceOf[Map[String,String]]
        options.put ("path", filename)
        options.put ("StorageLevel", "MEMORY_AND_DISK_SER");
        options.put ("ch.ninecode.cim.make_edges", "true"); // backwards compatibility
        options.put ("ch.ninecode.cim.do_join", "false");
        val elements = session.sqlContext.read.format ("ch.ninecode.cim").options (options).load (files:_*)
        val count = elements.count

        val read = System.nanoTime ()

        val shortcircuit = new ShortCircuit ()
        shortcircuit._StorageLevel = StorageLevel.MEMORY_AND_DISK_SER
        shortcircuit.preparation (session.sparkContext, session.sqlContext, "csv=" + FILE_DEPOT + "KS_Leistungen.csv")

        val prep = System.nanoTime ()

        val rdd = shortcircuit.stuff (session.sparkContext, session.sqlContext, "transformer=all") // TRA5401

        val graph = System.nanoTime ()

        val results = rdd.collect

        val fetch = System.nanoTime ()

        println (s"""
        id,Name,ik,ik3pol,ip,Transformer,r,x,r0,x0,wires_valid,trafo_valid,fuse_valid,x,y,fuses""")
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
        println ("print: " + (System.nanoTime () - fetch) / 1e9 + " seconds")
        println ();

    }

}
