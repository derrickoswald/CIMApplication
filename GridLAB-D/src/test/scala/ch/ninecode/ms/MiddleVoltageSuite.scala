package ch.ninecode.ms

import java.nio.file.Files
import java.nio.file.Paths
import java.util.Calendar
import java.util.HashMap

import org.apache.spark.SparkConf
import org.apache.spark.graphx.Graph
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.scalatest.fixture.FunSuite

import ch.ninecode.cim._
import ch.ninecode.model._

import javax.xml.bind.DatatypeConverter

class MiddleVoltageSuite extends FunSuite
{
    val FILE_DEPOT = "private_data/"

    type FixtureParam = SparkSession

    def withFixture (test: OneArgTest): org.scalatest.Outcome =
    {
        // create the fixture
        val start = System.nanoTime ()

        // create the configuration
        val configuration = new SparkConf (false)
        configuration.setAppName ("MiddleVoltageSuite")
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
        configuration.registerKryoClasses (Array (classOf[PreEdge], classOf[Extremum], classOf[PostEdge]))
        // register topological classes
        configuration.registerKryoClasses (Array (classOf[CuttingEdge], classOf[TopologicalData]))
        // register GridLAB-D classes
        configuration.registerKryoClasses (Array (
            classOf[ch.ninecode.gl.PreNode],
            classOf[ch.ninecode.gl.PreEdge],
            classOf[ch.ninecode.gl.ThreePhaseComplexDataElement]))
        // register Einspeiseleistung classes
        configuration.registerKryoClasses (Array (
            classOf[ch.ninecode.esl.Experiment],
            classOf[ch.ninecode.esl.MaxEinspeiseleistung],
            classOf[ch.ninecode.esl.MaxPowerFeedingNodeEEA],
            classOf[ch.ninecode.esl.PV],
            classOf[ch.ninecode.esl.PowerFeedingNode],
            classOf[ch.ninecode.esl.PreCalculationResults],
            classOf[ch.ninecode.esl.Trafokreis],
            classOf[ch.ninecode.esl.StartingTrafos]))
        configuration.set ("spark.ui.showConsoleProgress", "false")

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

    test ("Basic")
    {
        session: SparkSession ⇒

        val begin = System.nanoTime ()

        val root = "bkw_cim_export_sias_current_20161220_Wohlen_bei_Bern"
        val filename =
            FILE_DEPOT + root + ".rdf"

        val options = MiddleVoltageOptions (
            verbose = true,
            cim_reader_options = scala.collection.mutable.HashMap[String, String] (),
            three = false,
            trafos = "",
            export_only = false,
            erase = false,
            short_circuit = "",
            files = List(filename)
        )
        val ms = MiddleVoltage (session, options)
        val count = ms.run ()

        val total = System.nanoTime ()
        println ("total: " + (total - begin) / 1e9 + " seconds " + count + " UST\n")
    }
}
