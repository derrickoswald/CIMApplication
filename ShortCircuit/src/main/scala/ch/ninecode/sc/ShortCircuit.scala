package ch.ninecode.sc

import scala.collection.Map

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
import ch.ninecode.net.Net
import ch.ninecode.net.TransformerData
import ch.ninecode.net.TransformerIsland
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

    type Container = String
    type CondEquipmentID = String
    type TerminalID = Int

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

    def setName (rdd: RDD[_], name: String): Unit =
    {
        val _ = rdd.setName(name)
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

    def calculateTraceResults (transformers: RDD[TransformerIsland]): RDD[ScResult] =
    {
        // create the initial Graph with ScNode vertices
        val initial = get_inital_graph()
        val initial_with_starting_nodes: Graph[ScNode, ScEdge] = add_starting_nodes(initial, transformers)

        val sct = ShortCircuitTrace(session, options)
        val graph = sct.trace(initial_with_starting_nodes)
        setName(graph.edges, "graph edges")
        setName(graph.vertices, "graph vertices")

        // get the visited nodes with their data
        val result: RDD[ScNode] = graph.vertices.filter(null != _._2.impedance).values
        persist(result, "scresult")

        val g: RDD[(ScNode, Int, String, String)] = pass_trace_result_to_all_nodes(result)

        log.info("computing results")
        g.map(calculate_short_circuit).persist(storage_level)
    }

    @SuppressWarnings(Array("org.wartremover.warts.Null"))
    private def add_starting_nodes (initial: Graph[ScNode, ScEdge], transformers: RDD[TransformerIsland]): Graph[ScNode, ScEdge] =
    {
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

            ScNode(
                id_seq = node.id_seq,
                voltage = node.voltage,
                source_id = trafo.transformer.transformer_name,
                id_prev = "self",
                impedance = trafo.lv_impedance(node.voltage),
                errors = problems
            )
        }

        val starting_nodes: RDD[StartingTrafos] = transformers.flatMap(trafo_mapping).setName("starting_nodes")
        val starting_trafos_with_edges = starting_nodes.keyBy(_.nsPin).join(initial.edges.flatMap(both_ends).groupByKey)
            .setName("starting_trafos_with_edges")

        val initial_with_starting_nodes: Graph[ScNode, ScEdge] = initial.joinVertices(starting_trafos_with_edges)(add_starting_trafo).persist(storage_level)
        setName(initial_with_starting_nodes.edges, "initial_with_starting_nodes edges")
        setName(initial_with_starting_nodes.vertices, "initial_with_starting_nodes vertices")

        initial_with_starting_nodes
    }

    private def pass_trace_result_to_all_nodes (result: RDD[ScNode]): RDD[(ScNode, TerminalID, CondEquipmentID, Container)] =
    {
        val result_keyed = result.keyBy(_.id_seq)
        val terminals_keyed: RDD[(String, Terminal)] = getOrElse[Terminal].distinct.keyBy(_.TopologicalNode)
        val result_per_terminals: RDD[(ScNode, Terminal)] = result_keyed.join(terminals_keyed).values.setName("result_per_terminals")
        val result_per_terminals_keyed_by_cond_equipment = result_per_terminals.keyBy(_._2.ConductingEquipment)

        val container_per_cond_equipment: RDD[(CondEquipmentID, Container)] = get_container_per_equipment

        val results_with_container: RDD[(ScNode, TerminalID, CondEquipmentID, Container)] =
                result_per_terminals_keyed_by_cond_equipment
                .join(container_per_cond_equipment)
                .map(
                    (x: (CondEquipmentID, ((ScNode, Terminal), Container))) =>
                    {
                        val scNode: ScNode = x._2._1._1
                        val container: Container = x._2._2
                        val cond_equip_id: CondEquipmentID = x._1
                        val terminal = x._2._1._2
                        val terminal_id: TerminalID = terminal.ACDCTerminal.sequenceNumber
                        (scNode, terminal_id, cond_equip_id, container)
                    })

        results_with_container.distinct()
    }

    private def get_container_per_equipment: RDD[(CondEquipmentID, Container)] =
    {
        // resolve to top level containers
        // the equipment container for a transformer could be a Station or a Bay or VoltageLevel ... the last two of which have a reference to their station
        val conducting_equipment = getOrElse[ConductingEquipment].keyBy(_.Equipment.EquipmentContainer)
        val elements = getOrElse[Element].keyBy(_.id)
        val cond_equip_with_elements = conducting_equipment.leftOuterJoin(elements)

        val container_per_cond_equipment: RDD[(CondEquipmentID, Container)] = cond_equip_with_elements.values.map(x =>
        {
            val (conducting_equipment, element_option) = x
            val equipment_id = conducting_equipment.id
            element_option match
            {
                case Some(station: Substation) => (equipment_id, station.id)
                case Some(bay: Bay) => (equipment_id, bay.Substation)
                case Some(level: VoltageLevel) => (equipment_id, level.Substation)
                case _ => (equipment_id, "")
            }
        })

        container_per_cond_equipment
    }

    @SuppressWarnings(Array("org.wartremover.warts.TraversableOps"))
    def run (): RDD[ScResult] =
    {
        assert(null != getOrElse[TopologicalNode], "no topology")
        val transformers: RDD[TransformerIsland] = get_starting_transformers

        val traced_results: RDD[ScResult] = calculateTraceResults(transformers).setName("traced_results")
        val non_computable_results: RDD[ScResult] = clean_results(traced_results)

        val traced_results_keyed = traced_results.keyBy(_.node)
        val non_computable_results_keyed = non_computable_results.keyBy(_.node)
        val computable_results: RDD[(String, ScResult)] = traced_results_keyed.subtractByKey(non_computable_results_keyed)

        val gridlab_results: RDD[ScResult] = calculate_nonradial(transformers, computable_results.map(_._2))

        val gridlab_results_keyed = gridlab_results.keyBy(_.node)
        val reduced_trace_results: RDD[ScResult] = computable_results.subtractByKey(gridlab_results_keyed).values
        spark.sparkContext.union(reduced_trace_results, non_computable_results, gridlab_results)
    }

    def calculate_nonradial (transformers: RDD[TransformerIsland], cleaned_trace_results: RDD[ScResult]): RDD[ScResult] =
    {
        val scNonRadial = ScNonRadial(session, storage_level, options)
        val gridlab_results: RDD[(String, String, Impedanzen, Branch)] = scNonRadial.run_loadflow(transformers, cleaned_trace_results)

        // map to the type returned by the trace, use the existing value where possible
        val original_keyed = cleaned_trace_results.keyBy(_.node)
        val results_keyed = gridlab_results.keyBy(_._2)

        // transformer id, node mrid, attached equipment mrid, nominal node voltage, and impedance at the node
        results_keyed.join(original_keyed).values.map(x =>
        {
            val (transformer, node, ztrafo, branches) = x._1
            val original = x._2
            val z = if (null == branches) ztrafo else branches.z(ztrafo)

            calculate_short_circuit((
                ScNode(
                    node,
                    original.voltage,
                    transformer,
                    original.prev,
                    z,
                    branches,
                    List(ScError(fatal = false, invalid = false, "computed by load-flow")) // replace the errors
                ),
                original.terminal,
                original.equipment,
                original.container
            ))
        }).persist(storage_level)
    }

    private def clean_results (traced_results: RDD[ScResult]): RDD[ScResult] =
    {
        def other_error (s: String): Boolean = !ScNonRadial.need_load_flow(s) && s.startsWith("INVALID")

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
            if (0 != options.trafos.length)
                transformer_data
                    .filter(transformer => options.trafos.contains(transformer.transformer.id))
                    .groupBy(_.node1.TopologicalIsland)
                    .values
                    .map(TransformerIsland.apply)
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
                        org.apache.log4j.LogManager.getLogger("ch.ninecode.sc.ScBranches").setLevel(org.apache.log4j.Level.INFO)
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
