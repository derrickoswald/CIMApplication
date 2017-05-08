package ch.ninecode.gl

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Calendar

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession

import org.apache.spark.graphx.Edge
import org.apache.spark.graphx.EdgeDirection
import org.apache.spark.graphx.EdgeTriplet
import org.apache.spark.graphx.Graph
import org.apache.spark.graphx.VertexRDD
import org.apache.spark.graphx.EdgeRDD
import org.apache.spark.graphx.VertexId
import org.apache.spark.storage.StorageLevel

import ch.ninecode.model._

case class PowerFeedingNode (
    id_seq: String,
    voltage: Double,
    source_obj: StartingTrafos,
    sum_r: Double,
    min_ir: Double,
    multiple_paths: Boolean) extends GLMNode
{
    override def id () = id_seq
    override def nominal_voltage () = voltage
}

case class MaxPowerFeedingNodeEEA (
    id_seq: String,
    voltage: Double,
    source_obj: Array[TData],
    max_power_feeding: Double,
    eea: Iterable[PV],
    reason: String,
    details: String)
    extends Serializable
{
    def nis_number: String =
    {
        val n = id_seq.indexOf("_")
        if (0 < n)
            id_seq.substring(0, n)
        else
            id_seq
    }
}
case class StartingTrafos(osPin: VertexId, nsPin: VertexId, trafo_id: Array[TData], r: Double, ratedS: Double) extends Serializable
case class PreCalculationResults (
    simulation: Int,
    has: RDD[MaxPowerFeedingNodeEEA],
    vertices: RDD[PowerFeedingNode],
    edges: RDD[(String, PreEdge)])

class PowerFeeding(initial: Graph[PreNode, PreEdge]) extends Serializable
{
    // check if messages should pass through and edge
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

    // return length, resistance and maximum curret for an edge
    def line_details (edge: PreEdge): Tuple3[Double, Double, Double] =
    {
        if (edge.element.isInstanceOf[ACLineSegment])
            (
                edge.element.asInstanceOf[ACLineSegment].Conductor.len / 1000.0,
                edge.element.asInstanceOf[ACLineSegment].r,
                edge.ratedCurrent
            )
        else
            (0.0, 0.0, Double.PositiveInfinity)
    }

    def vertexProgram (id: VertexId, v: PowerFeedingNode, message: PowerFeedingNode): PowerFeedingNode =
    {
        if (message.sum_r > v.sum_r || message.min_ir < v.min_ir) message else v
    }

    def sendMessage (triplet: EdgeTriplet[PowerFeedingNode, PreEdge]): Iterator[(VertexId, PowerFeedingNode)] =
    {
        if ((null != triplet.srcAttr.source_obj) || (null != triplet.dstAttr.source_obj))
            if (shouldContinue (triplet.attr.element))
                if (triplet.srcAttr.source_obj != null && triplet.dstAttr.source_obj == null)
                {
                    val (dist_km, r, ir) = line_details (triplet.attr)
                    val sum_r = triplet.srcAttr.sum_r + r * dist_km
                    val min_ir = math.min(triplet.srcAttr.min_ir, ir)
                    Iterator ((triplet.dstId, PowerFeedingNode (triplet.dstAttr.id_seq, triplet.dstAttr.voltage, triplet.srcAttr.source_obj, sum_r, min_ir, triplet.srcAttr.multiple_paths)))
                }
                else if (triplet.srcAttr.source_obj == null && triplet.dstAttr.source_obj != null)
                {
                    val (dist_km, r, ir) = line_details (triplet.attr)
                    val sum_r = triplet.dstAttr.sum_r + r * dist_km
                    val min_ir = math.min(triplet.dstAttr.min_ir, ir)
                    Iterator ((triplet.srcId, PowerFeedingNode (triplet.srcAttr.id_seq, triplet.srcAttr.voltage, triplet.dstAttr.source_obj, sum_r, min_ir, triplet.dstAttr.multiple_paths)))
                }
                else
                    Iterator.empty
            else
                Iterator.empty
        else
            Iterator.empty
    }

    def mergeMessage(a: PowerFeedingNode, b: PowerFeedingNode): PowerFeedingNode =
    {
        a.copy(multiple_paths=true)
    }

    def trace(starting_nodes: Array[StartingTrafos]): Graph[PowerFeedingNode, PreEdge] =
    {
        // create the initial Graph with PowerFeedingNode vertecies
        def starting_map (id: VertexId, v: PreNode): PowerFeedingNode =
        {
            starting_nodes.find (s ⇒ s.nsPin == id) match
            {
                case Some (node) =>
                    PowerFeedingNode (v.id_seq, v.voltage, node, 0.0, Double.PositiveInfinity, false)
                case None =>
                    PowerFeedingNode(v.id_seq, v.voltage, null.asInstanceOf[StartingTrafos], Double.NegativeInfinity, Double.PositiveInfinity, false)
            }
        }
        val graph = initial.mapVertices (starting_map)

        // run Pregel
        val default_message = PowerFeedingNode(null, 0, null.asInstanceOf[StartingTrafos], Double.NegativeInfinity, Double.PositiveInfinity, false)
        graph.pregel[PowerFeedingNode] (default_message, 10000, EdgeDirection.Either) (
            vertexProgram,
            sendMessage,
            mergeMessage
        )
    }

    def calc_max_feeding_power(node: PowerFeedingNode): MaxPowerFeedingNodeEEA =
    {
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
            if (node.multiple_paths)
                (trafo_ratedS, "transformer limit", "multiple paths")
            else if ((trafo_ratedS < p_max_u) && (trafo_ratedS < p_max_i))
                (trafo_ratedS, "transformer limit", "assuming no EEA")
            else if (p_max_u < p_max_i)
                (p_max_u, "voltage limit", "assuming no EEA")
            else
                (p_max_i, "current limit", "assuming no EEA")

        MaxPowerFeedingNodeEEA(node.id_seq, node.voltage, trafo_id, p_max, null, reason, details)
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

}

object PowerFeeding
{
    def trafo_mapping(use_topological_nodes: Boolean) (tdata: Array[TData]): StartingTrafos =
    {
      val pn = PreNode ("", 0.0)
      val v0 = pn.vertex_id (if (use_topological_nodes) tdata(0).terminal0.TopologicalNode else tdata(0).terminal0.ConnectivityNode)
      val v1 = pn.vertex_id (if (use_topological_nodes) tdata(0).terminal1.TopologicalNode else tdata(0).terminal1.ConnectivityNode)
      val ratedS = tdata.map(_.end1.ratedS).sum
      val r = if (tdata.length > 1)
      {
          // ToDo: handle 3 or more transformers ganged together
          val r1 = tdata(0).end1.r
          val r2 = tdata(1).end1.r
          (r1 * r2) / (r1 + r2)
      }
      else
          tdata(0).end1.r
      StartingTrafos (v0, v1, tdata, r, ratedS)
    }

    def threshold_calculation (session: SparkSession, initial: Graph[PreNode, PreEdge], sdata: RDD[Tuple2[String,Iterable[PV]]], transformers: Array[Array[TData]], gridlabd: GridLABD, storage_level: StorageLevel): PreCalculationResults =
    {
        val use_topological_nodes: Boolean = true
        val power_feeding = new PowerFeeding(initial)
        val start_ids = transformers.map (trafo_mapping (use_topological_nodes))

        val graph = power_feeding.trace (start_ids)
        val house_nodes = power_feeding.get_treshold_per_has (graph.vertices.values.filter(_.source_obj != null))
        val traced_house_nodes_EEA = house_nodes.keyBy(_.id_seq).leftOuterJoin(sdata).values

        val has = traced_house_nodes_EEA.map(node =>
        {
            node._2 match
            {
                case Some (eea) =>
                    node._1.copy(eea = eea)
                case None =>
                    node._1
            }
        })

        val simulation = Database.store_precalculation ("Threshold Precalculation", Calendar.getInstance (), gridlabd) (has)
        println ("the simulation number is " + simulation)

        def mapGraphEdges(triplet: EdgeTriplet[PowerFeedingNode, PreEdge]): (String, PreEdge) =
        {
          val source = triplet.srcAttr.source_obj
          val target = triplet.dstAttr.source_obj
          
          var ret = (null.asInstanceOf[String], triplet.attr)
          if (source != null && target != null && source.trafo_id != null && target.trafo_id != null) {
            val source_trafo_id = gridlabd.trafokreis_key(source.trafo_id)
            val target_trafo_id = gridlabd.trafokreis_key(target.trafo_id)
            if (source_trafo_id == target_trafo_id)
              ret = (source_trafo_id, triplet.attr) 
          }
          ret
        }
        
        val vertices = graph.vertices.values
        val edges = graph.triplets.map(mapGraphEdges)

        has.persist (storage_level)
        vertices.persist (storage_level)
        edges.persist (storage_level)
        session.sparkContext.getCheckpointDir match
        {
            case Some (dir) => has.checkpoint (); vertices.checkpoint (); edges.checkpoint ()
            case None =>
        }
        
        PreCalculationResults (simulation, has, vertices, edges)
    }
}