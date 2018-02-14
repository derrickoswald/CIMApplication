package ch.ninecode.sc

import ch.ninecode.cim.CIMNetworkTopologyProcessor

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
import ch.ninecode.cim.CIMRDD
import ch.ninecode.model._

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

    val default_impendanz = Impedanzen (Complex (Double.PositiveInfinity, Double.PositiveInfinity), Complex (Double.PositiveInfinity, Double.PositiveInfinity))
    val default_node = ScNode ("", 0.0, null, null, null, null)

    def has (string: String): String =
    {
        val index = string.lastIndexOf ("_")
        if (-1 != index)
            string.substring (0, index)
        else
            string
    }

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
        ScNode (node.id, voltage, null, null, null, null)
    }

    def edge_operator (voltages: Map[String, Double]) (arg: ((Element, Option[Iterable[PowerTransformerEnd]]), Iterable[Terminal])): List[ScEdge] =
    {
        var ret = List[ScEdge] ()

        val e = arg._1._1
        val pte_op = arg._1._2
        val t_it = arg._2

        // get the ConductingEquipment
        var c = e
        while ((null != c) && !c.getClass.getName.endsWith (".ConductingEquipment"))
            c = c.sup

        if (null != c)
        {
            // get the equipment
            val equipment = c.asInstanceOf[ConductingEquipment]
            // sort terminals by sequence number (and hence the primary is index 0)
            val terminals = t_it.toArray.sortWith (_.ACDCTerminal.sequenceNumber < _.ACDCTerminal.sequenceNumber)
            // make a list of voltages
            val volt = 1000.0 * voltages.getOrElse (equipment.BaseVoltage, 0.0)
            val volts =
                pte_op match
                {
                    case Some (x: Iterable[PowerTransformerEnd]) ⇒
                        // sort ends by end number
                        // ToDo: handle the case where terminal sequence and end sequence aren't the same
                        val tends = x.toArray.sortWith (_.TransformerEnd.endNumber < _.TransformerEnd.endNumber)
                        tends.map (e ⇒ 1000.0 * voltages.getOrElse (e.TransformerEnd.BaseVoltage, 0.0))
                    case None ⇒
                        Array[Double] (volt, volt)
                }
            val impedance = e match
            {
                case line: ACLineSegment ⇒
                    val dist_km = line.Conductor.len / 1000.0
                    Impedanzen (Complex (line.r * dist_km, line.x * dist_km), Complex (line.r0 * dist_km, line.x0 * dist_km))
                case transformer: PowerTransformer ⇒
                    pte_op match
                    {
                        case Some (x: Iterable[PowerTransformerEnd]) ⇒
                            val tends = x.toArray.sortWith (_.TransformerEnd.endNumber < _.TransformerEnd.endNumber)
                            val end = tends (0) // primary
                            val z = Complex (end.r, end.x)
                            Impedanzen (z, z)
                        case None ⇒
                            // ToDo what if no data
                            Impedanzen (0.0, 0.0)
                    }

                case _ ⇒
                    Impedanzen (0.0, 0.0)
            }

            // Note: we eliminate 230V edges because transformer information doesn't exist and
            // see also NE-51 NIS.CIM: Export / Missing 230V connectivity
            if (!volts.contains (230.0))
                // make a short-circuit edge for each pair of terminals
                ret = terminals.length match {
                    case 1 ⇒
                        ret :+
                            ScEdge (
                                terminals(0).ACDCTerminal.id,
                                terminals(0).TopologicalNode,
                                volts(0),
                                "",
                                "",
                                volts(0),
                                terminals(0).ConductingEquipment,
                                equipment,
                                e,
                                impedance)
                    case _ ⇒
                        for (i ← 1 until terminals.length) // for comprehension: iterate omitting the upper bound
                        {
                            ret = ret :+ ScEdge (
                                terminals(0).ACDCTerminal.id,
                                terminals(0).TopologicalNode,
                                volts(0),
                                terminals(i).ACDCTerminal.id,
                                terminals(i).TopologicalNode,
                                volts(i),
                                terminals(0).ConductingEquipment,
                                equipment,
                                e,
                                impedance)
                        }
                        ret
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

    def get_inital_graph (): Graph[ScNode, ScEdge] =
    {
        // get a map of voltages
        val voltages = get[BaseVoltage].map ((v) ⇒ (v.id, v.nominalVoltage)).collectAsMap ()

        // get the terminals
        val terminals = get[Terminal].filter (null != _.ConnectivityNode)

        // get the terminals keyed by equipment
        val terms = terminals.groupBy (_.ConductingEquipment)

        // get all elements
        val elements = get[Element]("Elements")

        // get the transformer ends keyed by transformer
        val ends = get[PowerTransformerEnd].groupBy (_.PowerTransformer)

        // handle transformers specially, by attaching all PowerTransformerEnd objects to the elements
        val elementsplus = elements.keyBy (_.id).leftOuterJoin (ends)

        // map the terminal 'pairs' to edges
        val edges = elementsplus.join (terms).flatMapValues (edge_operator (voltages)).values

        // eliminate edges with only one connectivity node, or the same connectivity node
        val real_edges = edges.filter (x ⇒ null != x.id_cn_1 && null != x.id_cn_2 && "" != x.id_cn_1 && "" != x.id_cn_2 && x.id_cn_1 != x.id_cn_2)

        // get terminal to voltage mapping by referencing the equipment voltage for each of two terminals
        val tv = edges.keyBy (_.id_seq_1).union (edges.keyBy (_.id_seq_2)).distinct

        // get the nodes RDD
        val tnodes = get[TopologicalNode]
        // map the topological nodes to prenodes with voltages
        val nodes = tnodes.keyBy (_.id).join (terminals.keyBy (_.TopologicalNode)).values.keyBy (_._2.id).join (tv).values.map (topological_node_operator).distinct

        // persist edges and nodes to avoid recompute
        val xedges = real_edges.map (make_graph_edges)
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
                val errors = if (null != message.error) if (null == v.errors) List (message.error) else v.errors :+ message.error else v.errors
                var z = if ((null != message.ref) && (null != message.edge)) Impedanzen (message.ref.impedanz + message.edge.impedanz, message.ref.null_impedanz + message.edge.null_impedanz) else v.impedance
                var fuses = if (null != message.fuses) message.fuses else v.fuses
                v.copy (source = message.source, impedance = z, fuses = fuses, errors = errors)
            }
        }

        def handleMesh (triplet: EdgeTriplet[ScNode, ScEdge]): Iterator[(VertexId, ScMessage)] =
        {
            // don't propagate indefinitely
            if (triplet.srcAttr.noFatalErrors && triplet.dstAttr.noFatalErrors)
                if (!triplet.srcAttr.reinforcement && !triplet.dstAttr.reinforcement)
                {
                    // check if the non-null impedance difference matches what we expect for this cable
                    triplet.attr.element match
                    {
                        case line: ACLineSegment ⇒
                            val diff = triplet.srcAttr.impedance - triplet.dstAttr.impedance
                            val expected = triplet.attr.impedanceTo ("not used")
                            val isequal = Math.abs (!diff.impedanz - !expected.impedanz) < 1e-6 && Math.abs (!diff.null_impedanz - !expected.null_impedanz) < 1e-6
                            if (isequal)
                                Iterator.empty
                            else
                            {
                                val error = ScError (true, "non-radial network detected through %s".format (triplet.attr.id_equ))
                                log.error (error.message)
                                Iterator (
                                    (triplet.dstId, ScMessage (triplet.dstAttr.source, null, null, null, triplet.srcAttr.id_seq, error)),
                                    (triplet.srcId, ScMessage (triplet.srcAttr.source, null, null, null, triplet.dstAttr.id_seq, error))
                                )
                            }
                        case _ ⇒
                            Iterator.empty
                    }
                }
                else
                    Iterator.empty
            else
                Iterator.empty
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
                        Iterator ((triplet.dstId, ScMessage (triplet.srcAttr.source, from, to, fuses, triplet.srcAttr.id_seq, null)))
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
                        Iterator ((triplet.srcId, ScMessage (triplet.dstAttr.source, from, to, fuses, triplet.dstAttr.id_seq, null)))
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
                a.copy (error = error)
            }
            else
            {
                val parallel = Impedanzen (a.edge.impedanz.parallel_impedanz (b.edge.impedanz), a.edge.null_impedanz.parallel_impedanz (b.edge.null_impedanz))
                val warning = ScError (false, "reinforcement detected from %s".format (a.previous_node))
                a.copy (edge = parallel, error = warning)
            }
        }

        initial.pregel (ScMessage (null, null, null, null, null, null), 10000, EdgeDirection.Either) (vprog, sendMessage, mergeMessage)
    }

    // compute the short-circuit values
    def calculate_short_circuit (node: ScNode): HouseConnection =
    {
        val v2 = node.voltage
        val root3 = Math.sqrt (3.0)

        // Einpoligen Kurzschlussstrom berechnen
        val ik_z = root3 * options.cmin * v2
        val ik_n_sqrt1 = !node.impedance.impedanz
        val ik_n_sqrt2 = !node.impedance.null_impedanz
        val ik_n = 2 * ik_n_sqrt1 + ik_n_sqrt2
        val ik = ik_z / ik_n

        // Dreipoligen Kurzschlussstrom berechnen
        val ik3pol_n = root3 * !node.impedance.impedanz
        val ik3pol = options.cmax * v2 / ik3pol_n

        // Stosskurzschlussstrom berechnen
        // was       val ip = (1.02 + 0.98 * Math.exp (-3.0 * (trafo_r1 + netz_r1) / (trafo_x1 + Math.abs (netz_x1)))) * Math.sqrt (2) * ik3pol

        // maximum aperiodic short-circuit current according to IEC 60909-0, see for example:
        // http://at.dii.unipd.it/renato.gobbo/didattica/corsi/Componenti_tecnologie_elettrici/ABB_swithgear_manual_E11/ABB_11_E_03_druck.pdf pp71-80
        // http://studiecd.dk/cahiers_techniques/Calculation_of_short_circuit_currents.pdf pp7-10
        val kappa =
            if ((0.0 == node.impedance.impedanz.im) && (0.0 == node.impedance.impedanz.re))
                1.02 + 0.98 * Math.exp (-3.0)
            else if (0.0 == node.impedance.impedanz.im)
                0.0
            else
                1.02 + 0.98 * Math.exp (-3.0 * node.impedance.impedanz.re / node.impedance.impedanz.im)
        val ip = kappa * Math.sqrt (2) * ik3pol

        // short-circuit power at the point of common coupling
        val sk = (v2 * v2) / !node.impedance.impedanz

        // maximum (motor) power (W) for repetition_rate<0.01/min and 0.01≤r<0.1 /min, override default settings for pf=1.0=cos(90), inrush=1x
        val m3phmax = MaximumStartingCurrent.max_power_3_phase_motor (sk, node.impedance.impedanz, options.cosphi, options.starting_ratio)
        val m1phmax = MaximumStartingCurrent.max_power_1_phase_line_to_neutral_motor (sk, node.impedance.impedanz, options.cosphi, options.starting_ratio)
        val mllmax = MaximumStartingCurrent.max_power_1_phase_line_to_line_motor (sk, node.impedance.impedanz, options.cosphi, options.starting_ratio)

        HouseConnection (node.id_seq, has (node.id_seq), node.source,
            node.impedance.impedanz.re, node.impedance.impedanz.im, node.impedance.null_impedanz.re, node.impedance.null_impedanz.im,
            node.fuses, node.errors, ik, ik3pol, ip, sk, m3phmax._1, m1phmax._1, mllmax._1, m3phmax._2, m1phmax._2, mllmax._2)
    }

    def run (): RDD[HouseConnection] =
    {
        // check if topology exists, and if not then generate it
        if (null == get[TopologicalNode])
        {
            val ntp = new CIMNetworkTopologyProcessor (session, storage_level, true, false) // force retain fuses
            val elements = ntp.process (false)
            log.info ("%d elements after topology generated".format (elements.count ()))
        }

        val _transformers = new Transformers (spark, storage_level)
        val tdata = _transformers.getTransformerData (true, options.default_supply_network_short_circuit_power, options.default_supply_network_short_circuit_impedance)

        val transformers: Array[Array[TData]] = if (null != options.trafos && "" != options.trafos && "all" != options.trafos) {
            val trafos = Source.fromFile (options.trafos, "UTF-8").getLines ().filter (_ != "").toArray
            val selected = tdata.filter (t ⇒ { trafos.contains (t.transformer.id) })
            selected.groupBy (t ⇒ t.terminal1.TopologicalNode).values.map (_.toArray).collect
        }
        else {
            // do all low voltage power transformers
            // ToDo: fix this 1kV multiplier on the voltages
            val niederspannug = tdata.filter ((td) ⇒ td.voltage0 != 0.4 && td.voltage1 == 0.4)
            niederspannug.groupBy (_.terminal1.TopologicalNode).values.map (_.toArray).collect
        }

        val starting_nodes = transformers.map ((txs) ⇒ trafo_mapping (TransformerSet (txs, options.default_transformer_power_rating, options.default_transformer_impedance)))

        // create the initial Graph with ScNode vertices
        def starting_map (starting_nodes: Array[StartingTrafos]) (id: VertexId, v: ScNode): ScNode =
        {
            starting_nodes.find (trafo ⇒ trafo.nsPin == id || trafo.osPin == id) match
            {
                case Some (node) ⇒
                    // assign source and impedances to starting transformer primary and secondary
                    if (node.osPin == id)
                        ScNode (v.id_seq, v.voltage, node.transformer.transformer_name, node.primary_impedance, null, null)
                    else
                    {
                        val errors = if (node.transformer.total_impedance._2) List (ScError (false, "transformer has no impedance value, using default %s".format (options.default_transformer_impedance))) else null
                        ScNode (v.id_seq, v.voltage, node.transformer.transformer_name, node.secondary_impedance, null, errors)
                    }
                case None ⇒
                    v
            }
        }

        val initial = get_inital_graph ()
        val initial_with_starting_nodes = initial.mapVertices (starting_map (starting_nodes)).persist (storage_level)
        val graph = trace (initial_with_starting_nodes)

        // get the leaf nodes with their data
        val has = graph.vertices.filter (null != _._2.impedance).values
        has.setName ("house_connections")
        has.persist (storage_level)

        has.map (calculate_short_circuit)
    }
}