package ch.ninecode.esl

import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import org.scalatest.fixture.FunSuite

import ch.ninecode.cim.CHIM
import ch.ninecode.cim.PreEdge
import ch.ninecode.cim.PostEdge
import ch.ninecode.cim.Extremum
import ch.ninecode.cim.CuttingEdge
import ch.ninecode.cim.TopologicalData
import ch.ninecode.model.Element
import ch.ninecode.model.BasicElement
import ch.ninecode.model.Unknown

class GridLABDSuite extends FunSuite
{
    val FILE_DEPOT = "private_data/"

    type FixtureParam = SparkSession

    def withFixture (test: OneArgTest): org.scalatest.Outcome =
    {
        // create the fixture
        val start = System.nanoTime ()

        // create the configuration
        val configuration = new SparkConf (false)
        configuration.setAppName ("GridLABDSuite")
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
            classOf[ch.ninecode.gl.PV],
            classOf[ch.ninecode.gl.ThreePhaseComplexDataElement]))
        // register Einspeiseleistung classes
        configuration.registerKryoClasses (Array (
            classOf[ch.ninecode.esl.Experiment],
            classOf[ch.ninecode.esl.MaxEinspeiseleistung],
            classOf[ch.ninecode.esl.MaxPowerFeedingNodeEEA],
            classOf[ch.ninecode.esl.PowerFeedingNode],
            classOf[ch.ninecode.esl.PreCalculationResults],
            classOf[ch.ninecode.esl.Trafokreis],
            classOf[ch.ninecode.esl.StartingTrafos]))
        configuration.set ("spark.ui.showConsoleProgress", "false")

        val session = SparkSession.builder ().config (configuration).getOrCreate () // create the fixture
        session.sparkContext.setLogLevel ("WARN") // Valid log levels include: ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN

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

        val root = if (false)
            "bkw_cim_export_sias_current_20161220_Haelig"
        else
            "bkw_cim_export_sias_current_20161220_Haelig_no_EEA7355"
        val filename =
            FILE_DEPOT + root + ".rdf"

        val options = EinspeiseleistungOptions (
            verbose = true,
            cim_reader_options = scala.collection.mutable.HashMap[String, String] (),
            three = false,
            precalculation = false,
            trafos = "",
            export_only = false,
            all = true,
            erase = false,
            simulation = -1,
            reference = -1,
            delta = 1e-6,
            number = -1,
            precalc_factor = 1.5,
            workdir = "file://" + System.getProperty ("user.dir") + "/simulation/",
            files = List(filename)
        )
        val eins = Einspeiseleistung (session, options)
        val count = eins.run ()

        val total = System.nanoTime ()
        println ("total: " + (total - begin) / 1e9 + " seconds " + count + " trafokreise\n")
    }
}
