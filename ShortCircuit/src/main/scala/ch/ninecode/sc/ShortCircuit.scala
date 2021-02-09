package ch.ninecode.sc

import java.nio.charset.StandardCharsets
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

import scala.collection.Map
import scala.io.Source

import org.apache.log4j.Level
import org.apache.log4j.LogManager
import org.apache.spark.graphx.Edge
import org.apache.spark.graphx.Graph
import org.apache.spark.graphx.VertexId
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.cim.CIMClasses
import ch.ninecode.cim.CIMRDD
import ch.ninecode.cim.DefaultSource
import ch.ninecode.gl.GLMEdge
import ch.ninecode.gl.GLMLineEdge
import ch.ninecode.gl.GLMSwitchEdge
import ch.ninecode.gl.GLMTransformerEdge
import ch.ninecode.gl.GridLABD
import ch.ninecode.model.ACLineSegment
import ch.ninecode.model.BaseVoltage
import ch.ninecode.model.Bay
import ch.ninecode.model.ConductingEquipment
import ch.ninecode.model.Element
import ch.ninecode.model.PowerTransformer
import ch.ninecode.model.PowerTransformerEnd
import ch.ninecode.model.Substation
import ch.ninecode.model.Terminal
import ch.ninecode.model.TopologicalNode
import ch.ninecode.model.UserAttribute
import ch.ninecode.model.VoltageLevel
import ch.ninecode.net.Island.identifier
import ch.ninecode.net.Island.island_id
import ch.ninecode.net.Net
import ch.ninecode.net.TransformerData
import ch.ninecode.net.TransformerIsland
import ch.ninecode.net.TransformerServiceArea
import ch.ninecode.net.Transformers
import ch.ninecode.sc.ScEdge.resistanceAt
import ch.ninecode.util.CIMInitializer
import ch.ninecode.util.Complex
import ch.ninecode.util.Graphable
import ch.ninecode.util.Main
import ch.ninecode.util.MainOptions
import ch.ninecode.util.SparkOptions
import ch.ninecode.util.ThreePhaseComplexDataElement
import ch.ninecode.util.Util


/**
 * Short circuit calculation.
 * Uses GraphX to trace the topology and generate the short circuit results at each node.
 *
 * @param session       the Spark session
 * @param storage_level specifies the <a href="https://spark.apache.org/docs/latest/programming-guide.html#which-storage-level-to-choose">Storage Level</a>
 *                      used to persist and serialize the objects
 * @param options       options for short-circuit processing
 */
case class ShortCircuit (session: SparkSession, storage_level: StorageLevel, options: ShortCircuitOptions) extends CIMRDD with Graphable
{
    implicit val spark: SparkSession = session
    implicit val log: Logger = LoggerFactory.getLogger(getClass)
    implicit val storage: StorageLevel = storage_level

    val default_node: ScNode = ScNode()

    def edge_operator (voltages: Map[String, Double])(arg: (Element, Iterable[(Terminal, Option[End])], Option[String])): List[ScEdge] =
    {
        val (element, t_it, standard) = arg

        // get the ConductingEquipment
        var c = element
        while ((null != c) && !c.getClass.getName.endsWith(".ConductingEquipment"))
            c = c.sup

        c match
        {
            case equipment: ConductingEquipment =>
                // sort terminals by sequence number
                // except if it has ends, and then sort so the primary is index 0
                def sortnumber (arg: (Terminal, Option[End])) = arg._2 match
                {
                    case Some(end) => end.endNumber
                    case None => arg._1.ACDCTerminal.sequenceNumber
                }

                // the equipment voltage - doesn't work for transformers
                val volt = 1000.0 * voltages.getOrElse(equipment.BaseVoltage, 0.0)
                val terminals = t_it.toArray.sortWith(sortnumber(_) < sortnumber(_)).map(terminal_end =>
                {
                    val voltage =
                        terminal_end._2 match
                        {
                            case Some(end) =>
                                1000.0 * voltages.getOrElse(end.BaseVoltage, 0.0)
                            case None =>
                                volt
                        }
                    val impedance = element match
                    {
                        case line: ACLineSegment =>
                            val dist_km = line.Conductor.len / 1000.0
                            Impedanzen(
                                Complex(resistanceAt(options.low_temperature, options.base_temperature, line.r) * dist_km, line.x * dist_km),
                                Complex(resistanceAt(options.low_temperature, options.base_temperature, line.r0) * dist_km, line.x0 * dist_km),
                                Complex(resistanceAt(options.high_temperature, options.base_temperature, line.r) * dist_km, line.x * dist_km),
                                Complex(resistanceAt(options.high_temperature, options.base_temperature, line.r0) * dist_km, line.x0 * dist_km))
                        case _: PowerTransformer =>
                            terminal_end._2 match
                            {
                                case Some(end) =>
                                    val z = Complex(end.r, end.x)
                                    Impedanzen(z, z, z, z)
                                case None =>
                                    Impedanzen(0.0, 0.0, 0.0, 0.0) // no end on a transformer terminal? WTF?
                            }

                        case _ =>
                            Impedanzen(0.0, 0.0, 0.0, 0.0)
                    }
                    (terminal_end._1, voltage, impedance)
                })
                // make a short-circuit edge for each pair of terminals, zero and one length lists of terminals have been filtered out
                val eq = terminals(0)._1.ConductingEquipment
                val node1 = terminals(0)._1.TopologicalNode
                val voltage1 = terminals(0)._2
                val z = terminals(0)._3
                val ret = for (i <- 1 until terminals.length; // for comprehension: iterate omitting the upper bound
                               node2 = terminals(i)._1.TopologicalNode;
                               // eliminate edges with only one connectivity node, or the same connectivity node
                               if null != node1 && null != node2 && "" != node1 && "" != node2 && node1 != node2)
                    yield ScEdge(
                        node1,
                        voltage1,
                        node2,
                        terminals(i)._2,
                        terminals.length,
                        eq,
                        element,
                        standard,
                        z
                    )
                ret.toList
            case _ =>
                // shouldn't happen, terminals always reference ConductingEquipment, right?
                List()
        }
    }

    def trafo_mapping (transformer_island: TransformerIsland): Iterator[StartingTrafos] =
    {
        // if a transformer set has multiple transformers, their primaries and secondaries are tied together
        // but we need to handle three winding transformers
        transformer_island
            .transformers
            .flatMap(
                set =>
                {
                    val transformer = set.transformers(0)
                    for (lv_node <- transformer.nodes.tail)
                        yield StartingTrafos(vertex_id(transformer.node0.id), vertex_id(lv_node.id), set)
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
            case Some(pte) =>
                Some(End(pte.PowerTransformer, pte.TransformerEnd.endNumber, pte.TransformerEnd.BaseVoltage, pte.r, pte.x))
            case None =>
                None
        }
        (terminal, end)
    }

    def persist (x: RDD[_], name: String): Unit =
    {
        x.name = name
        val _ = x.persist(storage_level)
        if (spark.sparkContext.getCheckpointDir.isDefined)
            x.checkpoint()
    }

    def getFuseStandards: RDD[(String, String)] =
    {
        val types = Array("DIN", "SEV")
        val attributes = getOrElse[UserAttribute]
        attributes.filter(x => types.contains(x.name)).map(x => (x.value, x.name))
    }

    def get_inital_graph (): Graph[ScNode, ScEdge] =
    {
        // get a map of voltages
        val voltages = getOrElse[BaseVoltage].map(v => (v.id, v.nominalVoltage)).collectAsMap()

        // get the terminals in the topology
        val terminals = getOrElse[Terminal].filter(null != _.TopologicalNode)

        // handle transformers specially, by attaching PowerTransformerEnd objects to the terminals

        // get the transformer ends keyed by terminal, only one end can reference any one terminal
        val ends = getOrElse[PowerTransformerEnd].keyBy(_.TransformerEnd.Terminal)
        // attach the ends to terminals
        val t = terminals.keyBy(_.id).leftOuterJoin(ends).values.map(make_ends_meet)
        // get the terminals keyed by equipment and filter for two (or more) terminals
        val terms = t.groupBy(_._1.ConductingEquipment).filter(_._2.size > 1)

        // map the terminal 'pairs' to edges
        val edges = getOrElse[Element]
            .keyBy(_.id)
            .join(terms)
            .leftOuterJoin(getFuseStandards)
            .values
            .map(x => (x._1._1, x._1._2, x._2))
            .flatMap(edge_operator(voltages))

        // get the nodes and voltages from the edges
        val nodes = edges.flatMap(
            x => List(
                ScNode(id_seq = x.id_cn_1, voltage = x.v1),
                ScNode(id_seq = x.id_cn_2, voltage = x.v2))).distinct

        // persist edges and nodes to avoid recompute
        val xedges = edges.map(e => Edge(e.vertex_id(e.id_cn_1), e.vertex_id(e.id_cn_2), e))
        val xnodes = nodes.map(v => (v.vertex_id(v.id_seq), v))
        persist(xedges, "xedges")
        persist(xnodes, "xnodes")

        Graph[ScNode, ScEdge](xnodes, xedges, default_node, storage_level, storage_level)
    }

    def calculate_one (voltage: Double, impedanz: Complex, null_impedanz: Complex): ScIntermediate =
    {
        val root3 = Math.sqrt(3.0)

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
            1.02 + 0.98 * Math.exp(-3.0)
        else
            if (0.0 == impedanz.im)
                0.0
            else
                1.02 + 0.98 * Math.exp(-3.0 * impedanz.re / impedanz.im)
        val ip = kappa * Math.sqrt(2) * ik3pol

        // short-circuit power at the point of common coupling
        val sk = (voltage * voltage) / !impedanz

        // maximum (motor) power (W) for repetition_rate<0.01/min and 0.01≤r<0.1 /min, override default settings for pf=1.0=cos(90), inrush=1x
        val imax3ph = MaximumStartingCurrent.max_current_3_phase(sk, impedanz, voltage, options)
        val imax1ph = MaximumStartingCurrent.max_current_1_phase(sk, impedanz, voltage, options)
        val imax2ph = MaximumStartingCurrent.max_current_2_phase(sk, impedanz, voltage, options)

        ScIntermediate(ik, ik3pol, ip, sk, imax3ph._1, imax1ph._1, imax2ph._1, imax3ph._2, imax1ph._2, imax2ph._2)
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
                (ScIntermediate(), ScIntermediate())
            else
                (calculate_one(v2, node.impedance.impedanz_low, node.impedance.null_impedanz_low),
                    calculate_one(v2, node.impedance.impedanz_high, node.impedance.null_impedanz_high))
        val costerm = MaximumStartingCurrent.costerm(node.impedance.impedanz_low, options)

        ScResult(node.id_seq, equipment, node.voltage, terminal, container,
            if (null == node.errors) List() else node.errors.map(_.toString),
            node.source_id, node.id_prev, node.impedance.impedanz_low.re, node.impedance.impedanz_low.im,
            node.impedance.null_impedanz_low.re, node.impedance.null_impedanz_low.im,
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
        val elementindex = s.indexOf("%")
        if (-1 == elementindex) s else s.substring(0, elementindex)
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
                    val elementindex = datum.element.indexOf("%")
                    if (-1 == elementindex)
                        None
                    else
                        Some((datum.element.substring(0, elementindex), datum.element.substring(elementindex + 1), value))
                }
                else
                    None
            case _ =>
                None
        }
    }

    // extract TRAxxx from the path name returned by wholeTextFiles
    def extract_trafo (k: (String, String)): (String, String) =
    {
        val (path, contents) = k
        val trafo_pattern = ".*/(.*)/output.txt"
        val trafo = path.replaceAll(trafo_pattern, "$1")
        (trafo, contents)
    }

    // convert GridLAB-D output date-time into unix epoch long value
    val toTimeStamp: String => Long =
    {
        val date_format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z")
        string =>
            try
            {
                date_format.parse(string).getTime
            }
            catch
            {
                case pe: ParseException =>
                    log.warn(pe.getMessage)
                    0L
            }
    }

    def toD (string: String): Double =
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

    def read_output_files (one_phase: Boolean, workdir_slash: String, filenames: Array[String]): RDD[(String, ThreePhaseComplexDataElement)] =
    {
        val pattern = java.util.regex.Pattern.compile("# output_data/([^.]*).csv run at (.*) on (\\d*) nodes")
        val executors = session.sparkContext.getExecutorMemoryStatus.keys.size - 1
        val path = filenames.map(file => s"$workdir_slash$file/output.txt").mkString(",")
        val files = session.sparkContext.wholeTextFiles(path, executors)

        def read (f: String): TraversableOnce[ThreePhaseComplexDataElement] =
        {
            var experiment: String = ""
            var timestamp: Long = 0L
            var records: Int = 0
            val units = "Volts"
            val content = f.split("\n")
            val nothing = ThreePhaseComplexDataElement("", 0L, 0.0, 0.0, 0.0, "")

            def makeResult (c: String): ThreePhaseComplexDataElement =
            {
                if (c.startsWith("#"))
                {
                    val matcher = pattern.matcher(c)
                    if (matcher.find)
                    {
                        val dump = c.substring(matcher.start(1), matcher.end(1))
                        experiment = if (dump.endsWith("_voltdump")) dump.substring(0, dump.length - 9) else dump
                        timestamp = toTimeStamp(c.substring(matcher.start(2), matcher.end(2)))
                        records = c.substring(matcher.start(3), matcher.end(3)).toInt
                    }
                    nothing
                }
                else
                    if (c.startsWith("node_name"))
                        nothing
                    else
                    {
                        val c_arr = c.split(",")
                        if (c_arr.length == 7)
                            if (one_phase)
                                ThreePhaseComplexDataElement(c_arr(0), timestamp, Complex(toD(c_arr(1)), toD(c_arr(2))), Complex(0.0), Complex(0.0), units)
                            else
                                ThreePhaseComplexDataElement(c_arr(0), timestamp, Complex(toD(c_arr(1)), toD(c_arr(2))), Complex(toD(c_arr(3)), toD(c_arr(4))), Complex(toD(c_arr(5)), toD(c_arr(6))), units)
                        else
                        {
                            log.error(s"""$experiment voltage dump text "$c" cannot be interpreted as three phase complex $units""")
                            nothing
                        }
                    }
            }

            content.map(makeResult).filter("" != _.element)
        }

        files.map(extract_trafo).flatMapValues(read)
    }

    /**
     * Reduce series connected elements.
     *
     * @param network     the current network to be reduced
     * @param trafo_nodes the list of starting trafo nodes
     * @return the reduced network with one pair of series elements converted to a series branch
     */
    def reduce_series (network: Iterable[Branch], trafo_nodes: Array[String], mrid: String): (Boolean, Iterable[Branch]) = // (reduced?, network)
    {
        // check for series elements, eliminate making a series connection across the house or trafo
        val prepend =
            for
                {
                branch <- network
                house = branch.from == mrid
                if !house
                trafo = trafo_nodes.contains(branch.from)
                if !trafo
                buddies = network.filter(x => (branch.from == x.to) || (branch.from == x.from && branch != x))
                if buddies.size == 1
                buddy :: _ = buddies
            }
                yield (branch, buddy)

        val append =
            for
                {
                branch <- network
                house = branch.to == mrid
                if !house
                trafo = trafo_nodes.contains(branch.to)
                if !trafo
                buddies = network.filter(x => (branch.to == x.from) || (branch.to == x.to && branch != x))
                if buddies.size == 1
                buddy :: _ = buddies
            }
                yield (branch, buddy)

        val series = prepend ++ append

        series match
        {
            case (branch, buddy) :: _ =>
                // only do one reduction at a time... I'm not smart enough to figure out how to do it in bulk
                val rest = network.filter(x => branch != x && buddy != x)
                val new_series = if (buddy.to == branch.from)
                    buddy.add_in_series(branch)
                else
                    if (branch.to == buddy.from)
                        branch.add_in_series(buddy)
                    else
                    // choose the simplest element to reverse
                        branch match
                        {
                            case _: SimpleBranch =>
                                buddy.add_in_series(branch.reverse)
                            case _: TransformerBranch =>
                                buddy.add_in_series(branch.reverse)
                            case _ =>
                                buddy.reverse.add_in_series(branch)
                        }
                (true, Seq(new_series) ++ rest)
            case _ =>
                (false, network)
        }
    }

    /**
     * Reduce parallel connected elements.
     *
     * @param network the current network to be reduced
     * @return the reduced network with one pair of parallel elements converted to a parallel branch
     */
    @SuppressWarnings(Array("org.wartremover.warts.TraversableOps"))
    def reduce_parallel (network: Iterable[Branch]): (Boolean, Iterable[Branch]) = // (reduced?, network)
    {
        // check for parallel elements
        val parallel = for
            {
            branch <- network
            buddies = network.filter(x => ((branch.from == x.from) && (branch.to == x.to)) && (branch != x))
            if buddies.nonEmpty
        }
            yield buddies ++ Seq(branch)
        parallel match
        {
            case set :: _ =>
                // only do one reduction at a time... I'm not smart enough to figure out how to do it in bulk
                (true, Seq(set.head.add_in_parallel(set.tail)) ++ network.filter(x => !set.toSeq.contains(x)))
            case _ =>
                (false, network)
        }
    }

    def reduce (branches: Iterable[Branch], trafo_nodes: Array[String], mrid: String): Iterable[Branch] =
    {
        // step by step reduce the network to a single branch through series and parallel reductions
        var done = false
        var network: Iterable[Branch] = branches
        do
        {
            val (modified, net) = reduce_series(network, trafo_nodes, mrid)
            network = net
            done = !modified
            if (done)
            {
                val (modified, net) = reduce_parallel(network)
                network = net
                done = !modified
                // check that all branches start from the transformer
                if (done)
                    if (!network.forall(x => trafo_nodes.contains(x.from)))
                    {
                        val max = network.map(_.current).foldLeft(Double.MinValue)((x: Double, y: Double) => if (x > y) x else y)
                        val significant = max * 0.01 // 1% of the maximum current
                        val filtered = network.filter(x =>
                            (x.current > significant)
                                || trafo_nodes.contains(x.from)
                                || trafo_nodes.contains(x.to)
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

    @SuppressWarnings(Array("org.wartremover.warts.TraversableOps"))
    def makeCableBranch (cable: GLMLineEdge, voltage1: ThreePhaseComplexDataElement, voltage2: ThreePhaseComplexDataElement, v1: Double, v2: Double): List[Branch] =
    {
        // Adjust this threshold according the chosen "default_maximum_voltage_error" in gridlabd
        if (Math.abs(v1 - v2) < 1e-5)
            List()
        else
        {
            val line = cable.lines.head
            val dist_km = line.Conductor.len / 1000.0
            var z = Impedanzen(
                Complex(resistanceAt(options.low_temperature, options.base_temperature, line.r) * dist_km, line.x * dist_km),
                Complex(resistanceAt(options.low_temperature, options.base_temperature, line.r0) * dist_km, line.x0 * dist_km),
                Complex(resistanceAt(options.high_temperature, options.base_temperature, line.r) * dist_km, line.x * dist_km),
                Complex(resistanceAt(options.high_temperature, options.base_temperature, line.r0) * dist_km, line.x0 * dist_km))
            for (l <- cable.lines.tail)
            {
                val z1 = Impedanzen(
                    Complex(resistanceAt(options.low_temperature, options.base_temperature, l.r) * dist_km, l.x * dist_km),
                    Complex(resistanceAt(options.low_temperature, options.base_temperature, l.r0) * dist_km, l.x0 * dist_km),
                    Complex(resistanceAt(options.high_temperature, options.base_temperature, l.r) * dist_km, l.x * dist_km),
                    Complex(resistanceAt(options.high_temperature, options.base_temperature, l.r0) * dist_km, l.x0 * dist_km))
                z = Impedanzen(
                    z.impedanz_low.parallel_impedanz(z1.impedanz_low),
                    z.null_impedanz_low.parallel_impedanz(z1.null_impedanz_low),
                    z.impedanz_high.parallel_impedanz(z1.impedanz_high),
                    z.null_impedanz_high.parallel_impedanz(z1.null_impedanz_high))
            }
            val name = line.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.name
            if (v1 > v2)
                List(SimpleBranch(cable.cn1, cable.cn2, ((voltage1.value_a - voltage2.value_a) / z.impedanz_low).modulus, cable.id, name, None, "", z))
            else
                List(SimpleBranch(cable.cn2, cable.cn1, ((voltage2.value_a - voltage1.value_a) / z.impedanz_low).modulus, cable.id, name, None, "", z))
        }
    }

    @SuppressWarnings(Array("org.wartremover.warts.TraversableOps"))
    def makeSwitchBranch (switch: GLMSwitchEdge, lvnodes: Array[String], mrid: String, v1: Double, v2: Double): List[Branch] =
    {
        if (!switch.closed)
            List()
        else
        {
            val rating = if (switch.fuse) Some(switch.ratedCurrent) else None
            val name = switch.data.switches.head.asSwitch.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.name
            val stds = switch.data.switches.flatMap(_.standard).toArray.distinct
            val std = if (0 == stds.length) "" else stds(0) // ToDo: what if parallel switches have different standards?
            if (lvnodes.contains(switch.cn1))
                List(SimpleBranch(switch.cn1, switch.cn2, 0.0, switch.id, name, rating, std))
            else
                if (lvnodes.contains(switch.cn2))
                    List(SimpleBranch(switch.cn2, switch.cn1, 0.0, switch.id, name, rating, std))
                else
                    if (mrid == switch.cn2)
                        List(SimpleBranch(switch.cn1, switch.cn2, 0.0, switch.id, name, rating, std))
                    else
                        if (mrid == switch.cn1)
                            List(SimpleBranch(switch.cn2, switch.cn1, 0.0, switch.id, name, rating, std))
                        else
                            if (v1 > v2)
                                List(SimpleBranch(switch.cn1, switch.cn2, 0.0, switch.id, name, rating, std))
                            else
                                List(SimpleBranch(switch.cn2, switch.cn1, 0.0, switch.id, name, rating, std))
        }
    }

    def makeTransformerBranch (transformer: GLMTransformerEdge, v1: Double, v2: Double): List[Branch] =
    {
        List(TransformerBranch(transformer.cn1, transformer.cn2, 0.0, transformer.transformer.transformer_name, transformer.id,
            transformer.transformer.power_rating, v1, v2, transformer.transformer.total_impedance_per_unit._1))

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
        val lvnodes: Array[String] = trafokreis.island.transformers.flatMap(x => for (node <- x.transformers(0).nodes.tail) yield node.id)

        // get directed edges hi→lo voltage = Branch from→to
        val graph_edges: Iterable[Branch] = edges.flatMap(
            x =>
            {
                data.find(y => y.element == x.cn1) match
                {
                    case Some(voltage1) =>
                        data.find(y => y.element == x.cn2) match
                        {
                            case Some(voltage2) =>
                                val v1 = voltage1.value_a.modulus
                                val v2 = voltage2.value_a.modulus
                                x match
                                {
                                    case switch: GLMSwitchEdge =>
                                        makeSwitchBranch(switch, lvnodes, experiment.mrid, v1, v2)
                                    case cable: GLMLineEdge =>
                                        makeCableBranch(cable, voltage1, voltage2, v1, v2)
                                    case transformer: GLMTransformerEdge =>
                                        makeTransformerBranch(transformer, v1, v2)
                                    case _ =>
                                        log.error(s"unexpected edge type ${x.toString}")
                                        if (v1 > v2)
                                            List(SimpleBranch(x.cn1, x.cn2, 0.0, x.id, "", None, ""))
                                        else
                                            List(SimpleBranch(x.cn2, x.cn1, 0.0, x.id, "", None, ""))
                                }
                            case None =>
                                List()
                        }
                    case None =>
                        List()
                }
            }
        )

        def connectedTo (branch: Branch, edges: Iterable[Branch]): Boolean =
        {
            edges.exists(edge => edge != branch && (branch.to == edge.to || branch.to == edge.from))
        }

        def connectedFrom (branch: Branch, edges: Iterable[Branch]): Boolean =
        {
            edges.exists(edge => edge != branch && (branch.from == edge.to || branch.from == edge.from))
        }

        // eliminate branches in the tree than only have one end connected - except for the starting and ending node
        def no_stubs (edges: Iterable[Branch], start: Array[String], end: String)(branch: Branch): Boolean =
        {
            val to = connectedTo(branch, edges)
            val from = connectedFrom(branch, edges)
            val connected = to && from
            val into = start.contains(branch.to)
            val infrom = start.contains(branch.from)

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
            family = family.filter(no_stubs(family, lvnodes, experiment.mrid))
        }
        while (count != family.size)
        val branches = reduce(family, lvnodes, experiment.mrid)
        // ToDo: this will need to be revisited for mesh networks where there are multiple supplying transformers - finding the first is not sufficient
        val twig = branches.find(
            branch =>
                ((experiment.mrid == branch.to) && lvnodes.contains(branch.from)) ||
                    ((experiment.mrid == branch.from) && lvnodes.contains(branch.to))
        )

        // compute the impedance from start to end
        // ToDo: this will need to be revisited for mesh networks where there are multiple supplying transformers
        val tx = StartingTrafos(0L, 0L, trafokreis.island.transformers(0))
        val (path, impedance) = twig match
        {
            case Some(branch) =>
                val _branch = if (experiment.mrid == branch.to) branch else branch.reverse
                val v = experiment.voltage / _branch.voltageRatio
                (_branch, tx.lv_impedance(v))
            case _ =>
                val b: Branch = if (lvnodes.contains(experiment.mrid))
                    SimpleBranch(experiment.mrid, experiment.mrid, 0.0, experiment.mrid, "", None, "")
                else
                {
                    val lv = lvnodes.mkString(",")
                    val trace = branches.map(_.asString).mkString("\n")
                    log.error(s"complex branch network from $lv to ${experiment.mrid}\n$trace")
                    // get the total current to the energy consumer
                    val directs = branches.filter(experiment.mrid == _.to)
                    val sum = directs.map(_.current).sum
                    // generate a fake impedance
                    ComplexBranch(lvnodes.mkString(","), experiment.mrid, sum, branches.toArray)
                }
                (b, tx.lv_impedance(experiment.voltage))
        }
        (experiment.trafo, experiment.mrid, experiment.equipment, experiment.voltage, impedance, path)
    }

    def setName (rdd: RDD[_], name: String): Unit =
    {
        val _ = rdd.setName(name)
    }

    /**
     * Perform gridlabd via Spark pipe() and collect the experimental results.
     *
     * @param gridlabd    the object to solve the .glm files and read the recorders
     * @param one_phase   if <code>true</code>, create single phase results, otherwise three phase results
     * @param isMax       If <code>true</code> use maximum currents (lowest impedances) [for motor starting currents],
     *                    otherwise minimum currents (highest impedances) [for fuse sizing and specificity].
     * @param simulations the simulations with experiments
     * @return an RDD of tuples with the transformer id, node mrid, attached equipment mrid,
     *         nominal node voltage, and impedance at the node
     */
    def solve_and_analyse (
        gridlabd: GridLABD,
        one_phase: Boolean,
        isMax: Boolean,
        simulations: RDD[SimulationTransformerServiceArea]): RDD[(String, String, String, Double, Impedanzen, Branch)] =
    {
        val b4_solve = System.nanoTime()
        val trafos = simulations.map(_.island.island_name)
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
        val output = read_output_files(one_phase, gridlabd.workdir_slash, trafos.collect).setName("output")

        val read = System.nanoTime()
        log.info("read: %s seconds".format((read - solved) / 1e9))

        val values: RDD[(island_id, Iterable[ThreePhaseComplexDataElement])] = output
            .map(x => (s"${x._1}_${x._2.millis}", x._2))
            .groupByKey
            .setName("values")
        val groups = simulations.flatMap(
            (simulation: SimulationTransformerServiceArea) => simulation.experiments.map(
                (experiment: ScExperiment) => (s"${experiment.trafo}_${experiment.t1.getTimeInMillis}", (simulation, experiment))))
            .setName("groups")

        val exp = groups
            .join(values)
            .values
            .setName("exp")

        val z = exp
            .map(evaluate)
            .setName("z")

        val anal = System.nanoTime()
        log.info("analyse: %s seconds".format((anal - read) / 1e9))
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
        val _DateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z")
        if (USE_UTC)
            _DateFormat.setTimeZone(TimeZone.getTimeZone("UTC"))
        else
            _DateFormat.setTimeZone(TimeZone.getTimeZone("CET"))

        def generate (gridlabd: GridLABD, trafokreis: SimulationTransformerServiceArea): Unit =
        {
            val generator = ScGLMGenerator(one_phase = true, temperature = temperature, date_format = _DateFormat, trafokreis, isMax = isMax)
            gridlabd.export(generator)
        }

        val gridlabd = new GridLABD(session, storage_level = storage_level, workdir = options.workdir, cable_impedance_limit = options.cable_impedance_limit)
        val experiments = simulations.flatMap(
            x =>
            {
                generate(gridlabd, x)
                x.experiments
            }
        ).persist(storage_level)
            .setName("experiments")

        def short (exp: ScExperiment): Array[Byte] =
        {
            val gigaohm = Complex(1e9)

            def addrow (time: Calendar, impedance: Complex): String =
            {
                val timestamp = _DateFormat.format(time.getTime)
                s"$timestamp,${impedance.re},${impedance.im}"
            }

            val content = List(
                addrow(exp.t0, gigaohm), // gridlab extends the first and last rows till infinity -> make them zero
                addrow(exp.t1, exp.impedance),
                addrow(exp.t2, gigaohm) // gridlab extends the first and last rows till infinity -> make them zero
            )
            content.mkString("", "\n", "\n").getBytes(StandardCharsets.UTF_8)
        }

        def generate_player_file (gridlabd: GridLABD)(experiment: ScExperiment): Int =
        {
            if (false)
            {
                gridlabd.writeInputFile(experiment.trafo, "input_data/" + experiment.mrid + "_R.csv", short(experiment))
                gridlabd.writeInputFile(experiment.trafo, "input_data/" + experiment.mrid + "_S.csv", short(experiment))
                gridlabd.writeInputFile(experiment.trafo, "input_data/" + experiment.mrid + "_T.csv", short(experiment))
            }
            else
                gridlabd.writeInputFile(experiment.trafo, "input_data/" + experiment.mrid + ".csv", short(experiment))
            1
        }

        val n = experiments.map(generate_player_file(gridlabd)).setName("n")
        log.info("""running %s experiments""".format(n.count))

        solve_and_analyse(gridlabd = gridlabd, one_phase = true, isMax, simulations)
    }

    @SuppressWarnings(Array("org.wartremover.warts.Null"))
    private def zero (list: RDD[(String, String)], results: RDD[ScResult]): RDD[ScResult] =
    {
        results.keyBy(_.tx).join(list).values.map((result: (ScResult, String)) =>
        {
            result._1.copy(
                errors = List(result._2),
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
        })
    }

    @SuppressWarnings(Array("org.wartremover.warts.Null"))
    def calculateTraceResults (transformers: RDD[TransformerIsland]): RDD[ScResult] =
    {
        // create the initial Graph with ScNode vertices
        val initial = get_inital_graph()

        def both_ends (edge: Edge[ScEdge]): Iterable[(VertexId, ScEdge)] = List((edge.srcId, edge.attr), (edge.dstId, edge.attr))

        def add_starting_trafo (vid: VertexId, node: ScNode, attached: (StartingTrafos, Iterable[ScEdge])): ScNode =
        {
            val (trafo, edges) = attached

            val errors =
                if (trafo.transformer.total_impedance._2)
                    List(ScError(fatal = false, invalid = false, s"transformer has no impedance value, using default ${options.default_transformer_impedance}"))
                else
                    null
            val problems = edges.foldLeft(errors)((errors, edge) => edge.hasIssues(errors, options))
            ScNode(id_seq = node.id_seq, voltage = node.voltage, source_id = trafo.transformer.transformer_name, id_prev = "self", impedance = trafo.lv_impedance(node.voltage), errors = problems)
        }

        val starting_nodes: RDD[StartingTrafos] = transformers.flatMap(trafo_mapping).setName("starting_nodes")
        val starting_trafos_with_edges = starting_nodes.keyBy(_.nsPin).join(initial.edges.flatMap(both_ends).groupByKey)
            .setName("starting_trafos_with_edges")
        val initial_with_starting_nodes: Graph[ScNode, ScEdge] = initial.joinVertices(starting_trafos_with_edges)(add_starting_trafo).persist(storage_level)
        setName(initial_with_starting_nodes.edges, "initial_with_starting_nodes edges")
        setName(initial_with_starting_nodes.vertices, "initial_with_starting_nodes vertices")

        val sct = ShortCircuitTrace(session, options)
        val graph = sct.trace(initial_with_starting_nodes)
        setName(graph.edges, "graph edges")
        setName(graph.vertices, "graph vertices")

        // get the visited nodes with their data
        val result = graph.vertices.filter(null != _._2.impedance).values
        persist(result, "scresult")

        log.info("computing results")
        // join results with terminals to get equipment
        val d = result.keyBy(_.id_seq).join(getOrElse[Terminal].keyBy(_.TopologicalNode)).values.setName("d")
        // join with equipment to get containers
        val e = d.keyBy(_._2.ConductingEquipment).join(getOrElse[ConductingEquipment].keyBy(_.id)).map(x => (x._2._1._1, x._2._1._2, x._2._2)).setName("e")
        val f = e.keyBy(_._3.Equipment.EquipmentContainer).leftOuterJoin(getOrElse[Element].keyBy(_.id)).map(x => (x._2._1._1, x._2._1._2, x._2._1._3, x._2._2)).setName("f")

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
                case Some(station: Substation) => (node, terminal.ACDCTerminal.sequenceNumber, equipment.id, station.id)
                case Some(bay: Bay) => (node, terminal.ACDCTerminal.sequenceNumber, equipment.id, bay.Substation)
                case Some(level: VoltageLevel) => (node, terminal.ACDCTerminal.sequenceNumber, equipment.id, level.Substation)
                case _ => (node, terminal.ACDCTerminal.sequenceNumber, equipment.id, "")
            }
        }

        val g: RDD[(ScNode, Int, String, String)] = f.map(station_fn)

        // compute results
        g.map(calculate_short_circuit).persist(storage_level)
    }

    // execute GridLAB-D to approximate the impedances and replace the error records
    @SuppressWarnings(Array("org.wartremover.warts.Throw", "org.wartremover.warts.AsInstanceOf"))
    def fix (problem_transformers: RDD[TransformerIsland], original_results: RDD[ScResult]): RDD[ScResult] =
    {
        val n = problem_transformers.count
        log.info(s"""performing load-flow for $n non-radial network${if (n > 1) "s" else ""}""")

        // transformer area calculations
        val tsa = TransformerServiceArea(session, storage_level, calculate_public_lighting = options.calculate_public_lighting)
        val trafos_islands: RDD[(identifier, island_id)] = tsa.getTransformerServiceAreas.map(_.swap).setName("trafos_islands") // (trafosetid, islandid)
        def set_island (island: TransformerIsland): Iterable[(identifier, identifier)] =
        {
            for (set <- island.transformers)
                yield (set.transformer_name, set.transformer_name)
        }

        val trafo_island_mapping: RDD[(identifier, island_id)] = problem_transformers
            .flatMap(set_island)
            .join(trafos_islands)
            .values
            .setName("trafo_island_mapping")

        if (!trafo_island_mapping.filter(_._2 != "").isEmpty)
        {
            val island_helper = new ShortCircuitIsland(session, storage_level, options)
            val graph_stuff = island_helper.queryNetwork(trafo_island_mapping) // ([nodes], [edges])
            val areas = graph_stuff._1.groupByKey.join(graph_stuff._2.groupByKey).persist(storage_level)

            // set up simulations
            val now = javax.xml.bind.DatatypeConverter.parseDateTime("2018-07-19T12:00:00")

            def notTheTransformers (island: TransformerIsland): GLMEdge => Boolean =
            {
                val trafos = island.transformers.flatMap(
                    trafo =>
                    {
                        Set(trafo.transformer_name) ++ trafo.transformers.map(x => x.transformer.id).toSet
                    }
                ).toSet

                {
                    case t: GLMTransformerEdge =>
                        !trafos.contains(t.transformer.transformer_name)
                    case _ => true
                }
            }

            val simulations: RDD[SimulationTransformerServiceArea] = areas
                .join(problem_transformers.keyBy(_.island_name))
                .values
                .map(
                    x =>
                    {
                        val ((nodes, edges), island) = x.asInstanceOf[((Iterable[SimulationNode], Iterable[GLMEdge]), TransformerIsland)]
                        SimulationTransformerServiceArea(
                            island = island,
                            nodes = nodes,
                            edges = edges.filter(notTheTransformers(island)),
                            start_time = now,
                            directory = island.island_name)
                    })
                .setName("simulations")
                .persist(storage_level)

            // perform remedial simulations produces (trafoid, nodeid, equipment, voltage, trafo.Z, Branch)
            val results = remedial(simulations, options.low_temperature, isMax = true).persist(storage_level)
            log.info("""ran %s experiments""".format(results.count()))
            // map to the type returned by the trace, use the existing value where possible
            val original_keyed = original_results.keyBy(x => s"${x.tx}_${x.node}")
            // transformer id, node mrid, attached equipment mrid, nominal node voltage, and impedance at the node
            results.keyBy(x => s"${x._1}_${x._2}").join(original_keyed).values.map(
                (x: ((String, String, String, Double, Impedanzen, Branch), ScResult)) =>
                {
                    // TODO: remove not used parameter ...@_
                    val (transformer@_, node@_, equipment@_, v, ztrafo, branches) = x._1
                    val original = x._2
                    val z = if (null == branches) ztrafo else branches.z(ztrafo)
                    calculate_short_circuit((
                        ScNode(original.node, v, original.tx, original.prev, z, branches, List(ScError(fatal = false, invalid = false, "computed by load-flow"))), // replace the errors
                        original.terminal, original.equipment, original.container
                    ))
                }).persist(storage_level)
        }
        else
        {
            log.info("TopologicalIsland elements not found, cannot use GridLAB-D to fix radial network errors")
            None.asInstanceOf[RDD[ch.ninecode.sc.ScResult]]
        }
    }

    @SuppressWarnings(Array("org.wartremover.warts.TraversableOps"))
    def run (): RDD[ScResult] =
    {
        assert(null != getOrElse[TopologicalNode], "no topology")

        val transformers: RDD[TransformerIsland] = get_starting_transformers
        val traced_results: RDD[ScResult] = calculateTraceResults(transformers).setName("traced_results")
        val cleaned_results: RDD[ScResult] = clean_results(traced_results)

        val cleaned_trace_results: RDD[(String, ScResult)] = traced_results.keyBy(_.node)
            .subtractByKey(cleaned_results.keyBy(_.node))

        val gridlab_results: RDD[ScResult] = run_gridlab(transformers, cleaned_trace_results)
        val reduced_trace_results: RDD[ScResult] = cleaned_trace_results.subtractByKey(gridlab_results.keyBy(_.node)).values

        spark.sparkContext.union(reduced_trace_results, cleaned_results, gridlab_results)
    }

    private def run_gridlab (transformers: RDD[TransformerIsland], cleaned_trace_results: RDD[(String, ScResult)]) =
    {
        // find transformers where there are non-radial networks and fix them
        def need_load_flow (error: String): Boolean =
            error.startsWith("FATAL: non-radial network detected") ||
                error.startsWith("INVALID: 3 transformer windings") ||
                error.startsWith("INVALID: low voltage")

        val problem_trafos: Array[String] = cleaned_trace_results.values
            .filter(result => result.errors.exists(need_load_flow))
            .map(_.tx)
            .distinct
            .collect()

        val gridlab_islands: RDD[TransformerIsland] = transformers.filter(trafoisland =>
        {
            val meshedNetwork = trafoisland.transformers.length > 1
            val errors = problem_trafos.contains(trafoisland.transformers.head.transformer_name)
            meshedNetwork || errors
        })

        val gridlab_results: RDD[ScResult] = fix(gridlab_islands, cleaned_trace_results).setName("fixed_results")
        gridlab_results
    }

    private def clean_results (traced_results: RDD[ScResult]): RDD[ScResult] =
    {
        def other_error (s: String): Boolean =
            !(s.startsWith("FATAL: non-radial network detected") ||
                s.startsWith("INVALID: 3 transformer windings") || // ToDo: Remove when 3W transformer test for SC is available
                s.startsWith("INVALID: low voltage")) &&
                s.startsWith("INVALID")

        val verboten_trafos: RDD[(String, String)] = traced_results
            .filter(result => result.errors.exists(other_error))
            .flatMap(result => result.errors.filter(other_error).map(err => (result.tx, err)))
            .distinct
            .persist(storage_level)
            .setName("verboten_trafos")

        // ensure that each element of a transformer service area has an error and 0.0 for all current/fuse values
        zero(verboten_trafos, traced_results).setName("cleaned_results")
    }

    private def get_starting_transformers: RDD[TransformerIsland] =
    {
        val transformer_data: RDD[TransformerData] = Transformers(
            spark,
            storage_level,
            options.default_short_circuit_power_max,
            options.default_short_circuit_impedance_max,
            options.default_short_circuit_angle_max,
            options.default_short_circuit_power_min,
            options.default_short_circuit_impedance_min,
            options.default_short_circuit_angle_min)
            .getTransformers()
            .setName("transformer_data")


        val transformers: RDD[TransformerIsland] =
            if (null != options.trafos && "" != options.trafos && "all" != options.trafos)
            {
                val source = Source.fromFile(options.trafos, "UTF-8")
                val trafos = source.getLines().filter(_ != "").toArray
                source.close()
                transformer_data
                    .filter(transformer => trafos.contains(transformer.transformer.id))
                    .groupBy(_.node1.TopologicalIsland)
                    .values
                    .map(TransformerIsland.apply)
            }
            else
            // do all low voltage power transformers
                transformer_data
                    .filter(td => (td.v0 > 1000.0) && (td.v1 <= 1000.0)) // ToDo: don't hard code this rule
                    .groupBy(_.node1.TopologicalIsland)
                    .values
                    .map(TransformerIsland.apply)
        setName(transformers, "transformers")
        log.info("%s starting transformers".format(transformers.count))

        transformers
    }
}

object ShortCircuit extends CIMInitializer[ShortCircuitOptions] with Main
{
    /**
     * The list of classes that can be persisted in RDD.
     */
    lazy val classes: Array[Class[_]] =
    {
        Array(
            classOf[ch.ninecode.sc.Impedanzen],
            classOf[ch.ninecode.sc.ScEdge],
            classOf[ch.ninecode.sc.ScError],
            classOf[ch.ninecode.sc.ScIntermediate],
            classOf[ch.ninecode.sc.ScMessage],
            classOf[ch.ninecode.sc.ScNode],
            classOf[ch.ninecode.sc.ScResult],
            classOf[ch.ninecode.sc.ShortCircuit],
            classOf[ch.ninecode.sc.ShortCircuitOptions],
            classOf[ch.ninecode.sc.StartingTrafos]
        )
    }

    def execute (options: ShortCircuitOptions): Unit =
    {
        if (options.verbose)
            LogManager.getLogger(getClass).setLevel(Level.INFO)
        if (options.main_options.valid)
        {
            if (options.cim_options.files.nonEmpty)
            {
                val session: SparkSession = createSession(options)
                readCIM(session, options)
                time("execution: %s seconds")
                {
                    val sc = ShortCircuit(session, options.cim_options.storage, options)
                    val results = sc.run()
                    options.output match
                    {
                        case ShortCircuitOutputType.SQLite =>
                            val db = Database(options)
                            val _ = db.store(results)
                        case ShortCircuitOutputType.Cassandra =>
                            val opts = if ("" == options.id)
                                options.copy(id = java.util.UUID.randomUUID.toString)
                            else
                                options
                            val cassandra = ScCassandra(session, opts)
                            val _ = cassandra.store(results)
                    }
                }
            }
            else
                log.error("no CIM files specified")
        }
    }

    def main (args: Array[String])
    {
        val have = util.Properties.versionNumberString
        val need = scala_library_version
        if (have != need)
        {
            log.error(s"Scala version ($have) does not match the version ($need) used to build $application_name")
            sys.exit(1)
        }
        else
        {
            // get the necessary jar files to send to the cluster
            val jars = Set(
                jarForObject(new DefaultSource()),
                jarForObject(ShortCircuitOptions())
            ).toArray

            // compose the classes to be registered with Kryo
            val kryo = Array.concat(
                // register CIMReader classes
                CIMClasses.list,
                // register Net classes
                Net.classes,
                // register GridLAB-D classes
                GridLABD.classes,
                // register ShortCircuit classes
                ShortCircuit.classes,
                // register Util classes
                Util.classes)

            // initialize the default options
            val temp = ShortCircuitOptions()
            val default = ShortCircuitOptions(
                main_options = MainOptions(application_name, application_version),
                spark_options = SparkOptions(jars = jars, kryo = kryo),
                cim_options = temp.cim_options
            )

            // parse the command line arguments
            new ShortCircuitOptionsParser(default).parse(args, default) match
            {
                case Some(options) =>
                    // execute the main program if everything checks out
                    if (options.verbose)
                    {
                        org.apache.log4j.LogManager.getLogger("ch.ninecode.sc.ShortCircuit").setLevel(org.apache.log4j.Level.INFO)
                        org.apache.log4j.LogManager.getLogger("ch.ninecode.sc.Database").setLevel(org.apache.log4j.Level.INFO)
                    }
                    execute(options)
                    if (!options.main_options.unittest)
                        sys.exit(0)
                case None =>
                    sys.exit(1)
            }
        }
    }
}
