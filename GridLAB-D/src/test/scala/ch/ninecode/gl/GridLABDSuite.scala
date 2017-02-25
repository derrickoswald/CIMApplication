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
    val FILE_DEPOT = "src/test/resources/"

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

    def readFile (session: SparkSession, filename: String): DataFrame =
    {
        val files = filename.split (",")
        val options = new HashMap[String, String] ()
        options.put ("path", filename)
        options.put ("StorageLevel", "MEMORY_AND_DISK_SER")
        options.put ("ch.ninecode.cim.make_edges", "false")
        options.put ("ch.ninecode.cim.do_join", "false")
        options.put ("ch.ninecode.cim.do_topo", "true")
        options.put ("ch.ninecode.cim.do_topo_islands", "false")
        val element = session.read.format ("ch.ninecode.cim").options (options).load (files:_*)

        return (element)
    }

    test ("Basic")
    {
        session: SparkSession ⇒

        val begin = System.nanoTime ()

        val root = if (false) "bkw_cim_export_haelig" else "bkw_cim_export_haelig_no_EEA7355" // Hälig
        //val root = "NIS_CIM_Export_sias_current_20161220_Sample4" // Häuselacker
        //val root = "NIS_CIM_Export_sias_current_20161220_Brügg bei Biel_V9_assets_preview" // Brügg
        val filename =
            FILE_DEPOT + root + ".rdf"
        val sim = -1

        val elements = readFile (session, filename)
        println (elements.count () + " elements")
        val read = System.nanoTime ()
        println ("read : " + (read - begin) / 1e9 + " seconds")

        // set up for execution
        val gridlabd = new GridLABD (session)
        gridlabd.HDFS_URI = "" // local
        gridlabd._StorageLevel = StorageLevel.MEMORY_AND_DISK_SER
        gridlabd.DELETE_SIMULATION_FILES = false
        gridlabd.DELETE_INTERMEDIATE_FILES = false

        val _transformers = new Transformers (session, gridlabd._StorageLevel)
        val tdata = _transformers.getTransformerData ()
        tdata.persist (gridlabd._StorageLevel)

        val transformers = if (-1 != sim)
        {
            // do transformers specified in the database under the given simulation
            val trafos = Database.fetchTransformersWithEEA (sim)
            val selected = tdata.filter ((x) => trafos.contains (x.transformer.id))
            selected.groupBy (_.terminal1.TopologicalNode).values.map (_.toArray).collect
        }
        else
        {
            // ToDo: fix this 1kV multiplier on the voltages
            val niederspannug = tdata.filter ((td) => td.voltage0 != 0.4 && td.voltage1 == 0.4)
            niederspannug.groupBy (_.terminal1.TopologicalNode).values.map (_.toArray).collect
        }
        println (transformers.map ((x) => x.map (_.transformer.id).mkString ("&")).mkString ("\n"))

        // prepare the initial graph
        val (xedges, xnodes) = gridlabd.prepare ()

        val prepare = System.nanoTime ()
        println ("prepare: " + (prepare - read) / 1e9 + " seconds")

        val cdata = gridlabd.getCableMaxCurrent ()
        val results = transformers.par.map (
            (s) =>
            {
                val simulation = gridlabd.trafokreis (s)
                val hdata = if (-1 != sim) Database.fetchHouseMaximumsForTransformer (sim, simulation) else Array[Tuple2[String,Double]]()
                // construct the initial graph from the real edges and nodes
                val initial = Graph.apply[PreNode, PreEdge] (xnodes, xedges, PreNode ("", 0.0), gridlabd._StorageLevel, gridlabd._StorageLevel)
                val rdd = gridlabd.einspeiseleistung (initial, tdata, cdata, hdata) (s)
                val id = Database.store ("Einspeiseleistung", Calendar.getInstance ()) (simulation, rdd)
                gridlabd.cleanup (simulation)
                id
            }
        )

        val calculate = System.nanoTime ()
        println ("calculate: " + (calculate - prepare) / 1e9 + " seconds")

        println ()
    }

}
