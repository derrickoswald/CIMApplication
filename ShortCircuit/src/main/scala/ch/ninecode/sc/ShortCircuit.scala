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
    val default_node = ScNode ("", 0.0, null, null.asInstanceOf[Impedanzen])

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
        val vertex = if (term.ACDCTerminal.sequenceNumber == 1) edge.v1 else edge.v2
        ScNode (node.id, vertex, null, null)
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
        def vprog (id: VertexId, v: ScNode, message: ScNode): ScNode =
        {
            if (null == message)
                v
            else if (null == v)
                message
            else if (null == message.impedance)
                v
            else if (null == v.impedance)
                message
            else if (message.impedance.impedanz < v.impedance.impedanz)
                message
            else
                v
        }

        def sendMessage (triplet: EdgeTriplet[ScNode, ScEdge]): Iterator[(VertexId, ScNode)] =
        {
            if (triplet.srcAttr.impedance != null && triplet.dstAttr.impedance == null)
                if (triplet.attr.shouldContinueTo (triplet.dstAttr.id_seq))
                    Iterator ((triplet.dstId, ScNode (triplet.dstAttr.id_seq, triplet.dstAttr.voltage, triplet.srcAttr.source, triplet.attr.impedanceTo (triplet.dstAttr.id_seq, triplet.srcAttr.impedance))))
                else
                    Iterator.empty
            else if (triplet.srcAttr.impedance == null && triplet.dstAttr.impedance != null)
                if (triplet.attr.shouldContinueTo (triplet.srcAttr.id_seq))
                    Iterator ((triplet.srcId, ScNode (triplet.srcAttr.id_seq, triplet.srcAttr.voltage, triplet.dstAttr.source, triplet.attr.impedanceTo (triplet.srcAttr.id_seq, triplet.dstAttr.impedance))))
                else
                    Iterator.empty
            else
                Iterator.empty
        }

        def mergeMessage (a: ScNode, b: ScNode): ScNode =
        {
            a.copy (impedance = Impedanzen (a.impedance.impedanz.parallel_impedanz (b.impedance.impedanz), a.impedance.null_impedanz.parallel_impedanz (b.impedance.null_impedanz)))
        }

        initial.pregel (default_node, 10000, EdgeDirection.Either) (vprog, sendMessage, mergeMessage)
    }

    // compute the short-circuit values
    def calculate_short_circuit (node: ScNode): HouseConnection =
    {
        // ToDo: expose these constants
        val c = 1.0
        val cmin = 0.90

        val v2 = node.voltage
        val root3 = Math.sqrt (3.0)

        // Einpoligen Kurzschlussstrom berechnen
        val ik_z = root3 * cmin * v2
        val ik_n_sqrt1 = !node.impedance.impedanz
        val ik_n_sqrt2 = !node.impedance.null_impedanz
        val ik_n = 2 * ik_n_sqrt1 + ik_n_sqrt2
        val ik = ik_z / ik_n

        // Dreipoligen Kurzschlussstrom berechnen
        val ik3pol_n = root3 * !node.impedance.impedanz
        val ik3pol = c * v2 / ik3pol_n

        // Stosskurzschlussstrom berechnen
        // was       val ip = (1.02 + 0.98 * Math.exp (-3.0 * (trafo_r1 + netz_r1) / (trafo_x1 + Math.abs (netz_x1)))) * Math.sqrt (2) * ik3pol

        // maximum aperiodic short-circuit current according to IEC 60909-0, see for example:
        // http://www.dii.unipd.it/-renato.gobbo/didattica/corsi/Componenti_tecnologie_elettrici/ABB_swithgear_manual_E11/ABB_11_E_03_druck.pdf pp71-80
        // http://studiecd.dk/cahiers_techniques/Calculation_of_short_circuit_currents.pdf pp7-10
        val r_over_x = node.impedance.impedanz.re / node.impedance.impedanz.im
        val kappa = 1.02 + 0.98 * Math.exp (-3.0 * r_over_x)
        val ip = kappa * Math.sqrt (2) * ik3pol

        // short-circuit power at the point of common coupling
        val sk = (v2 * v2) / !node.impedance.impedanz

        HouseConnection (node.id_seq, has (node.id_seq), node.source, node.impedance.impedanz.re, node.impedance.impedanz.im, node.impedance.null_impedanz.re, node.impedance.null_impedanz.im, ik, ik3pol, ip, sk)
    }

    def run (): RDD[HouseConnection] =
    {
        val _transformers = new Transformers (spark, storage_level)
        val tdata = _transformers.getTransformerData (true, options.default_supply_network_short_circuit_power, options.default_supply_network_short_circuit_angle)

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

        val starting_nodes = transformers.map ((txs) ⇒ trafo_mapping (TransformerSet (txs)))

        // create the initial Graph with ScNode vertices
        def starting_map (starting_nodes: Array[StartingTrafos]) (id: VertexId, v: ScNode): ScNode =
        {
            starting_nodes.find (trafo ⇒ trafo.nsPin == id || trafo.osPin == id) match
            {
                case Some (node) ⇒
                    // assign source and impedances to starting transformer primary and secondary
                    if (node.osPin == id)
                        ScNode (v.id_seq, v.voltage, node.transformer.transformer_name, node.primary_impedance)
                    else
                        ScNode (v.id_seq, v.voltage, node.transformer.transformer_name, node.secondary_impedance)
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