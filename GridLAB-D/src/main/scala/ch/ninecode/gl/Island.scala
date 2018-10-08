package ch.ninecode.gl

import scala.collection.Map

import org.apache.spark.graphx.Edge
import org.apache.spark.graphx.Graph
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.cim.CIMRDD
import ch.ninecode.model._

/**
 * A topological island utility class to get edges and nodes.
 *
 * @param spark The current spark session.
 * @param storage_level The storage level to use in persisting the edges and nodes.
 */
class Island (
    spark: SparkSession,
    storage_level: StorageLevel = StorageLevel.fromString ("MEMORY_AND_DISK_SER"))
extends CIMRDD
with Serializable
{
    implicit val session: SparkSession = spark
    implicit val log: Logger = LoggerFactory.getLogger (getClass)
    import Island._

    /**
     * Return <code>true</code> if there is connectivity through the edge (if the Pregel algorithm should continue tracing) or not.
     */
    def connected (element: Element): Boolean =
    {
        val clazz = element.getClass.getName
        val cls = clazz.substring (clazz.lastIndexOf (".") + 1)
        cls match
        {
            case "Switch" ⇒             !element.asInstanceOf[Switch].normalOpen
            case "Cut" ⇒                !element.asInstanceOf[Cut].Switch.normalOpen
            case "Disconnector" ⇒       !element.asInstanceOf[Disconnector].Switch.normalOpen
            case "Fuse" ⇒               !element.asInstanceOf[Fuse].Switch.normalOpen
            case "GroundDisconnector" ⇒ !element.asInstanceOf[GroundDisconnector].Switch.normalOpen
            case "Jumper" ⇒             !element.asInstanceOf[Jumper].Switch.normalOpen
            case "MktSwitch" ⇒          !element.asInstanceOf[MktSwitch].Switch.normalOpen
            case "ProtectedSwitch" ⇒    !element.asInstanceOf[ProtectedSwitch].Switch.normalOpen
            case "Breaker" ⇒            !element.asInstanceOf[Breaker].ProtectedSwitch.Switch.normalOpen
            case "LoadBreakSwitch" ⇒    !element.asInstanceOf[LoadBreakSwitch].ProtectedSwitch.Switch.normalOpen
            case "Recloser" ⇒           !element.asInstanceOf[Recloser].ProtectedSwitch.Switch.normalOpen
            case "Sectionaliser" ⇒      !element.asInstanceOf[Sectionaliser].Switch.normalOpen
            case "Conductor" ⇒          true
            case "ACLineSegment" ⇒      true
            case "PowerTransformer" ⇒   false
            case _ ⇒
                log.error("trace setup encountered edge " + element.id + " with unhandled class '" + cls + "', assumed conducting")
                true
        }
    }

    /**
     * Warn of special cases of transformers.
     *
     * @param element Element to test
     * @param num_terminals total number of terminals on the ConductingEquipment
     * @param v1 primary voltage
     * @param v2 secondary voltage
     * @return an error string with additional information about validity
     */
    def hasIssues (element: Element, num_terminals: Int, v1: Double, v2: Double): String =
    {
        element match
        {
            case _: PowerTransformer ⇒
                // Three Winding Transformer - if there are more than 2 PowerTransformerEnd associated to the PowerTransformer
                if (num_terminals > 2)
                    "%s transformer windings for edge %s".format (num_terminals, element.id)
                // Voltage Regulator Transformer: if there are less than 3 PowerTransformerEnd associated to the PowerTransformer and the voltage of the two ends are both <= 400V
                else if (v1 == v2)
                    "voltage (%sV) regulator edge %s".format (v1, element.id)
                // Low Voltage Transmission: if there are less than 3 PowerTransformerEnd associated to the PowerTransformer and the voltage of the two ends are both <= 1kV and one end is < 1kV
                else if (v1 <= 1000.0 && v2 <= 1000.0)
                    "low voltage (%sV:%sV) subtransmission edge %s".format (v1, v2, element.id)
                else
                    null
            case _ ⇒
                null
        }
    }

    def edge_operator (arg: (Element, Iterable[(Terminal, Double)], Double)): List[PreEdge] =
    {
        var ret = List[PreEdge]()

        val element = arg._1
        val terminals: Array[(Terminal, Double)] = arg._2.toArray
        val ratedCurrent = arg._3
        // get the ConductingEquipment
        var cond = element
        while ((null != cond) && !cond.getClass.getName.endsWith(".ConductingEquipment"))
            cond = cond.sup
        if (null != cond)
        {
            // get the equipment
            val equipment = cond.asInstanceOf[ConductingEquipment]
            val conn = connected (element)
            // Note: we eliminate 230V edges because transformer information doesn't exist and
            // see also NE-51 NIS.CIM: Export / Missing 230V connectivity
            if (!terminals.map (_._2).contains (230.0))
                // make a pre-edge for each pair of terminals
                ret = terminals.length match
                {
                    case 1 ⇒
                        ret :+
                            PreEdge (
                                terminals(0)._1.id,
                                terminals(0)._1.TopologicalNode,
                                terminals(0)._2,
                                "",
                                "",
                                terminals(0)._2,
                                terminals(0)._1.ConductingEquipment,
                                conn,
                                null,
                                ratedCurrent,
                                element)
                    case _ ⇒
                        for (i ← 1 until terminals.length) // for comprehension: iterate omitting the upper bound
                        {
                            ret = ret :+ PreEdge (
                                terminals(0)._1.id,
                                terminals(0)._1.TopologicalNode,
                                terminals(0)._2,
                                terminals(i)._1.id,
                                terminals(i)._1.TopologicalNode,
                                terminals(i)._2,
                                terminals(0)._1.ConductingEquipment,
                                conn,
                                hasIssues (element, terminals.length, terminals(0)._2, terminals(i)._2),
                                ratedCurrent,
                                element)
                        }
                        ret
                }
        }
        //else // shouldn't happen, terminals always reference ConductingEquipment, right?
        // throw new Exception ("element " + e.id + " is not derived from ConductingEquipment")
        // ProtectionEquipment and CurrentRelay are emitted with terminals even though they shouldn't be

        ret
    }

    /**
     * Get pairs of cable id and maximum current.
     */
    def getCableMaxCurrent: RDD[(String, Double)] =
    {
        val lines = get[ACLineSegment].keyBy (_.Conductor.ConductingEquipment.Equipment.PowerSystemResource.AssetDatasheet)
        val cables = lines.join (get[WireInfo].keyBy (_.id)).values.map (x ⇒ (x._1.id, x._2.ratedCurrent))

        cables.name = "cables"
        cables.persist (storage_level)
        if (session.sparkContext.getCheckpointDir.isDefined) cables.checkpoint ()

        cables
    }

    /**
     * Predicate to eliminate edges with only one connectivity node, or the same connectivity node.
     *
     * @param edge The edge to check
     * @return <code>true</code> if the edge is valid.
     */
    def edgefilter (edge: PreEdge): Boolean =
    {
        (null != edge.cn1) && (null != edge.cn2) && ("" != edge.cn1) && ("" != edge.cn2) && (edge.cn1 != edge.cn2)
    }

    /**
     * Associate a voltage with each terminal.
     *
     * For transformer objects, the terminals for the transformer ends are used instead of the raw terminals list,
     * so that both (or all) ends of the transformer are included to make edges. In this case
     * the voltages are picked up from the transformer ends.
     * Otherwise the base voltage for the conducting equipment is associated to each terminal.
     *
     * @param voltages The list of base voltage names with voltage value
     * @param arg Equipment ID, element and associated terminals, transformer ends and associated terminals (only for transformers)
     * @return (mRID, (ConductingEquipment, Terminals&Voltages))
     */
    def attach_voltages  (voltages: Map[String, Double]) (arg: (String, (Iterable[(Element, Terminal)], Option[Iterable[(PowerTransformerEnd, Terminal)]]))): (String, (Element, Iterable[(Terminal, Double)])) =
    {
        def voltage (base: String): Double = 1000.0 * voltages.getOrElse (base, 0.0)

        val mRID: String = arg._1
        val element: Element = arg._2._1.head._1
        val terminals: Iterable[Terminal] = arg._2._1.map (_._2)
        val maybe_ends: Option[Iterable[(PowerTransformerEnd, Terminal)]] = arg._2._2
        // get the ConductingEquipment
        var cond = element
        while ((null != cond) && !cond.getClass.getName.endsWith(".ConductingEquipment"))
            cond = cond.sup
        if (null != cond)
        {
            maybe_ends match
            {
                case Some (ends: Iterable[(PowerTransformerEnd, Terminal)]) ⇒
                    // use the Terminals list from the power transformer so that primary and secondary (secondaries) are included
                    val sorted_ends: Seq[(PowerTransformerEnd, Terminal)] = ends.toList.sortWith (_._1.TransformerEnd.endNumber < _._1.TransformerEnd.endNumber)
                    (mRID, (element, sorted_ends.map (x ⇒ (x._2, voltage (x._1.TransformerEnd.BaseVoltage)))))
                case None ⇒
                    // get the equipment
                    val equipment = cond.asInstanceOf[ConductingEquipment]
                    val volt = voltage (equipment.BaseVoltage)
                    (mRID, (element, terminals.map ((_, volt))))
            }
        }
        else
        {
            log.error ("element %s is not conducting equipment".format (mRID))
            (mRID, (element, List()))
        }
    }

    /**
     * Add rated current values to edges.
     *
     * @param arg (mRID, ((ConductingEquipment, Terminals&Voltages), {sometimes rated current})
     * @return (ConductingEquipment, Terminals&Voltages, ratedCurrent_or_infinity)
     */
    def attach_currents (arg: (String, ((Element, Iterable[(Terminal, Double)]), Option[Double]))): (Element, Iterable[(Terminal, Double)], Double) =
    {
        val element: Element = arg._2._1._1
        val terminals_voltages: Iterable[(Terminal, Double)] = arg._2._1._2
        val maybe_current: Option[Double] = arg._2._2
        val max: Double = Double.PositiveInfinity
        (element, terminals_voltages, maybe_current match { case Some (i) ⇒ i case None ⇒ max })
    }

    /**
     * Generate edge and node RDDs for the island.
     *
     * @param island The island name (TopologicalIsland mRDI).
     * @return A tuple of edge and node RDD' suitable for GraphX.
     */
    def prepare (island: String): (RDD[PreEdge], RDD[PreNode]) =
    {
        // get a map of voltages
        val voltages = get[BaseVoltage].map(v ⇒ (v.id, v.nominalVoltage)).collectAsMap ()

        // get the island terminals keyed by equipment
        val terminals = get[Terminal].keyBy (_.TopologicalNode).join (get[TopologicalNode].keyBy (_.id)).filter (island == _._2._2.TopologicalIsland).map (_._2._1).keyBy (_.ConductingEquipment)

        // get all conducting equipment in the island with its terminals
        val eq = get[Element] ("Elements").keyBy (_.id).join (terminals).groupByKey

        // get the transformer ends and associated terminals keyed by transformer
        val ends = get[PowerTransformerEnd].keyBy (_.TransformerEnd.Terminal).join (get[Terminal].keyBy (_.id)).values.groupBy (_._1.PowerTransformer)

        // assign voltages to each terminal and pick up the (primary) transformer terminals
        val equip = eq.leftOuterJoin (ends).map (attach_voltages (voltages))

        // join with WireInfo to get ratedCurrent (only for ACLineSegments)
        val equipment = equip.leftOuterJoin (getCableMaxCurrent).map (attach_currents)

        // map to edges
        val edges = equipment.flatMap (edge_operator).filter (edgefilter)

        // make nodes from the edges
        val nodes = edges.flatMap (e ⇒ if (e.cn2 != "") List (PreNode (e.cn1, e.v1, e.problem), PreNode (e.cn2, e.v2, e.problem)) else List (PreNode (e.cn1, e.v1, e.problem))).distinct

        (edges, nodes)
    }

    /**
     * Generate the GraphX graph for the island.
     *
     * @param island The island name (TopologicalIsland mRDI).
     * @param vertex_data The data to use as the default vertex attribute.
     * @return A tuple of edge and node RDD' suitable for GraphX.
     */
    def graph (island: String, vertex_data: PreNode): Graph[PreNode, PreEdge] =
    {
        val (edges, nodes) = prepare (island)

        // persist edges and nodes to avoid recompute
        val xedges = edges.map (e ⇒ Edge (e.vertex_id (e.cn1), e.vertex_id (e.cn2), e))
        val xnodes = nodes.map (v ⇒ (v.vertex_id (v.id), v))
        val e = xedges.count
        xedges.name = "xedges"
        xedges.persist (storage_level)
        val n = xnodes.count
        xnodes.name = "xnodes"
        xnodes.persist (storage_level)
        if (session.sparkContext.getCheckpointDir.isDefined) { xedges.checkpoint (); xnodes.checkpoint () }

        // construct the initial graph from the edges and nodes
        Graph[PreNode, PreEdge] (xnodes, xedges, vertex_data, storage_level, storage_level)
    }

    /**
     * Generate RDD of nodes and edges for GridLAB-D based on a mapping from some <em>identifier</em> to island mRID.
     *
     * The <em>identifier</em> can be a transformer set id, e.g. "TRA1234_TRA1235"
     * and the mapping would label each island with its supply transformer
     * in a many-to-one fashion, i.e. each island belongs to only one transformer set id
     * and all the islands having the same transformer set id belong to the
     * set of islands in the same transformer service area (Trafokreis).
     *
     * Or, the <em>identifier</em> can be a feeder mRID, e.g. "ABG1234"
     * and the mapping would label each island with such an identifier
     * in a many-to-one fashion, i.e. each island belongs to only one feeder mRID,
     * and all the islands having the same feeder mRID belong to the
     * set of islands in the same feeder service area (Abgangkreis).
     *
     * @param identifiers_islands mapping from identifier to island mRID
     * @param node_maker method to create a GLMNode from a node and the details about connected terminals, elements and voltages
     * @param edge_maker method to create a GLMEdge from an iterator over groups of terminals and their element that belong to the same edge (may be parallel)
     * @return
     */
    def queryNetwork (
        identifiers_islands: IslandMap,
        node_maker: RDD[NodeParts] ⇒ RDD[(identifier, GLMNode)],
        edge_maker: RDD[EdgeParts] ⇒ RDD[(identifier, GLMEdge)]): (Nodes, Edges) =
    {
        // the mapping between island and transformer service area
        val islands_trafos: RDD[(island_id, identifier)] = identifiers_islands.map (_.swap)
        // get nodes by TopologicalIsland
        val members: RDD[(node_id, island_id)] = get[TopologicalNode].map (node ⇒ (node.id, node.TopologicalIsland))
        // get terminals by TopologicalIsland
        val terminals: RDD[(island_id, Terminal)] = get[Terminal].keyBy (_.TopologicalNode).join (members).map (_._2.swap)
        // map terminals to transformer service areas
        val transformers_terminals: RDD[(identifier, Terminal)] = terminals.join (islands_trafos).values.map (_.swap)
        // get equipment attached to each terminal
        val terminals_equipment: RDD[(Iterable[(identifier, Terminal)], ConductingEquipment)] = transformers_terminals.groupBy (_._2.ConductingEquipment).join (get[ConductingEquipment].keyBy (_.id)).values.cache

        // where two or more pieces of equipment connect to the same topological node, we would like the equipment that only has one terminal, e.g. EnergyConsumer, rather than the ACLineSgment
        val one_terminal_equipment: RDD[(String, ((identifier, Terminal), ConductingEquipment))] = terminals_equipment.filter (1 == _._1.size).map (x ⇒ (x._1.head, x._2)).keyBy (_._1._2.TopologicalNode)
        val all_equipment: RDD[(String, ((identifier, Terminal), ConductingEquipment))] = terminals_equipment.flatMap (x ⇒ x._1.map (y ⇒ (y, x._2))).keyBy (_._1._2.TopologicalNode)
        // preferentially take the single terminal equipment, but in all cases keep only one equipment
        val t_e: RDD[((identifier, Terminal), ConductingEquipment)] = all_equipment.leftOuterJoin (one_terminal_equipment).values.map (
            {
                case (_, Some (single)) ⇒ single
                case (other, None) ⇒ other
            }
        )

        // make nodes
        val m: RDD[(identifier, (Terminal, ConductingEquipment))] = t_e.map (x ⇒ (x._1._1, (x._1._2, x._2)))
        // key by BaseVoltage - handle PowerTransformer specially, otherwise it could be just .keyBy (_._2._2.BaseVoltage)
        val m_key_by_BaseVoltage = m.keyBy (_._2._1.id).leftOuterJoin (get[PowerTransformerEnd].keyBy (_.TransformerEnd.Terminal)).values
            .map (
                {
                    case (x, Some (end)) ⇒ (end.TransformerEnd.BaseVoltage, x)
                    case (x, None) ⇒ (x._2._2.BaseVoltage, x)
                }
            )

        val n: RDD[(identifier, (Terminal, ConductingEquipment, BaseVoltage))] = m_key_by_BaseVoltage.join (get[BaseVoltage].keyBy (_.id)).values.map (x ⇒ (x._1._1, (x._1._2._1, x._1._2._2, x._2)))
        val nn: RDD[(identifier, (Terminal, Element, BaseVoltage))] = n.keyBy (_._2._2.id).join (get[Element]("Elements").keyBy (_.id)).values.map (x ⇒ (x._1._1, (x._1._2._1, x._2, x._1._2._3)))

        val o: RDD[NodeParts] = nn.groupBy (_._2._1.TopologicalNode)
        val nodes: RDD[(identifier, GLMNode)] = node_maker (o).cache

        // get all equipment with two nodes in the transformer service area that separate different TopologicalIsland (these are the edges)
        val ff: RDD[(Iterable[(identifier, Terminal)], Element)] = terminals_equipment.keyBy (_._2.id).join (get[Element]("Elements").keyBy (_.id)).values.map (x ⇒ (x._1._1, x._2)).cache
        val tte: RDD[(Iterable[(identifier, Terminal)], Element)] = ff.filter (x ⇒ x._1.size > 1 && (x._1.head._1 == x._1.tail.head._1) && (x._1.head._2.TopologicalNode != x._1.tail.head._2.TopologicalNode))
        // combine parallel edges
        val eq: RDD[EdgeParts] = tte.keyBy (_._1.map (_._2.TopologicalNode).toArray.sortWith (_ < _).mkString ("_")).groupByKey.values
        // make edges
        val edges: RDD[(identifier, GLMEdge)] = edge_maker (eq).cache

        (nodes, edges)
    }
}

object Island
{
    type identifier = String
    type island_id = String
    type node_id = String
    type IslandMap = RDD[(identifier, island_id)]
    type Nodes = RDD[(identifier, GLMNode)]
    type Edges = RDD[(identifier, GLMEdge)]
    type NodeParts = (node_id, Iterable[(identifier, (Terminal, Element, BaseVoltage))])
    type EdgeParts = Iterable[(Iterable[(identifier, Terminal)], Element)]
}