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
import ch.ninecode.model.ACLineSegment
import ch.ninecode.model.BaseVoltage
import ch.ninecode.model.ConductingEquipment
import ch.ninecode.model.Element
import ch.ninecode.model.PowerTransformerEnd
import ch.ninecode.model.Terminal
import ch.ninecode.model.TopologicalNode
import ch.ninecode.model.WireInfo

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
        session.sparkContext.getCheckpointDir match
        {
            case Some (_) ⇒ cables.checkpoint()
            case None ⇒
        }

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
        val nodes = edges.flatMap (e ⇒ if (e.cn2 != "") List (PreNode (e.cn1, e.v1), PreNode (e.cn2, e.v2)) else List (PreNode (e.cn1, e.v1))).distinct

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
        session.sparkContext.getCheckpointDir match
        {
            case Some (_) ⇒
                xedges.checkpoint ()
                xnodes.checkpoint ()
            case None ⇒
        }

        // construct the initial graph from the edges and nodes
        Graph.apply[PreNode, PreEdge] (xnodes, xedges, vertex_data, storage_level, storage_level)
    }

    def queryNetwork (
         trafos_islands: IslandMap,
         node_maker: RDD[NodeParts] ⇒ RDD[(transformerset_id, GLMNode)],
         edge_maker: RDD[EdgeParts] ⇒ RDD[(transformerset_id, GLMEdge)]): (Nodes, Edges) =
    {
        // the mapping between island and transformer service area
        val islands_trafos: RDD[(island_id, transformerset_id)] = trafos_islands.map (_.swap)
        // get nodes by TopologicalIsland
        val members: RDD[(node_id, island_id)] = get[TopologicalNode].map (node ⇒ (node.id, node.TopologicalIsland))
        // get terminals by TopologicalIsland
        val terminals: RDD[(island_id, Terminal)] = get[Terminal].keyBy (_.TopologicalNode).join (members).map (_._2.swap)
        // map terminals to transformer service areas
        val transformers_terminals: RDD[(transformerset_id, Terminal)] = terminals.join (islands_trafos).values.map (_.swap)
        // get equipment attached to each terminal
        val terminals_equipment: RDD[(Iterable[(transformerset_id, Terminal)], ConductingEquipment)] = transformers_terminals.groupBy (_._2.ConductingEquipment).join (get[ConductingEquipment].keyBy (_.id)).values

        // where two or more pieces of equipment connect to the same topological node, we would like the equipment that only has one terminal, e.g. EnergyConsumer, rather than the ACLineSgment
        val one_terminal_equipment: RDD[(String, ((transformerset_id, Terminal), ConductingEquipment))] = terminals_equipment.filter (1 == _._1.size).map (x ⇒ (x._1.head, x._2)).keyBy (_._1._2.TopologicalNode)
        val all_equipment: RDD[(String, ((transformerset_id, Terminal), ConductingEquipment))] = terminals_equipment.flatMap (x ⇒ x._1.map (y ⇒ (y, x._2))).keyBy (_._1._2.TopologicalNode)
        // preferentially take the single terminal equipment, but in all cases keep only one equipment
        val t_e: RDD[((transformerset_id, Terminal), ConductingEquipment)] = all_equipment.leftOuterJoin (one_terminal_equipment).values.map (
            {
                case (_, Some (single)) ⇒ single
                case (other, None) ⇒ other
            }
        )

        // make nodes
        val m: RDD[(transformerset_id, (Terminal, ConductingEquipment))] = t_e.map (x ⇒ (x._1._1, (x._1._2, x._2)))
        // key by BaseVoltage - handle PowerTransformer specially, otherwise it could be just .keyBy (_._2._2.BaseVoltage)
        val m_key_by_BaseVoltage = m.keyBy (_._2._1.id).leftOuterJoin (get[PowerTransformerEnd].keyBy (_.TransformerEnd.Terminal)).values
            .map (
                {
                    case (x, Some (end)) ⇒ (end.TransformerEnd.BaseVoltage, x)
                    case (x, None) ⇒ (x._2._2.BaseVoltage, x)
                }
            )

        val n: RDD[(transformerset_id, (Terminal, ConductingEquipment, BaseVoltage))] = m_key_by_BaseVoltage.join (get[BaseVoltage].keyBy (_.id)).values.map (x ⇒ (x._1._1, (x._1._2._1, x._1._2._2, x._2)))
        val nn: RDD[(transformerset_id, (Terminal, Element, BaseVoltage))] = n.keyBy (_._2._2.id).join (get[Element]("Elements").keyBy (_.id)).values.map (x ⇒ (x._1._1, (x._1._2._1, x._2, x._1._2._3)))

        val o: RDD[NodeParts] = nn.groupBy (_._2._1.TopologicalNode)
        val nodes: RDD[(transformerset_id, GLMNode)] = node_maker (o)

        // get all equipment with two nodes in the transformer service area that separate different TopologicalIsland (these are the edges)
        val ff: RDD[(Iterable[(transformerset_id, Terminal)], Element)] = terminals_equipment.keyBy (_._2.id).join (get[Element]("Elements").keyBy (_.id)).values.map (x ⇒ (x._1._1, x._2))
        val tte: RDD[(Iterable[(transformerset_id, Terminal)], Element)] = ff.filter (x ⇒ x._1.size > 1 && (x._1.head._1 == x._1.tail.head._1) && (x._1.head._2.TopologicalNode != x._1.tail.head._2.TopologicalNode))
        // combine parallel edges
        val eq: RDD[EdgeParts] = tte.keyBy (_._1.map (_._2.TopologicalNode).toArray.sortWith (_ < _).mkString ("_")).groupByKey.values
        // make edges
        val edges: RDD[(transformerset_id, GLMEdge)] = edge_maker (eq)

        (nodes, edges)
    }
}

object Island
{
    type transformerset_id = String
    type island_id = String
    type node_id = String
    type IslandMap = RDD[(transformerset_id, island_id)]
    type Nodes = RDD[(transformerset_id, GLMNode)]
    type Edges = RDD[(transformerset_id, GLMEdge)]
    type NodeParts = (node_id, Iterable[(transformerset_id, (Terminal, Element, BaseVoltage))])
    type EdgeParts = Iterable[(Iterable[(transformerset_id, Terminal)], Element)]
}