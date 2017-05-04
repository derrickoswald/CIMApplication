package ch.ninecode.gl

import java.net.URI
import java.util.Calendar

import scala.collection.mutable.HashMap
import scala.io.Source

import org.apache.spark.graphx.Graph
import org.apache.spark.rdd.RDD
import org.apache.spark.rdd.RDD.rddToPairRDDFunctions
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

case class Einspeiseleistung (session: SparkSession, options: EinspeiseleistungOptions)
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

    /**
     * Find the minimum value solution from a collection
     * NOTE: we don't have to sort by time, since the power is monotonically increasing,
     * just by selecting the minimum power solution we've chosen the first measurement over the limit
     */
    def finder(values: Iterable[(Experiment, ThreePhaseComplexDataElement, String, String)]): MaxEinspeiseleistung =
    {
        def seqop(current: MaxEinspeiseleistung, arg: (Experiment, ThreePhaseComplexDataElement, String, String)): MaxEinspeiseleistung =
        {
            val experiment = arg._1
            val data = arg._2
            val reason = arg._3
            val details = arg._4
            val steps = Math.round((data.millis - experiment.t1.getTimeInMillis()) / (experiment.interval * 1000))
            val ok_steps = if (0 < steps) steps - 1 else 0 // the step before the limit was exceeded is the maximum value
            val kw = if (reason == "no limit") Double.PositiveInfinity else experiment.from + (experiment.step * ok_steps)
            current.max match {
                case None ⇒
                    MaxEinspeiseleistung (experiment.trafo, experiment.house, Some (kw), reason, details)
                case Some(kw1) ⇒
                    if (kw1 < kw) current else MaxEinspeiseleistung(experiment.trafo, experiment.house, Some(kw), reason, details)
            }
        }
        def combop(a: MaxEinspeiseleistung, b: MaxEinspeiseleistung): MaxEinspeiseleistung =
        {
            a.max match
            {
                case None ⇒
                    b
                case Some(kw1) ⇒
                    b.max match
                    {
                        case None ⇒
                            a
                        case Some(kw2) ⇒
                            if (kw1 < kw2) a else b
                    }
            }
        }
        val trafo = values.head._1.trafo
        val house = values.head._1.house
        values.aggregate(MaxEinspeiseleistung(trafo, house, None, "unknown", ""))(seqop, combop)
    }

    def voltcheck(experiments: Iterable[Experiment], elements: Iterable[ThreePhaseComplexDataElement], max: Double): Iterable[(Experiment, ThreePhaseComplexDataElement, String, String)] =
    {
        val limit = "voltage limit"

        // eliminate current measurements and measurements within tolerance
        def interesting1ph(r: ThreePhaseComplexDataElement): Boolean =
        {
            return (
                (r.units == "Volts") &&
                (r.value_a.abs < 1000.0) && // ToDo: remove hard-coded constraint for niederspannung
                (r.value_a.abs > max))
        }
        def interesting3ph(r: ThreePhaseComplexDataElement): Boolean =
        {
            return (
                (r.units == "Volts") &&
                (r.value_a.abs < 1000.0) && // ToDo: remove hard-coded constraint for niederspannung
                ((r.value_a.abs > max) || (r.value_b.abs > max) || (r.value_c.abs > max)))
        }

        // assign an experiment to each measurement
        def assign(experiments: Iterable[Experiment])(r: ThreePhaseComplexDataElement): List[(Experiment, ThreePhaseComplexDataElement, String, String)] =
        {
            for (e ← experiments) {
                if ((e.t1.getTimeInMillis() <= r.millis) && (e.t2.getTimeInMillis() >= r.millis))
                    return (List ((e, r, limit, r.element + " > " + max + " Volts")))
            }
            List()
        }

        val overV = elements.filter (if (options.three) interesting3ph else interesting1ph)
        overV.flatMap (assign (experiments))
    }

    def ampcheck(experiments: Iterable[Experiment], elements: Iterable[ThreePhaseComplexDataElement], cdata: Iterable[Tuple2[String, Double]]): Iterable[(Experiment, ThreePhaseComplexDataElement, String, String)] =
    {
        val limit = "current limit"

            // eliminate measurements below capacity
            def interesting1ph(arg: Tuple2[ThreePhaseComplexDataElement, Double]): Boolean =
                {
                    val r = arg._1
                    val max = arg._2
                    r.value_a.abs / Math.sqrt(3) > max
                }
            def interesting3ph(arg: Tuple2[ThreePhaseComplexDataElement, Double]): Boolean =
                {
                    val r = arg._1
                    val max = arg._2
                    ((r.value_a.abs > max) || (r.value_b.abs > max) || (r.value_c.abs > max))
                }

            // assign an experiment to each measurement
            def assign(experiments: Iterable[Experiment])(arg: Tuple2[ThreePhaseComplexDataElement, Double]): Iterable[(Experiment, ThreePhaseComplexDataElement, String, String)] =
                {
                    val r = arg._1
                    val max = arg._2
                    for (e ← experiments) {
                        if ((e.t1.getTimeInMillis() <= r.millis) && (e.t2.getTimeInMillis() >= r.millis))
                            return (List ((e, r, limit, r.element + " > " + max + " Amps")))
                    }
                    List()
                }

        val cdata_map = cdata.toMap
        val joined_elements = elements.map(e ⇒ {
            val max_val = cdata_map.get(e.element)
            val max = if (max_val.isDefined)
                max_val.get
            else
                Double.PositiveInfinity
            (e, max)
        })

        val overI = joined_elements.filter (if (options.three) interesting3ph else interesting1ph)
        overI.flatMap (assign (experiments))
    }

    def powercheck(experiments: Iterable[Experiment], elements: Iterable[ThreePhaseComplexDataElement], power: Double, trafo_name: String): Iterable[(Experiment, ThreePhaseComplexDataElement, String, String)] =
    {
        val limit = "transformer limit"

        // eliminate voltage measurements and measurements below capacity
        def interesting1ph(i: Double)(r: ThreePhaseComplexDataElement): Boolean =
        {
            return (
                if ((r.element == trafo_name) &&
                    (r.units == "Amps") && // redundant
                    (r.value_a.abs > i))
                    true
                else
                    false)
        }
        def interesting3ph(i: Double)(r: ThreePhaseComplexDataElement): Boolean =
        {
            return (
                if ((r.element == trafo_name) &&
                    (r.units == "Amps") && // redundant
                    ((r.value_a.abs > i) || (r.value_b.abs > i) || (r.value_c.abs > i)))
                    true
                else
                    false)
        }

        // assign an experiment to each measurement
        def assign(experiments: Iterable[Experiment])(r: ThreePhaseComplexDataElement): Iterable[(Experiment, ThreePhaseComplexDataElement, String, String)] =
        {
            for (e ← experiments) {
                if ((e.t1.getTimeInMillis() <= r.millis) && (e.t2.getTimeInMillis() >= r.millis))
                    return (List ((e, r, limit, r.element + " > " + power + " Watts")))
            }
            List()
        }

        // P = VI = 400 / sqrt(3) * I [one phase] = sqrt(3) * 400 * I [three phase] 
        val i = if (options.three) power / (400.0 * math.sqrt (3)) else math.sqrt (3) * power / 400.0
        val overI = elements.filter (if (options.three) interesting3ph (i) else interesting1ph (i))
        overI.flatMap (assign (experiments))
    }

    def analyse(trafo: (String, ((Double, Iterable[(String, Double)]), (Iterable[ThreePhaseComplexDataElement], Iterable[Experiment])))): List[MaxEinspeiseleistung] =
    {
        val cdata = trafo._2._1._2

        val nominal = 400.0 // ToDo: get voltage from CIM
        val tolerance = 3.0
        val max = nominal + (nominal * tolerance / 100.0)
        // could also check for under the minimum; r.value_a.abs < min

        // get the maximum transformer power as sum(Trafo_Power)*1.44 (from YF)
        val trafo_power = trafo._2._1._1
        // get the name of the transformer recorder (matches Trans.emit)
        val trafo_name = trafo._1

        val complexDataElements = trafo._2._2._1
        val experiments = trafo._2._2._2

        val v = voltcheck(experiments, complexDataElements, max)
        val i = ampcheck(experiments, complexDataElements, cdata)
        val p = powercheck (experiments, complexDataElements, trafo_power, trafo_name)

        // establish a "no limit found" default
        val s = experiments.map(
            (x) ⇒
                {
                    (
                        x,
                        ThreePhaseComplexDataElement(x.house, x.t2.getTimeInMillis, Double.PositiveInfinity, Double.PositiveInfinity, Double.PositiveInfinity, ""),
                        "no limit",
                        "")
                })

        val ret = s ++ v ++ i ++ p groupBy (k ⇒ k._1.house)
        ret.values.map(v ⇒ finder(v)).toList
    }

    def solve_and_analyse (gridlabd: GridLABD, reduced_trafos: RDD[(String, (Double, Iterable[(String, Double)]))], experiments: RDD[Experiment]): RDD[MaxEinspeiseleistung] =
    {
        val b4_solve = System.nanoTime()
        val success = gridlabd.solve(reduced_trafos.map(_._1))
        val solved = System.nanoTime()
        println("solve success: " + success)
        println("solve: " + (solved - b4_solve) / 1e9 + " seconds")

        val output = gridlabd.read_output_files (!options.three, reduced_trafos)

        val read = System.nanoTime()
        println("read: " + (read - solved) / 1e9 + " seconds")

        val prepared_results = reduced_trafos.join(output.cogroup(experiments.keyBy(_.trafo)))
        prepared_results.flatMap(analyse)
    }

    def einspeiseleistung (gridlabd: GridLABD, trafokreise: RDD[Trafokreis]): RDD[MaxEinspeiseleistung] =
    {
        val start = System.nanoTime()

        def doit (t: Trafokreis): Array[Experiment] =
        {
            gridlabd.export (t);
            t.experiments
        }
        val experiments = trafokreise.flatMap (doit).cache
        println ("created: " + experiments.count + " experiments")

        val write = System.nanoTime()
        println("export: " + (write - start) / 1e9 + " seconds")

        var ret = null.asInstanceOf[RDD[MaxEinspeiseleistung]]
        if (!options.export_only)
        {

            val reduced_trafos = trafokreise.map (t ⇒ {
                val transformers = t.transformers.map(_.end1.ratedS).sum
                val cdata_iter = t.edges.filter(_.ratedCurrent < Double.PositiveInfinity).map(e ⇒ (e.element.id, e.ratedCurrent))
                (t.trafo, (transformers, cdata_iter))
            }).cache

            val max_values = solve_and_analyse(gridlabd, reduced_trafos, experiments)
            println("read results: " + max_values.count)

            val b4_experiment = System.nanoTime()
            val experiments2 = experiments.keyBy(_.house).leftOuterJoin(max_values.keyBy(_.house)).map(house ⇒ {
                val experiment = house._2._1
                val max_option = house._2._2

                val step = 1000.0
                var riser = step
                var to = experiment.to
                var from = to - experiment.step
                if (max_option.isDefined) {
                    val max = max_option.get
                    if (max.reason != "no limit" && max.max.isDefined) {
                        val max_val = max.max.get
                        if (max_val > experiment.step) {
                            to = max_val + step
                            from = max_val - experiment.step
                        }
                        else {
                            to = experiment.step
                            from = 0
                        }
                        val steps = experiment.window / experiment.interval - 2 // total possible number of steps in the experiment (need 0 input on both ends, hence -2)
                        if (!(steps * step >= (to - from)))
                            riser = math.ceil ((to - from) / steps / step) * step // limit as ceiling(minimum step size) in thousands

                    }
                }
                experiment.copy(from = from, to = to, step = riser)
            }).cache

            val experiment_adjusted = System.nanoTime()
            println("experiment2: " + (experiment_adjusted - b4_experiment) / 1e9 + " seconds")

            trafokreise.map(t ⇒ gridlabd.cleanup (t.trafo, false)).count

            val filedelete = System.nanoTime()
            println("filedelete: " + (filedelete - experiment_adjusted) / 1e9 + " seconds")

            experiments2.map(experiment ⇒ {
                gridlabd.fileWriter.writeInputFile(experiment.trafo, "input_data/" + experiment.house + ".csv", gridlabd.fileWriter.ramp_up(experiment, 0.0))
            }).count

            val export2 = System.nanoTime()
            println("export2: " + (export2 - filedelete) / 1e9 + " seconds")

            ret = solve_and_analyse(gridlabd, reduced_trafos, experiments2).cache
            println("ret: " + ret.count)

            val analyse = System.nanoTime()
            println("analyse includes solve : " + (analyse - export2) / 1e9 + " seconds")

            val b4_db = System.nanoTime()
            Database.store("Einspeiseleistung", Calendar.getInstance())(ret.collect)
            val dbsave = System.nanoTime()
            println("dbsave: " + (dbsave - b4_db) / 1e9 + " seconds")

            if (options.erase)
                trafokreise.map(t ⇒ gridlabd.cleanup(t.trafo, true)).count
        }

        ret
    }

    def run ()
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
        val gridlabd = new GridLABD (session, !options.three)
        gridlabd.HDFS_URI =
        {
            val name = options.files (0).replace (" ", "%20")
            val uri = new URI (name)
            if (null == uri.getScheme)
                ""
            else
                uri.getScheme + "://" + uri.getAuthority + "/"
        }
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
            einspeiseleistung (gridlabd, filtered_trafos)

            println ("finished " + trafo_list.count + " trafokreis")
        }

        val calculate = System.nanoTime ()
        println ("calculate: " + (calculate - precalc) / 1e9 + " seconds")
    }
}