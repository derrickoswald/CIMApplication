package ch.ninecode.gl

import org.apache.spark.rdd.RDD

import org.apache.spark.graphx.Edge
import org.apache.spark.graphx.EdgeDirection
import org.apache.spark.graphx.EdgeTriplet
import org.apache.spark.graphx.Graph
import org.apache.spark.graphx.VertexRDD
import org.apache.spark.graphx.EdgeRDD
import org.apache.spark.graphx.VertexId

import ch.ninecode.model._

case class PowerFeedingNode(id_seq: String, voltage: Double, source_obj: StartingTrafos, sum_r: Double, min_ir: Double) extends Serializable
case class MaxPowerFeedingNodeEEA(has_id: String, source_obj: String, max_power_feeding: Double, has_eea: Boolean, reason: String, details: String) extends Serializable
case class StartingTrafos(vertexId: VertexId, trafo_id: String, r: Double, ratedS: Double) extends Serializable

class PowerFeeding(initial: Graph[PreNode, PreEdge]) extends Serializable {

    def make_graph_vertices(v: PreNode): Tuple2[VertexId, PowerFeedingNode] =
    {
        val node = PowerFeedingNode(v.id_seq, v.voltage, null.asInstanceOf[StartingTrafos], Double.NegativeInfinity, Double.PositiveInfinity)
        (v.vertex_id (v.id_seq), node)
    }

    def make_graph_edges(e: PreEdge): Edge[PreEdge] =
    {
        Edge (e.vertex_id (e.id_cn_1), e.vertex_id (e.id_cn_2), e)
    }

    def shouldContinue(element: Element): Boolean =
    {
        val clazz = element.getClass.getName
        val cls = clazz.substring (clazz.lastIndexOf (".") + 1)
        val ret = cls match {
            case "Switch" ⇒
                !element.asInstanceOf[Switch].normalOpen
            case "Cut" ⇒
                !element.asInstanceOf[Cut].Switch.normalOpen
            case "Disconnector" ⇒
                !element.asInstanceOf[Disconnector].Switch.normalOpen
            case "Fuse" ⇒
                !element.asInstanceOf[Fuse].Switch.normalOpen
            case "GroundDisconnector" ⇒
                !element.asInstanceOf[GroundDisconnector].Switch.normalOpen
            case "Jumper" ⇒
                !element.asInstanceOf[Jumper].Switch.normalOpen
            case "ProtectedSwitch" ⇒
                !element.asInstanceOf[ProtectedSwitch].Switch.normalOpen
            case "Sectionaliser" ⇒
                !element.asInstanceOf[Sectionaliser].Switch.normalOpen
            case "Breaker" ⇒
                !element.asInstanceOf[Breaker].ProtectedSwitch.Switch.normalOpen
            case "LoadBreakSwitch" ⇒
                !element.asInstanceOf[LoadBreakSwitch].ProtectedSwitch.Switch.normalOpen
            case "Recloser" ⇒
                !element.asInstanceOf[Recloser].ProtectedSwitch.Switch.normalOpen
            case "PowerTransformer" ⇒
                false
            case _ ⇒
                true
        }
        return (ret)
    }

    def vertexProgram(id: VertexId, v: PowerFeedingNode, message: PowerFeedingNode): PowerFeedingNode =
    {
        if (message.sum_r > v.sum_r || message.min_ir < v.min_ir) message else v
    }

    def sendMessage(starting_id: Array[StartingTrafos])(triplet: EdgeTriplet[PowerFeedingNode, PreEdge]): Iterator[(VertexId, PowerFeedingNode)] =
    {
        var ret: Iterator[(VertexId, PowerFeedingNode)] = Iterator.empty

        val start = starting_id.find(s ⇒ s.vertexId == triplet.dstId)
        if (triplet.dstAttr != null && triplet.dstAttr.id_seq != null && !start.isEmpty && triplet.dstAttr.source_obj == null) {
            ret = Iterator((triplet.dstId, PowerFeedingNode (triplet.dstAttr.id_seq, triplet.dstAttr.voltage, start.get, 0.0, Double.PositiveInfinity)))
        }
        else {
            if (shouldContinue (triplet.attr.element)) {
                var dist_km = 0.0
                var r = 0.0
                var ir = Double.PositiveInfinity
                if (triplet.attr.element.isInstanceOf[ACLineSegment]) {
                    r = triplet.attr.element.asInstanceOf[ACLineSegment].r
                    dist_km = triplet.attr.element.asInstanceOf[ACLineSegment].Conductor.len / 1000.0
                    ir = triplet.attr.ratedCurrent
                }

                if (triplet.srcAttr.source_obj != null && triplet.dstAttr.source_obj == null) {
                    val sum_r = triplet.srcAttr.sum_r + r * dist_km
                    val min_ir = math.min(triplet.srcAttr.min_ir, ir)
                    ret = Iterator((triplet.dstId, PowerFeedingNode (triplet.dstAttr.id_seq, triplet.dstAttr.voltage, triplet.srcAttr.source_obj, sum_r, min_ir)))
                }
                else if (triplet.srcAttr.source_obj == null && triplet.dstAttr.source_obj != null) {
                    val sum_r = triplet.dstAttr.sum_r + r * dist_km
                    val min_ir = math.min(triplet.dstAttr.min_ir, ir)
                    ret = Iterator((triplet.srcId, PowerFeedingNode (triplet.srcAttr.id_seq, triplet.srcAttr.voltage, triplet.dstAttr.source_obj, sum_r, min_ir)))
                }
            }
        }

        return (ret)
    }

    def mergeMessage(a: PowerFeedingNode, b: PowerFeedingNode): PowerFeedingNode =
    {
        if (a.sum_r > b.sum_r) a else b
    }

    def trace(starting_nodes: Array[StartingTrafos]): (VertexRDD[PowerFeedingNode], EdgeRDD[PreEdge]) =
    {
        val transformer_circle = initial.vertices.id
        val graph = initial.mapVertices((id, v) ⇒ PowerFeedingNode(v.id_seq, v.voltage, null.asInstanceOf[StartingTrafos], Double.NegativeInfinity, Double.PositiveInfinity))

        val default_message = PowerFeedingNode(null, 0, null.asInstanceOf[StartingTrafos], Double.NegativeInfinity, Double.PositiveInfinity)

        val feeding_power = graph.pregel[PowerFeedingNode] (default_message, 10000, EdgeDirection.Either) (
            vertexProgram,
            sendMessage (starting_nodes),
            mergeMessage
        )

        (feeding_power.vertices, feeding_power.edges)
    }

    def calc_max_feeding_power(node: PowerFeedingNode): MaxPowerFeedingNodeEEA =
    {
        val has_id = has(node.id_seq)
        val r = node.sum_r
        val v = node.voltage
        val min_ir = node.min_ir
        val trafo_id = node.source_obj.trafo_id
        val trafo_ratedS = node.source_obj.ratedS
        val trafo_r = node.source_obj.r
        val r_summe = math.sqrt(3) * r + trafo_r

        val p_max_u = math.sqrt(3) * 1.03 * 0.03 * v * v / r_summe
        val p_max_i = math.sqrt(3) * min_ir * (v + r_summe * min_ir)
        val (p_max, reason, details) =
            if ((trafo_ratedS < p_max_u) && (trafo_ratedS < p_max_i))
                (trafo_ratedS, "transformer limit", "assuming no EEA")
            else if (p_max_u < p_max_i)
                (p_max_u, "voltage limit", "assuming no EEA")
            else
                (p_max_i, "current limit", "assuming no EEA")

        MaxPowerFeedingNodeEEA(has_id, trafo_id, p_max, false, reason, details)
    }

    def has(string: String): String =
    {
        string.substring (0, string.indexOf ("_"))
    }

    def get_treshold_per_has(nodes: RDD[PowerFeedingNode]): RDD[MaxPowerFeedingNodeEEA] =
    {
        val houses = nodes.filter(_.id_seq.startsWith("HAS"))
        houses.map(calc_max_feeding_power)
    }

    def join_eea(house_nodes: RDD[MaxPowerFeedingNodeEEA], solars: RDD[PV]): RDD[(MaxPowerFeedingNodeEEA, Option[SolarGeneratingUnit])] =
    {
        val keyed_solar = solars.map(s ⇒ (has(s.node), s.solar))
        house_nodes.keyBy(_.has_id).leftOuterJoin(keyed_solar).values
    }
}