package ch.ninecode.gl


import java.net.URI

import scala.collection.mutable.HashMap
import scala.io.Source

import org.apache.spark.graphx.Graph
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel

import ch.ninecode.cim.CIMNetworkTopologyProcessor

case class EinspeiseleistungOptions (
    cim_reader_options: Iterable[(String, String)],
    three: Boolean = false,
    precalculation: Boolean = false,
    trafos: String = "",
    export_only: Boolean = false,
    all: Boolean = false,
    erase: Boolean = false,
    simulation: Int = -1,
    reference: Int = -1,
    delta: Double = 1e-6,
    number: Int = -1,
    short_circuit: String = "",
    files: Seq[String] = Seq()
)

case class Einspeiseleistung (options: EinspeiseleistungOptions)
{
    def run (session: SparkSession)
    {
        val start = System.nanoTime ()

        // determine transformer list if any
        val trafos = if ("" != options.trafos)
            // do all transformers listed in the file
            Source.fromFile (options.trafos, "UTF-8").getLines ().filter (_ != "").toArray
        else if (-1 != options.simulation)
            // do all transformers with EEA which are not yet processed
            Database.fetchTransformersWithEEA (options.simulation)
        else
            null
        if ((null != trafos) && (0 == trafos.length))
        {
            println ("no transformers to process")
            sys.exit (1)
        }

        // read the file
        val reader_options = new HashMap[String, String] ()
        reader_options ++= options.cim_reader_options
        reader_options.put ("path", options.files.mkString (","))
        reader_options.put ("ch.ninecode.cim.make_edges", "false")
        reader_options.put ("ch.ninecode.cim.do_join", "false")
        reader_options.put ("ch.ninecode.cim.do_topo", "false") // use the topological processor after reading
        reader_options.put ("ch.ninecode.cim.do_topo_islands", "false")
        val elements = session.read.format ("ch.ninecode.cim").options (reader_options).load (options.files:_*)
        if (-1 != session.sparkContext.master.indexOf ("sandbox")) // are we in development
            elements.explain
        else
            println (elements.count () + " elements")

        val read = System.nanoTime ()
        println ("read : " + (read - start) / 1e9 + " seconds")

        // identify topological nodes
        val ntp = new CIMNetworkTopologyProcessor (session, StorageLevel.fromString ("MEMORY_AND_DISK_SER"))
        val ele = ntp.process (false)
        println (ele.count () + " elements")

        val topo = System.nanoTime ()
        println ("topology : " + (topo - read) / 1e9 + " seconds")

        // prepare for precalculation
        val gridlabd = new GridLABD (session)
        gridlabd.HDFS_URI =
        {
            val name = options.files (0).replace (" ", "%20")
            val uri = new URI (name)
            if (null == uri.getScheme)
                ""
            else
                uri.getScheme + "://" + uri.getAuthority + "/"
        }

        gridlabd.DELETE_SIMULATION_FILES = options.erase
        gridlabd.USE_ONE_PHASE = !options.three
        gridlabd.EXPORT_ONLY = options.export_only
        gridlabd.STORAGE_LEVEL = StorageLevel.MEMORY_AND_DISK_SER

        // prepare the initial graph edges and nodes
        val (xedges, xnodes) = gridlabd.prepare ()
//
// java.lang.ClassCastException: org.apache.spark.graphx.impl.ShippableVertexPartition cannot be cast to scala.Product2
// https://issues.apache.org/jira/browse/SPARK-14804 Graph vertexRDD/EdgeRDD checkpoint results ClassCastException: 
// Fix Version/s: 2.0.3, 2.1.1, 2.2.0
//                session.sparkContext.getCheckpointDir match
//                {
//                    case Some (dir) => initial.checkpoint ()
//                    case None =>
//                }

        val _transformers = new Transformers (session, gridlabd.STORAGE_LEVEL)
        val tdata = _transformers.getTransformerData (options.short_circuit)

        // get the existing photo-voltaic installations keyed by terminal
        val sdata = gridlabd.getSolarInstallations (true)

        // determine the set of transformers to work on
        val transformers = if (null != trafos)
        {
            val selected = tdata.filter ((x) => trafos.contains (x.transformer.id))
            selected.groupBy (_.terminal1.TopologicalNode).values.map (_.toArray).collect
        }
        else
        {
            // do all low voltage power transformers
            // ToDo: fix this 1kV multiplier on the voltages
            val niederspannug = tdata.filter ((td) => td.voltage0 != 0.4 && td.voltage1 == 0.4)
            niederspannug.groupBy (_.terminal1.TopologicalNode).values.map (_.toArray).collect
        }

        val prepare = System.nanoTime ()
        println ("prepare: " + (prepare - topo) / 1e9 + " seconds")

        // do the pre-calculation
        val precalc_results =
        {
            // construct the initial graph from the real edges and nodes
            val initial = Graph.apply[PreNode, PreEdge] (xnodes, xedges, PreNode ("", 0.0), gridlabd.STORAGE_LEVEL, gridlabd.STORAGE_LEVEL)
            PowerFeeding.threshold_calculation (session, initial, sdata, transformers, gridlabd)
        }

        val houses = if (options.all)
            precalc_results.has
        else if (-1 != options.reference)
        {
            val changed = Database.fetchHousesWithDifferentEEA (precalc_results.simulation, options.reference, options.delta)
            precalc_results.has.filter ((x) => changed.contains (gridlabd.has(x.id_seq)))
        }
        else
            precalc_results.has.filter(_.eea != null)

        val trafo_list = houses.keyBy (a => gridlabd.trafokreis_key (a.source_obj)).groupByKey.map (_._2.head.source_obj)
        println ("" + trafo_list.count + " transformers to process")

        val precalc = System.nanoTime ()
        println ("precalculation: " + (precalc - prepare) / 1e9 + " seconds")

        // do gridlab simulation if not just pre-calculation
        if (!options.precalculation)
        {
            val vertices = precalc_results.vertices.filter(_.source_obj != null).keyBy(v => gridlabd.trafokreis_key(v.source_obj.trafo_id)) 
            val edges  = precalc_results.edges.filter(_._1 != null)
            val has = precalc_results.has.keyBy(h => gridlabd.trafokreis_key(h.source_obj))
            val grouped_precalc_results = vertices.groupWith(edges, has)

            val trafokreise = trafo_list.keyBy(gridlabd.trafokreis_key(_)).leftOuterJoin(grouped_precalc_results)
            val filtered_trafos = trafokreise.filter(_._2._2.isDefined)
            println("filtered_trafos: " + filtered_trafos.count)
            gridlabd.einspeiseleistung(filtered_trafos)

            println ("finished " + trafo_list.count + " trafokreis")
        }

        val calculate = System.nanoTime ()
        println ("calculate: " + (calculate - precalc) / 1e9 + " seconds")
    }
}