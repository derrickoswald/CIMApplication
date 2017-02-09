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

case class PowerFeedingNode (id_seq: String, voltage: Double, source_obj: String, sum_r: Double, sum_x: Double) extends Graphable with Serializable
case class MaxPowerFeedingNodeEEA (has_id: String, source_obj: String, max_power_feeding: Double, has_eea: Boolean) extends Graphable with Serializable

class PowerFeeding (initial: Graph[PreNode, PreEdge]) extends Serializable
{
  
  def make_graph_vertices (v: PreNode): Tuple2[VertexId, PowerFeedingNode] =
    {
        val node = PowerFeedingNode(v.id_seq, v.voltage, "", Double.NegativeInfinity, Double.NegativeInfinity) 
        (v.vertex_id (v.id_seq), node)
    }

  def make_graph_edges (e: PreEdge): Edge[PreEdge] =
  {
      Edge (e.vertex_id (e.id_cn_1), e.vertex_id (e.id_cn_2), e)
  }
  
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
      
  def vertexProgram (id: VertexId, v: PowerFeedingNode, message: PowerFeedingNode): PowerFeedingNode =
  {
      if (message.sum_r > v.sum_r) message else v
  }
  
  def sendMessage (starting_id: Array[VertexId], trafo: String) (triplet: EdgeTriplet[PowerFeedingNode, PreEdge]): Iterator[(VertexId, PowerFeedingNode)] =
  {
      var ret:Iterator[(VertexId, PowerFeedingNode)] = Iterator.empty

      if (triplet.dstAttr != null && triplet.dstAttr.id_seq != null && starting_id.contains(triplet.dstId) && triplet.dstAttr.source_obj == "")
      {
        ret = Iterator((triplet.dstId, PowerFeedingNode (triplet.dstAttr.id_seq, triplet.dstAttr.voltage, trafo, 0.0, 0.0)))
      } else 
      {
        if (shouldContinue (triplet.attr.element))
        {
          var dist_km = 0.0
          var r = 0.0
          var x = 0.0
          val clazz = triplet.attr.element.getClass.getName
          if (clazz.substring (clazz.lastIndexOf (".") + 1) == "ACLineSegment")
          {
            r = triplet.attr.element.asInstanceOf[ACLineSegment].r
            x = triplet.attr.element.asInstanceOf[ACLineSegment].x
            dist_km = triplet.attr.element.asInstanceOf[ACLineSegment].length / 1000.0
          }

          if (triplet.srcAttr.source_obj != "" && triplet.dstAttr.source_obj == "")
          {
            val sum_r = triplet.srcAttr.sum_r + r * dist_km
            val sum_x = triplet.srcAttr.sum_x + x * dist_km
            ret = Iterator((triplet.dstId, PowerFeedingNode (triplet.dstAttr.id_seq, triplet.dstAttr.voltage, triplet.srcAttr.source_obj, sum_r, sum_x)))
          } 
          else if (triplet.srcAttr.source_obj == "" && triplet.dstAttr.source_obj != "")
          {
            val sum_r = triplet.dstAttr.sum_r + r * dist_km
            val sum_x = triplet.dstAttr.sum_x + x * dist_km
            ret = Iterator((triplet.srcId, PowerFeedingNode (triplet.srcAttr.id_seq, triplet.srcAttr.voltage, triplet.dstAttr.source_obj, sum_r, sum_x)))
          }
        }
      }
      
      return (ret)
  }

  def mergeMessage (a: PowerFeedingNode, b: PowerFeedingNode): PowerFeedingNode = 
  {
    if (a.sum_r > b.sum_r) a else b
  }
  
  def trace (starting_nodes: Array[VertexId], trafo: String): (VertexRDD[PowerFeedingNode], EdgeRDD[PreEdge]) = 
  {
    val transformer_circle = initial.vertices.id
    val graph = initial.mapVertices((id, v) => PowerFeedingNode(v.id_seq, v.voltage, "", Double.NegativeInfinity, Double.NegativeInfinity))
    
    val default_message = PowerFeedingNode(null, 0, "", Double.NegativeInfinity, Double.NegativeInfinity)
    
    val feeding_power = graph.pregel[PowerFeedingNode] (default_message, 10000, EdgeDirection.Either) (
            vertexProgram,
            sendMessage (starting_nodes, trafo),
            mergeMessage
        )
        
    (feeding_power.vertices, feeding_power.edges)
  }
  
  def calc_max_feeding_power(node: PowerFeedingNode): MaxPowerFeedingNodeEEA = 
  {
    val r = node.sum_r
    val x = node.sum_x
    val z_t = math.sqrt(r * r + x * x)
    val p_max = node.voltage * 0.03 * node.voltage * 0.03 / z_t
    val has_id = has(node.id_seq)
    MaxPowerFeedingNodeEEA(has_id, node.source_obj, p_max, false)
  }
  
  def has (string: String): String =
  {
      string.substring (0, string.indexOf ("_"))
  }
  
  def get_treshold_per_has (nodes: RDD[PowerFeedingNode]): RDD[MaxPowerFeedingNodeEEA] = 
  {
    val houses = nodes.filter(_.id_seq.startsWith("HAS"))
    houses.map(calc_max_feeding_power)
  }
  
  def has_eea (house_nodes: RDD[MaxPowerFeedingNodeEEA], solars: RDD[PV]): RDD[(MaxPowerFeedingNodeEEA, Option[SolarGeneratingUnit])] =
  {
    val keyed_solar = solars.map(s => (has(s.node), s.solar))
    house_nodes.keyBy(_.has_id).leftOuterJoin(keyed_solar).values
  }
}