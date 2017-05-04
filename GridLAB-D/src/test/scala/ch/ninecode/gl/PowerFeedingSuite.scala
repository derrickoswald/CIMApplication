package ch.ninecode.gl

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.sql.Timestamp
import java.sql.Types
import java.util.Calendar
import java.util.HashMap

import org.apache.spark.graphx.Graph
import org.apache.spark.graphx.VertexId
import org.apache.spark.SparkConf
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.scalatest.fixture.FunSuite

import ch.ninecode.cim._
import ch.ninecode.model._

import javax.xml.bind.DatatypeConverter

class PowerFeedingSuite extends FunSuite
{
    val FILE_DEPOT = "private_data/"

    type FixtureParam = SparkSession

    def withFixture (test: OneArgTest): org.scalatest.Outcome =
    {
        // create the fixture
        val start = System.nanoTime ()

        // create the configuration
        val configuration = new SparkConf (false)
        configuration.setAppName ("PowerFeedingSuite")
        configuration.setMaster ("local[2]")
        configuration.set ("spark.driver.memory", "2g")
        configuration.set ("spark.executor.memory", "4g")
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
        options.put ("ch.ninecode.cim.do_topo", "true")
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

        val use_topological_nodes = true
        val root = if (true) "bkw_cim_export_haelig" else "bkw_cim_export_haelig_no_EEA7355" // Hälig
        val filename = FILE_DEPOT + root + ".rdf"
        val elements = readFile (session, filename)

        val read = System.nanoTime ()
        println ("read : " + (read - begin) / 1e9 + " seconds")

        // set up for execution
        val gridlabd = new GridLABD (session, true)
        gridlabd.HDFS_URI = "" // local
        gridlabd.STORAGE_LEVEL = StorageLevel.MEMORY_AND_DISK_SER

        // prepare the initial graph
        val (xedges, xnodes) = gridlabd.prepare ()

        val _transformers = new Transformers (session, gridlabd.STORAGE_LEVEL)
        val tdata = _transformers.getTransformerData (null)
        tdata.persist (gridlabd.STORAGE_LEVEL)
        // ToDo: fix this 1kV multiplier on the voltages
        val niederspannug = tdata.filter ((td) => td.voltage0 != 0.4 && td.voltage1 == 0.4)
        val transformers = niederspannug.groupBy (_.terminal1.TopologicalNode).values.map (_.toArray).collect

        val solars = gridlabd.getSolarInstallations (use_topological_nodes)
        // construct the initial graph from the real edges and nodes
        val initial = Graph.apply[PreNode, PreEdge] (xnodes, xedges, PreNode ("", 0.0), gridlabd.STORAGE_LEVEL, gridlabd.STORAGE_LEVEL)
        val power_feeding = new PowerFeeding(initial)

        val start_ids = transformers.map (PowerFeeding.trafo_mapping (use_topological_nodes))
        val graph = power_feeding.trace(start_ids)
        val house_nodes = power_feeding.get_treshold_per_has(graph.vertices.values.filter(_.source_obj != null))
        val traced_house_nodes_EEA = house_nodes.keyBy(_.id_seq).leftOuterJoin(solars).values

        val has = traced_house_nodes_EEA.map(node => 
          {
            val result = node._2 match
            {
              case Some (eea) => 
                node._1.copy(eea = eea)
              case None => 
                node._1
            }
            result
          }).distinct

        val simulation = Database.store_precalculation ("Threshold Precalculation", Calendar.getInstance (), gridlabd) (has)
        println ("the simulation number is " + simulation)

        val trafo_string = has.filter(_.eea != null).map(x => gridlabd.trafokreis_key (x.source_obj)).distinct.collect.mkString("\n")
        Files.write (Paths.get ("simulation/trafos.txt"), trafo_string.getBytes (StandardCharsets.UTF_8))
    }

}
