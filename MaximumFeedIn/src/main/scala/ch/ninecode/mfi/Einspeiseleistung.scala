package ch.ninecode.mfi

import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

import scala.collection._

import ch.ninecode.cim.CIMRDD
import ch.ninecode.gl.GLMEdge
import ch.ninecode.gl.GridLABD
import ch.ninecode.gl.GridlabFailure
import ch.ninecode.gl.PreEdge
import ch.ninecode.gl.PreNode
import ch.ninecode.gl.Solar
import ch.ninecode.model.ConductingEquipment
import ch.ninecode.model.Terminal
import ch.ninecode.net.TransformerData
import ch.ninecode.net.TransformerIsland
import ch.ninecode.net.Transformers
import ch.ninecode.util.Complex
import ch.ninecode.util.ThreePhaseComplexDataElement
import org.apache.spark.graphx.Graph
import org.apache.spark.rdd.RDD
import org.apache.spark.rdd.RDD.rddToPairRDDFunctions
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.Logger
import org.slf4j.LoggerFactory

case class Einspeiseleistung (session: SparkSession, options: EinspeiseleistungOptions) extends CIMRDD
{
    if (options.verbose)
    {
        val el = org.apache.log4j.LogManager.getLogger("ch.ninecode.mfi.Einspeiseleistung")
        if (!el.isInfoEnabled)
            el.setLevel(org.apache.log4j.Level.INFO)
        val fl = org.apache.log4j.LogManager.getLogger("ch.ninecode.mfi.PowerFeeding")
        if (!fl.isInfoEnabled)
            fl.setLevel(org.apache.log4j.Level.INFO)
        val pl = org.apache.log4j.LogManager.getLogger("ch.ninecode.mfi.PowerFeeding$")
        if (!pl.isInfoEnabled)
            pl.setLevel(org.apache.log4j.Level.INFO)
        val gl = org.apache.log4j.LogManager.getLogger("ch.ninecode.gl.GridLABD")
        if (!gl.isInfoEnabled)
            gl.setLevel(org.apache.log4j.Level.INFO)
    }
    implicit val spark: SparkSession = session
    implicit val log: Logger = LoggerFactory.getLogger(getClass)
    val storage_level: StorageLevel = options.cim_options.storage
    val workdir: String = options.getWorkDir
    var gridlabd: GridLABD = new GridLABD(session, storage_level, workdir, options.cable_impedance_limit)

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

    def makeTrafokreis (start: Calendar, options: EinspeiseleistungOptions, subtransmission_trafos: Array[TransformerData])(arg: (String, (TransformerIsland, (Iterable[PowerFeedingNode], Iterable[PreEdge], Iterable[MaxPowerFeedingNodeEEA])))): Trafokreis =
    {
        def notTheTransformer (island: TransformerIsland)(edge: GLMEdge): Boolean =
            island.transformers.forall(
                tx =>
                    edge match
                    {
                        case t: PreEdge => t.id != tx.transformer_name
                        case _ => true
                    }
            )

        val (trafokreise, (transformers, (nodes, edges, mpfne))) = arg
        Trafokreis(start, trafokreise, transformers, nodes, edges.filter(notTheTransformer(transformers)), mpfne, options, subtransmission_trafos)
    }

    /**
     * Find the minimum value solution from a collection
     * NOTE: we don't have to sort by time, since the power is monotonically increasing,
     * just by selecting the minimum power solution we've chosen the first measurement over the limit
     */
    def finder (experiment: Experiment, values: Iterable[(ThreePhaseComplexDataElement, String, String)]): MaxEinspeiseleistung =
    {
        def seqop (current: MaxEinspeiseleistung, arg: (ThreePhaseComplexDataElement, String, String)): MaxEinspeiseleistung =
        {
            val data = arg._1
            val reason = arg._2
            val details = arg._3
            val steps = Math.round((data.millis - experiment.t1.getTimeInMillis) / (experiment.interval * 1000))
            // subtract off the mandatory first zero step required by GridLAB-D
            val ok_steps = if (0 < steps) steps - 1 else 0
            val increments = if (0 < ok_steps) ok_steps - 1 else 0
            val kw = if (reason == "no limit") Double.PositiveInfinity else experiment.from + (experiment.step * increments)
            current.max match
            {
                case None =>
                    MaxEinspeiseleistung(experiment.trafo, experiment.feeder, experiment.node, experiment.house, Some(kw), reason, details)
                case Some(kw1) =>
                    if (kw1 < kw) current else MaxEinspeiseleistung(experiment.trafo, experiment.feeder, experiment.node, experiment.house, Some(kw), reason, details)
            }
        }

        def combop (a: MaxEinspeiseleistung, b: MaxEinspeiseleistung): MaxEinspeiseleistung =
        {
            a.max match
            {
                case None =>
                    b
                case Some(kw1) =>
                    b.max match
                    {
                        case None =>
                            a
                        case Some(kw2) =>
                            if (kw1 < kw2) a else b
                    }
            }
        }

        values.aggregate(MaxEinspeiseleistung(experiment.trafo, experiment.feeder, experiment.node, experiment.house, None, "unknown", ""))(seqop, combop)
    }

    def overvoltage_one_phase (r: ThreePhaseComplexDataElement, threshold: Double): Boolean = r.value_a.abs > threshold

    def overvoltage_three_phase (r: ThreePhaseComplexDataElement, threshold: Double): Boolean = (r.value_a.abs > threshold) || (r.value_b.abs > threshold) || (r.value_c.abs > threshold)

    val overvoltage: (ThreePhaseComplexDataElement, Double) => Boolean = if (options.three) overvoltage_three_phase else overvoltage_one_phase

    def voltcheck (experiments: Iterable[Experiment], options: EinspeiseleistungOptions, elements: Iterable[ThreePhaseComplexDataElement], feeders: Iterable[(String, String)]): Iterable[(Experiment, ThreePhaseComplexDataElement, String, String)] =
    {
        val limit = "voltage limit"

        // look up node to get feeder (discard duplicates, all nodes have a single feeder)
        val lookup = feeders.toMap

        // ToDo: get voltage from CIM
        val nominal = 400.0
        val max = nominal * (1.0 + (options.voltage_threshold / 100.0))
        val neighbormax = nominal * (1.0 + (options.voltage_threshold2 / 100.0))
        // could also check for under the minimum; r.value_a.abs < min

        def getThreshold (element: String, experiment: Experiment): Double =
        {
            lookup.get(element) match
            {
                case Some(feeder) => if (feeder == experiment.feeder) max else neighbormax
                case None => max
            }
        }
        // assign an experiment to each measurement - if it's over-voltage
        elements.filter(x => (x.units == "Volts") && overvoltage(x, Math.min(max, neighbormax))).flatMap(
            x =>
            {
                for
                    {
                    e <- experiments
                    if (e.t1.getTimeInMillis <= x.millis) && (e.t2.getTimeInMillis >= x.millis)
                    threshold = getThreshold(x.element, e)
                    if overvoltage(x, threshold)
                }
                    yield (e, x, limit, s"${x.element} > $threshold Volts")
            }
        )
    }

    def overcurrent_one_phase (r: ThreePhaseComplexDataElement, threshold: Double): Boolean = r.value_a.abs / Math.sqrt(3) > threshold

    def overcurrent_three_phase (r: ThreePhaseComplexDataElement, threshold: Double): Boolean = (r.value_a.abs > threshold) || (r.value_b.abs > threshold) || (r.value_c.abs > threshold)

    val overcurrent: (ThreePhaseComplexDataElement, Double) => Boolean = if (options.three) overcurrent_three_phase else overcurrent_one_phase

    def ampcheck (experiments: Iterable[Experiment], options: EinspeiseleistungOptions, elements: Iterable[ThreePhaseComplexDataElement], cdata: Iterable[(String, Double)], feeders: Iterable[(String, String)]): Iterable[(Experiment, ThreePhaseComplexDataElement, String, String)] =
    {
        val limit = "current limit"

        // look up node to get feeders
        val lookup = if (options.ignore_other)
            feeders.groupBy(_._1).map(x => (x._1, x._2.map(_._2).toList.distinct))
        else
            Map[String, List[String]]()

        // look up cable to get current rating
        val cdata_map = cdata.toMap

        // assign an experiment to each measurement - if it's over-current
        elements.filter(x => (x.units == "Amps") && overcurrent(x, 0.1)).flatMap(
            x =>
            {
                for
                    {
                    e <- experiments
                    if (e.t1.getTimeInMillis <= x.millis) && (e.t2.getTimeInMillis >= x.millis)
                    if !options.ignore_other || lookup.getOrElse(x.element, List()).contains(e.feeder)
                    threshold = cdata_map.getOrElse(x.element, Double.PositiveInfinity)
                    if overcurrent(x, threshold)
                }
                    yield (e, x, limit, s"${x.element} > $threshold Amps")
            }
        )
    }

    def powercheck (experiments: Iterable[Experiment], elements: Iterable[ThreePhaseComplexDataElement], power: Double, trafo_name: String): Iterable[(Experiment, ThreePhaseComplexDataElement, String, String)] =
    {
        val limit = "transformer limit"

        // eliminate voltage measurements and measurements below capacity
        def interesting1ph (i: Double)(r: ThreePhaseComplexDataElement): Boolean =
        {
            if ((r.element == trafo_name) &&
                (r.units == "Amps") && // redundant
                (r.value_a.abs > i))
                true
            else
                false
        }

        def interesting3ph (i: Double)(r: ThreePhaseComplexDataElement): Boolean =
        {
            if ((r.element == trafo_name) &&
                (r.units == "Amps") && // redundant
                ((r.value_a.abs > i) || (r.value_b.abs > i) || (r.value_c.abs > i)))
                true
            else
                false
        }

        // assign an experiment to each measurement
        def assign (experiments: Iterable[Experiment])(r: ThreePhaseComplexDataElement): Iterable[(Experiment, ThreePhaseComplexDataElement, String, String)] =
        {
            for
                {
                e <- experiments
                if (e.t1.getTimeInMillis <= r.millis) && (e.t2.getTimeInMillis >= r.millis)
            }
                yield (e, r, limit, s"${r.element} > $power Watts")
        }

        // P = VI = 400 / sqrt(3) * I [one phase] = sqrt(3) * 400 * I [three phase]
        // ToDo: remove hard-coded voltage
        val i = if (options.three) power / (400.0 * math.sqrt(3)) else power / 400.0
        val overI = elements.filter(if (options.three) interesting3ph(i) else interesting1ph(i))
        overI.flatMap(assign(experiments))
    }

    def analyse (options: EinspeiseleistungOptions, errors: Array[GridlabFailure])(trafo: (String, ((Double, Iterable[(String, Double)], Iterable[(String, String)]), (Iterable[ThreePhaseComplexDataElement], Iterable[Experiment])))): List[MaxEinspeiseleistung] =
    {
        // get the maximum transformer power as sum(Trafo_Power)*1.44 (from YF)
        val trafo_power = trafo._2._1._1
        val cdata = trafo._2._1._2
        val feeders = trafo._2._1._3

        // get the name of the transformer recorder (matches Trans.emit)
        val trafo_name = trafo._1

        val complexDataElements = trafo._2._2._1
        val error = errors.find(_.trafoID == trafo_name)
        if (complexDataElements.nonEmpty && error.isEmpty)
        {
            val experiments = trafo._2._2._2

            val v = voltcheck(experiments, options, complexDataElements, feeders)
            val i = ampcheck(experiments, options, complexDataElements, cdata, feeders)
            val p = powercheck(experiments, complexDataElements, trafo_power, trafo_name)

            // establish a "no limit found" default
            val s = experiments.map(
                x =>
                {
                    (
                        x,
                        ThreePhaseComplexDataElement(x.house, x.t2.getTimeInMillis, Double.PositiveInfinity, Double.PositiveInfinity, Double.PositiveInfinity, ""),
                        "no limit",
                        ""
                    )
                }
            )

            // rearrange results from "by node" to results "by house"
            val raw_results = s ++ v ++ i ++ p groupBy (_._1.node)
            val shuffle = experiments.flatMap(
                experiment =>
                {
                    raw_results.get(experiment.node) match
                    {
                        case Some(values) =>
                            Some((experiment, values))
                        case None =>
                            None
                    }
                }
            )
            val ret = shuffle.map(
                x =>
                {
                    val (experiment, values) = x
                    (experiment, values.map(y => (y._2, y._3, y._4)))
                }
            )
            // find the least maximum
            ret.flatMap(
                x =>
                {
                    val (experiment, maximums) = x
                    val result: MaxEinspeiseleistung = finder(experiment, maximums)
                    // apply across all houses attached to this node
                    experiment.houses.map(
                        house =>
                            result.copy(house = house)
                    )
                }
            ).toList
        }
        else
        {
            val errorMessage = error match
            {
                case Some(problem) => problem.errorMessages.mkString("\n")
                case None => "no returned errors"
            }
            trafo._2._2._2.map(e => MaxEinspeiseleistung(e.trafo, e.feeder, e.node, e.house, None, s"gridlabd failed \n $errorMessage", "no results")).toList
        }
    }

    def solve_and_analyse (gridlabd: GridLABD, reduced_trafos: RDD[(String, (Double, Iterable[(String, Double)], Iterable[(String, String)]))], experiments: RDD[Experiment]): RDD[MaxEinspeiseleistung] =
    {
        val b4_solve = System.nanoTime()
        val trafos = reduced_trafos.map(_._1)
        val gridlabFailures = gridlabd.solve(trafos)
        val solved = System.nanoTime()
        if (gridlabFailures.isEmpty)
            log.info("solve: %s seconds successful".format((solved - b4_solve) / 1e9))
        else
        {
            log.error("solve: %s seconds failed".format((solved - b4_solve) / 1e9))
            gridlabFailures.foreach(failure =>
            {
                log.error(s"${failure.trafoID} has failures: ")
                failure.errorMessages.foreach(log.error)
            })
        }
        val output = gridlabd.read_output_files(!options.three, trafos.collect)
        val read = System.nanoTime()
        log.info(s"read: ${(read - solved) / 1e9} seconds")
        val prepared_results = reduced_trafos.join(output.cogroup(experiments.keyBy(_.trafo)))
        val ret = prepared_results.flatMap(analyse(options, gridlabFailures))
        val anal = System.nanoTime()
        log.info(s"analyse: ${(anal - read) / 1e9} seconds")
        ret
    }

    def ramp_up (exp: Experiment): Array[Byte] =
    {
        // https://en.wikipedia.org/wiki/Power_factor
        // Power factors are usually stated as "leading" or "lagging" to show the sign of the phase angle.
        // Capacitive loads are leading (current leads voltage), and inductive loads are lagging (current lags voltage).
        // So, without it being stated we assume PF is leading and that a negative power factor is actually an indicator of a lagging power factor.
        val phi = -math.signum(options.cosphi) * math.acos(math.abs(options.cosphi))
        val unitvector = new Complex(math.cos(phi), math.sin(phi))

        def addrow (time: Calendar, power: Double): String =
        {
            val maxP = -power * unitvector
            val max = if (!options.three)
                maxP.asString(6)
            else
                (maxP / 3).asString(6) // negative load injects power, 1/3 per phase
            val t = _DateFormat.format(time.getTime)
            time.add(Calendar.SECOND, exp.interval)
            s"$t,$max\n"
        }

        val time = exp.t1
        // gridlab extends the first and last rows till infinity -> make them zero
        val ret = new StringBuilder(addrow(time, 0.0))
        var power = exp.from
        while (power <= exp.to)
        {
            val _ = ret.append(addrow(time, power))
            power = power + exp.step
        }
        // gridlab extends the first and last rows till infinity -> make them zero
        val _ = ret.append(addrow(time, 0.0))

        ret.toString.getBytes(StandardCharsets.UTF_8)
    }

    def generate_player_file (gridlabd: GridLABD)(experiment: Experiment): Int =
    {
        if (options.three)
        {
            val bytes = ramp_up(experiment)
            gridlabd.writeInputFile(experiment.trafo, s"input_data/${experiment.house}_R.csv", bytes)
            gridlabd.writeInputFile(experiment.trafo, s"input_data/${experiment.house}_S.csv", bytes)
            gridlabd.writeInputFile(experiment.trafo, s"input_data/${experiment.house}_T.csv", bytes)
        }
        else
            gridlabd.writeInputFile(experiment.trafo, s"input_data/${experiment.house}.csv", ramp_up(experiment))
        1
    }

    @SuppressWarnings(Array("org.wartremover.warts.TraversableOps"))
    def einspeiseleistung (gridlabd: GridLABD, trafokreise: RDD[Trafokreis], feeder_map: RDD[(String, Iterable[(String, String)])], storage_level: StorageLevel = StorageLevel.MEMORY_AND_DISK_SER): Unit =
    {
        val start = System.nanoTime()

        def doit (t: Trafokreis): Array[Experiment] =
        {
            val generator = new EinspeiseleistungGLMGenerator(!options.three, _DateFormat, t, options.base_temperature, options.sim_temperature)
            gridlabd.export(generator)
            t.experiments
        }

        val experiments = trafokreise.flatMap(doit).persist(storage_level)
        log.info(s"created: ${experiments.count} experiments")

        val write = System.nanoTime()
        log.info(s"export: ${(write - start) / 1e9} seconds")

        if (!options.export_only)
        {
            val c = experiments.map(generate_player_file(gridlabd)).count
            log.info(s"${c.toString} experiments")

            val reduced_trafos = trafokreise
                .flatMap(x => x.transformers.transformers.map(y => (y.transformer_name, x)))
                .join(feeder_map)
                .values
                .groupBy(_._1.transformers.island_name)
                .values
                .map(x => (x.head._1, x.flatMap(y => y._2)))
                .map(
                    x =>
                    {
                        val (trafokreis, feeders) = x
                        val rating = trafokreis.transformers.power_rating
                        val cdata_iter = trafokreis.edges.filter(_.ratedCurrent < Double.PositiveInfinity).groupBy(_.key).values
                            .map(edges => (edges.map(_.element.id).toArray.sortWith(_ < _)(0), edges.map(_.ratedCurrent).sum))
                        (trafokreis.trafo, (rating, cdata_iter, feeders))
                    }
                ).persist(storage_level)

            var ret = solve_and_analyse(gridlabd, reduced_trafos, experiments).persist(storage_level)
            log.info(s"results: ${ret.count}")

            val b4_experiment = System.nanoTime()
            val experiments2 = experiments.keyBy(_.house).leftOuterJoin(ret.keyBy(_.house)).map(
                house =>
                {
                    val experiment = house._2._1
                    val max_option = house._2._2

                    val step = 1000.0
                    var riser = step
                    var to = experiment.to
                    var from = to - experiment.step
                    max_option match
                    {
                        case Some(maximum) =>
                            maximum.max match
                            {
                                case Some(f) =>
                                    if (maximum.reason != "no limit")
                                    {
                                        from = f
                                        to = from + experiment.step
                                        val steps = experiment.window / experiment.interval - 2 // total possible number of steps in the experiment (need 0 input on both ends, hence -2)
                                        if (!(steps * step >= (to - from)))
                                            riser = math.ceil((to - from) / steps / step) * step // limit as ceiling(minimum step size) in thousands

                                    }
                                case _ =>
                            }
                        case _ =>
                    }
                    experiment.copy(from = from, to = to, step = riser)
                }
            ).persist(storage_level)

            val experiment_adjusted = System.nanoTime()
            log.info(s"experiment2: ${(experiment_adjusted - b4_experiment) / 1e9} seconds")

            trafokreise.foreach(t => gridlabd.cleanup(t.trafo, includes_glm = false, includes_input = true, includes_output = options.erase))
            val d = experiments2.map(generate_player_file(gridlabd)).count
            log.info(s"${d.toString} experiments")

            val export2 = System.nanoTime()
            log.info(s"export2: ${(export2 - experiment_adjusted) / 1e9} seconds")

            ret = solve_and_analyse(gridlabd, reduced_trafos, experiments2).persist(storage_level)

            val analyse = System.nanoTime()
            log.info(s"solve and analyse: ${(analyse - export2) / 1e9} seconds ${ret.count} results")

            val b4_db = System.nanoTime()
            val id = options.output match
            {
                case MaximumFeedInOutputType.SQLite =>
                    Database.store("Einspeiseleistung", options.outputfile)(ret.collect).toString
                case MaximumFeedInOutputType.Cassandra =>
                    MaximumFeedInCassandra (session, options).store("Einspeiseleistung")(ret)._1
            }
            val dbsave = System.nanoTime()
            log.info(s"database save: ${(dbsave - b4_db) / 1e9} seconds simulation id=$id")

            trafokreise.foreach(t => gridlabd.cleanup(t.trafo, options.erase, options.erase, options.erase))
        }
    }

    def combinePFN (arg: Iterable[Iterable[PowerFeedingNode]]): Iterable[PowerFeedingNode] =
    {
        val the_map = mutable.Map[String, PowerFeedingNode]()
        arg.foreach(x => x.foreach(y => the_map(y.id) = y))
        the_map.values
    }

    def combinePE (arg: Iterable[Iterable[PreEdge]]): Iterable[PreEdge] =
    {
        val the_map = mutable.Map[String, PreEdge]()
        arg.foreach(x => x.foreach(y => the_map(y.id) = y))
        the_map.values
    }

    def combineMPFN (arg: Iterable[Iterable[MaxPowerFeedingNodeEEA]]): Iterable[MaxPowerFeedingNodeEEA] =
    {
        val the_map = mutable.Map[String, MaxPowerFeedingNodeEEA]()
        arg.foreach(x => x.foreach(y => the_map(y.mrid) = y))
        the_map.values
    }

    def initializeTransformers (): (RDD[TransformerIsland], Array[TransformerData]) =
    {
        // determine transformer list if any
        val trafos = if (options.trafos.nonEmpty)
            Some(options.trafos.toArray)
        else
            if (-1 != options.simulation)
            {
                // do all transformers with EEA which are not yet processed
                val records = Database.fetchTransformersWithEEA(options.simulation, options.outputfile)
                if (0 == records.length)
                {
                    log.error("prior simulation has no transformers to process")
                    sys.exit(1)
                }
                Some(records)
            }
            else
                None

        def island (td: TransformerData): String = td.node1.TopologicalIsland

        // get the distribution transformers
        val transformer_data: RDD[TransformerData] = Transformers(session, storage_level).getTransformers()
        if (log.isDebugEnabled)
            transformer_data.map(_.asString).collect.foreach(log.debug)

        def isSubtransmissionVoltage (v: (String, Double)): Boolean =
        {
            (v._2 <= 1000.0) && (v._2 > 400.0) // ToDo: don't hard code these voltage values
        }

        def isSubtransmission (trafo: TransformerData): Boolean =
        {
            trafo.voltages.exists(isSubtransmissionVoltage) || (trafo.v0 == trafo.v1) // or voltage regulator
        }

        val subtransmission_trafos: Array[TransformerData] = transformer_data.filter(isSubtransmission).collect

        def isLowVoltage (td: TransformerData): Boolean =
        {
            (td.v0 > 1000.0) && (td.v1 == 400.0) // ToDo: don't hard code these voltage values
        }

        // determine the set of transformers to work on
        val transformers: RDD[TransformerIsland] = trafos match
        {
            case Some(lines) =>
                val selected = transformer_data.filter(x => lines.contains(x.transformer.id)).distinct
                selected.groupBy(island).values.map(TransformerIsland.apply)
            case None =>
                // do all low voltage power transformers
                val niederspannug = transformer_data.filter(isLowVoltage).distinct
                niederspannug.groupBy(island).values.map(TransformerIsland.apply)
        }
        transformers.persist(storage_level).name = "Transformers"
        (transformers, subtransmission_trafos)
    }

    def preCalculation (transformers: RDD[TransformerIsland]): PreCalculationResults =
    {
        // prepare the initial graph edges and nodes
        val (xedges, xnodes) = gridlabd.prepare()

        // get the existing photo-voltaic installations keyed by terminal
        val solar = Solar(session, topologicalnodes = true, storage_level)
        val sdata = solar.getSolarInstallations

        if (log.isDebugEnabled)
            transformers.foreach(trafo => log.debug(s"$trafo.island_name ${trafo.power_rating / 1000.0}kVA"))

        // do the pre-calculation
        val precalc_results =
        {
            // construct the initial graph from the real edges and nodes
            val initial = Graph.apply[PreNode, PreEdge](xnodes, xedges, PreNode("", 0.0, ""), storage_level, storage_level)
            val pf = new PowerFeeding(session, storage_level, options.base_temperature, options.sim_temperature)
            pf.threshold_calculation(initial, sdata, transformers, options)
        }
        precalc_results
    }

    @SuppressWarnings(Array("org.wartremover.warts.TraversableOps", "org.wartremover.warts.Null"))
    def run (): Long =
    {
        val start = System.nanoTime()

        val (transformers, subtransmission_trafos) = initializeTransformers()
        val precalc_results = preCalculation(transformers)
        val houses = if (options.all)
            precalc_results.has
        else
            if (-1 != options.reference)
            {
                val changed: Array[String] = Database.fetchHousesWithDifferentEEA(precalc_results.simulation, options.reference, options.delta, options.outputfile)
                precalc_results.has.filter(x => changed.contains(x.mrid))
            }
            else
                precalc_results.has.filter(x => (x.eea != null) || (x.reason == "non-radial network"))

        // get a list of invalid nodes and group by transformer
        val invalid = houses.filter(_.problem).keyBy(_.source_obj).groupByKey

        val trafo_island = transformers.flatMap(island => island.transformers.map(trafo => (trafo.transformer_name, island)))
        val trafo_list = houses
            .keyBy(_.source_obj)
            .groupByKey
            .subtractByKey(invalid)
            .join(trafo_island)
            .values
            .map(_._2)
            // doesn't work: .distinct
            .groupBy(_.island_name)
            .values
            .map(_.head)
        var count = trafo_list.count
        log.info(s"$count transformer service areas to process")
        if (log.isDebugEnabled)
            trafo_list.foreach(trafo => log.debug(s"$trafo.island_name ${trafo.power_rating / 1000.0}kVA"))

        val precalc = System.nanoTime()
        log.info(s"precalculation: ${(precalc - start) / 1e9} seconds")

        // do gridlab simulation if not just pre-calculation
        if (!options.precalculation)
        {
            val vertices = precalc_results.vertices.filter(_.source_obj != null)
            val edges = precalc_results.edges.filter(_._1 != null)
            val has = precalc_results.has.keyBy(_.source_obj)
            val grouped_precalc_results = vertices.keyBy(_.source_obj.trafo_id).groupWith(edges, has)

            val t0 = javax.xml.bind.DatatypeConverter.parseDateTime("2017-05-04 12:00:00".replace(" ", "T"))

            val trafokreise = trafo_list
                .flatMap(x => x.transformers.map(y => (y.transformer_name, x)))
                .join(grouped_precalc_results)
                .values
                .groupBy(_._1.island_name)
                .map(
                    x => (x._1, (x._2.head._1, (combinePFN(x._2.map(_._2._1)), combinePE(x._2.map(_._2._2)), combineMPFN(x._2.map(_._2._3))))))
                .map(makeTrafokreis(t0, options, subtransmission_trafos))

            count = trafokreise.count
            log.info(s"transformer service areas: $count")
            if (0 != count)
            {
                // (trafoid (nodeid, feeder))
                val trafos_nodes_feeders = vertices.map(x => (x.source_obj.trafo_id, (x.id, if (null != x.feeder) x.feeder.feeder_id else null)))
                // (nodeid, equipmentid)
                val nodes_equipment = get[Terminal].keyBy(_.ConductingEquipment).groupByKey.join(get[ConductingEquipment].keyBy(_.id))
                    .flatMap(x => x._2._1.map(y => y.TopologicalNode).toSet.map((z: String) => (z, x._2._2.id)))
                // (trafoid, (equipment, feeder)) -- with duplicates, possibly with different feeders, for two terminal equipment
                val trafo_equipment_feeder = nodes_equipment.join(trafos_nodes_feeders.keyBy(_._2._1)).values
                    .map(x => (x._2._1, (x._1, x._2._2._2)))
                val feeder_map = trafo_equipment_feeder.groupByKey
                einspeiseleistung(gridlabd, trafokreise, feeder_map, storage_level)
                log.info(s"finished $count transformer service areas")
            }

            val calculate = System.nanoTime()
            log.info(s"calculate: ${(calculate - precalc) / 1e9} seconds")
        }

        count
    }
}

object Einspeiseleistung
{
    /**
     * The list of classes that can be persisted.
     */
    lazy val classes: Array[Class[_]] =
    {
        Array(
            classOf[ch.ninecode.mfi.Einspeiseleistung],
            classOf[ch.ninecode.mfi.EinspeiseleistungGLMGenerator],
            classOf[ch.ninecode.mfi.EinspeiseleistungOptions],
            classOf[ch.ninecode.mfi.Experiment],
            classOf[ch.ninecode.mfi.Feeder],
            classOf[ch.ninecode.mfi.MaxEinspeiseleistung],
            classOf[ch.ninecode.mfi.MaxPowerFeedingNodeEEA],
            classOf[ch.ninecode.mfi.PowerFeeding],
            classOf[ch.ninecode.mfi.PowerFeedingNode],
            classOf[ch.ninecode.mfi.PreCalculationResults],
            classOf[ch.ninecode.mfi.StartingTrafo],
            classOf[ch.ninecode.mfi.Trafokreis]
        )
    }
}
