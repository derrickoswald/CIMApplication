package ch.ninecode.mfi

import java.net.URI
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

import scala.collection.mutable.HashMap
import scala.io.Source

import org.apache.spark.graphx.Graph
import org.apache.spark.rdd.RDD
import org.apache.spark.rdd.RDD.rddToPairRDDFunctions
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.cim.CIMNetworkTopologyProcessor
import ch.ninecode.cim.CIMRDD
import ch.ninecode.cim.CIMTopologyOptions
import ch.ninecode.gl.GridLABD
import ch.ninecode.gl.PreEdge
import ch.ninecode.gl.PreNode
import ch.ninecode.gl.Solar
import ch.ninecode.gl.ThreePhaseComplexDataElement
import ch.ninecode.gl.TransformerSet
import ch.ninecode.gl.Transformers

case class Einspeiseleistung (session: SparkSession, options: EinspeiseleistungOptions) extends CIMRDD
{
    if (options.verbose)
    {
        val el = org.apache.log4j.LogManager.getLogger ("ch.ninecode.mfi.Einspeiseleistung")
        if (!el.isInfoEnabled)
            el.setLevel (org.apache.log4j.Level.INFO)
        val fl = org.apache.log4j.LogManager.getLogger ("ch.ninecode.mfi.PowerFeeding")
        if (!fl.isInfoEnabled)
            fl.setLevel (org.apache.log4j.Level.INFO)
        val pl = org.apache.log4j.LogManager.getLogger ("ch.ninecode.mfi.PowerFeeding$")
        if (!pl.isInfoEnabled)
            pl.setLevel (org.apache.log4j.Level.INFO)
    }
    implicit val spark: SparkSession = session
    implicit val log: Logger = LoggerFactory.getLogger (getClass)

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

    def makeTrafokreis (start: Calendar, options: EinspeiseleistungOptions) (arg: (String, (TransformerSet, Option[(Iterable[PowerFeedingNode], Iterable[PreEdge], Iterable[MaxPowerFeedingNodeEEA])]))): Trafokreis =
    {
        arg match
        {
            case (trafokreise, (transformers, Some (x))) =>
                Trafokreis (start, trafokreise, transformers, x._1, x._2, x._3, options)
            case _ =>
                null
        }
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
            val ok_steps = if (0 < steps) steps - 1 else 0 // subtract off the mandatory first zero step required by GridLAB-D
            val kw = if (reason == "no limit") Double.PositiveInfinity else experiment.from + (experiment.step * ok_steps)
            current.max match {
                case None ⇒
                    MaxEinspeiseleistung (experiment.trafo, experiment.feeder, experiment.node, experiment.house, Some (kw), reason, details)
                case Some(kw1) ⇒
                    if (kw1 < kw) current else MaxEinspeiseleistung (experiment.trafo, experiment.feeder, experiment.node, experiment.house, Some(kw), reason, details)
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
        values.aggregate (MaxEinspeiseleistung (experiment.trafo, experiment.feeder, experiment.node, experiment.house, None, "unknown", ""))(seqop, combop)
    }

    def overvoltage_one_phase (r: ThreePhaseComplexDataElement, threshold: Double): Boolean = r.value_a.abs > threshold
    def overvoltage_three_phase (r: ThreePhaseComplexDataElement, threshold: Double): Boolean = (r.value_a.abs > threshold) || (r.value_b.abs > threshold) || (r.value_c.abs > threshold)
    val overvoltage: (ThreePhaseComplexDataElement, Double) ⇒ Boolean = if (options.three) overvoltage_three_phase else overvoltage_one_phase

    def voltcheck (experiments: Iterable[Experiment], elements: Iterable[ThreePhaseComplexDataElement], max: Double, neighbormax: Double, feeders: Iterable[(String, String)]): Iterable[(Experiment, ThreePhaseComplexDataElement, String, String)] =
    {
        val limit = "voltage limit"

        // swap the list around so we can look up node to get feeder
        val lookup = feeders.map (x ⇒ (x._2, x._1)).toMap
        // assign an experiment to each measurement - if it's over-voltage
        elements.filter (_.units == "Volts").flatMap (
            x ⇒
            {
                for
                {
                    e ← experiments
                    if (e.t1.getTimeInMillis <= x.millis) && (e.t2.getTimeInMillis >= x.millis)
                    feeder = lookup.getOrElse (x.element, null)
                    threshold = if (null == feeder) max else if (feeder == e.feeder) max else neighbormax
                    if overvoltage (x, threshold)
                }
                yield (e, x, limit, x.element + " > " + threshold + " Volts")
            }
        )
    }

    def ampcheck (experiments: Iterable[Experiment], elements: Iterable[ThreePhaseComplexDataElement], cdata: Iterable[(String, Double)]): Iterable[(Experiment, ThreePhaseComplexDataElement, String, String)] =
    {
        val limit = "current limit"

        // eliminate measurements below capacity
        def interesting1ph(arg: (ThreePhaseComplexDataElement, Double)): Boolean =
        {
            val r = arg._1
            val max = arg._2
            r.value_a.abs / Math.sqrt(3) > max
        }
        def interesting3ph(arg: (ThreePhaseComplexDataElement, Double)): Boolean =
        {
            val r = arg._1
            val max = arg._2
            (r.value_a.abs > max) || (r.value_b.abs > max) || (r.value_c.abs > max)
        }

        // assign an experiment to each measurement
        def assign(experiments: Iterable[Experiment])(arg: (ThreePhaseComplexDataElement, Double)): Iterable[(Experiment, ThreePhaseComplexDataElement, String, String)] =
        {
            val r = arg._1
            val max = arg._2
            for
            {
                e ← experiments
                if (e.t1.getTimeInMillis <= r.millis) && (e.t2.getTimeInMillis >= r.millis)
            }
            yield (e, r, limit, r.element + " > " + max + " Amps")
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
            if ((r.element == trafo_name) &&
                (r.units == "Amps") && // redundant
                (r.value_a.abs > i))
                true
            else
                false
        }
        def interesting3ph(i: Double)(r: ThreePhaseComplexDataElement): Boolean =
        {
            if ((r.element == trafo_name) &&
                (r.units == "Amps") && // redundant
                ((r.value_a.abs > i) || (r.value_b.abs > i) || (r.value_c.abs > i)))
                true
            else
                false
        }

        // assign an experiment to each measurement
        def assign(experiments: Iterable[Experiment])(r: ThreePhaseComplexDataElement): Iterable[(Experiment, ThreePhaseComplexDataElement, String, String)] =
        {
            for
            {
                e ← experiments
                if (e.t1.getTimeInMillis <= r.millis) && (e.t2.getTimeInMillis >= r.millis)
            }
            yield (e, r, limit, r.element + " > " + power + " Watts")
        }

        // P = VI = 400 / sqrt(3) * I [one phase] = sqrt(3) * 400 * I [three phase] 
        val i = if (options.three) power / (400.0 * math.sqrt (3)) else power / 400.0 // ToDo: remove hard-coded voltage
        val overI = elements.filter (if (options.three) interesting3ph (i) else interesting1ph (i))
        overI.flatMap (assign (experiments))
    }

    def analyse (trafo: (String, ((Double, Iterable[(String, Double)], Iterable[(String, String)]), (Iterable[ThreePhaseComplexDataElement], Iterable[Experiment])))): List[MaxEinspeiseleistung] =
    {
        // get the maximum transformer power as sum(Trafo_Power)*1.44 (from YF)
        val trafo_power = trafo._2._1._1
        val cdata = trafo._2._1._2
        val feeders = trafo._2._1._3

        val nominal = 400.0 // ToDo: get voltage from CIM
        val tolerance = 3.0 // same feeder tolerance
        val max = nominal + (nominal * tolerance / 100.0)
        val neighbortolerance = 5.0 // neighboring feeder tolerance
        val neighbormax = nominal + (nominal * neighbortolerance / 100.0)
        // could also check for under the minimum; r.value_a.abs < min

        // get the name of the transformer recorder (matches Trans.emit)
        val trafo_name = trafo._1

        val complexDataElements = trafo._2._2._1
        if (complexDataElements.nonEmpty)
        {
            val experiments = trafo._2._2._2

            val v = voltcheck (experiments, complexDataElements, max, neighbormax, feeders)
            val i = ampcheck (experiments, complexDataElements, cdata)
            val p = powercheck (experiments, complexDataElements, trafo_power, trafo_name)

            // establish a "no limit found" default
            val s = experiments.map (
                x ⇒
                {
                    (
                        x,
                        ThreePhaseComplexDataElement (x.house, x.t2.getTimeInMillis, Double.PositiveInfinity, Double.PositiveInfinity, Double.PositiveInfinity, ""),
                        "no limit",
                        ""
                    )
                }
            )

            // rearrange results from "by node" to results "by house"
            val g = s ++ v ++ i ++ p groupBy (_._1.node)
            val shuffle = experiments.map (x ⇒ (x, g.get (x.node))).filter (_._2.isDefined).map (x ⇒ (x._1, x._2.orNull))
            val ret = shuffle.map (x ⇒ (x._2.head._1.copy (house = x._1.house), x._2.map (y ⇒ (y._2, y._3, y._4)))) // need to grab the experiment from the node for this house
            ret.map (x ⇒ finder (x._1, x._2)).toList
            // ToDo: actually, the step before the limit was exceeded is the maximum value
        }
        else
            trafo._2._2._2.map (e => MaxEinspeiseleistung (e.trafo, e.feeder, e.node, e.house, None, "gridlab failed", "no results")).toList
    }

    def solve_and_analyse (gridlabd: GridLABD, reduced_trafos: RDD[(String, (Double, Iterable[(String, Double)], Iterable[(String, String)]))], experiments: RDD[Experiment]): RDD[MaxEinspeiseleistung] =
    {
        val b4_solve = System.nanoTime ()
        val success = gridlabd.solve (reduced_trafos.map (_._1))
        val solved = System.nanoTime ()
        if (success)
            log.info ("solve: " + (solved - b4_solve) / 1e9 + " seconds successful")
        else
            log.error ("solve: " + (solved - b4_solve) / 1e9 + " seconds failed")
        val output = gridlabd.read_output_files (!options.three)
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
        val ret = new StringBuilder ()
        def addrow (time: Calendar, power: Double, angle: Double): Unit =
        {
            ret.append (_DateFormat.format(time.getTime))
            ret.append (",")
            if (!options.three)
            {
                ret.append (-power)
                ret.append ("\n")
            }
            else
            {
                ret.append (-power / 3) // negative load injects power, 1/3 per phase
                ret.append ("<")
                ret.append (angle)
                ret.append ("d\n")
            }
            time.add (Calendar.SECOND, exp.interval)
        }
        val time = exp.t1
        addrow (time, 0.0, angle) // gridlab extends the first and last rows till infinity -> make them zero
        var power = exp.from
        while (power <= exp.to)
        {
            addrow (time, power, angle)
            power = power + exp.step
        }
        addrow (time, 0.0, angle) // gridlab extends the first and last rows till infinity -> make them zero

        ret.toString.getBytes (StandardCharsets.UTF_8)
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

    def einspeiseleistung (gridlabd: GridLABD, trafokreise: RDD[Trafokreis], feeder_map: RDD[(String, Iterable[(String, String)])], storage_level: StorageLevel = StorageLevel.MEMORY_AND_DISK_SER): RDD[MaxEinspeiseleistung] =
    {
        val start = System.nanoTime()

        def doit (t: Trafokreis): Array[Experiment] =
        {
            val generator = new EinspeiseleistungGLMGenerator (!options.three, _DateFormat, t)
            gridlabd.export (generator)
            t.experiments
        }
        val experiments = trafokreise.flatMap (doit).persist (storage_level)
        log.info ("created: " + experiments.count + " experiments")

        val write = System.nanoTime()
        log.info ("export: " + (write - start) / 1e9 + " seconds")

        var ret = null.asInstanceOf[RDD[MaxEinspeiseleistung]]
        if (!options.export_only)
        {
            val c = experiments.map (generate_player_file (gridlabd)).count
            log.info (c.toString + " experiments")

            val reduced_trafos = trafokreise.keyBy (_.trafo).join (feeder_map).values.map (
                x ⇒ {
                    val t = x._1
                    val feeders = x._2
                    val transformers = t.transformers.power_rating
                    val cdata_iter = t.edges.filter (_.ratedCurrent < Double.PositiveInfinity).groupBy (_.key).values
                        .map (edges ⇒ (edges.map (_.element.id).toArray.sortWith (_ < _) (0), edges.map (_.ratedCurrent).sum))
                    (t.trafo, (transformers, cdata_iter, feeders))
                }).persist (storage_level)

            ret = solve_and_analyse (gridlabd, reduced_trafos, experiments).persist (storage_level)
            log.info ("results: " + ret.count)

            val b4_experiment = System.nanoTime()
            val experiments2 = experiments.keyBy (_.house).leftOuterJoin (ret.keyBy (_.house)).map (
                house ⇒
                {
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
                }).persist (storage_level)

            val experiment_adjusted = System.nanoTime()
            log.info ("experiment2: " + (experiment_adjusted - b4_experiment) / 1e9 + " seconds")

            trafokreise.map(t ⇒ gridlabd.cleanup (t.trafo, false, true)).count
            val d = experiments2.map (generate_player_file (gridlabd)).count
            log.info (d.toString + " experiments")

            val export2 = System.nanoTime()
            log.info ("export2: " + (export2 - experiment_adjusted) / 1e9 + " seconds")

            ret = solve_and_analyse (gridlabd, reduced_trafos, experiments2).persist (storage_level)

            val analyse = System.nanoTime()
            log.info ("solve and analyse: " + (analyse - export2) / 1e9 + " seconds " + ret.count + " results")

            val b4_db = System.nanoTime()
            Database.store("Einspeiseleistung", Calendar.getInstance())(ret.collect)
            val dbsave = System.nanoTime()
            log.info ("database save: " + (dbsave - b4_db) / 1e9 + " seconds")

            trafokreise.map(t ⇒ gridlabd.cleanup(t.trafo, options.erase, options.erase)).count
        }

        ret
    }

    /**
     * Generate a working directory matching the files.
     */
    def derive_work_dir (files: Seq[String]): String =
    {
        val file = files.head.split (",")(0).replace (" ", "%20")
        val uri = new URI (file)
        if (null == uri.getScheme)
            "/simulation/"
        else
            uri.getScheme + "://" + (if (null == uri.getAuthority) "" else uri.getAuthority) + "/simulation/"
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

        // identify topological nodes if necessary
        val tns = session.sparkContext.getPersistentRDDs.filter(_._2.name == "TopologicalNode")
        if (tns.isEmpty || tns.head._2.isEmpty)
        {
            val ntp = CIMNetworkTopologyProcessor (session)
            val ele = ntp.process (
                CIMTopologyOptions (
                    identify_islands = false,
                    storage = storage_level))
            log.info (ele.count () + " elements")
        }
        else
            log.info (session.sparkContext.getPersistentRDDs.filter(_._2.name == "Elements").head._2.count () + " elements")

        val topo = System.nanoTime ()
        log.info ("topology: " + (topo - read) / 1e9 + " seconds")

        // prepare for precalculation
        val topological_nodes = true
        val workdir = if ("" == options.workdir) derive_work_dir (options.files) else options.workdir
        val gridlabd = new GridLABD (session, topological_nodes, !options.three, storage_level, workdir)

        // get the distribution transformers
        val tdata = new Transformers (session, storage_level).getTransformerData (topological_nodes)
        if (log.isDebugEnabled)
            tdata.map (_.asString).collect.foreach (log.debug)

        // prepare the initial graph edges and nodes
        val (xedges, xnodes) = gridlabd.prepare ()

        // get the existing photo-voltaic installations keyed by terminal
        val solar = Solar (session, topological_nodes, storage_level)
        val sdata = solar.getSolarInstallations

        // determine the set of transformers to work on
        val transformers = if (null != trafos)
        {
            val selected = tdata.filter (x => trafos.contains (x.transformer.id)).distinct
            selected.groupBy (t => gridlabd.node_name (t.terminal1)).values.map (x ⇒ TransformerSet (x.toArray))
        }
        else
        {
            // do all low voltage power transformers
            val niederspannug = tdata.filter (td => td.voltage0 != 0.4 && td.voltage1 == 0.4).distinct
            niederspannug.groupBy (t => gridlabd.node_name (t.terminal1)).values.map (x ⇒ TransformerSet (x.toArray))
        }
        transformers.persist (storage_level)
        transformers.name = "Transformers"

        val prepare = System.nanoTime ()
        log.info ("prepare: " + (prepare - topo) / 1e9 + " seconds")
        if (log.isDebugEnabled)
            transformers.map (trafo ⇒ log.debug ("%s %gkVA %g:%g".format (trafo.transformer_name, trafo.power_rating / 1000, trafo.v0, trafo.v1)))

        // do the pre-calculation
        val precalc_results =
        {
            // construct the initial graph from the real edges and nodes
            val initial = Graph.apply[PreNode, PreEdge] (xnodes, xedges, PreNode ("", 0.0, null), storage_level, storage_level)
            val pf = new PowerFeeding (session, storage_level)
            pf.threshold_calculation (initial, sdata, transformers)
        }

        val houses = if (options.all)
            precalc_results.has
        else if (-1 != options.reference)
        {
            val changed: Array[String] = Database.fetchHousesWithDifferentEEA (precalc_results.simulation, options.reference, options.delta)
            precalc_results.has.filter (x => changed.contains (x.mrid))
        }
        else
            precalc_results.has.filter (x ⇒ (x.eea != null) || (x.reason == "non-radial network"))

        // get a list of invalid nodes and group by transformer
        val invalid = houses.filter (_.problem).keyBy (_.source_obj).groupByKey

        val trafo_list = houses.keyBy (_.source_obj).groupByKey.subtractByKey (invalid).join (transformers.keyBy (_.transformer_name)).values.map (_._2)
        log.info ("" + trafo_list.count + " transformers to process")
        if (log.isDebugEnabled)
            trafo_list.foreach (trafo ⇒ log.debug ("%s %gkVA %g:%g".format (trafo.transformer_name, trafo.power_rating / 1000, trafo.v0, trafo.v1)))

        val precalc = System.nanoTime ()
        log.info ("precalculation: " + (precalc - prepare) / 1e9 + " seconds")

        // do gridlab simulation if not just pre-calculation
        if (!options.precalculation)
        {
            val vertices = precalc_results.vertices.filter(_.source_obj != null)
            val edges  = precalc_results.edges.filter(_._1 != null)
            val has = precalc_results.has.keyBy(_.source_obj)
            val grouped_precalc_results = vertices.keyBy(_.source_obj.trafo_id).groupWith(edges, has)

            val trafokreise = trafo_list.keyBy(_.transformer_name).leftOuterJoin(grouped_precalc_results)
            val t0 = javax.xml.bind.DatatypeConverter.parseDateTime ("2017-05-04 12:00:00".replace (" ", "T"))

            val filtered_trafos = trafokreise.filter(_._2._2.isDefined).map (makeTrafokreis (t0, options))
            val count = trafo_list.count
            log.info ("filtered_trafos: " + count)
            if (0 != count)
            {
                val feeder_map = vertices.map (x ⇒ (x.source_obj.trafo_id, (if (null != x.feeder) x.feeder.feeder_id else null, x.id))).groupByKey
                einspeiseleistung (gridlabd, filtered_trafos, feeder_map, storage_level)
                log.info ("finished " + count + " trafokreis")
            }
        }

        val calculate = System.nanoTime ()
        log.info ("calculate: " + (calculate - precalc) / 1e9 + " seconds")

        trafo_list.count
    }
}

object Einspeiseleistung
{
    /**
     * The list of classes that can be persisted.
     */
    lazy val classes: Array[Class[_]] =
    {
        Array (
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
