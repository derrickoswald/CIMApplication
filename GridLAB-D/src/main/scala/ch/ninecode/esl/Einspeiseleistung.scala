package ch.ninecode.esl

import java.net.URI
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

import scala.collection.mutable.HashMap
import scala.io.Source

import org.apache.spark.graphx.Graph
import org.apache.spark.rdd.RDD
import org.apache.spark.rdd.RDD.rddToPairRDDFunctions
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.LoggerFactory

import ch.ninecode.cim.CIMNetworkTopologyProcessor
import ch.ninecode.gl._
import ch.ninecode.model._

case class EinspeiseleistungOptions (
    verbose: Boolean = false,
    cim_reader_options: Iterable[(String, String)] = new HashMap[String, String] (),
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

/**
 * Stepped experiment parameters.
 * @param trafo CIM MRID of the transformer feeding the house.
 * @param house CIM MRID of house being experimented on.
 * @param t0 Origin for all experiments.
 * @param t1 Start time for this experiment.
 * @param slot Unique experiment number (slot in windowed time).
 * @param window Duration of the experiment (seconds).
 * @param interval Duration between steps in the experiment (seconds).
 * @param from Starting PV power (kW).
 * @param to Ending PV power (kW).
 * @param step Power increment, resolution of the Einspeiseleistung value (kW).
 */
case class Experiment(
    trafo: String,
    house: String,
    t0: Calendar,
    slot: Int,
    window: Int,
    interval: Int,
    from: Double,
    to: Double,
    step: Double)
{
    /**
     * Calendar duplication utility function.
     * @param c The Calendar value to be cloned.
     */
    def dup(c: Calendar): Calendar = c.clone().asInstanceOf[Calendar]

    /**
     * The start time of the experiment.
     */
    def t1 = { val t = dup (t0); t.add (Calendar.SECOND, slot * window); t }

    /**
     * The end time of the experiment.
     */
    def t2 = { val t = dup (t0); t.add (Calendar.SECOND, (slot + 1) * window); t }
}

/**
 * Final result record.
 * @param trafo MRID of transformer feeding the house.
 * @param house MRID of the house.
 * @param max Maximum feed in power (kW) or None if no limit was found.
 * @param reason Explanatory reason for the limit (voltage, current or power exceeded).
 * @param details The test which caused the limit including the network element.
 */
case class MaxEinspeiseleistung(
    trafo: String,
    house: String,
    max: Option[Double],
    reason: String,
    details: String)

trait Problem
{
    import Problem._
    def node (t: Terminal) = if (USE_TOPOLOGICAL_NODES) t.TopologicalNode else t.ConnectivityNode

    def name (): String
    def start_time (): Calendar
    def finish_time (): Calendar
    def swing_node (): String
    def swing_node_voltage (): Double
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

    val log = LoggerFactory.getLogger (getClass)

    val window = 3 * 60 // window size in simulated seconds per experiment
    val margin = 1.50 // check up to 50% over the precalculated value
    val step = 10000.0
    def significant (h: MaxPowerFeedingNodeEEA) = (h.max_power_feeding > 1000.0) // don't do houses where we already know it's less than a kilowatt
    def gen_exp (h: (MaxPowerFeedingNodeEEA, Int)) =
    {
        val house = h._1.nis_number // the house under test
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

    def swing_node_voltage (): Double = transformers(0).voltage0 * 1000
}

/**
 * Photovoltaic attachment.
 * Generating equipment attached to a node.
 * @param node ConnectivityNode or TopologicalNode MRID.
 * @param solar SolarGeneratingUnit object attached to the node.
 */
case class PV(
    node: String,
    solar: SolarGeneratingUnit)

case class Einspeiseleistung (session: SparkSession, options: EinspeiseleistungOptions)
{
    if (options.verbose)
    {
        org.apache.log4j.LogManager.getLogger ("ch.ninecode.esl.Einspeiseleistung").setLevel (org.apache.log4j.Level.INFO)
        org.apache.log4j.LogManager.getLogger ("ch.ninecode.esl.PowerFeeding$").setLevel (org.apache.log4j.Level.INFO)
    }
    val log = LoggerFactory.getLogger (getClass)

    // for dates without time zones, the timezone of the machine is used:
    //    date +%Z
    // timezone can be set on each node of the cluster with:
    //    dpkg-reconfigure tzdata
    // then choose Europe and then choose Zürich
    //
    // all dates generated by this program include the time zone
    val USE_UTC = true
    val _DateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z")
    if (USE_UTC)
        _DateFormat.setTimeZone(TimeZone.getTimeZone("UTC"))

    /**
     * Lookup CIM RDD by name.
     * @param name The unqualified name of the RDD (name of the class)
     * @return The RDD found or null if nothing was found.
     */
    def get (name: String): RDD[Element] =
    {
        val rdds = session.sparkContext.getPersistentRDDs
        for (key <- rdds.keys)
        {
            val rdd = rdds (key)
            if (rdd.name == name)
                return (rdd.asInstanceOf[RDD[Element]])
        }

        return (null)
    }

    def filterValidSolarUnits(pv: RDD[PV]): RDD[PV] =
    {
        val lifecycle = get("LifecycleDate").asInstanceOf[RDD[LifecycleDate]]
        val asset = get("Asset").asInstanceOf[RDD[Asset]]
        val lifecycle_per_eea = asset.keyBy(_.lifecycle).join(lifecycle.keyBy(_.id)).map(l ⇒ (l._2._1.IdentifiedObject.name, (l._2._2)))
        val pv_lifecycle = pv.keyBy(_.solar.id).leftOuterJoin(lifecycle_per_eea)

        def lifecycleValid(lifecycle: LifecycleDate): Boolean =
        {
            if (lifecycle.installationDate != null)
                true
            else if (lifecycle.receivedDate != null)
            {
                val _DateFormat = new SimpleDateFormat("dd.MM.yyyy")
                val receivedDate = _DateFormat.parse(lifecycle.receivedDate)
                val now = new Date()
                val diffTime = now.getTime() - receivedDate.getTime()
                val diffDays = diffTime / (1000 * 60 * 60 * 24);
                diffDays < 400
            }
            else
                false
        }

        val valid_pv = pv_lifecycle.filter(p ⇒ {
            val lifecycle_option = p._2._2
            if (lifecycle_option.isDefined)
                lifecycleValid(lifecycle_option.get)
            else
                false
        })

        valid_pv.map(_._2._1)
    }

    // get the existing photo-voltaic installations keyed by terminal
    def getSolarInstallations(topologicalnodes: Boolean, storage_level: StorageLevel): RDD[Tuple2[String, Iterable[PV]]] =
    {
        // note there are two independent linkages happening here through the UserAttribute class:
        // - SolarGeneratingUnit to ServiceLocation
        // - ServiceLocation to EnergyConsumer

        // link to service location ids via UserAttribute
        val attributes = get("UserAttribute").asInstanceOf[RDD[UserAttribute]]

        // user attributes link through string quantities
        val strings = get("StringQuantity").asInstanceOf[RDD[StringQuantity]]

        // get solar to service linkage, e.g. ("EEA5280", "MST115133")
        // and service to house linkage, e.g. ("MST115133", "HAS138130")
        val pairs = attributes.keyBy(_.value).join(strings.keyBy(_.id)).values.map(x ⇒ (x._1.name, x._2.value))

        // get a simple list of house to pv id pairs
        val links = pairs.join(pairs.map(x ⇒ (x._2, x._1))).values

        // get the pv stations
        val solars = get("SolarGeneratingUnit").asInstanceOf[RDD[SolarGeneratingUnit]]

        // get a simple list of house to pv pairs
        val house_solars = links.map(x ⇒ (x._2, x._1)).join(solars.keyBy(_.id)).values

        // get the terminals
        val terminals = get("Terminal").asInstanceOf[RDD[Terminal]]

        // link to the connectivity/topological node through the terminal
        val t = terminals.keyBy(_.ConductingEquipment).join(house_solars).values.map(
            (x) ⇒ PV(if (topologicalnodes) x._1.TopologicalNode else x._1.ConnectivityNode, x._2))

        val filteredPV = filterValidSolarUnits(t)
        val pv = filteredPV.groupBy(_.node)

        pv.persist(storage_level)
        session.sparkContext.getCheckpointDir match {
            case Some(dir) ⇒ pv.checkpoint()
            case None ⇒
        }

        pv
    }

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
    def finder (values: Iterable[(Experiment, ThreePhaseComplexDataElement, String, String)]): MaxEinspeiseleistung =
    {
        def seqop (current: MaxEinspeiseleistung, arg: (Experiment, ThreePhaseComplexDataElement, String, String)): MaxEinspeiseleistung =
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

    def voltcheck (experiments: Iterable[Experiment], elements: Iterable[ThreePhaseComplexDataElement], max: Double): Iterable[(Experiment, ThreePhaseComplexDataElement, String, String)] =
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
                    return (List ((e, r, limit, e.house + " > " + max + " Volts")))
            }
            List()
        }

        val overV = elements.filter (if (options.three) interesting3ph else interesting1ph)
        overV.flatMap (assign (experiments))
    }

    def ampcheck (experiments: Iterable[Experiment], elements: Iterable[ThreePhaseComplexDataElement], cdata: Iterable[Tuple2[String, Double]]): Iterable[(Experiment, ThreePhaseComplexDataElement, String, String)] =
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

    def powercheck (experiments: Iterable[Experiment], elements: Iterable[ThreePhaseComplexDataElement], power: Double, trafo_name: String): Iterable[(Experiment, ThreePhaseComplexDataElement, String, String)] =
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

    def analyse (trafo: (String, ((Double, Iterable[(String, Double)]), (Iterable[ThreePhaseComplexDataElement], Iterable[Experiment])))): List[MaxEinspeiseleistung] =
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
        if (!complexDataElements.isEmpty)
        {
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
            ret.values.map (v ⇒ finder(v)).toList
        }
        else
            trafo._2._2._2.map ((e) => MaxEinspeiseleistung (e.trafo, e.house, None, "gridlab failed", "no results")).toList
    }

    def solve_and_analyse (gridlabd: GridLABD, reduced_trafos: RDD[(String, (Double, Iterable[(String, Double)]))], experiments: RDD[Experiment]): RDD[MaxEinspeiseleistung] =
    {
        val b4_solve = System.nanoTime ()
        val success = gridlabd.solve (reduced_trafos.map (_._1))
        val solved = System.nanoTime ()
        if (success)
            log.info ("solve: " + (solved - b4_solve) / 1e9 + " seconds successful")
        else
            log.error ("solve: " + (solved - b4_solve) / 1e9 + " seconds failed")
        val output = gridlabd.read_output_files (!options.three, reduced_trafos)
        val read = System.nanoTime ()
        log.info ("read: " + (read - solved) / 1e9 + " seconds")
        val prepared_results = reduced_trafos.join (output.cogroup (experiments.keyBy (_.trafo)))
        val ret = prepared_results.flatMap (analyse)
        val anal = System.nanoTime ()
        log.info ("analyse: " + (anal - read) / 1e9 + " seconds")
        ret
    }

    def ramp_up (exp: Experiment, angle: Double): Array[Byte] =
    {
        val ret = new StringBuilder()
        def addrow(time: Calendar, power: Double, angle: Double) =
        {
            ret.append(_DateFormat.format(time.getTime()))
            ret.append(",")
            if (!options.three) {
                ret.append(-power)
                ret.append("\n")
            }
            else {
                ret.append(-power / 3) // negative load injects power, 1/3 per phase
                ret.append("<")
                ret.append(angle)
                ret.append("d\n")
            }
            time.add(Calendar.SECOND, exp.interval)
        }
        val time = exp.t1
        addrow(time, 0.0, angle) // gridlab extends the first and last rows till infinity -> make them zero
        var power = exp.from
        while (power < exp.to) {
            addrow(time, power, angle)
            power = power + exp.step
        }
        addrow(time, 0.0, angle) // gridlab extends the first and last rows till infinity -> make them zero

        return (ret.toString().getBytes(StandardCharsets.UTF_8))
    }

    def generate_player_file (gridlabd: GridLABD) (experiment: Experiment): Int =
    {
        if (options.three)
        {
            val r_phase = 0.0
            val s_phase = 240.0
            val t_phase = 120.0
            gridlabd.writeInputFile (experiment.trafo, "input_data/" + experiment.house + "_R.csv", ramp_up (experiment, r_phase))
            gridlabd.writeInputFile (experiment.trafo, "input_data/" + experiment.house + "_S.csv", ramp_up (experiment, s_phase))
            gridlabd.writeInputFile (experiment.trafo, "input_data/" + experiment.house + "_T.csv", ramp_up (experiment, t_phase))
        }
        else
            gridlabd.writeInputFile (experiment.trafo, "input_data/" + experiment.house + ".csv", ramp_up (experiment, 0.0))
        1
    }

    def einspeiseleistung (gridlabd: GridLABD, trafokreise: RDD[Trafokreis]): RDD[MaxEinspeiseleistung] =
    {
        val start = System.nanoTime()

        def doit (t: Trafokreis): Array[Experiment] =
        {
            val generator = new EinspeiseleistungGLMGenerator (!options.three, _DateFormat, t)
            gridlabd.export (generator)
            t.experiments
        }
        val experiments = trafokreise.flatMap (doit).cache
        log.info ("created: " + experiments.count + " experiments")

        val write = System.nanoTime()
        log.info ("export: " + (write - start) / 1e9 + " seconds")

        var ret = null.asInstanceOf[RDD[MaxEinspeiseleistung]]
        if (!options.export_only)
        {
            val c = experiments.map (generate_player_file (gridlabd)_).count
            log.info (c.toString + " experiments")

            val reduced_trafos = trafokreise.map (t ⇒ {
                val transformers = t.transformers.map(_.end1.ratedS).sum
                val cdata_iter = t.edges.filter(_.ratedCurrent < Double.PositiveInfinity).map(e ⇒ (e.element.id, e.ratedCurrent))
                (t.trafo, (transformers, cdata_iter))
            }).cache

            val max_values = solve_and_analyse (gridlabd, reduced_trafos, experiments)
            log.info ("results: " + max_values.count)

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
            log.info ("experiment2: " + (experiment_adjusted - b4_experiment) / 1e9 + " seconds")

            trafokreise.map(t ⇒ gridlabd.cleanup (t.trafo, false)).count
            val d = experiments2.map (generate_player_file (gridlabd)_).count
            log.info (d.toString + " experiments")

            val export2 = System.nanoTime()
            log.info ("export2: " + (export2 - experiment_adjusted) / 1e9 + " seconds")

            ret = solve_and_analyse (gridlabd, reduced_trafos, experiments2).cache

            val analyse = System.nanoTime()
            log.info ("solve and analyse: " + (analyse - export2) / 1e9 + " seconds " + ret.count + " results")

            val b4_db = System.nanoTime()
            Database.store("Einspeiseleistung", Calendar.getInstance())(ret.collect)
            val dbsave = System.nanoTime()
            log.info ("database save: " + (dbsave - b4_db) / 1e9 + " seconds")

            if (options.erase)
                trafokreise.map(t ⇒ gridlabd.cleanup(t.trafo, true)).count
        }

        ret
    }

    def run (): Long =
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
            log.error  ("no transformers to process")
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
            log.info (elements.count () + " elements")

        val read = System.nanoTime ()
        log.info ("read: " + (read - start) / 1e9 + " seconds")

        val storage_level = options.cim_reader_options.find (_._1 == "StorageLevel") match
        {
            case Some ((_, storage)) => StorageLevel.fromString (storage)
            case _ => StorageLevel.fromString ("MEMORY_AND_DISK_SER")
        }

        // identify topological nodes
        val ntp = new CIMNetworkTopologyProcessor (session, storage_level)
        val ele = ntp.process (false)
        log.info (ele.count () + " elements")

        val topo = System.nanoTime ()
        log.info ("topology: " + (topo - read) / 1e9 + " seconds")

        // prepare for precalculation
        val gridlabd = new GridLABD (session, !options.three, storage_level)
        gridlabd.HDFS_URI =
        {
            val name = options.files (0).replace (" ", "%20")
            val uri = new URI (name)
            if (null == uri.getScheme)
                ""
            else
                uri.getScheme + "://" + uri.getAuthority + "/"
        }

        // prepare the initial graph edges and nodes
        val (xedges, xnodes) = gridlabd.prepare ()

        val _transformers = new Transformers (session, storage_level)
        val tdata = _transformers.getTransformerData (gridlabd.USE_TOPOLOGICAL_NODES, options.short_circuit)

        // get the existing photo-voltaic installations keyed by terminal
        val sdata = getSolarInstallations (gridlabd.USE_TOPOLOGICAL_NODES, storage_level)

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
        log.info ("prepare: " + (prepare - topo) / 1e9 + " seconds")

        // do the pre-calculation
        val precalc_results =
        {
            // construct the initial graph from the real edges and nodes
            val initial = Graph.apply[PreNode, PreEdge] (xnodes, xedges, PreNode ("", 0.0), storage_level, storage_level)
            // java.lang.ClassCastException: org.apache.spark.graphx.impl.ShippableVertexPartition cannot be cast to scala.Product2
            // https://issues.apache.org/jira/browse/SPARK-14804 Graph vertexRDD/EdgeRDD checkpoint results ClassCastException: 
            // Fix Version/s: 2.0.3, 2.1.1, 2.2.0
//            session.sparkContext.getCheckpointDir match
//            {
//                case Some (dir) => initial.checkpoint ()
//                case None =>
//            }
            PowerFeeding.threshold_calculation (session, initial, sdata, transformers, gridlabd, storage_level)
        }

        val houses = if (options.all)
            precalc_results.has
        else if (-1 != options.reference)
        {
            val changed = Database.fetchHousesWithDifferentEEA (precalc_results.simulation, options.reference, options.delta)
            precalc_results.has.filter ((x) => changed.contains (x.nis_number))
        }
        else
            precalc_results.has.filter(_.eea != null)

        val trafo_list = houses.keyBy (a => gridlabd.trafokreis_key (a.source_obj)).groupByKey.map (_._2.head.source_obj)
        log.info ("" + trafo_list.count + " transformers to process")

        val precalc = System.nanoTime ()
        log.info ("precalculation: " + (precalc - prepare) / 1e9 + " seconds")

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
            log.info ("filtered_trafos: " + filtered_trafos.count)
            einspeiseleistung (gridlabd, filtered_trafos)

            log.info ("finished " + trafo_list.count + " trafokreis")
        }

        val calculate = System.nanoTime ()
        log.info ("calculate: " + (calculate - precalc) / 1e9 + " seconds")

        trafo_list.count
    }
}