package ch.ninecode.mfi

import java.util.Calendar

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.apache.spark.graphx.EdgeDirection
import org.apache.spark.graphx.EdgeTriplet
import org.apache.spark.graphx.Graph
import org.apache.spark.graphx.VertexId
import org.apache.spark.graphx.Graph.graphToGraphOps
import org.apache.spark.rdd.RDD.rddToPairRDDFunctions
import org.apache.spark.storage.StorageLevel
import org.slf4j.LoggerFactory
import org.slf4j.Logger

import ch.ninecode.cim.CIMRDD
import ch.ninecode.gl.GridLABD
import ch.ninecode.gl.PV
import ch.ninecode.gl.PreEdge
import ch.ninecode.gl.PreNode
import ch.ninecode.gl.TransformerSet
import ch.ninecode.model.ACLineSegment
import ch.ninecode.model.EnergyConsumer
import ch.ninecode.model.Terminal

class PowerFeeding (session: SparkSession) extends CIMRDD with Serializable
{
    implicit val spark: SparkSession = session
    implicit val log: Logger = LoggerFactory.getLogger (getClass)

    // return length, resistance and maximum curret for an edge
    def line_details (edge: PreEdge): (Double, Double, Double) =
    {
        edge.element match
        {
            case line: ACLineSegment ⇒ (line.Conductor.len / 1000.0, line.r, edge.ratedCurrent)
            case _ ⇒ (0.0, 0.0, Double.PositiveInfinity)
        }
    }

    def vertexProgram (id: VertexId, v: PowerFeedingNode, message: PowerFeedingNode): PowerFeedingNode =
    {
        if (message.sum_r > v.sum_r || message.min_ir < v.min_ir) message else v
    }

    def sendMessage (triplet: EdgeTriplet[PowerFeedingNode, PreEdge]): Iterator[(VertexId, PowerFeedingNode)] =
    {
        if ((null != triplet.srcAttr.source_obj) || (null != triplet.dstAttr.source_obj))
            if (triplet.attr.connected)
                if (triplet.srcAttr.source_obj != null && triplet.dstAttr.source_obj == null)
                {
                    val (dist_km, r, ir) = line_details (triplet.attr)
                    val sum_r = triplet.srcAttr.sum_r + r * dist_km
                    val min_ir = math.min(triplet.srcAttr.min_ir, ir)
                    val problem = if (null != triplet.srcAttr.problem) triplet.srcAttr.problem else triplet.attr.problem
                    val message = PowerFeedingNode (triplet.dstAttr.id, triplet.dstAttr.nominal_voltage, triplet.srcAttr.source_obj, sum_r, min_ir, problem)
                    if (log.isDebugEnabled)
                        log.debug ("%s <-- %s".format (triplet.dstId.toString,  message.asString))
                    Iterator ((triplet.dstId, message))
                }
                else if (triplet.srcAttr.source_obj == null && triplet.dstAttr.source_obj != null)
                {
                    val (dist_km, r, ir) = line_details (triplet.attr)
                    val sum_r = triplet.dstAttr.sum_r + r * dist_km
                    val min_ir = math.min(triplet.dstAttr.min_ir, ir)
                    val problem = if (null != triplet.dstAttr.problem) triplet.dstAttr.problem else triplet.attr.problem
                    val message = PowerFeedingNode (triplet.srcAttr.id, triplet.srcAttr.nominal_voltage, triplet.dstAttr.source_obj, sum_r, min_ir, problem)
                    if (log.isDebugEnabled)
                        log.debug ("%s <-- %s".format (triplet.srcId.toString, message.asString))
                    Iterator ((triplet.srcId, message))
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
        val node = a.copy (problem = "non-radial network")
        if (log.isDebugEnabled)
            log.debug ("merge %s & %s".format (a.asString, b.asString))
        node
    }

    def trace (initial: Graph[PreNode, PreEdge], starting_nodes: Array[StartingTrafos]): Graph[PowerFeedingNode, PreEdge] =
    {
        log.info ("trace")
        // create the initial Graph with PowerFeedingNode vertecies
        def starting_map (id: VertexId, v: PreNode): PowerFeedingNode =
        {
            starting_nodes.find (s ⇒ s.nsPin == id) match
            {
                case Some (node) =>
                    PowerFeedingNode (v.id, v.nominal_voltage, node, 0.0, Double.PositiveInfinity, v.problem)
                case None =>
                    PowerFeedingNode (v.id, v.nominal_voltage, null.asInstanceOf[StartingTrafos], Double.NegativeInfinity, Double.PositiveInfinity, v.problem)
            }
        }
        val graph = initial.mapVertices (starting_map)

        // run Pregel
        val default_message = PowerFeedingNode(null, 0, null.asInstanceOf[StartingTrafos], Double.NegativeInfinity, Double.PositiveInfinity, null)
        graph.pregel[PowerFeedingNode] (default_message, 10000, EdgeDirection.Either) (
            vertexProgram,
            sendMessage,
            mergeMessage
        )
    }

    def calc_max_feeding_power(args: (PowerFeedingNode, Option[String])): MaxPowerFeedingNodeEEA =
    {
        val node: PowerFeedingNode = args._1
        val psrtype: Option[String] = args._2
        val r = node.sum_r
        val v = node.nominal_voltage
        val min_ir = node.min_ir
        val trafo_id = node.source_obj.trafo_id
        val trafo_ratedS = node.source_obj.ratedS
        val trafo_r = node.source_obj.z.re
        val r_summe = math.sqrt(3) * r + trafo_r

        val p_max_u = math.sqrt(3) * 1.03 * 0.03 * v * v / r_summe
        val p_max_i = math.sqrt(3) * min_ir * (v + r_summe * min_ir)
        val (p_max, reason, details) =
            if (null != node.problem)
                (trafo_ratedS, node.problem, null)
            else if ((trafo_ratedS < p_max_u) && (trafo_ratedS < p_max_i))
                (trafo_ratedS, "transformer limit", "assuming no EEA")
            else if (p_max_u < p_max_i)
                (p_max_u, "voltage limit", "assuming no EEA")
            else
                (p_max_i, "current limit", "assuming no EEA")

        MaxPowerFeedingNodeEEA (node.id, node.nominal_voltage, psrtype.orNull, trafo_id, p_max, null, reason, details)
    }

    def has(string: String): String =
    {
        string.substring (0, string.indexOf ("_"))
    }

    def get_treshold_per_has(nodes: RDD[PowerFeedingNode]): RDD[MaxPowerFeedingNodeEEA] =
    {
        val houses: RDD[PowerFeedingNode] = nodes.filter (_.sum_r > 0.0)
        val psrtype = get[Terminal].keyBy (_.ConductingEquipment).join (get[EnergyConsumer].keyBy(_.id)).values.map (x ⇒ (x._1.TopologicalNode, x._2.ConductingEquipment.Equipment.PowerSystemResource.PSRType))
        houses.keyBy (_.id).leftOuterJoin (psrtype).values.map (calc_max_feeding_power)
    }

    def trafo_mapping (transformers: TransformerSet): StartingTrafos =
    {
        val pn = PreNode ("", 0.0, null)
        val v0 = pn.vertex_id (transformers.node0)
        val v1 = pn.vertex_id (transformers.node1)
        val ratedS = transformers.power_rating
        val impedance = transformers.total_impedance_per_unit._1
        StartingTrafos (v0, v1, transformers.transformer_name, impedance, ratedS)
    }

    def threshold_calculation (initial: Graph[PreNode, PreEdge], sdata: RDD[(String, Iterable[PV])], transformers: Array[TransformerSet], gridlabd: GridLABD, storage_level: StorageLevel): PreCalculationResults =
    {
        val start_ids = transformers.map (trafo_mapping)

        val graph = trace (initial, start_ids)
        val house_nodes = get_treshold_per_has (graph.vertices.values.filter(_.source_obj != null))
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

        val simulation = Database.store_precalculation ("Threshold Precalculation", Calendar.getInstance ()) (has)
        log.info ("the simulation number is " + simulation)

        def mapGraphEdges(triplet: EdgeTriplet[PowerFeedingNode, PreEdge]): (String, PreEdge) =
        {
            val source = triplet.srcAttr.source_obj
            val target = triplet.dstAttr.source_obj

            var ret = (null.asInstanceOf[String], triplet.attr)
            if (source != null && target != null && source.trafo_id != null && target.trafo_id != null) {
                val source_trafo_id = source.trafo_id
                val target_trafo_id = target.trafo_id
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
        if (session.sparkContext.getCheckpointDir.isDefined) { has.checkpoint (); vertices.checkpoint (); edges.checkpoint () }

        PreCalculationResults (simulation, has, vertices, edges)
    }
}
