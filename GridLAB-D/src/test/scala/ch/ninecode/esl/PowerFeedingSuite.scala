package ch.ninecode.esl

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Calendar
import java.util.HashMap

import org.apache.spark.graphx.Graph
import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.scalatest.fixture.FunSuite
import ch.ninecode.cim.CIMClasses
import ch.ninecode.gl.GridLABD
import ch.ninecode.gl.PreEdge
import ch.ninecode.gl.PreNode
import ch.ninecode.gl.Solar
import ch.ninecode.gl.TransformerSet
import ch.ninecode.gl.Transformers

class PowerFeedingSuite extends FunSuite
{
    val PRIVATE_FILE_DEPOT = "private_data/"

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

        // register CIMReader classes
        configuration.registerKryoClasses (CIMClasses.list)
        // register GridLAB-D classes
        configuration.registerKryoClasses (GridLABD.classes)
        // register Einspeiseleistung classes
        configuration.registerKryoClasses (Einspeiseleistung.classes)
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

    def readFile (session: SparkSession, filename: String): Unit =
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
    }

    test ("Basic")
    {
        session: SparkSession ⇒

        val begin = System.nanoTime ()

        val root = if (true) "bkw_cim_export_haelig" else "bkw_cim_export_haelig_no_EEA7355" // Hälig
        val filename = PRIVATE_FILE_DEPOT + root + ".rdf"
        readFile (session, filename)

        val read = System.nanoTime ()
        println ("read : " + (read - begin) / 1e9 + " seconds")

        // set up for execution
        val use_topological_nodes = true
        val gridlabd = new GridLABD (session = session, topological_nodes = use_topological_nodes, one_phase = true, workdir = ".")
        val storage_level = StorageLevel.MEMORY_AND_DISK_SER

        // prepare the initial graph
        val (xedges, xnodes) = gridlabd.prepare ()

        val _transformers = new Transformers (session, storage_level)
        val tdata = _transformers.getTransformerData (use_topological_nodes)
        tdata.persist (storage_level)
        // ToDo: fix this 1kV multiplier on the voltages
        val niederspannug = tdata.filter (td => td.voltage0 != 0.4 && td.voltage1 == 0.4)
        val transformers = niederspannug.groupBy (t => gridlabd.node_name (t.terminal1)).values.map (_.toArray).map (TransformerSet (_)).collect

        // get the existing photo-voltaic installations keyed by terminal
        val solar = Solar (session, use_topological_nodes, storage_level)
        val solars = solar.getSolarInstallations

        // construct the initial graph from the real edges and nodes
        val initial = Graph.apply[PreNode, PreEdge] (xnodes, xedges, PreNode ("", 0.0), storage_level, storage_level)
        val power_feeding = new PowerFeeding(session, initial)

        val start_ids = transformers.map (PowerFeeding.trafo_mapping)
        val graph = power_feeding.trace(start_ids)
        val house_nodes = power_feeding.get_treshold_per_has(graph.vertices.values.filter(_.source_obj != null))

        val HAS138130: MaxPowerFeedingNodeEEA =
        {
            val nodes = house_nodes.filter (_.nis_number == "HAS138130").collect
            assert (1 == nodes.length, "HAS138130 not found")
            nodes(0)
        }
        assert (Math.abs (HAS138130.max_power_feeding - 87273.723) < 0.5, "expected 87kW")

        val traced_house_nodes_EEA = house_nodes.keyBy(_.id_seq).leftOuterJoin(solars).values
        val has = traced_house_nodes_EEA.map (
            node =>
            {
                node._2 match
                {
                    case Some (eea) =>
                        node._1.copy (eea = eea)
                    case None =>
                        node._1
                }
            }
        ).distinct

        val simulation = Database.store_precalculation ("Threshold Precalculation", Calendar.getInstance ()) (has)
        println ("the simulation number is " + simulation)

        val trafo_string = has.filter(_.eea != null).map(_.source_obj).distinct.collect.mkString("\n")
        Files.write (Paths.get ("simulation/trafos.txt"), trafo_string.getBytes (StandardCharsets.UTF_8))

        assert (trafo_string == "TRA5200", "expected one trafokreis has an EEA")
    }
}
