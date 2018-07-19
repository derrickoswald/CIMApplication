package ch.ninecode.sc

import scala.collection.Map
import scala.io.Source
import org.apache.spark.graphx.Edge
import org.apache.spark.graphx.EdgeDirection
import org.apache.spark.graphx.EdgeTriplet
import org.apache.spark.graphx.Graph
import org.apache.spark.graphx.VertexId
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.cim.CIMNetworkTopologyProcessor
import ch.ninecode.cim.CIMRDD
import ch.ninecode.gl.Complex
import ch.ninecode.gl.TData
import ch.ninecode.gl.TransformerSet
import ch.ninecode.gl.Transformers
import ch.ninecode.model._
import ch.ninecode.sc.ScEdge.resistanceAt

/**
 * Short circuit calculation.
 * Uses GraphX to trace the topology and generate the short ciruit ressults at each node.
 *
 * @param session the Spark session
 * @param storage_level specifies the <a href="https://spark.apache.org/docs/latest/programming-guide.html#which-storage-level-to-choose">Storage Level</a> used to persist and serialize the objects
 * @param options options for short-circuit processing
 */
case class ShortCircuit (session: SparkSession, storage_level: StorageLevel, options: ShortCircuitOptions)
    extends
        CIMRDD
    with
        Serializable
{
    implicit val spark: SparkSession = session
    implicit val log: Logger = LoggerFactory.getLogger (getClass)

    val default_impendanz = Impedanzen (
        Complex (Double.PositiveInfinity, Double.PositiveInfinity),
        Complex (Double.PositiveInfinity, Double.PositiveInfinity),
        Complex (Double.PositiveInfinity, Double.PositiveInfinity),
        Complex (Double.PositiveInfinity, Double.PositiveInfinity))
    val default_node = ScNode ("", 0.0, null, null, null, null, null)

    def make_graph_vertices (v: ScNode): (VertexId, ScNode) =
    {
        (v.vertex_id (v.id_seq), v)
    }

    def make_graph_edges (e: ScEdge): Edge[ScEdge] =
    {
        Edge (e.vertex_id (e.id_cn_1), e.vertex_id (e.id_cn_2), e)
    }

    def topological_node_operator (arg: ((TopologicalNode, Terminal), ScEdge)): ScNode =
    {
        val node = arg._1._1
        val term = arg._1._2
        val edge = arg._2
        val voltage = if (term.ACDCTerminal.sequenceNumber == 1) edge.v1 else edge.v2
        ScNode (node.id, voltage, null, null, null, null, null)
    }

    def edge_operator (voltages: Map[String, Double]) (arg: (Element, Iterable[(Terminal, Option[End])])): List[ScEdge] =
    {
        var ret = List[ScEdge] ()

        val element = arg._1
        val t_it = arg._2

        // get the ConductingEquipment
        var c = element
        while ((null != c) && !c.getClass.getName.endsWith (".ConductingEquipment"))
            c = c.sup

        if (null != c)
        {
            // get the equipment
            val equipment = c.asInstanceOf[ConductingEquipment]

            // sort terminals by sequence number
            // except if it has ends, and then sort so the primary is index 0
            def sortnumber (arg: (Terminal, Option[End])) = arg._2 match { case Some (end) ⇒ end.endNumber case None ⇒ arg._1.ACDCTerminal.sequenceNumber }
            // the equipment voltage - doesn't work for transformers
            val volt = 1000.0 * voltages.getOrElse (equipment.BaseVoltage, 0.0)
            val terminals = t_it.toArray.sortWith (sortnumber (_) < sortnumber (_)).map (terminal_end ⇒
                {
                    val voltage =
                        terminal_end._2 match
                        {
                            case Some (end) ⇒
                                1000.0 * voltages.getOrElse (end.BaseVoltage, 0.0)
                            case None ⇒
                                volt
                        }
                    val impedance = element match
                    {
                        case line: ACLineSegment ⇒
                            val dist_km = line.Conductor.len / 1000.0
                            Impedanzen (
                                Complex (resistanceAt (options.low_temperature, options.base_temperature, line.r) * dist_km, line.x * dist_km),
                                Complex (resistanceAt (options.low_temperature, options.base_temperature, line.r0) * dist_km, line.x0 * dist_km),
                                Complex (resistanceAt (options.high_temperature, options.base_temperature, line.r) * dist_km, line.x * dist_km),
                                Complex (resistanceAt (options.high_temperature, options.base_temperature, line.r0) * dist_km, line.x0 * dist_km))
                        case transformer: PowerTransformer ⇒
                            terminal_end._2 match
                            {
                                case Some (end) ⇒
                                    val z = Complex (end.r, end.x)
                                    Impedanzen (z, z, z, z)
                                case None ⇒
                                    Impedanzen (0.0, 0.0, 0.0, 0.0) // no end on a transformer terminal? WTF?
                            }

                        case _ ⇒
                            Impedanzen (0.0, 0.0, 0.0, 0.0)
                    }
                    (terminal_end._1, voltage, impedance)
                })
            // Note: we eliminate 230V edges because transformer information doesn't exist and
            // see also NE-51 NIS.CIM: Export / Missing 230V connectivity
            if (!terminals.map (_._2).contains (230.0))
            {
                // make a short-circuit edge for each pair of terminals, zero and one length lists of terminals have been filtered out
                val eq = terminals(0)._1.ConductingEquipment
                val id1 = terminals(0)._1.ACDCTerminal.id
                val node1 = terminals(0)._1.TopologicalNode
                val voltage1 = terminals(0)._2
                val z = terminals(0)._3
                for (i ← 1 until terminals.length) // for comprehension: iterate omitting the upper bound
                {
                    val node2 = terminals(i)._1.TopologicalNode
                    // eliminate edges with only one connectivity node, or the same connectivity node
                    if (null != node1 && null != node2 && "" != node1 && "" != node2 && node1 != node2)
                        ret = ret :+ ScEdge (
                            id1,
                            node1,
                            voltage1,
                            terminals(i)._1.ACDCTerminal.id,
                            node2,
                            terminals(i)._2,
                            eq,
                            element,
                            z
                        )
                }
            }
        }
        //else // shouldn't happen, terminals always reference ConductingEquipment, right?

        ret
    }

    def trafo_mapping (tdata: TransformerSet): StartingTrafos =
    {
        val pn = default_node
        val v0 = pn.vertex_id (tdata.node0)
        val v1 = pn.vertex_id (tdata.node1)
        StartingTrafos (v0, v1, tdata)
    }

    case class End (PowerTransformer: String, endNumber: Int, BaseVoltage: String, r: Double, x: Double)
    def make_ends_meet (arg: (Terminal, Option[PowerTransformerEnd])): (Terminal, Option[End]) =
    {
        val terminal = arg._1
        val end = arg._2 match
        {
            case Some (pte) ⇒
                Some (End (pte.PowerTransformer, pte.TransformerEnd.endNumber, pte.TransformerEnd.BaseVoltage, pte.r, pte.x))
            case None ⇒
                None
        }
        (terminal, end)
    }

    def get_inital_graph (): Graph[ScNode, ScEdge] =
    {
        // get a map of voltages
        val voltages = get[BaseVoltage].map (v ⇒ (v.id, v.nominalVoltage)).collectAsMap ()

        // get the terminals in the topology
        val terminals = get[Terminal].filter (null != _.TopologicalNode)

        // handle transformers specially, by attaching PowerTransformerEnd objects to the terminals

        // get the transformer ends keyed by terminal, only one end can reference any one terminal
        val ends = get[PowerTransformerEnd].keyBy (_.TransformerEnd.Terminal)
        // attach the ends to terminals
        val t = terminals.keyBy (_.id).leftOuterJoin (ends).values.map (make_ends_meet)
        // get the terminals keyed by equipment and filter for two (or more) terminals
        val terms = t.groupBy (_._1.ConductingEquipment).filter (_._2.size > 1)

        // map the terminal 'pairs' to edges
        val edges = get[Element]("Elements").keyBy (_.id).join (terms).values.flatMap (edge_operator (voltages))

        // get terminal to voltage mapping by referencing the equipment voltage for each of two terminals
        val tv = edges.keyBy (_.id_seq_1).union (edges.keyBy (_.id_seq_2)).distinct

        // map the topological nodes to short circuit nodes with voltages
        val nodes = get[TopologicalNode].keyBy (_.id).join (terminals.keyBy (_.TopologicalNode)).values.keyBy (_._2.id).join (tv).values.map (topological_node_operator).distinct

        // persist edges and nodes to avoid recompute
        val xedges = edges.map (make_graph_edges)
        val xnodes = nodes.map (make_graph_vertices)
        xedges.name = "xedges"
        xedges.persist (storage_level)
        xnodes.name = "xnodes"
        xnodes.persist (storage_level)
        spark.sparkContext.getCheckpointDir match
        {
            case Some (_) ⇒
                xedges.checkpoint ()
                xnodes.checkpoint ()
            case None ⇒
        }

        Graph.apply[ScNode, ScEdge] (xnodes, xedges, default_node, storage_level, storage_level)
    }

    def trace (initial: Graph[ScNode, ScEdge]): Graph[ScNode, ScEdge] =
    {
        // do the Pregel algorithm
        def vprog (id: VertexId, v: ScNode, message: ScMessage): ScNode =
        {
            if (null == message.source) // handle the initial message by keeping the same vertex node
                v
            else
            {
                val errors = message.combine_errors (v.errors, message.errors, options.messagemax)
                val z = if ((null != message.ref) && (null != message.edge)) message.ref + message.edge else v.impedance
                val fuses = if (null != message.fuses) message.fuses else v.fuses
                v.copy (source = message.source, id_prev = message.previous_node, impedance = z, fuses = fuses, errors = errors)
            }
        }

        def handleMesh (triplet: EdgeTriplet[ScNode, ScEdge]): Iterator[(VertexId, ScMessage)] =
        {
            // don't propagate indefinitely
            if (triplet.srcAttr.fatalErrors || triplet.dstAttr.fatalErrors)
                Iterator.empty
            else
                if ((triplet.srcAttr.id_prev == triplet.dstAttr.id_seq) || (triplet.dstAttr.id_prev == triplet.srcAttr.id_seq)) // reinforcement
                    Iterator.empty
                else
                {
                    // check if the non-null impedance difference matches what we expect for this cable
                    triplet.attr.element match
                    {
                        case _: ACLineSegment ⇒
                            val diff = triplet.srcAttr.impedance - triplet.dstAttr.impedance
                            val expected = triplet.attr.impedanceTo ("not important")
                            val isequal = Math.abs (!diff.impedanz_low - !expected.impedanz_low) < 1e-6 && Math.abs (!diff.null_impedanz_low - !expected.null_impedanz_low) < 1e-6
                            if (isequal)
                                Iterator.empty
                            else
                            {
                                val error = ScError (true, "non-radial network detected through %s".format (triplet.attr.id_equ))
                                log.error (error.message)
                                // neither node has a fatal error yet, send a message to both to mark them with a fatal error
                                Iterator (
                                    (triplet.dstId, ScMessage (triplet.dstAttr.source, null, null, null, triplet.srcAttr.id_seq, List (error))),
                                    (triplet.srcId, ScMessage (triplet.srcAttr.source, null, null, null, triplet.dstAttr.id_seq, List (error)))
                                )
                            }
                        case _ ⇒
                            Iterator.empty
                    }
                }
        }

        def sendMessage (triplet: EdgeTriplet[ScNode, ScEdge]): Iterator[(VertexId, ScMessage)] =
        {
            val x =
            if (triplet.srcAttr.impedance != null)
                if (triplet.dstAttr.impedance == null)
                    if (triplet.attr.shouldContinueTo (triplet.dstAttr.id_seq))
                    {
                        val from = triplet.attr.impedanceFrom (triplet.dstAttr.id_seq, triplet.srcAttr.impedance)
                        val to = triplet.attr.impedanceTo (triplet.dstAttr.id_seq)
                        val fuses = triplet.attr.fusesTo (triplet.srcAttr.fuses)
                        Iterator ((triplet.dstId, ScMessage (triplet.srcAttr.source, from, to, fuses, triplet.srcAttr.id_seq, triplet.srcAttr.errors)))
                    }
                    else
                        Iterator.empty
                else
                    handleMesh (triplet)
            else if (triplet.dstAttr.impedance != null)
                if (triplet.srcAttr.impedance == null)
                    if (triplet.attr.shouldContinueTo (triplet.srcAttr.id_seq))
                    {
                        val from = triplet.attr.impedanceFrom (triplet.srcAttr.id_seq, triplet.dstAttr.impedance)
                        val to = triplet.attr.impedanceTo (triplet.srcAttr.id_seq)
                        val fuses = triplet.attr.fusesTo (triplet.dstAttr.fuses)
                        Iterator ((triplet.srcId, ScMessage (triplet.dstAttr.source, from, to, fuses, triplet.dstAttr.id_seq, triplet.dstAttr.errors)))
                    }
                    else
                        Iterator.empty
                else
                    handleMesh (triplet)
            else
                Iterator.empty
            x
        }

        def mergeMessage (a: ScMessage, b: ScMessage): ScMessage =
        {
            if (a.previous_node != b.previous_node)
            {
                val error = ScError (true, "non-radial network detected from %s to %s".format (a.previous_node, b.previous_node))
                log.error (error.message)
                a.copy (errors = a.combine_errors (a.errors, b.combine_errors (b.errors, List (error), options.messagemax), options.messagemax))
            }
            else
            {
                val parallel = a.edge.parallel (b.edge)
                val warning = ScError (false, "reinforcement detected from %s".format (a.previous_node))
                a.copy (edge = parallel, errors = a.combine_errors (a.errors, b.combine_errors (b.errors, List (warning), options.messagemax), options.messagemax))
            }
        }

        initial.pregel (ScMessage (null, null, null, null, null, null), 10000, EdgeDirection.Either) (vprog, sendMessage, mergeMessage)
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
        else if (0.0 == impedanz.im)
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
        val low = calculate_one (v2, node.impedance.impedanz_low, node.impedance.null_impedanz_low)
        val high = calculate_one (v2, node.impedance.impedanz_high, node.impedance.null_impedanz_high)

        ScResult (node.id_seq, equipment, terminal, container,
            if (null == node.errors) List () else node.errors.map (_.toString),
            node.source, node.id_prev,
            node.impedance.impedanz_low.re, node.impedance.impedanz_low.im, node.impedance.null_impedanz_low.re, node.impedance.null_impedanz_low.im,
            low.ik,  low.ik3pol,  low.ip,  low.sk,  low.imax_3ph_low,  low.imax_1ph_low,  low.imax_2ph_low,  low.imax_3ph_med,  low.imax_1ph_med,  low.imax_2ph_med,
            node.impedance.impedanz_high.re, node.impedance.impedanz_high.im, node.impedance.null_impedanz_high.re, node.impedance.null_impedanz_high.im,
            high.ik, high.ik3pol, high.ip, high.sk,
            node.fuses, FData.fuse (high.ik), FData.fuseOK (high.ik, node.fuses))
    }

    def pickone (singles: Array[String]) (nodes: Iterable[(String, SimulationNode)]): (String, SimulationNode) =
    {
        nodes.find (node ⇒ singles.contains (node._2.equipment)) match
        {
            case Some (node) ⇒ node
            case _ ⇒ nodes.head // just take the first
        }
    }

    def queryNetwork (trafos_islands: RDD[(String, String)]): RDD[(String, (Iterable[SimulationNode], Iterable[Iterable[SimulationEdge]]))] =
    {
        log.info ("""resolving nodes and edges by transformer service area""")

        // the mapping between island and transformer service area
        val islands_trafos = trafos_islands.map (x ⇒ (x._2, x._1)) // (islandid, transformersetid)
        // get nodes by TopologicalIsland
        val members = get[TopologicalNode].map (node ⇒ (node.id, node.TopologicalIsland)) // (nodeid, islandid)
        // get terminals by TopologicalIsland
        val terminals = get[Terminal].keyBy (_.TopologicalNode).join (members).map (x ⇒ (x._2._2, x._2._1)) // (islandid, terminal)
        // get equipment in the TopologicalIsland and associated Terminal
        val equipment_terminals = get[ConductingEquipment].keyBy (_.id).join (terminals.keyBy (_._2.ConductingEquipment)).values.map (x ⇒ (x._2._1, x._1, x._2._2)) // (island, equipment, terminal)
        // make a list of all single terminal equipment as the preferred association to the node
        val singles = equipment_terminals.groupBy (_._3.ConductingEquipment).filter (1 == _._2.size).map (_._2.head._2.id).collect
        // get all nodes with their voltage - it is assumed that some equipment on the transformer secondary (secondaries) has a voltage
        // but this doesn't include the transformer primary node - it's not part of the topology
        // ToDo: fix this 1kV multiplier on the voltages
        val nodes = islands_trafos.join (equipment_terminals.keyBy (_._2.BaseVoltage).join (get[BaseVoltage].keyBy (_.id)).values.map (
            node ⇒ (node._1._1, SimulationNode (node._1._3.TopologicalNode, node._1._2.id, node._2.nominalVoltage * 1000.0, node._1._2.Equipment.PowerSystemResource.PSRType))
        ).groupBy (_._2.id_seq).values.map (pickone (singles))).values // (transformersetid, node)
        // get equipment in the transformer service area
        val equipment = equipment_terminals.map (x ⇒ (x._1, (x._2, x._3))).join (islands_trafos)
            .map (x ⇒ (x._2._2, (x._1, x._2._1._1, x._2._1._2))) // (transformersetid, (islandid, equipment, terminal))

        // get all equipment with two nodes in the transformer service area that separate different TopologicalIsland (these are the edges)
        val two_terminal_equipment = equipment.groupBy (x ⇒ x._2._2.id).filter (
            edge ⇒ edge._2.size > 1 && (edge._2.head._1 == edge._2.tail.head._1) && (edge._2.head._2._3.TopologicalNode != edge._2.tail.head._2._3.TopologicalNode)
        ) // (equipmentid, [(txarea, (islandid, equipment, terminal))])
        // convert ConductingEquipment to Element with Terminal(s)
        val elements = get[Element]("Elements").keyBy (_.id).join (two_terminal_equipment)
            .values.map (x ⇒ (x._2.head._1, (x._1, x._2.map (x ⇒ x._2._3)))) // (txarea, (element, [terminals]))
        // combine parallel equipment
        val eq3 = elements.keyBy (_._2._2.map (_.TopologicalNode).toArray.sortWith (_ < _).mkString ("_")).groupByKey.values
        // the transformer service area is the same, so pull it out of the Iterator
        val eq4 = eq3.map (x ⇒ (x.head._1, x.map (y ⇒ y._2)))
        // create the edges keyed by transformersetid
        val edges = eq4.map (v ⇒
            (v._1,
                v._2.map (
                    edge ⇒ SimulationEdge (edge._1.id, edge._2.head.TopologicalNode, edge._2.tail.head.TopologicalNode, edge._1)
                )
            )
        )
        nodes.groupByKey.join (edges.groupByKey).cache
    }

    def run (): RDD[ScResult] =
    {
        // check if topology exists, and if not then generate it
        if (null == get[TopologicalNode])
        {
            val ntp = new CIMNetworkTopologyProcessor (session, storage_level, true, true, false) // force retain switches and fuses
            val elements = ntp.process (true)
            log.info ("%d elements after topology generated".format (elements.count ()))
        }

        val _transformers = new Transformers (spark, storage_level)
        val tdata = _transformers.getTransformerData (true, options.default_short_circuit_power, options.default_short_circuit_impedance)

        val transformers: Array[Array[TData]] = if (null != options.trafos && "" != options.trafos && "all" != options.trafos) {
            val trafos = Source.fromFile (options.trafos, "UTF-8").getLines ().filter (_ != "").toArray
            val selected = tdata.filter (t ⇒ { trafos.contains (t.transformer.id) })
            selected.groupBy (t ⇒ t.terminal1.TopologicalNode).values.map (_.toArray).collect
        }
        else {
            // do all low voltage power transformers
            // ToDo: fix this 1kV multiplier on the voltages
            val niederspannug = tdata.filter (td ⇒ td.voltage0 != 0.4 && td.voltage1 == 0.4)
            niederspannug.groupBy (_.terminal1.TopologicalNode).values.map (_.toArray).collect
        }

        val starting_nodes = transformers.map (txs ⇒ trafo_mapping (TransformerSet (txs, options.default_transformer_power_rating, options.default_transformer_impedance)))

        // create the initial Graph with ScNode vertices
        def starting_map (starting_nodes: Array[StartingTrafos]) (id: VertexId, v: ScNode): ScNode =
        {
            starting_nodes.find (trafo ⇒ trafo.nsPin == id || trafo.osPin == id) match
            {
                case Some (node) ⇒
                    // assign source and impedances to starting transformer primary and secondary
                    if (node.osPin == id)
                        ScNode (v.id_seq, v.voltage, node.transformer.transformer_name, "network", node.primary_impedance, null, null)
                    else
                    {
                        val errors = if (node.transformer.total_impedance._2) List (ScError (false, "transformer has no impedance value, using default %s".format (options.default_transformer_impedance))) else null
                        ScNode (v.id_seq, v.voltage, node.transformer.transformer_name, "self", node.secondary_impedance, null, errors)
                    }
                case None ⇒
                    v
            }
        }

        val initial = get_inital_graph ()
        val initial_with_starting_nodes = initial.mapVertices (starting_map (starting_nodes)).persist (storage_level)
        val graph = trace (initial_with_starting_nodes)

        // get the visited nodes with their data
        val result = graph.vertices.filter (null != _._2.impedance).values
        result.setName ("scresult")
        result.persist (storage_level)

        // join results with terminals to get equipment
        val d = result.keyBy (_.id_seq).join (get[Terminal].keyBy (_.TopologicalNode)).values
        // join with equipment to get containers
        val e = d.keyBy (_._2.ConductingEquipment).join (get[ConductingEquipment].keyBy (_.id)).map (x ⇒ (x._2._1._1, x._2._1._2, x._2._2))
        val f = e.keyBy (_._3.Equipment.EquipmentContainer).leftOuterJoin (get[Element]("Elements").keyBy (_.id)).map (x ⇒ (x._2._1._1, x._2._1._2, x._2._1._3, x._2._2))
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
                case Some (bay: Bay)            => (node, terminal.ACDCTerminal.sequenceNumber, equipment.id, bay.Substation)
                case Some (level: VoltageLevel) => (node, terminal.ACDCTerminal.sequenceNumber, equipment.id, level.Substation)
                case _ => (node, terminal.ACDCTerminal.sequenceNumber, equipment.id, null)
            }
        }
        val g = f.map (station_fn)

        // compute results
        val results = g.map (calculate_short_circuit)

        // find transformers where there are non-radial networks
        val problem_trafos = results.filter (result ⇒ result.errors.exists (s ⇒ s.startsWith ("FATAL: non-radial network detected"))).map (result ⇒ result.tx).distinct
        if (0 != problem_trafos.count)
        {
            // execute GridLAB-D to get the actual impedances
            val tsa = TransformerServiceArea (session)
            val trafos_islands = tsa.getTransformerServiceAreas.map (x ⇒ (x._2, x._1)) // (trafosetid, islandid)
            val problem_trafos_islands = problem_trafos.keyBy (x ⇒ x).join (trafos_islands).values // (trafosetid, islandid)
            val simulations = queryNetwork (problem_trafos_islands)
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
            classOf[ch.ninecode.gl.PreNode],
            classOf[ch.ninecode.gl.PreEdge],
            classOf[ch.ninecode.gl.PV],
            classOf[ch.ninecode.gl.ThreePhaseComplexDataElement],
            classOf[ch.ninecode.sc.Impedanzen],
            classOf[ch.ninecode.sc.ScEdge],
            classOf[ch.ninecode.sc.ScError],
            classOf[ch.ninecode.sc.ScIntermediate],
            classOf[ch.ninecode.sc.ScMessage],
            classOf[ch.ninecode.sc.ScNode],
            classOf[ch.ninecode.sc.ScResult],
            classOf[ch.ninecode.sc.ShortCircuit],
            classOf[ch.ninecode.sc.ShortCircuitOptions],
            classOf[ch.ninecode.sc.StartingTrafos])
    }
}
