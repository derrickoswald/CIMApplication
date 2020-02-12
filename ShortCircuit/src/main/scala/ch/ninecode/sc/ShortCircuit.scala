package ch.ninecode.sc

import java.nio.charset.StandardCharsets
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

import scala.collection.Map
import scala.io.Source

import org.apache.spark.graphx.Edge
import org.apache.spark.graphx.Graph
import org.apache.spark.graphx.VertexId
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.cim.CIMRDD
import ch.ninecode.gl.Complex
import ch.ninecode.gl.GLMEdge
import ch.ninecode.gl.GLMNode
import ch.ninecode.gl.Graphable
import ch.ninecode.gl.GridLABD
import ch.ninecode.gl.Island
import ch.ninecode.gl.Island._
import ch.ninecode.gl.LineEdge
import ch.ninecode.gl.SwitchEdge
import ch.ninecode.gl.ThreePhaseComplexDataElement
import ch.ninecode.gl.TransformerData
import ch.ninecode.gl.TransformerEdge
import ch.ninecode.gl.TransformerIsland
import ch.ninecode.gl.TransformerServiceArea
import ch.ninecode.gl.TransformerSet
import ch.ninecode.gl.Transformers
import ch.ninecode.sc.ScEdge.resistanceAt
import ch.ninecode.model._

/**
 * Short circuit calculation.
 * Uses GraphX to trace the topology and generate the short circuit results at each node.
 *
 * @param session       the Spark session
 * @param storage_level specifies the <a href="https://spark.apache.org/docs/latest/programming-guide.html#which-storage-level-to-choose">Storage Level</a> used to persist and serialize the objects
 * @param options       options for short-circuit processing
 */
case class ShortCircuit (session: SparkSession, storage_level: StorageLevel, options: ShortCircuitOptions) extends CIMRDD with Graphable with Serializable
{
    implicit val spark: SparkSession = session
    implicit val log: Logger = LoggerFactory.getLogger (getClass)

    val default_node: ScNode = ScNode ("", 0.0, null, null, null, null, null, null)

    def edge_operator (voltages: Map[String, Double])(arg: (Element, Iterable[(Terminal, Option[End])])): List[ScEdge] =
    {
        var ret = List [ScEdge]()

        val element = arg._1
        val t_it = arg._2

        // get the ConductingEquipment
        var c = element
        while ((null != c) && !c.getClass.getName.endsWith (".ConductingEquipment"))
            c = c.sup

        if (null != c)
        {
            // get the equipment
            val equipment = c.asInstanceOf [ConductingEquipment]

            // sort terminals by sequence number
            // except if it has ends, and then sort so the primary is index 0
            def sortnumber (arg: (Terminal, Option[End])) = arg._2 match
            {
                case Some (end) => end.endNumber
                case None => arg._1.ACDCTerminal.sequenceNumber
            }

            // the equipment voltage - doesn't work for transformers
            val volt = 1000.0 * voltages.getOrElse (equipment.BaseVoltage, 0.0)
            val terminals = t_it.toArray.sortWith (sortnumber (_) < sortnumber (_)).map (terminal_end =>
            {
                val voltage =
                    terminal_end._2 match
                    {
                        case Some (end) =>
                            1000.0 * voltages.getOrElse (end.BaseVoltage, 0.0)
                        case None =>
                            volt
                    }
                val impedance = element match
                {
                    case line: ACLineSegment =>
                        val dist_km = line.Conductor.len / 1000.0
                        Impedanzen (
                            Complex (resistanceAt (options.low_temperature, options.base_temperature, line.r) * dist_km, line.x * dist_km),
                            Complex (resistanceAt (options.low_temperature, options.base_temperature, line.r0) * dist_km, line.x0 * dist_km),
                            Complex (resistanceAt (options.high_temperature, options.base_temperature, line.r) * dist_km, line.x * dist_km),
                            Complex (resistanceAt (options.high_temperature, options.base_temperature, line.r0) * dist_km, line.x0 * dist_km))
                    case _: PowerTransformer =>
                        terminal_end._2 match
                        {
                            case Some (end) =>
                                val z = Complex (end.r, end.x)
                                Impedanzen (z, z, z, z)
                            case None =>
                                Impedanzen (0.0, 0.0, 0.0, 0.0) // no end on a transformer terminal? WTF?
                        }

                    case _ =>
                        Impedanzen (0.0, 0.0, 0.0, 0.0)
                }
                (terminal_end._1, voltage, impedance)
            })
            // make a short-circuit edge for each pair of terminals, zero and one length lists of terminals have been filtered out
            val eq = terminals (0)._1.ConductingEquipment
            val node1 = terminals (0)._1.TopologicalNode
            val voltage1 = terminals (0)._2
            val z = terminals (0)._3
            for (i <- 1 until terminals.length) // for comprehension: iterate omitting the upper bound
            {
                val node2 = terminals (i)._1.TopologicalNode
                // eliminate edges with only one connectivity node, or the same connectivity node
                if (null != node1 && null != node2 && "" != node1 && "" != node2 && node1 != node2)
                    ret = ret :+ ScEdge (
                        node1,
                        voltage1,
                        node2,
                        terminals (i)._2,
                        terminals.length,
                        eq,
                        element,
                        z
                    )
            }
        }
        //else // shouldn't happen, terminals always reference ConductingEquipment, right?

        ret
    }

    def trafo_mapping (transformer_island: TransformerIsland): Iterator[StartingTrafos] =
    {
        // if a transformer set has multiple transformers, their primaries and secondaries are tied together
        // but we need to handle three winding transformers
        transformer_island
            .transformers
            .flatMap (
                set =>
                {
                    val transformer = set.transformers (0)
                    for (lv_node <- transformer.nodes.tail)
                        yield StartingTrafos (vertex_id (transformer.node0.id), vertex_id (lv_node.id), set)
                }
            )
            .toIterator
    }

    case class End (PowerTransformer: String, endNumber: Int, BaseVoltage: String, r: Double, x: Double)

    def make_ends_meet (arg: (Terminal, Option[PowerTransformerEnd])): (Terminal, Option[End]) =
    {
        val terminal = arg._1
        val end = arg._2 match
        {
            case Some (pte) =>
                Some (End (pte.PowerTransformer, pte.TransformerEnd.endNumber, pte.TransformerEnd.BaseVoltage, pte.r, pte.x))
            case None =>
                None
        }
        (terminal, end)
    }

    def get_inital_graph (): Graph[ScNode, ScEdge] =
    {
        // get a map of voltages
        val voltages = get [BaseVoltage].map (v => (v.id, v.nominalVoltage)).collectAsMap ()

        // get the terminals in the topology
        val terminals = get [Terminal].filter (null != _.TopologicalNode)

        // handle transformers specially, by attaching PowerTransformerEnd objects to the terminals

        // get the transformer ends keyed by terminal, only one end can reference any one terminal
        val ends = get [PowerTransformerEnd].keyBy (_.TransformerEnd.Terminal)
        // attach the ends to terminals
        val t = terminals.keyBy (_.id).leftOuterJoin (ends).values.map (make_ends_meet)
        // get the terminals keyed by equipment and filter for two (or more) terminals
        val terms = t.groupBy (_._1.ConductingEquipment).filter (_._2.size > 1)

        // map the terminal 'pairs' to edges
        val edges = get [Element]("Elements").keyBy (_.id).join (terms).values.flatMap (edge_operator (voltages))

        // get the nodes and voltages from the edges
        val nodes = edges.flatMap (
            x => List (
                ScNode (x.id_cn_1, x.v1, null, null, null, null, null, null),
                ScNode (x.id_cn_2, x.v2, null, null, null, null, null, null))).distinct

        // persist edges and nodes to avoid recompute
        val xedges = edges.map (e => Edge (e.vertex_id (e.id_cn_1), e.vertex_id (e.id_cn_2), e))
        val xnodes = nodes.map (v => (v.vertex_id (v.id_seq), v))
        xedges.name = "xedges"
        xedges.persist (storage_level)
        xnodes.name = "xnodes"
        xnodes.persist (storage_level)
        if (spark.sparkContext.getCheckpointDir.isDefined)
        {
            xedges.checkpoint ()
            xnodes.checkpoint ()
        }

        Graph [ScNode, ScEdge](xnodes, xedges, default_node, storage_level, storage_level)
    }

    def calculate_one (voltage: Double, impedanz: Complex, null_impedanz: Complex): ScIntermediate =
    {
        val root3 = Math.sqrt (3.0)

        // Einpoligen Kurzschlussstrom berechnen
        val ik_z = root3 * options.cmin * voltage
        val ik_n_sqrt1 = !impedanz
        val ik_n_sqrt2 = !null_impedanz
        val ik_n = 2 * ik_n_sqrt1 + ik_n_sqrt2
        val ik = ik_z / ik_n

        // Dreipoligen Kurzschlussstrom berechnen
        val ik3pol_n = root3 * !impedanz
        val ik3pol = options.cmax * voltage / ik3pol_n

        // Stosskurzschlussstrom berechnen
        // was       val ip = (1.02 + 0.98 * Math.exp (-3.0 * (trafo_r1 + netz_r1) / (trafo_x1 + Math.abs (netz_x1)))) * Math.sqrt (2) * ik3pol

        // maximum aperiodic short-circuit current according to IEC 60909-0, see for example:
        // http://at.dii.unipd.it/renato.gobbo/didattica/corsi/Componenti_tecnologie_elettrici/ABB_swithgear_manual_E11/ABB_11_E_03_druck.pdf pp71-80
        // http://studiecd.dk/cahiers_techniques/Calculation_of_short_circuit_currents.pdf pp7-10
        val kappa =
        if ((0.0 == impedanz.im) && (0.0 == impedanz.re))
            1.02 + 0.98 * Math.exp (-3.0)
        else
            if (0.0 == impedanz.im)
                0.0
            else
                1.02 + 0.98 * Math.exp (-3.0 * impedanz.re / impedanz.im)
        val ip = kappa * Math.sqrt (2) * ik3pol

        // short-circuit power at the point of common coupling
        val sk = (voltage * voltage) / !impedanz

        // maximum (motor) power (W) for repetition_rate<0.01/min and 0.01≤r<0.1 /min, override default settings for pf=1.0=cos(90), inrush=1x
        val imax3ph = MaximumStartingCurrent.max_current_3_phase (sk, impedanz, voltage, options)
        val imax1ph = MaximumStartingCurrent.max_current_1_phase (sk, impedanz, voltage, options)
        val imax2ph = MaximumStartingCurrent.max_current_2_phase (sk, impedanz, voltage, options)

        ScIntermediate (ik, ik3pol, ip, sk, imax3ph._1, imax1ph._1, imax2ph._1, imax3ph._2, imax1ph._2, imax2ph._2)
    }

    // compute the short-circuit values
    def calculate_short_circuit (arg: (ScNode, Int, String, String)): ScResult =
    {
        val node = arg._1
        val terminal = arg._2
        val equipment = arg._3
        val container = arg._4
        val v2 = node.voltage
        val (low, high) =
            if (node.invalidErrors)
                (ScIntermediate (), ScIntermediate ())
            else
                (calculate_one (v2, node.impedance.impedanz_low, node.impedance.null_impedanz_low),
                    calculate_one (v2, node.impedance.impedanz_high, node.impedance.null_impedanz_high))
        val costerm = MaximumStartingCurrent.costerm (node.impedance.impedanz_low, options)

        ScResult (node.id_seq, equipment, node.voltage, terminal, container,
            if (null == node.errors) List () else node.errors.map (_.toString),
            node.source_id, node.source_impedance, node.id_prev,
            node.impedance.impedanz_low.re, node.impedance.impedanz_low.im, node.impedance.null_impedanz_low.re, node.impedance.null_impedanz_low.im,
            low.ik, low.ik3pol, low.ip, low.sk, costerm,
            low.imax_3ph_low, low.imax_1ph_low, low.imax_2ph_low, low.imax_3ph_med, low.imax_1ph_med, low.imax_2ph_med,
            node.impedance.impedanz_high.re, node.impedance.impedanz_high.im, node.impedance.null_impedanz_high.re, node.impedance.null_impedanz_high.im,
            high.ik, high.ik3pol, high.ip, high.sk, node.branches)
    }


    /**
     * Get the node part of the element name.
     *
     * @param s the element name as pulled out by filename stripping
     * @return the node (first) part of the element name
     */
    def extract_node (s: String): String =
    {
        val elementindex = s.indexOf ("%")
        if (-1 == elementindex) s else s.substring (0, elementindex)
    }

    /**
     * Yield live branches.
     *
     * Get the edges with current flowing through them.
     *
     * @param arg tuple of transformer and recorder value
     * @return tuple of the node, the edge and the magnitude of the current
     */
    def alive (arg: (String, ThreePhaseComplexDataElement)): Option[(String, String, Double)] =
    {
        val datum = arg._2
        datum.units match
        {
            case "Amps" =>
                val value = datum.value_a.modulus
                if (value > 1e-5)
                {
                    val elementindex = datum.element.indexOf ("%")
                    if (-1 == elementindex)
                        None
                    else
                        Some (datum.element.substring (0, elementindex), datum.element.substring (elementindex + 1), value)
                }
                else
                    None
            case _ =>
                None
        }
    }

    def read_output_files (one_phase: Boolean, workdir_slash: String): RDD[(String, ThreePhaseComplexDataElement)] =
    {
        val date_format = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss z")

        def toTimeStamp (string: String): Long =
        {
            try
            {
                date_format.parse (string).getTime
            }
            catch
            {
                case pe: ParseException =>
                    log.warn (pe.getMessage)
                    0L
            }
        }

        val pattern = java.util.regex.Pattern.compile ("# output_data/([^.]*).csv run at (.*) on (\\d*) nodes")
        val path = workdir_slash + "*/output.txt"
        val executors = session.sparkContext.getExecutorMemoryStatus.keys.size - 1
        val files = session.sparkContext.wholeTextFiles (path, executors)

        // extract TRAxxx from the path name
        def extract_trafo (k: (String, String)): (String, String) =
        {
            val path = k._1
            val trafo_pattern = ".*/(.*)/output.txt"
            val trafo = path.replaceAll (trafo_pattern, "$1")
            (trafo, k._2)
        }

        def ToD (string: String): Double =
        {
            try
            {
                string.toDouble
            }
            catch
            {
                case _: NumberFormatException =>
                    string match
                    {
                        case "inf" => Double.PositiveInfinity
                        case "-inf" => Double.NegativeInfinity
                        case "nan" => Double.NaN
                        case "-nan" => Double.NaN
                        case _ => 0.0
                    }
                case _: Throwable => 0.0
            }
        }

        def read (f: String): TraversableOnce[ThreePhaseComplexDataElement] =
        {
            var experiment: String = ""
            var timestamp: Long = 0L
            var records: Int = 0
            val units = "Volts"
            val content = f.split ("\n")

            def makeResult (c: String): ThreePhaseComplexDataElement =
            {
                if (c.startsWith ("#"))
                {
                    val matcher = pattern.matcher (c)
                    if (matcher.find)
                    {
                        val dump = c.substring (matcher.start (1), matcher.end (1))
                        experiment = if (dump.endsWith ("_voltdump")) dump.substring (0, dump.length - 9) else dump
                        timestamp = toTimeStamp (c.substring (matcher.start (2), matcher.end (2)))
                        records = c.substring (matcher.start (3), matcher.end (3)).toInt
                    }
                    null
                }
                else if (c.startsWith ("node_name"))
                    null
                else
                {
                    val c_arr = c.split (",")
                    if (c_arr.length == 7)
                        if (one_phase)
                            ThreePhaseComplexDataElement (c_arr (0), timestamp, Complex (ToD (c_arr (1)), ToD (c_arr (2))), Complex (0.0), Complex (0.0), units)
                        else
                            ThreePhaseComplexDataElement (c_arr (0), timestamp, Complex (ToD (c_arr (1)), ToD (c_arr (2))), Complex (ToD (c_arr (3)), ToD (c_arr (4))), Complex (ToD (c_arr (5)), ToD (c_arr (6))), units)
                    else
                    {
                        log.error ("""%s voltage dump text "%s" cannot be interpreted as three phase complex %s""".format (experiment, c, units))
                        null
                    }
                }
            }

            content.map (makeResult).filter (_ != null)
        }

        files.map (extract_trafo).flatMapValues (read)
    }

    /**
     * Reduce series connected elements.
     *
     * @param network the current network to be reduced
     * @param trafo_nodes the list of starting trafo nodes
     * @return the reduced network with one pair of series elements converted to a series branch
     */
    def reduce_series (network: Iterable[Branch], trafo_nodes: Array[String]): (Boolean, Iterable[Branch]) = // (reduced?, network)
    {
        // check for series elements
        val series = for
            {
                branch <- network
                buddies = network.filter (x ⇒ (branch.from == x.to) || (branch.from == x.from && branch != x && !trafo_nodes.contains (branch.from)))
                if buddies.size == 1
                buddy = buddies.head
            }
            yield (branch, buddy)
        if (series.nonEmpty)
        {
            // only do one reduction at a time... I'm not smart enough to figure out how to do it in bulk
            val (branch, buddy) = series.head
            val rest = network.filter (x => branch != x && buddy != x)
            val new_series = if (branch.from == buddy.to)
                buddy.add_in_series (branch)
            else
                // choose the simplest element to reverse
                branch match
                {
                    case _: SimpleBranch =>
                        buddy.add_in_series (branch.reverse)
                    case _: TransformerBranch =>
                        buddy.add_in_series (branch.reverse)
                    case _ =>
                        buddy.reverse.add_in_series (branch)
                }
            (true, Seq (new_series) ++ rest)
        }
        else
            (false, network)
    }

    /**
     * Reduce parallel connected elements.
     *
     * @param network the current network to be reduced
     * @return the reduced network with one pair of parallel elements converted to a parallel branch
     */
    def reduce_parallel (network: Iterable[Branch]): (Boolean, Iterable[Branch]) = // (reduced?, network)
    {
        // check for parallel elements
        val parallel = for
            {
                branch <- network
                buddies = network.filter (x => ((branch.from == x.from) && (branch.to == x.to)) && (branch != x))
                if buddies.nonEmpty
            }
            yield buddies ++ Seq (branch)
        if (parallel.nonEmpty)
        {
            // only do one reduction at a time... I'm not smart enough to figure out how to do it in bulk
            val set = parallel.head
            (true, Seq (set.head.add_in_parallel (set.tail)) ++ network.filter (x => !set.toSeq.contains (x)))
        }
        else
            (false, network)
    }

    def reduce (branches: Iterable[Branch], trafo_nodes: Array[String], mrid: String): Iterable[Branch] =
    {
        // step by step reduce the network to a single branch through series and parallel reductions
        var done = false
        var network: Iterable[Branch] = branches
        do
        {
            val (modified, net) = reduce_series (network, trafo_nodes)
            network = net
            done = !modified
            if (done)
            {
                val (modified, net) = reduce_parallel (network)
                network = net
                done = !modified
                // check that all branches start from the transformer
                if (done)
                    if (!network.forall (x => trafo_nodes.contains (x.from)))
                    {
                        val max = network.map (_.current).max
                        val significant = max * 0.01 // 1% of the maximum current
                        val filtered = network.filter (x =>
                            (x.current > significant)
                            || trafo_nodes.contains (x.from)
                            || trafo_nodes.contains (x.to)
                            || (x.from == mrid)
                            || (x.to == mrid))
                        if (filtered.size < network.size)
                        {
                            done = false
                            network = filtered
                        }
                    }
            }
        }
        while (!done)

        network
    }

    /**
     * Evaluate the results of an experiment.
     *
     * An experiment is a GridLAB-D load-flow evaluation of a transformer service area where the house under test is
     * set as a load of a constant impedance (100 ohms) and we have captured the voltage dump (the voltage at each node)
     * under these conditions.
     * The transformer service area has the nodes and edges that were used in generating the .glm file for GridLAB-D.
     * So the task is to determine the current direction in each edge by examining the difference in voltage between
     * the two terminal nodes of each edge and from that determine an equivalent Branch circuit (series and
     * parallel components) with the edge impedances.
     *
     * @param exp all the data, the simulation and specific experiment plus all the voltage readings
     * @return a tuple with the transformer id, node mrid, attached equipment mrid, nominal node voltage, secondary impedance of the source transformer and an equivalent circuit
     */
    def evaluate (exp: ((SimulationTransformerServiceArea, ScExperiment), Iterable[ThreePhaseComplexDataElement])): (String, String, String, Double, Impedanzen, Branch) =
    {
        val trafokreis: SimulationTransformerServiceArea = exp._1._1
        val experiment: ScExperiment = exp._1._2
        val edges: Iterable[GLMEdge] = exp._1._1.edges
        val data: Iterable[ThreePhaseComplexDataElement] = exp._2
        val lvnodes: Array[String] = trafokreis.island.transformers.flatMap (x => for (node <- x.transformers(0).nodes.tail) yield node.id)

        // get directed edges hi→lo voltage = Branch from→to
        val graph_edges = edges.flatMap (
            x =>
            {
                data.find (y => y.element == x.cn1) match
                {
                    case Some (voltage1) =>
                        data.find (y => y.element == x.cn2) match
                        {
                            case Some (voltage2) =>
                                val v1 = voltage1.value_a.modulus
                                val v2 = voltage2.value_a.modulus
                                x match
                                {
                                    case switch: SwitchEdge =>
                                        if (switch.normalOpen)
                                            List ()
                                        else
                                        {
                                            val rating = if (switch.fuse) Some (switch.ratedCurrent) else None
                                            val name = switch.toSwitch (switch.switches.head).ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.name
                                            if (lvnodes.contains (x.cn1))
                                                List (SimpleBranch (x.cn1, x.cn2, 0.0, x.id, name, rating))
                                            else if (lvnodes.contains (x.cn2))
                                                List (SimpleBranch (x.cn2, x.cn1, 0.0, x.id, name, rating))
                                            else if (experiment.mrid == x.cn2)
                                                List (SimpleBranch (x.cn1, x.cn2, 0.0, x.id, name, rating))
                                            else if (experiment.mrid == x.cn1)
                                                List (SimpleBranch (x.cn2, x.cn1, 0.0, x.id, name, rating))
                                            else
                                                if (v1 > v2)
                                                    List (SimpleBranch (x.cn1, x.cn2, 0.0, x.id, name, rating))
                                                else
                                                    List (SimpleBranch (x.cn2, x.cn1, 0.0, x.id, name, rating))
                                        }
                                    case cable: LineEdge =>
                                        if (Math.abs (v1 - v2) < 1e-6)
                                            List ()
                                        else
                                        {
                                            val line = cable.lines.head
                                            val dist_km = line.Conductor.len / 1000.0
                                            var z = Impedanzen (
                                                Complex (resistanceAt (options.low_temperature, options.base_temperature, line.r) * dist_km, line.x * dist_km),
                                                Complex (resistanceAt (options.low_temperature, options.base_temperature, line.r0) * dist_km, line.x0 * dist_km),
                                                Complex (resistanceAt (options.high_temperature, options.base_temperature, line.r) * dist_km, line.x * dist_km),
                                                Complex (resistanceAt (options.high_temperature, options.base_temperature, line.r0) * dist_km, line.x0 * dist_km))
                                            for (l <- cable.lines.tail)
                                            {
                                                val z1 = Impedanzen (
                                                    Complex (resistanceAt (options.low_temperature, options.base_temperature, l.r) * dist_km, l.x * dist_km),
                                                    Complex (resistanceAt (options.low_temperature, options.base_temperature, l.r0) * dist_km, l.x0 * dist_km),
                                                    Complex (resistanceAt (options.high_temperature, options.base_temperature, l.r) * dist_km, l.x * dist_km),
                                                    Complex (resistanceAt (options.high_temperature, options.base_temperature, l.r0) * dist_km, l.x0 * dist_km))
                                                z = Impedanzen (
                                                    z.impedanz_low.parallel_impedanz (z1.impedanz_low),
                                                    z.null_impedanz_low.parallel_impedanz (z1.null_impedanz_low),
                                                    z.impedanz_high.parallel_impedanz (z1.impedanz_high),
                                                    z.null_impedanz_high.parallel_impedanz (z1.null_impedanz_high))
                                            }
                                            val name = line.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.name
                                            if (v1 > v2)
                                                List (SimpleBranch (x.cn1, x.cn2, ((voltage1.value_a - voltage2.value_a) / z.impedanz_low).modulus, x.id, name, None, z))
                                            else
                                                List (SimpleBranch (x.cn2, x.cn1, ((voltage2.value_a - voltage1.value_a) / z.impedanz_low).modulus, x.id, name, None, z))
                                        }
                                    case transformer: TransformerEdge =>
                                        List (TransformerBranch (x.cn1, x.cn2, 0.0, transformer.transformer.transformer_name, x.id,
                                            transformer.transformer.power_rating, v1, v2, transformer.transformer.total_impedance_per_unit._1))
                                    case _ =>
                                        // e.g. transformer: TransformerEdge which never happens since the transformer is not included in the edges list
                                        log.error ("unexpected edge type %s".format (x.toString))
                                        if (v1 > v2)
                                            List (SimpleBranch (x.cn1, x.cn2, 0.0, "", x.id))
                                        else
                                            List (SimpleBranch (x.cn2, x.cn1, 0.0, "", x.id))
                                }
                            case None =>
                                List ()
                        }
                    case None =>
                        List ()
                }
            }
        )

        def connectedTo (branch: Branch, edges: Iterable[Branch]): Boolean =
        {
            edges.exists (edge ⇒ edge != branch && (branch.to == edge.to || branch.to == edge.from))
        }

        def connectedFrom (branch: Branch, edges: Iterable[Branch]): Boolean =
        {
            edges.exists (edge ⇒ edge != branch && (branch.from == edge.to || branch.from == edge.from))
        }

        // eliminate branches in the tree than only have one end connected - except for the starting and ending node
        def no_stubs (edges: Iterable[Branch], start: Array[String], end: String) (branch: Branch): Boolean =
        {
            val to = connectedTo (branch, edges)
            val from = connectedFrom (branch, edges)
            val connected = to && from
            val into = start.contains (branch.to)
            val infrom = start.contains (branch.from)

            connected ||
            (end == branch.to && (infrom || from)) ||
            (end == branch.from && (into || to)) ||
            (infrom && to) ||
            (into && from)
        }

        // reduce the tree to (hopefully) one branch spanning from start to end
        var family = graph_edges
        var count = family.size
        do
        {
            count = family.size
            family = family.filter (no_stubs (family, lvnodes, experiment.mrid))
        }
        while (count != family.size)
        val branches = reduce (family, lvnodes, experiment.mrid)
        // ToDo: this will need to be revisited for mesh networks where there are multiple supplying transformers - finding the first is not sufficient
        val branch = branches.find (
            branch =>
                ((experiment.mrid == branch.to) && lvnodes.contains (branch.from)) ||
                ((experiment.mrid == branch.from) && lvnodes.contains (branch.to))
        ).orNull

        // compute the impedance from start to end
        // ToDo: this will need to be revisited for mesh networks where there are multiple supplying transformers
        val tx = StartingTrafos (0L, 0L, trafokreis.island.transformers (0))
        val (path, impedance) = if (null == branch)
        {
            val b = if (lvnodes.contains (experiment.mrid))
                null
            else
            {
                log.error (
                    """complex branch network from %s to %s
                      |%s""".stripMargin.format (lvnodes.mkString (","), experiment.mrid, branches.map (_.asString).mkString ("\n")))
                // get the total current to the energy consumer
                val directs = branches.filter (experiment.mrid == _.to)
                val sum = directs.map (_.current).sum
                // generate a fake impedance
                ComplexBranch (lvnodes.mkString (","), experiment.mrid, sum, branches.toArray)
            }
            (b, tx.lv_impedance (experiment.voltage))
        }
        else
        {
            val _branch = if (experiment.mrid == branch.to) branch else branch.reverse
            val v = experiment.voltage / _branch.voltageRatio
            (_branch, tx.lv_impedance (v))
        }
        (experiment.trafo, experiment.mrid, experiment.equipment, experiment.voltage, impedance, path)
    }

    /**
     * Perform gridlabd via Spark pipe() and collect the experimental results.
     *
     * @param gridlabd    the object to solve the .glm files and read the recorders
     * @param one_phase   if <code>true</code>, create single phase results, otherwise three phase results
     * @param isMax       If <code>true</code> use maximum currents (lowest impedances) [for motor starting currents], otherwise minimum currents (highest impedances) [for fuse sizing and specificity].
     * @param simulations the simulations with experiments
     * @return an RDD of tuples with the transformer id, node mrid, attached equipment mrid, nominal node voltage, and impedance at the node
     */
    def solve_and_analyse (gridlabd: GridLABD, one_phase: Boolean, isMax: Boolean, simulations: RDD[SimulationTransformerServiceArea]): RDD[(String, String, String, Double, Impedanzen, Branch)] =
    {
        val b4_solve = System.nanoTime ()
        val trafos = simulations.map (_.island.island_name)
        val success = gridlabd.solve (trafos)
        val solved = System.nanoTime ()
        if (success._1)
            log.info ("solve: %s seconds successful".format ((solved - b4_solve) / 1e9))
        else
        {
            log.error ("solve: %s seconds failed".format ((solved - b4_solve) / 1e9))
            success._2.foreach (log.error)
        }
        val output = read_output_files (one_phase, gridlabd.workdir_slash)
        val read = System.nanoTime ()
        log.info ("read: %s seconds".format ((read - solved) / 1e9))

        val values = output.map (x => (x._1 + "_" + x._2.millis.toString, x._2)).groupByKey
        val groups = simulations.flatMap (
            (simulation: SimulationTransformerServiceArea) => simulation.experiments.map (
                (experiment: ScExperiment) => (simulation, experiment))).keyBy (
                    (pair: (SimulationTransformerServiceArea, ScExperiment)) =>
                        pair._2.trafo + "_" + pair._2.t1.getTimeInMillis.toString)
        val exp = groups.join (values).values

        val z = exp.map (evaluate)
        val anal = System.nanoTime ()
        log.info ("analyse: %s seconds".format ((anal - read) / 1e9))
        z
    }

    /**
     * Apply a GridLAB-D load flow analysis as a remedial work-around for mesh (non-radial) networks.
     * Exports GridLAB-D model files, adding player files of short-circuits (actually just a low impedance)
     * for each node of interest ina time-multiplexed window of "experiments". It then executes the load-flow
     * and time demultuplexes each experiment to generate the impedance of the network as seen at each node (of interest).
     *
     * @param simulations the RDD of transformer service areas to which this analysis should be applied
     * @param temperature the temerature at which to evaluate the impedances (°C)
     * @param isMax       If <code>true</code> use maximum currents (lowest impedances) [for motor starting currents], otherwise minimum currents (highest impedances) [for fuse sizing and specificity].
     * @return the RDD of tuples with the transformer id, node mrid, attached equipment mrid, nominal node voltage, supplying transformer impedance and network
     */
    def remedial (simulations: RDD[SimulationTransformerServiceArea], temperature: Double, isMax: Boolean): RDD[(String, String, String, Double, Impedanzen, Branch)] =
    {
        // for dates without time zones, the timezone of the machine is used:
        //    date +%Z
        // timezone can be set on each node of the cluster with:
        //    dpkg-reconfigure tzdata
        // then choose Europe and then choose Zürich
        //
        // all dates generated by this program include the time zone
        val USE_UTC = true
        val _DateFormat = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss z")
        if (USE_UTC)
            _DateFormat.setTimeZone (TimeZone.getTimeZone ("UTC"))
        else
            _DateFormat.setTimeZone (TimeZone.getTimeZone ("CET"))

        def generate (gridlabd: GridLABD, trafokreis: SimulationTransformerServiceArea): Unit =
        {
            val generator = ScGLMGenerator (one_phase = true, temperature = temperature, date_format = _DateFormat, trafokreis, isMax = isMax)
            gridlabd.export (generator)
        }

        val gridlabd = new GridLABD (session, topological_nodes = true, one_phase = true, storage_level = storage_level, workdir = options.workdir, cable_impedance_limit = options.cable_impedance_limit)
        val experiments = simulations.flatMap (
            x =>
            {
                generate (gridlabd, x)
                x.experiments
            }
        ).persist (storage_level)

        def short (exp: ScExperiment): Array[Byte] =
        {
            val ret = new StringBuilder ()
            val gigaohm = Complex (1e9, 0)

            def addrow (time: Calendar, impedance: Complex): Unit =
            {
                ret.append (_DateFormat.format (time.getTime))
                ret.append (",")
                ret.append (impedance.re)
                ret.append (",")
                ret.append (impedance.im)
                ret.append ("\n")
            }

            addrow (exp.t0, gigaohm) // gridlab extends the first and last rows till infinity -> make them zero
            addrow (exp.t1, exp.impedance)
            addrow (exp.t2, gigaohm) // gridlab extends the first and last rows till infinity -> make them zero

            ret.toString.getBytes (StandardCharsets.UTF_8)
        }

        def generate_player_file (gridlabd: GridLABD)(experiment: ScExperiment): Int =
        {
            if (false)
            {
                gridlabd.writeInputFile (experiment.trafo, "input_data/" + experiment.mrid + "_R.csv", short (experiment))
                gridlabd.writeInputFile (experiment.trafo, "input_data/" + experiment.mrid + "_S.csv", short (experiment))
                gridlabd.writeInputFile (experiment.trafo, "input_data/" + experiment.mrid + "_T.csv", short (experiment))
            }
            else
                gridlabd.writeInputFile (experiment.trafo, "input_data/" + experiment.mrid + ".csv", short (experiment))
            1
        }

        val n = experiments.map (generate_player_file (gridlabd)).count
        log.info ("""running %s experiments""".format (n))

        solve_and_analyse (gridlabd = gridlabd, one_phase = true, isMax, simulations)
    }

    def node_maker (rdd: RDD[NodeParts]): RDD[(identifier, GLMNode)] =
    {
        // ToDo: fix this 1kV multiplier on the voltages
        def voltage (base_voltage: BaseVoltage): Double = base_voltage.nominalVoltage * 1000.0

        def house (element: Element): Boolean = element match
        {
            case _: EnergyConsumer => true
            case _ => false
        }

        def busbar (element: Element): Boolean = element match
        {
            case _: BusbarSection => true
            case _ => false
        }

        val s: RDD[((node_id, Iterable[(identifier, (Terminal, Element, BaseVoltage))]), ConductingEquipment)] = rdd.keyBy (_._2.head._2._2.id).join (get [ConductingEquipment].keyBy (_.id)).values
        s.map (args =>
        {
            val iter = args._1._2
            val has = iter.find (h => house (h._2._2))
            val bus = iter.find (b => busbar (b._2._2))
            val ele: Element = has.getOrElse (bus.getOrElse (iter.head))._2._2
            (
                iter.head._1,
                SimulationNode (
                    args._1._1,
                    voltage (iter.head._2._3),
                    ele.id,
                    house (ele),
                    busbar (ele))
            )
        })
    }

    def makeTransformerEdge (subtransmission_trafos: Array[TransformerData])(elements: Iterable[Element], cn1: String, cn2: String): TransformerEdge =
    {
        val element = elements.head
        val trafo = subtransmission_trafos.filter (data => data.transformer.id == element.id)
        TransformerEdge (cn1, cn2, TransformerSet (trafo))
    }

    def edge_maker (subtransmission_trafos: Array[TransformerData]) (rdd: RDD[EdgeParts]): RDD[(identifier, GLMEdge)] =
    {
        val tedger = makeTransformerEdge (subtransmission_trafos)_
        rdd.map (
            args =>
            {
                // the terminals may be different for each element, but their TopologicalNode values are the same, so use the head
                val id_cn_1 = args.head._1.head._2.TopologicalNode
                val id_cn_2 = args.head._1.tail.head._2.TopologicalNode
                (args.head._1.head._1, GLMEdge.toGLMEdge (args.map (_._2), id_cn_1, id_cn_2, tedger))
            }
        )
    }

    def zero (list: RDD[(String, String)], results: RDD[ScResult]): RDD[ScResult] =
    {
        results.keyBy (_.tx).leftOuterJoin (list).values.map
        {
            case (result: ScResult, None) => result
            case (result: ScResult, Some (error)) =>
                result.copy (
                    errors = List (error),
                    low_r = 0.0,
                    low_x = 0.0,
                    low_r0 = 0.0,
                    low_x0 = 0.0,
                    low_ik = 0.0,
                    low_ik3pol = 0.0,
                    low_ip = 0.0,
                    low_sk = 0.0,
                    imax_3ph_low = 0.0,
                    imax_1ph_low = 0.0,
                    imax_2ph_low = 0.0,
                    imax_3ph_med = 0.0,
                    imax_1ph_med = 0.0,
                    imax_2ph_med = 0.0,
                    high_r = 0.0,
                    high_x = 0.0,
                    high_r0 = 0.0,
                    high_x0 = 0.0,
                    high_ik = 0.0,
                    high_ik3pol = 0.0,
                    high_ip = 0.0,
                    high_sk = 0.0,
                    branches = null)
        }
    }

    def calculateTraceResults (starting_nodes: RDD[StartingTrafos]): RDD[ScResult] =
    {
        // create the initial Graph with ScNode vertices
        val initial = get_inital_graph ()

        def both_ends (edge: Edge[ScEdge]): Iterable[(VertexId, ScEdge)] = List ((edge.srcId, edge.attr), (edge.dstId, edge.attr))

        def add_starting_trafo (vid: VertexId, node: ScNode, attached: (StartingTrafos, Iterable[ScEdge])): ScNode =
        {
            val trafo = attached._1
            val edges = attached._2

            val errors =
                if (trafo.transformer.total_impedance._2)
                    List (ScError (false, false, "transformer has no impedance value, using default %s".format (options.default_transformer_impedance)))
                else
                    null.asInstanceOf [List[ScError]]
            val problems = edges.foldLeft (errors)((errors, edge) => edge.hasIssues (errors, options))
            ScNode (node.id_seq, node.voltage, trafo.transformer.transformer_name, trafo.transformer.total_impedance._1, "self", trafo.lv_impedance (node.voltage), null, problems)
        }

        val starting_trafos_with_edges = starting_nodes.keyBy (_.nsPin).join (initial.edges.flatMap (both_ends).groupByKey)
        val initial_with_starting_nodes = initial.joinVertices (starting_trafos_with_edges)(add_starting_trafo).persist (storage_level)

        val sct = ShortCircuitTrace (session, options)
        val graph = sct.trace (initial_with_starting_nodes)

        // get the visited nodes with their data
        val result = graph.vertices.filter (null != _._2.impedance).values
        result.setName ("scresult")
        result.persist (storage_level)

        log.info ("computing results")
        // join results with terminals to get equipment
        val d = result.keyBy (_.id_seq).join (get [Terminal].keyBy (_.TopologicalNode)).values
        // join with equipment to get containers
        val e = d.keyBy (_._2.ConductingEquipment).join (get [ConductingEquipment].keyBy (_.id)).map (x => (x._2._1._1, x._2._1._2, x._2._2))
        val f = e.keyBy (_._3.Equipment.EquipmentContainer).leftOuterJoin (get [Element]("Elements").keyBy (_.id)).map (x => (x._2._1._1, x._2._1._2, x._2._1._3, x._2._2))

        // resolve to top level containers
        // the equipment container for a transformer could be a Station or a Bay or VoltageLevel ... the last two of which have a reference to their station
        def station_fn (arg: (ScNode, Terminal, ConductingEquipment, Option[Any])): (ScNode, Int, String, String) =
        {
            val node = arg._1
            val terminal = arg._2
            val equipment = arg._3
            val container = arg._4
            container match
            {
                case Some (station: Substation) => (node, terminal.ACDCTerminal.sequenceNumber, equipment.id, station.id)
                case Some (bay: Bay) => (node, terminal.ACDCTerminal.sequenceNumber, equipment.id, bay.Substation)
                case Some (level: VoltageLevel) => (node, terminal.ACDCTerminal.sequenceNumber, equipment.id, level.Substation)
                case _ => (node, terminal.ACDCTerminal.sequenceNumber, equipment.id, null)
            }
        }

        val g: RDD[(ScNode, Int, String, String)] = f.map (station_fn)

        // compute results
        g.map (calculate_short_circuit).persist (storage_level)
    }

    // execute GridLAB-D to approximate the impedances and replace the error records
    def fix (problem_transformers: RDD[TransformerIsland], original_results: RDD[ScResult], subtransmission_trafos: Array[TransformerData]): RDD[ScResult] =
    {
        val n = problem_transformers.count
        log.info (s"""performing load-flow for $n non-radial network${if (n > 1) "s" else ""}""")

        // transformer area calculations
        val tsa = TransformerServiceArea (session, storage_level)
        val trafos_islands: RDD[(identifier, island_id)] = tsa.getTransformerServiceAreas.map (_.swap) // (trafosetid, islandid)
        def set_island (island: TransformerIsland): Iterable[(identifier, identifier)] =
        {
            for (set <- island.transformers)
                yield (set.transformer_name, set.transformer_name)
        }
        val trafo_island_mapping: RDD[(identifier, island_id)] = problem_transformers
            .flatMap (set_island)
            .join (trafos_islands)
            .values

        if (!trafo_island_mapping.filter (_._2 != "").isEmpty)
        {
            val island_helper = new Island (session, storage_level, options.cable_impedance_limit)
            val graph_stuff = island_helper.queryNetwork (trafo_island_mapping, node_maker, edge_maker (subtransmission_trafos)) // ([nodes], [edges])
            val areas = graph_stuff._1.groupByKey.join (graph_stuff._2.groupByKey).persist (storage_level)

            // set up simulations
            val now = javax.xml.bind.DatatypeConverter.parseDateTime ("2018-07-19T12:00:00")
            def notTheTransformers (island: TransformerIsland) (edge: GLMEdge): Boolean =
            {
                edge match
                {
                    case t: TransformerEdge =>
                        val sets = island.transformers.map (_.transformer_name)
                        !sets.contains (t.transformer.transformer_name)
                    case _ => true
                }
            }
            val simulations = areas
                .join (problem_transformers.keyBy (_.island_name))
                .values
                .map (
                    x =>
                    {
                        val (island, nodes, edges) = (x._2, x._1._1, x._1._2)
                        SimulationTransformerServiceArea (
                            island = island,
                            nodes = nodes,
                            edges = edges.filter (notTheTransformers (island)),
                            start_time = now,
                            directory = island.island_name)
                    }
                )
            // perform remedial simulations produces (trafoid, nodeid, equipment, voltage, trafo.Z, Branch)
            val results = remedial (simulations, options.low_temperature, true).persist (storage_level)
            log.info ("""ran %s experiments""".format (results.count ()))
            // map to the type returned by the trace, use the existing value where possible
            val original_keyed = original_results.keyBy (x => x.tx + "_" + x.node)
            // transformer id, node mrid, attached equipment mrid, nominal node voltage, and impedance at the node
            val new_nodes = results.keyBy (x => x._1 + "_" + x._2).leftOuterJoin (original_keyed).values.map (
                x =>
                {
                    val v = x._1._4
                    val branches = x._1._6
                    val ztrafo = x._1._5
                    val z = if (null == branches) ztrafo else branches.z (ztrafo)
                    x._2 match
                    {
                        case Some (original) =>
                            (
                                ScNode (original.node, v, original.tx, original.tx_impedance, original.prev, z, branches, List (ScError (false, false, "computed by load-flow"))), // replace the errors
                                original.terminal, original.equipment, original.container
                            )
                        case None =>
                            (
                                ScNode (x._1._2, v, x._1._1, Complex(0), null, ztrafo, null, List (ScError (false, false, "computed by load-flow"))),
                                1, x._1._3, ""
                            )
                    }
                }
            )
            // calculate new short circuit result records
            val replacements = new_nodes.map (calculate_short_circuit).persist (storage_level)
            // replace the bad elements
            val bad_transformers = problem_transformers.flatMap (_.transformers.map (_.transformer_name)).collect.toSet
            val some = original_results.filter (x => !bad_transformers.contains (x.tx))
            some.union (replacements)
        }
        else
        {
            log.info ("TopologicalIsland elements not found, cannot use GridLAB-D to fix radial network errors")
            original_results
        }
    }

    def run (): RDD[ScResult] =
    {
        FData.fuse_sizing_table (options.fuse_table)
        assert (null != get [TopologicalNode], "no topology")

        val transformer_data = new Transformers (
            spark,
            storage_level,
            options.default_short_circuit_power_max,
            options.default_short_circuit_impedance_max,
            options.default_short_circuit_angle_max,
            options.default_short_circuit_power_min,
            options.default_short_circuit_impedance_min,
            options.default_short_circuit_angle_min
        ).getTransformers ()

        val transformers = if (null != options.trafos && "" != options.trafos && "all" != options.trafos)
        {
            val source = Source.fromFile (options.trafos, "UTF-8")
            val trafos = source.getLines ().filter (_ != "").toArray
            source.close ()
            transformer_data
                .filter (transformer => trafos.contains (transformer.transformer.id))
                .groupBy (_.node1.TopologicalIsland)
                .values
                .map (TransformerIsland.apply)
        }
        else
            // do all low voltage power transformers
            transformer_data
                .filter (td => (td.v0 > 1000.0) && (td.v1 <= 1000.0)) // ToDo: don't hard code this rule
                .groupBy (_.node1.TopologicalIsland)
                .values
                .map (TransformerIsland.apply)
        val starting_nodes = transformers.flatMap (trafo_mapping)
        log.info ("%s starting transformers".format (starting_nodes.count))

        var results = calculateTraceResults (starting_nodes)

        // find transformers where there are non-radial networks and fix them
        def need_load_flow (error: String): Boolean =
            error.startsWith ("FATAL: non-radial network detected") ||
            error.startsWith ("INVALID: 3 transformer windings") ||
            error.startsWith ("INVALID: low voltage")

        val problem_trafos = results
            .filter (result => result.errors.exists (need_load_flow))
            .map (_.tx)
            .distinct
            .map (set => (set, set))
            .persist (storage_level)

        // but not the ones that have another error
        def other_error (s: String): Boolean =
            !(s.startsWith ("FATAL: non-radial network detected") ||
              s.startsWith ("INVALID: 3 transformer windings") || // ToDo: Remove when 3W transformer test for SC is available
              s.startsWith ("INVALID: low voltage")) &&
              s.startsWith ("INVALID")

        val verboten_trafos = results
            .filter (result => result.errors.exists (other_error))
            .map (result => (result.tx, result.errors.filter (other_error).head))
            .distinct
            .persist (storage_level)

        val sets_by_island = transformers.flatMap (island => island.transformers.map (set => (set.transformer_name, island)))

        val problem_islands = problem_trafos
            .subtractByKey (verboten_trafos)
            .join (sets_by_island)
            .map (_._2._2)
            .distinct

        // ensure that each element of a transformer service area has an error and 0.0 for all current/fuse values
        if (0 != verboten_trafos.count)
            results = zero (verboten_trafos, results)

        if (0 != problem_islands.count)
        {
            def subtransmission (trafo: TransformerData): Boolean =
            {
                trafo.voltages.exists (v => (v._2 <= 1000.0) && (v._2 > 400.0)) || // ToDo: don't hard code these voltage values
                trafo.ends.length > 2
            }
            val subtransmission_trafos = transformer_data.filter (subtransmission).collect
            results = fix (problem_islands, results, subtransmission_trafos)
        }

        results
    }
}

object ShortCircuit
{
    /**
     * The list of classes that can be persisted in RDD.
     */
    lazy val classes: Array[Class[_]] =
    {
        Array (
            classOf [ch.ninecode.sc.Impedanzen],
            classOf [ch.ninecode.sc.ScEdge],
            classOf [ch.ninecode.sc.ScError],
            classOf [ch.ninecode.sc.ScIntermediate],
            classOf [ch.ninecode.sc.ScMessage],
            classOf [ch.ninecode.sc.ScNode],
            classOf [ch.ninecode.sc.ScResult],
            classOf [ch.ninecode.sc.ShortCircuit],
            classOf [ch.ninecode.sc.ShortCircuitOptions],
            classOf [ch.ninecode.sc.StartingTrafos]
        )
    }
}
