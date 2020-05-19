package ch.ninecode.sm

import org.apache.spark.graphx.EdgeDirection
import org.apache.spark.graphx.EdgeTriplet
import org.apache.spark.graphx.Graph
import org.apache.spark.graphx.Graph.graphToGraphOps
import org.apache.spark.graphx.VertexId

import ch.ninecode.model.Breaker
import ch.ninecode.model.ConductingEquipment
import ch.ninecode.model.Cut
import ch.ninecode.model.Disconnector
import ch.ninecode.model.Element
import ch.ninecode.model.Fuse
import ch.ninecode.model.GroundDisconnector
import ch.ninecode.model.Jumper
import ch.ninecode.model.LoadBreakSwitch
import ch.ninecode.model.ProtectedSwitch
import ch.ninecode.model.Recloser
import ch.ninecode.model.Sectionaliser
import ch.ninecode.model.Switch
import ch.ninecode.util._

// define the minimal node and edge classes
case class PreNode (id_seq: String, voltage: Double) extends Graphable with Serializable

case class PreEdge (id_seq_1: String, id_cn_1: String, v1: Double, id_seq_2: String, id_cn_2: String, v2: Double, id_equ: String, ratedCurrent: Double, equipment: ConductingEquipment, element: Element, length: Double) extends Graphable with Serializable
{
    // provide a key on the two connections, independant of to-from from-to ordering
    def key (): String =
    {
        if (id_cn_1 < id_cn_2) id_cn_1 + id_cn_2 else id_cn_2 + id_cn_1
    }
}

case class NodeData
(
    id_seq: String,
    voltage: Double,
    neighbor: String,
    parent: String,
    total_distance: Double,
    nearest_distance: Double
)

case class FinalNodeData
(
    name: String,
    ao_id: Array[String],
    voltage: Double,
    neighbor: String,
    parent: String,
    total_distance: Double,
    nearest_distance: Double
)

/**
 * Distance trace.
 * Concept:
 * Node data is null until a pregel message hits it.
 * A sent message is the new node data, that is the Pregel message type is the NodeData
 * It includes the id (I'm your neighbor), the cummulative distance and the hop distance.
 * Merge message shouldn't happen, but if it does, take the nearest neighbor
 * (Note: there is no backtrack, so it really ony works for acyclic graphs).
 *
 */
class Trace (initial: Graph[PreNode, PreEdge]) extends Serializable
{
    def vertexProgram (id: VertexId, v: NodeData, message: NodeData): NodeData =
    {
        if (v.total_distance > message.total_distance) message
        else v
    }

    // function to see if the Pregel algorithm should continue tracing or not
    def shouldContinue (element: Element): Boolean =
    {
        val clazz = element.getClass.getName
        val cls = clazz.substring (clazz.lastIndexOf (".") + 1)
        cls match
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
    }

    def sendMessage (starting_id: Array[VertexId])(triplet: EdgeTriplet[NodeData, PreEdge]): Iterator[(VertexId, NodeData)] =
    {
        var ret: Iterator[(VertexId, NodeData)] = Iterator.empty

        if (triplet.dstAttr != null && triplet.dstAttr.id_seq != null && starting_id.contains (triplet.dstId) && triplet.dstAttr.total_distance != 0.0)
        {
            ret = Iterator ((triplet.dstId, NodeData (triplet.dstAttr.id_seq, triplet.dstAttr.voltage, "", "", 0.0, 0.0)))
        } else
        {

            if (shouldContinue (triplet.attr.element))
            {
                val length = triplet.attr.length

                if (triplet.srcAttr.total_distance < triplet.dstAttr.total_distance)
                {
                    val new_total_distance = triplet.srcAttr.total_distance + length
                    if (new_total_distance < triplet.dstAttr.total_distance)
                    {
                        var neighbor = triplet.dstAttr.neighbor
                        var nearest_distance = triplet.dstAttr.nearest_distance
                        if (nearest_distance > length)
                        {
                            neighbor = triplet.srcAttr.id_seq
                            nearest_distance = length
                        }
                        ret = Iterator ((triplet.dstId, NodeData (triplet.dstAttr.id_seq, triplet.dstAttr.voltage, neighbor, triplet.srcAttr.id_seq, new_total_distance, nearest_distance)))
                    }
                } else
                    if (triplet.dstAttr.total_distance < triplet.srcAttr.total_distance)
                    {
                        val new_total_distance = triplet.dstAttr.total_distance + length
                        if (new_total_distance < triplet.srcAttr.total_distance)
                        {
                            var neighbor = triplet.srcAttr.neighbor
                            var nearest_distance = triplet.srcAttr.nearest_distance
                            if (nearest_distance > length)
                            {
                                neighbor = triplet.dstAttr.id_seq
                                nearest_distance = length
                            }
                            ret = Iterator ((triplet.srcId, NodeData (triplet.srcAttr.id_seq, triplet.srcAttr.voltage, neighbor, triplet.dstAttr.id_seq, new_total_distance, nearest_distance)))
                        }
                    }
            }
        }

        ret
    }

    def mergeMessage (a: NodeData, b: NodeData): NodeData =
    {
        if (a.total_distance < b.total_distance)
            a
        else
            b
    }

    // trace the graph with the Pregel algorithm
    def run (starting_id: Array[VertexId]): Graph[NodeData, PreEdge] =
    {
        // make a similar graph with distance values as vertex data
        val tree = initial.mapVertices ((_, vertex: PreNode) => NodeData (vertex.id_seq, vertex.voltage, "", "", Double.PositiveInfinity, Double.PositiveInfinity))

        // perform the trace, marking all traced nodes with the distance from the starting nodes
        val default_message = NodeData (null, 0, "", "", Double.PositiveInfinity, Double.PositiveInfinity)
        val tracedGraph = tree.pregel[NodeData](default_message, 10000, EdgeDirection.Either)(
            vertexProgram,
            sendMessage (starting_id),
            mergeMessage
        )

        tracedGraph
    }

}
