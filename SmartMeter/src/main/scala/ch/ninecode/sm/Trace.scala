package ch.ninecode.sm

import org.apache.spark.graphx.EdgeDirection
import org.apache.spark.graphx.EdgeTriplet
import org.apache.spark.graphx.Graph
import org.apache.spark.graphx.Graph.graphToGraphOps
import org.apache.spark.graphx.VertexId

import ch.ninecode.model._

// just to get a single copy of the vertex_id function
trait Graphable
{
    def vertex_id (string: String): VertexId =
    {
        string.hashCode.asInstanceOf[VertexId]
    }
}

// define the minimal node and edge classes
case class PreNode (id_seq: String, voltage: Double) extends Graphable with Serializable
case class PreEdge (id_seq_1: String, id_cn_1: String, v1: Double, id_seq_2: String, id_cn_2: String, v2: Double, id_equ: String, equipment: ConductingEquipment, element: Element) extends Graphable with Serializable
{
    // provide a key on the two connections, independant of to-from from-to ordering
    def key (): String =
    {
        if (id_cn_1 < id_cn_2) id_cn_1 + id_cn_2 else id_cn_2 + id_cn_1
    }
}

case class NodeData
(
    neighbor: VertexId,
    total_distance: Double,
    nearest_distance: Double
)

case class AugmentedNodeData
(
    id_seq: String,
    voltage: Double,
    neighbor: VertexId,
    total_distance: Double,
    nearest_distance: Double
)

case class FinalNodeData
(
    id_seq: String,
    voltage: Double,
    neighbor: String,
    total_distance: Double,
    nearest_distance: Double
)

/**
 * Distance trace.
 * Concept:
 *   Node data is null until a pregel message hits it.
 *   A sent message is the new node data, that is the Pregel message type is the NodeData
 *   It includes the id (I'm your neighbor), the cummulative distance and the hop distance.
 *   Merge message shouldn't happen, but if it does, take the nearest neighbor
 *   (Note: there is no backtrack, so it really ony works for acyclic graphs).
 *
 */
class Trace (initial: Graph[PreNode, PreEdge]) extends Serializable
{
    def vertexProgram (starting_nodes: Array[VertexId]) (id: VertexId, v: NodeData, message: NodeData): NodeData =
    {
        if (null != message)
            message
        else
            // on the first pass through the Pregel algorithm all nodes get a null message
            // if this node is in in the list of starting nodes, set the vertex data to zero
            if (starting_nodes.contains (id))
                NodeData (id, 0.0, 0.0)
            else
                null
    }

    // function to see if the Pregel algorithm should continue tracing or not
    def shouldContinue (element: Element): Boolean =
    {
        val clazz = element.getClass.getName
        val cls = clazz.substring (clazz.lastIndexOf (".") + 1)
        val ret = cls match
        {
            case "Switch" =>
                !element.asInstanceOf[Switch].normalOpen
            case "Cut" =>
                !element.asInstanceOf[Cut].Switch.normalOpen
            case "Disconnector" =>
                !element.asInstanceOf[Disconnector].Switch.normalOpen
            case "Fuse" =>
                !element.asInstanceOf[Fuse].Switch.normalOpen
            case "GroundDisconnector" =>
                !element.asInstanceOf[GroundDisconnector].Switch.normalOpen
            case "Jumper" =>
                !element.asInstanceOf[Jumper].Switch.normalOpen
            case "ProtectedSwitch" =>
                !element.asInstanceOf[ProtectedSwitch].Switch.normalOpen
            case "Sectionaliser" =>
                !element.asInstanceOf[Sectionaliser].Switch.normalOpen
            case "Breaker" =>
                !element.asInstanceOf[Breaker].ProtectedSwitch.Switch.normalOpen
            case "LoadBreakSwitch" =>
                !element.asInstanceOf[LoadBreakSwitch].ProtectedSwitch.Switch.normalOpen
            case "Recloser" =>
                !element.asInstanceOf[Recloser].ProtectedSwitch.Switch.normalOpen
            case "PowerTransformer" =>
                false
            case _ =>
                true
        }
        return (ret)
    }

    // function to get the edge distance
    def span (element: Element): Double =
    {
        val clazz = element.getClass.getName
        val cls = clazz.substring (clazz.lastIndexOf (".") + 1)
        val ret = cls match
        {
            case "ACLineSegment" =>
                element.asInstanceOf[ACLineSegment].Conductor.len
            case _ =>
                0.0
        }
        return (ret)
    }

    def sendMessage (triplet: EdgeTriplet[NodeData, PreEdge]): Iterator[(VertexId, NodeData)] =
    {
        var ret:Iterator[(VertexId, NodeData)] = Iterator.empty

        if (null == triplet.dstAttr) // a message is needed if the destination NodeData is null
        {
            if (null != triplet.srcAttr)
                if (shouldContinue (triplet.attr.element))
                {
                    val hop = span (triplet.attr.element)
                    ret = Iterator ((triplet.dstId, NodeData (triplet.srcId, triplet.srcAttr.total_distance + hop, hop)))
                }
        }
        else
            if (null == triplet.srcAttr) // a message is needed in the reverse direction if the source NodeData is null
                if (shouldContinue (triplet.attr.element))
                {
                    val hop = span (triplet.attr.element)
                    ret = Iterator ((triplet.srcId, NodeData (triplet.dstId, triplet.dstAttr.total_distance + hop, hop)))
                }

        return (ret)
    }

    def mergeMessage (a: NodeData, b: NodeData): NodeData =
    {
        if (a.total_distance < b.total_distance)
            a
        else
            b
    }

    // discard leaf edges that aren't transformers
    def keep (arg: Tuple3[PreEdge, Option[PreNode], Option[PreNode]]): Boolean =
    {
        val ret = arg match
        {
            case (_, Some (_), Some (_)) => // connected on both ends (not a leaf): keep
                true
            case (_, Some (_), None) | (_, None, Some (_)) => // connected on only one end (a leaf)
                val edge = arg._1
                edge.v1 != edge.v2 // keep if it spans a voltage change (a transformer)
            case (_, None, None) => // not connected (can't happen): discard
                false
        }

        return (ret)
    }

    // trace the graph with the Pregel algorithm
    def run (starting_nodes: Array[VertexId]) =
    {
        // make a similar graph with distance values as vertex data
        val tree = initial.mapVertices { case (_, _) => null.asInstanceOf[NodeData] }

        // perform the trace, marking all traced nodes with the distance from the starting nodes
        val default_message = null
        val graph = tree.pregel[NodeData] (default_message, 10000, EdgeDirection.Either) (
            vertexProgram (starting_nodes),
            sendMessage,
            mergeMessage
        )

        // get the list of traced vertices
        val touched = graph.vertices.filter (_._2 != null)
        val traced_nodes = touched.join (initial.vertices)
        val just_traced_nodes = traced_nodes.map ((x) => (x._1, x._2._2))

        // get the list of edges
        val edges = initial.edges.map (_.attr)

        // OK, this is subtle, edges that stop the trace (open switches, open fuses, transformers, etc.)
        // have one node that isn't in the traced_nodes RDD.
        //
        // There are two ways to handle this, add in the missing nodes
        // or remove the edges that have only one vertex in the trace.
        //
        // With the "add in the missing nodes" method, GridLAB-D fails with the message:
        //    "Newton-Raphson method is unable to converge to a solution at this operation point"
        // because of open switches that are on the edge of the graph (removing the switches and the
        // singly connected edge node allows the system to be solved).
        //
        // But, removing all singly-connected edges also removes transformers,
        // meaning there is no high voltage node to use as a primary slack bus.
        // Sure, a secondary low voltage slack bus could be used - effectively
        // ignoring the capability of the transformer - but instead we selectively remove
        // singly-connected edges that are not transformers.

        // get the edges with their connected nodes
        val one = edges.keyBy ((x) => x.vertex_id (x.id_cn_1)).leftOuterJoin (just_traced_nodes).values
        val two = one.keyBy ((x) => x._1.vertex_id (x._1.id_cn_2)).leftOuterJoin (just_traced_nodes).values.map ((x) => (x._1._1, x._1._2, x._2))

        // filter out non-transformer leaf edges
        val traced_edges = two.filter (keep).map (_._1)

        // create a more complete list of traced nodes using the edge list
        val all_traced_nodes = traced_edges.keyBy (_.id_cn_1).union (traced_edges.keyBy (_.id_cn_2)).join (initial.vertices.values.keyBy (_.id_seq)).reduceByKey ((a, b) ⇒ a).values.values

        // join the traced nodes with their vertex data
        def ff (a: Tuple2[PreNode, Option[NodeData]]) =
        {
            val node = a._1
            val data = a._2
            data match
            {
                case Some (data) =>
                    new AugmentedNodeData (node.id_seq, node.voltage, data.neighbor, data.total_distance, data.nearest_distance)
                case None =>
                    new AugmentedNodeData (node.id_seq, node.voltage, 0L, 0.0, 0.0)
            }
        }
        val traced_nodes_plus = all_traced_nodes.keyBy ((a) => a.vertex_id (a.id_seq)).leftOuterJoin (touched).values.map (ff)
        def gg (a: Tuple2[AugmentedNodeData, Option[PreNode]]) =
        {
            val data = a._1
            val node = a._2
            node match
            {
                case Some (node) =>
                    new FinalNodeData (data.id_seq, data.voltage, node.id_seq, data.total_distance, data.nearest_distance)
                case None =>
                    new FinalNodeData (data.id_seq, data.voltage, "", data.total_distance, data.nearest_distance)
            }
        }
        val final_traced_nodes = traced_nodes_plus.keyBy (_.neighbor).leftOuterJoin (just_traced_nodes).values.map (gg)

        (final_traced_nodes, traced_edges)
    }

}
