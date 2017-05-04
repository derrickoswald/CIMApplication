package ch.ninecode.gl

import java.net.URI
import java.util.Calendar

import scala.collection.mutable.HashMap
import scala.io.Source

import org.apache.spark.graphx.Graph
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.LoggerFactory

import ch.ninecode.cim.CIMNetworkTopologyProcessor
import ch.ninecode.model._

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

trait Problem
{
    import Problem._
    def node (t: Terminal) = if (USE_TOPOLOGICAL_NODES) t.TopologicalNode else t.ConnectivityNode

    def name (): String
    def start_time (): Calendar
    def finish_time (): Calendar
    def swing_node (): String
}

object Problem
{
    var USE_TOPOLOGICAL_NODES: Boolean = true
}

case class Trafokreis
(
    start: Calendar,
    trafo: String,
    transformers: Array[TData],
    nodes: Iterable[PowerFeedingNode],
    edges: Iterable[PreEdge],
    houses: Iterable[MaxPowerFeedingNodeEEA]
) extends Problem
{
    import Trafokreis._

    val window = 3 * 60 // window size in simulated seconds per experiment
    val margin = 1.25 // check up to 25% over the precalculated value
    val step = 10000.0
    def significant (h: MaxPowerFeedingNodeEEA) = (h.max_power_feeding > 1000.0) // don't do houses where we already know it's less than a kilowatt
    def nis_number (string: String): String =
    {
        val n = string.indexOf("_")
        if (0 < n)
            string.substring(0, n)
        else
            string
    }
    def gen_exp (h: (MaxPowerFeedingNodeEEA, Int)) =
    {
        val house = nis_number (h._1.id_seq) // the house under test
        val index = h._2 // experiment #
        def limit (d: Double) = math.ceil (d * margin / step) * step // limit as ceiling(d*margin%) in thousands
        val max = limit (h._1.max_power_feeding) // upper kilowatt limit to test
        val interval = 5 // seconds per step
        val steps = window / interval - 2 // total possible number of steps in the experiment (need 0 input on both ends, hence -2)
        val riser = if (steps * step >= max) step else math.ceil (max / steps / step) * step // limit as ceiling(minimum step size) in thousands
        Experiment (trafo, house, start_time (), index, window, interval, 0, max, riser) // in 5 second intervals go from 0 to max in steps of <1000>
    }

    // generate experiments
    lazy val experiments = houses.filter (significant).zipWithIndex.map (gen_exp).toArray

    def name (): String = trafo

    def start_time (): Calendar = start
    def finish_time (): Calendar =
    {
        val t = start_time ().clone().asInstanceOf[Calendar]
        t.add (Calendar.SECOND, experiments.length * window)
        t
    }

    // find the swing node
    def swing_node (): String =
    {
        transformers.size match
        {
            case 0 ⇒
                throw new IllegalStateException ("no transformers in TData array")
            case 1 ⇒
                node (transformers(0).terminal0)
            case _ ⇒
                val s = (node(transformers(0).terminal0), node(transformers(0).terminal1))
                if (!transformers.forall((x) ⇒ (node(x.terminal0) == s._1)))
                    log.error("transformer group " + trafo + " has different nodes on terminal 0 " + transformers.map((x) ⇒ node(x.terminal0)).mkString(" "))
                if (!transformers.forall((x) ⇒ (node(x.terminal1) == s._2)))
                    log.error("transformer group " + trafo + " has different nodes on terminal 1 " + transformers.map((x) ⇒ node(x.terminal1)).mkString(" "))
                node(transformers(0).terminal0)
        }
    }
}

object Trafokreis
{
    val log = LoggerFactory.getLogger (Trafokreis.getClass)
}

case class Einspeiseleistung (options: EinspeiseleistungOptions)
{
    def makeTrafokreis (start: Calendar) (arg: (String, (Array[TData], Option[(Iterable[PowerFeedingNode], Iterable[PreEdge], Iterable[MaxPowerFeedingNodeEEA])]))): Trafokreis =
    {
        arg match
        {
            case (trafokreise, (transformers, Some (x))) =>
                Trafokreis (start, trafokreise, transformers, x._1, x._2, x._3)
            case _ =>
                null
        }
    }

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
            val t0 = javax.xml.bind.DatatypeConverter.parseDateTime ("2017-05-04 12:00:00".replace (" ", "T"))

            val filtered_trafos = trafokreise.filter(_._2._2.isDefined).map (makeTrafokreis (t0)_)
            println("filtered_trafos: " + filtered_trafos.count)
            gridlabd.einspeiseleistung (filtered_trafos)

            println ("finished " + trafo_list.count + " trafokreis")
        }

        val calculate = System.nanoTime ()
        println ("calculate: " + (calculate - precalc) / 1e9 + " seconds")
    }
}