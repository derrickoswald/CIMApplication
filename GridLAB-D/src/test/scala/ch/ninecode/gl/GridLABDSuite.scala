package ch.ninecode.gl

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
            classOf[ch.ninecode.gl.Transformer],
            classOf[ch.ninecode.gl.ThreePhaseComplexDataElement]))
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

    def readFile (session: SparkSession, filename: String): RDD[Element] =
    {
        val files = filename.split (",")
        val options = new HashMap[String, String] ()
        options.put ("path", filename)
        options.put ("StorageLevel", "MEMORY_AND_DISK_SER")
        options.put ("ch.ninecode.cim.make_edges", "false")
        options.put ("ch.ninecode.cim.do_join", "false")
        options.put ("ch.ninecode.cim.do_topo", "false")
        options.put ("ch.ninecode.cim.do_topo_islands", "false")
        val elements = session.read.format ("ch.ninecode.cim").options (options).load (files:_*)
        println (elements.count () + " elements")
        val ntp = new CIMNetworkTopologyProcessor (session, StorageLevel.fromString ("MEMORY_AND_DISK_SER"))
        ntp.process (false)
    }

    test ("Basic")
    {
        session: SparkSession ⇒

        val begin = System.nanoTime ()

        val root = if (false) "bkw_cim_export_haelig" else "bkw_cim_export_haelig_no_EEA7355" // Hälig
        //val root = "NIS_CIM_Export_sias_current_20161220_Brügg bei Biel_V11_assets_preview" // Brügg
        val filename =
            FILE_DEPOT + root + ".rdf"
        val elements = readFile (session, filename)

        val read = System.nanoTime ()
        println ("read : " + (read - begin) / 1e9 + " seconds")

        // set up for execution
        val gridlabd = new GridLABD (session)
        gridlabd.HDFS_URI = "" // local
        gridlabd.STORAGE_LEVEL = StorageLevel.MEMORY_AND_DISK_SER
        gridlabd.DELETE_SIMULATION_FILES = false

        val _transformers = new Transformers (session, gridlabd.STORAGE_LEVEL)
        val tdata = _transformers.getTransformerData (null)
        tdata.persist (gridlabd.STORAGE_LEVEL)
        val cdata = gridlabd.getCableMaxCurrent ()
        cdata.persist (gridlabd.STORAGE_LEVEL)
        val sdata = gridlabd.getSolarInstallations (true)
        sdata.persist (gridlabd.STORAGE_LEVEL)

        // determine the set of transformers to work on
        var transformers = {
            // do all low voltage power transformers
            // ToDo: fix this 1kV multiplier on the voltages
            val niederspannug = tdata.filter ((td) => td.voltage0 != 0.4 && td.voltage1 == 0.4)
            niederspannug.groupBy (_.terminal1.TopologicalNode).values.map (_.toArray).collect
        }

        // prepare the initial graph
        val (xedges, xnodes) = gridlabd.prepare ()

        val prepare = System.nanoTime ()
        println ("prepare: " + (prepare - read) / 1e9 + " seconds")

        // do the pre-calculation
        val precalc_results =
        {
            // construct the initial graph from the real edges and nodes
            val initial = Graph.apply[PreNode, PreEdge] (xnodes, xedges, PreNode ("", 0.0), gridlabd.STORAGE_LEVEL, gridlabd.STORAGE_LEVEL)
            PowerFeeding.threshold_calculation (session, initial, sdata, transformers, gridlabd)
        }

        val precalc = System.nanoTime ()
        println ("precalc: " + (precalc - prepare) / 1e9 + " seconds")

        val trafo_list = precalc_results.has.filter(_.eea != null).keyBy (a => gridlabd.trafokreis_key(a.source_obj)).groupByKey.map (_._2.head.source_obj)
        println ("" + trafo_list.count + " transformers to process:")
        println (trafo_list.collect.map (a => gridlabd.trafokreis_key(a)).mkString ("\n"))

        val edges  = precalc_results.edges.filter(_._1 != null)
        val has = precalc_results.has.keyBy(h => gridlabd.trafokreis_key(h.source_obj))
        val vertices = precalc_results.vertices.filter(_.source_obj != null).keyBy(v => gridlabd.trafokreis_key(v.source_obj.trafo_id))
        val grouped_precalc_results = vertices.groupWith(edges, has)

        val trafokreise = trafo_list.keyBy(gridlabd.trafokreis_key(_)).leftOuterJoin(grouped_precalc_results)
        println("trafokreise: " + trafokreise.count)

        gridlabd.einspeiseleistung(trafokreise)        

        val calculate = System.nanoTime ()
        println ("calculate: " + (calculate - prepare) / 1e9 + " seconds")

        println ()
    }
}
