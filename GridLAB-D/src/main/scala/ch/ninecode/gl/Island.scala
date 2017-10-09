package ch.ninecode.gl

import org.apache.spark.graphx.Edge
import org.apache.spark.graphx.VertexId
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
import ch.ninecode.model.TopologicalIsland
import ch.ninecode.model.TopologicalNode
import ch.ninecode.model.WireInfo
import org.apache.spark.graphx.Graph

import scala.collection.Map

/**
 * A topological island utility class to get edges and nodes.
 *
 * @param spark The current spark session.
 * @param storage_level The storage level to use in persisting the edges and nodes.
 */
class Island (
    spark: SparkSession,
    storage_level: StorageLevel = StorageLevel.fromString ("MEMORY_AND_DISK_SER")) extends CIMRDD with Serializable
{
    implicit val session: SparkSession = spark
    implicit val log: Logger = LoggerFactory.getLogger (getClass)

    def edge_operator (voltages: Map[String, Double]) (arg: (Element, Iterable[Terminal], Double, Option[Iterable[PowerTransformerEnd]])): List[PreEdge] =
    {
        var ret = List[PreEdge]()

        def voltage (base: String): Double = 1000.0 * voltages.getOrElse (base, 0.0)

        val element = arg._1
        val terms = arg._2
        val ratedCurrent = arg._3
        val maybe_ends = arg._4
        // get the ConductingEquipment
        var cond = element
        while ((null != cond) && !cond.getClass.getName.endsWith(".ConductingEquipment"))
            cond = cond.sup
        if (null != cond)
        {
            // get the equipment
            val equipment = cond.asInstanceOf[ConductingEquipment]

            // sort terminals by sequence number (and hence the primary is index 0)
            val terminals = terms.toArray.sortWith (_.ACDCTerminal.sequenceNumber < _.ACDCTerminal.sequenceNumber)

            // make a list of voltages
            val volts =
                maybe_ends match
                {
                    case Some (ends: Iterable[PowerTransformerEnd]) ⇒
                        def volt_map (terminal: Terminal): Double =
                        {
                            ends.find (_.PowerTransformer == terminal.ConductingEquipment) match
                            {
                                case Some (end: PowerTransformerEnd) ⇒ voltage (end.TransformerEnd.BaseVoltage)
                                case None ⇒ log.error ("transformer end not found for terminal %s".format (terminal.id)); 0.0
                            }
                        }
                        terminals.map (volt_map)
                    case None ⇒
                        val volt = voltage (equipment.BaseVoltage)
                        Array[Double](volt, volt)
                }
            // Note: we eliminate 230V edges because transformer information doesn't exist and
            // see also NE-51 NIS.CIM: Export / Missing 230V connectivity
            if (!volts.contains (230.0))
                // make a pre-edge for each pair of terminals
                ret = terminals.length match
                {
                    case 1 ⇒
                        ret :+
                            PreEdge (
                                terminals(0).ACDCTerminal.id,
                                terminals(0).TopologicalNode,
                                volts(0),
                                "",
                                "",
                                volts(0),
                                terminals(0).ConductingEquipment,
                                ratedCurrent,
                                equipment,
                                element)
                    case _ ⇒
                        for (i ← 1 until terminals.length) // for comprehension: iterate omitting the upper bound
                        {
                            ret = ret :+ PreEdge (
                                terminals(0).ACDCTerminal.id,
                                terminals(0).TopologicalNode,
                                volts(0),
                                terminals(i).ACDCTerminal.id,
                                terminals(i).TopologicalNode,
                                volts(i),
                                terminals(0).ConductingEquipment,
                                ratedCurrent,
                                equipment,
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
        (null != edge.id_cn_1) && (null != edge.id_cn_2) && ("" != edge.id_cn_1) && ("" != edge.id_cn_2) && (edge.id_cn_1 != edge.id_cn_2)
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
        val voltages = get[BaseVoltage].map((v) ⇒ (v.id, v.nominalVoltage)).collectAsMap ()

        // get the island terminals keyed by equipment
        val terminals = get[Terminal].keyBy (_.TopologicalNode).join (get[TopologicalNode].keyBy (_.id)).filter (island == _._2._2.TopologicalIsland).map (_._2._1).keyBy (_.ConductingEquipment)

        // get all conducting equipment in the island
        val eq = get[Element] ("Elements").keyBy (_.id).join (terminals).map (x ⇒ (x._1, x._2._1))

        // get all terminals for this equipment
        val equipment = eq.join (get[Terminal].keyBy (_.ConductingEquipment).groupByKey)

        // join with WireInfo to get ratedCurrent (only for ACLineSegments)
        val equipment_rated = equipment.leftOuterJoin (getCableMaxCurrent).map (e ⇒ (e._1, (e._2._1._1, e._2._1._2, e._2._2 match { case Some (i) ⇒ i case None ⇒ Double.PositiveInfinity })))

        // get the transformer ends keyed by transformer
        val ends = get[PowerTransformerEnd].groupBy (_.PowerTransformer)

        // handle transformers specially, by attaching all PowerTransformerEnd objects to the elements
        def map2 (e: (String, ((Element, Iterable[Terminal], Double), Option[Iterable[PowerTransformerEnd]]))): (Element, Iterable[Terminal], Double, Option[Iterable[PowerTransformerEnd]]) = (e._2._1._1, e._2._1._2, e._2._1._3, e._2._2)
        val equipment_rated_ends: RDD[(Element, Iterable[Terminal], Double, Option[Iterable[PowerTransformerEnd]])] = equipment_rated.leftOuterJoin (ends).map (map2)

        // map to edges
        val edges = equipment_rated_ends.flatMap (edge_operator (voltages)).filter (edgefilter)

        val nodes = edges.flatMap (e ⇒ List (PreNode (e.id_cn_1, e.v1), PreNode (e.id_cn_2, e.v2))).distinct

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
        val xedges = edges.map (e ⇒ Edge (e.vertex_id (e.id_cn_1), e.vertex_id (e.id_cn_2), e))
        val xnodes = nodes.map (v ⇒ (v.vertex_id (v.id_seq), v))
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

        (xedges, xnodes)

        // construct the initial graph from the edges and nodes
        Graph.apply[PreNode, PreEdge] (xnodes, xedges, vertex_data, storage_level, storage_level)
    }
}
