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
import ch.ninecode.model.PowerTransformer
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

    import Trace._

    def vertexProgram (id: VertexId, v: NodeData, message: NodeData): NodeData =
    {
        if (v.total_distance > message.total_distance) message
        else v
    }

    /**
     * Method to determine if a switch is closed (both terminals are the same topological node).
     *
     * If the switch has the <code>open</code> attribute set, use that.
     * Otherwise if it has the <code>normalOpen</code> attribute set, use that.
     * Otherwise assume it is closed.
     *
     * @param switch The switch object to test.
     * @return <code>true</code> if the switch is closed, <code>false</code> otherwise.
     */
    def switchClosed (switch: Switch): Boolean =
    {
        if (0 != (switch.bitfields (openMask / 32) & (1 << (openMask % 32))))
            !switch.open // open valid
        else
            if (0 != (switch.bitfields (normalOpenMask / 32) & (1 << (normalOpenMask % 32))))
                !switch.normalOpen
            else
                true
    }

    // function to see if the Pregel algorithm should continue tracing or not
    def shouldContinue (element: Element): Boolean =
    {
        element match
        {
            case switch: Switch => switchClosed (switch)
            case cut: Cut => switchClosed (cut.Switch)
            case disconnector: Disconnector => switchClosed (disconnector.Switch)
            case fuse: Fuse => switchClosed (fuse.Switch)
            case gd: GroundDisconnector => switchClosed (gd.Switch)
            case jumper: Jumper => switchClosed (jumper.Switch)
            case ps: ProtectedSwitch => switchClosed (ps.Switch)
            case sectionaliser: Sectionaliser => switchClosed (sectionaliser.Switch)
            case breaker: Breaker => switchClosed (breaker.ProtectedSwitch.Switch)
            case lbs: LoadBreakSwitch => switchClosed (lbs.ProtectedSwitch.Switch)
            case recloser: Recloser => switchClosed (recloser.ProtectedSwitch.Switch)
            case _: PowerTransformer =>
                false
            case _ =>
                true
        }
    }

    def sendMessage (starting_id: Array[VertexId])(triplet: EdgeTriplet[NodeData, PreEdge]): Iterator[(VertexId, NodeData)] =
    {
        var ret: Iterator[(VertexId, NodeData)] = Iterator.empty

        if (triplet.dstAttr != null && triplet.dstAttr.id_seq != "" && starting_id.contains (triplet.dstId) && triplet.dstAttr.total_distance != 0.0)
        {
            ret = Iterator ((triplet.dstId, NodeData (triplet.dstAttr.id_seq, triplet.dstAttr.voltage, "", "", 0.0, 0.0)))
        }
        else
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
        val default_message = NodeData ("", 0, "", "", Double.PositiveInfinity, Double.PositiveInfinity)
        val tracedGraph = tree.pregel[NodeData](default_message, 10000, EdgeDirection.Either)(
            vertexProgram,
            sendMessage (starting_id),
            mergeMessage
        )

        tracedGraph
    }

}

object Trace
{
    /**
     * Index of normalOpen field in Switch bitmask.
     */
    val normalOpenMask: Int = Switch.fields.indexOf ("normalOpen")

    /**
     * Index of open field in Switch bitmask.
     */
    val openMask: Int = Switch.fields.indexOf ("open")
}