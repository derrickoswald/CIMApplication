package ch.ninecode.mfi

import org.apache.spark.graphx.EdgeDirection
import org.apache.spark.graphx.EdgeTriplet
import org.apache.spark.graphx.Graph
import org.apache.spark.graphx.Graph.graphToGraphOps
import org.apache.spark.graphx.VertexId
import org.apache.spark.graphx.VertexRDD
import org.apache.spark.rdd.RDD
import org.apache.spark.rdd.RDD.rddToPairRDDFunctions
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.cim.CIMRDD
import ch.ninecode.gl.PV
import ch.ninecode.gl.PreEdge
import ch.ninecode.gl.PreNode
import ch.ninecode.model.ACLineSegment
import ch.ninecode.model.ConductingEquipment
import ch.ninecode.model.Terminal
import ch.ninecode.net.TransformerIsland
import ch.ninecode.util.Complex

class PowerFeeding (session: SparkSession, storage_level: StorageLevel = StorageLevel.MEMORY_AND_DISK_SER, tbase: Double = 20, tsim: Double) extends CIMRDD with Serializable
{
    implicit val spark: SparkSession = session
    implicit val log: Logger = LoggerFactory.getLogger (getClass)

    val alpha: Double = 0.004

    def resistanceAt (t: Double, base: Double, r: Double): Double = (1.0 + (alpha * (t - base))) * r

    /**
     * Get details for an edge.
     *
     * <em>Note:
     * This is completely broken because it incorrectly assumes the per kilometer impedance is directly
     * contained by the ACLineSegment (to agree with legacy CIM export code) instead of using the
     * PerLengthImpedance relationship to get the PerLengthSequenceImpedance.
     * </em>
     *
     * @param edge the edge to get the details for
     * @return length, impedance and rated current for cables, otherwise zero impedance and infinite rated current
     */
    def line_details (edge: PreEdge): (Double, Complex, Double) =
    {
        edge.element match
        {
            // ToDo: fix this (see note above)
            case line: ACLineSegment => (line.Conductor.len / 1000.0, Complex (resistanceAt (tsim, tbase, line.r), line.x), edge.ratedCurrent)
            case _ => (0.0, 0.0, Double.PositiveInfinity)
        }
    }

    def vertexProgram (id: VertexId, v: PowerFeedingNode, message: PowerFeedingNode): PowerFeedingNode =
    {
        // keep special problem messages
        val problem = if (v.hasIssues)
            v.problem
        else
            message.problem
        if (message.sum_z.re > v.sum_z.re || message.min_ir < v.min_ir || message.hasIssues || message.hasNonRadial)
            message.copy (problem = problem)
        else
            v
    }

    def sendMessage (triplet: EdgeTriplet[PowerFeedingNode, PreEdge]): Iterator[(VertexId, PowerFeedingNode)] =
    {
        val dst = triplet.dstAttr
        val src = triplet.srcAttr
        if ((null == src.source_obj) && (null == dst.source_obj))
        {
            Iterator.empty
        } else
            if (src.id == dst.prev_node || dst.id == src.prev_node)
            {
                Iterator.empty
            } else
                if (!triplet.attr.connected)
                {
                    Iterator.empty
                } else
                    if (src.source_obj != null && dst.source_obj == null)
                    {
                        sendMessageToNode (src, dst, triplet.dstId, triplet.attr)
                    } else
                        if (src.source_obj == null && dst.source_obj != null)
                        {
                            sendMessageToNode (dst, src, triplet.srcId, triplet.attr)
                        } else
                        {
                            sendMessageWithIssues (triplet)
                        }
    }

    private def sendMessageToNode (from: PowerFeedingNode, to: PowerFeedingNode, toId: VertexId, edge: PreEdge): Iterator[(VertexId, PowerFeedingNode)] =
    {
        val (dist_km, z, ir) = line_details (edge)
        val sum_z = from.sum_z + z * dist_km
        val min_ir = math.min (from.min_ir, ir)
        val feeder = if (null != to.feeder) to.feeder else from.feeder
        val problem =
            if (from.nominal_voltage < to.nominal_voltage && to.nominal_voltage <= 1000.0) // ToDo: don't hard code these values
                s"low voltage (${to.nominal_voltage}V:${from.nominal_voltage}V) subtransmission edge ${edge.id}"
            else
                if (from.hasIssues) from.problem
                else
                    if (to.hasIssues) to.problem
                    else
                        if ("" != edge.problem) edge.problem
                        else from.problem
        val message = PowerFeedingNode (to.id, from.id, null, to.nominal_voltage, from.source_obj, feeder, sum_z, min_ir, problem)
        if (log.isDebugEnabled)
            log.info ("%s --> %s".format (from.id, message.asString))
        Iterator ((toId, message))
    }

    private def sendMessageWithIssues (triplet: EdgeTriplet[PowerFeedingNode, PreEdge]): Iterator[(VertexId, PowerFeedingNode)] =
    {
        val src = triplet.srcAttr
        val dst = triplet.dstAttr
        if (triplet.srcAttr.hasNonRadial || triplet.dstAttr.hasNonRadial)
        {
            // at least one node is marked as non-radial, so nothing to do
            Iterator.empty
        } else
            if (src.hasIssues && dst.hasIssues)
            {
                // both of the nodes already have issues, so nothing we can do
                Iterator.empty
            } else
                if (!src.hasIssues && !dst.hasIssues)
                {
                    // send message(s) to mark at least one as non-radial
                    Iterator ((triplet.srcId, triplet.srcAttr.copy (prev_node = triplet.dstAttr.id, problem = "non-radial network")), (triplet.dstId, triplet.dstAttr.copy (prev_node = triplet.srcAttr.id, problem = "non-radial network")))
                } else
                    if (!src.hasIssues)
                    {
                        Iterator ((triplet.srcId, triplet.srcAttr.copy (prev_node = triplet.dstAttr.id, problem = "non-radial network")))
                    } else
                    {
                        Iterator ((triplet.dstId, triplet.dstAttr.copy (prev_node = triplet.srcAttr.id, problem = "non-radial network")))
                    }
    }

    def mergeMessage (a: PowerFeedingNode, b: PowerFeedingNode): PowerFeedingNode =
    {
        val node = a.copy (problem = if (a.hasIssues) a.problem else
            if (b.hasIssues) b.problem else "non-radial network")
        if (log.isDebugEnabled)
            log.debug ("merge %s & %s".format (a.asString, b.asString))
        node
    }

    def trace (initial: Graph[PreNode, PreEdge], starting_nodes: RDD[StartingTrafo], feeders: RDD[Feeder]): Graph[PowerFeedingNode, PreEdge] =
    {
        log.info ("trace")

        // create the initial Graph with PowerFeedingNode vertices
        def add_feeder (id: VertexId, v: PreNode, feeder: Option[Feeder]): PowerFeedingNode =
            PowerFeedingNode (v.id, null, null, v.nominal_voltage, null.asInstanceOf [StartingTrafo], feeder.orNull, Double.NegativeInfinity, Double.PositiveInfinity, v.problem)

        val pregraph = initial.outerJoinVertices (feeders.keyBy (_.vertex))(add_feeder)

        def starting_map (id: VertexId, v: PowerFeedingNode, trafo: Option[StartingTrafo]): PowerFeedingNode =
            if (trafo.isDefined)
                v.copy (source_obj = trafo.get, sum_z = 0.0)
            else
                v

        val graph = pregraph.outerJoinVertices (starting_nodes.keyBy (_.nsPin))(starting_map).persist (storage_level)

        // run Pregel
        val default_message = PowerFeedingNode (null, null, null, 0, null.asInstanceOf [StartingTrafo], null, Double.NegativeInfinity, Double.PositiveInfinity)
        graph.pregel[PowerFeedingNode](default_message, 10000, EdgeDirection.Either)(
            vertexProgram,
            sendMessage,
            mergeMessage
        )
    }

    def calc_max_feeding_power (options: EinspeiseleistungOptions)(a: (PowerFeedingNode, Option[(String, String)])): MaxPowerFeedingNodeEEA =
    {
        val (node, equipment) = a
        val (mrid, psrtype) = equipment.getOrElse ((null, null))
        val z = node.sum_z
        val v = node.nominal_voltage
        val min_ir = node.min_ir
        val trafo_id = node.source_obj.trafo_id
        val feeder_id = if (null != node.feeder) node.feeder.feeder_id else null
        val trafo_ratedS = node.source_obj.ratedS
        val base_ohms = v * v / trafo_ratedS
        val trafo_z = node.source_obj.z * base_ohms
        val z_summe = z + trafo_z
        val threshold = options.voltage_threshold / 100.0

        def get_heuristic_p_max (edge: PreEdge): Double =
        {
            val (dist_km, z_cable_per_km, ratedCurrent) = line_details (edge)
            val z_cable = (z_cable_per_km * dist_km).modulus
            val v_cable = z_summe.modulus * ratedCurrent
            val p_max_current = math.sqrt (3) * ratedCurrent * (v + v_cable)
            val limit = options.voltage_threshold / 100.0
            val i_cable = (v * limit) / z_cable
            val p_max_voltage = (v * (1.0 + limit)) * i_cable
            Math.min (p_max_current, p_max_voltage)
        }

        val solver = SmaxSolver (threshold, options.cosphi)
        val p_max_u = solver.solve (v, z_summe).modulus
        val p_max_i = math.sqrt (3) * min_ir * (v + z_summe.modulus * min_ir)

        val (p_max, reason, details) =
            if ("" != node.problem)
            {
                // ToDo: this sum is not quite correct, and only works for identical edges
                // Edges should be merged to get the parallel impedance before calculating the voltage drop
                // but the problem also exists in sendMessage() where each edge is handled separately
                // so the fix would be to merge edges before doing the trace.
                val p_max_heuristic = node.conn_edge.map (get_heuristic_p_max).sum
                if (p_max_heuristic < trafo_ratedS)
                    (p_max_heuristic, "heuristic limit", "limitation of last cable(s)")
                else
                    (trafo_ratedS, node.problem, "transformer limit")
            }
            else
                if ((trafo_ratedS < p_max_u) && (trafo_ratedS < p_max_i))
                    (trafo_ratedS, "transformer limit", "assuming no EEA")
                else
                    if (p_max_u < p_max_i)
                        (p_max_u, "voltage limit", "assuming no EEA")
                    else
                        (p_max_i, "current limit", "assuming no EEA")

        MaxPowerFeedingNodeEEA (node.id, node.nominal_voltage, mrid, psrtype, trafo_id, feeder_id, p_max, null, reason, details)
    }

    def has (string: String): String =
    {
        string.substring (0, string.indexOf ("_"))
    }

    def get_threshold_per_has (nodes: RDD[PowerFeedingNode], options: EinspeiseleistungOptions): RDD[MaxPowerFeedingNodeEEA] =
    {
        val houses = nodes.filter (_.sum_z.re > 0.0)
        val psrtype = get [Terminal].keyBy (_.ConductingEquipment).groupByKey.join (get [ConductingEquipment].keyBy (_.id))
            .filter (_._2._1.size == 1).map (x => (x._2._1.head.TopologicalNode, (x._2._2.id, x._2._2.Equipment.PowerSystemResource.PSRType)))
        houses.keyBy (_.id).leftOuterJoin (psrtype).values.map (calc_max_feeding_power (options))
    }

    def trafo_mapping (island: TransformerIsland): Iterable[StartingTrafo] =
    {
        val pn = PreNode ("", 0.0, null)
        val ratedS = island.power_rating
        island.transformers.flatMap (
            transformers =>
            {
                val v0 = pn.vertex_id (transformers.node0)
                val impedance = transformers.total_impedance_per_unit._1
                for (end <- transformers.transformers (0).ends
                     if end.TransformerEnd.endNumber > 1)
                    yield
                        {
                            val number = end.TransformerEnd.endNumber - 1
                            val v1 = pn.vertex_id (transformers.transformers (0).terminals (number).TopologicalNode)
                            StartingTrafo (v0, v1, transformers.transformer_name, impedance, ratedS)
                        }
            }
        )
    }

    def threshold_calculation (initial: Graph[PreNode, PreEdge], sdata: RDD[(String, Iterable[PV])], transformers: RDD[TransformerIsland], options: EinspeiseleistungOptions): PreCalculationResults =
    {
        val feeders = new Feeders (session, storage_level)
        val graph = trace (initial, transformers.flatMap (trafo_mapping), feeders.getFeeders ())

        // raw nodes
        val nodes: VertexRDD[PowerFeedingNode] = graph.vertices.filter (_._2.source_obj != null)
        // get all edges per node
        val src_edges = graph.edges.map (e => (e.srcId, e.attr))
        val dst_edges = graph.edges.map (e => (e.dstId, e.attr))
        val union_edges = src_edges.union (dst_edges).groupByKey ()
        // get connected edge per node
        val grouped_nodes = nodes.leftOuterJoin (union_edges).values
        val nodes_with_edge = grouped_nodes.map (n =>
        {
            n._2 match
            {
                case Some (it) => n._1.copy (conn_edge = it.toArray)
                case None => n._1
            }
        })

        // find which ones detected they were in a non-radial network
        val nonradial = nodes.values.filter (_.hasNonRadial)
        // transformers with non-radial networks
        val bad_tx = nonradial.map (_.source_obj).distinct.collect
        // replace problem message in the nodes in those trafokreise (this overwrites any other problems so it's not perfect):
        val new_nodes = nodes_with_edge.map (x => if (bad_tx.contains (x.source_obj)) x.copy (problem = "non-radial network") else x)
        // then get the threshold:
        val house_nodes = get_threshold_per_has (new_nodes, options)
        val traced_house_nodes_EEA: RDD[(MaxPowerFeedingNodeEEA, Option[Iterable[PV]])] = house_nodes.keyBy (_.id_seq).leftOuterJoin (sdata).values

        // prioritize unhandled issues over a non-radial network problem
        def pickWorst (pfn: Iterable[PowerFeedingNode]): String =
        {
            val issues = pfn.filter (_.hasIssues)
            if (issues.nonEmpty)
                issues.head.problem
            else
                pfn.head.problem
        }

        // update each element in the transformer service area with bad value
        val problem_trafos = graph.vertices.values.filter (x => x.source_obj != null && (x.hasIssues || x.hasNonRadial)).keyBy (_.source_obj).groupByKey.map (x => (x._1.trafo_id, pickWorst (x._2)))
        val has = traced_house_nodes_EEA.map (
            node =>
            {
                node._2 match
                {
                    case Some (eea) =>
                        node._1.copy (eea = eea)
                    case None =>
                        node._1
                }
            }
        )
            .keyBy (_.source_obj).leftOuterJoin (problem_trafos).values.map (
            arg =>
            {
                arg._2 match
                {
                    case Some (problem) => arg._1.copy (reason = problem, details = null)
                    case None => arg._1
                }
            }
        )
            .persist (storage_level)

        val simulation = Database.store_precalculation ("Threshold Precalculation", options.outputfile)(has.filter (_.mrid != null))
        log.info ("the simulation number is " + simulation)

        def mapGraphEdges (triplet: EdgeTriplet[PowerFeedingNode, PreEdge]): (String, PreEdge) =
        {
            val source = triplet.srcAttr.source_obj
            val target = triplet.dstAttr.source_obj

            if (source != null && target != null && source.trafo_id != null && target.trafo_id != null)
                if (triplet.attr.connected)
                    (source.trafo_id, triplet.attr)
                else
                    (null.asInstanceOf [String], triplet.attr)
            else
                (null.asInstanceOf [String], triplet.attr)
        }

        val vertices = graph.vertices.values
        val edges = graph.triplets.map (mapGraphEdges)

        vertices.persist (storage_level)
        edges.persist (storage_level)
        if (session.sparkContext.getCheckpointDir.isDefined)
        {
            has.checkpoint ()
            vertices.checkpoint ()
            edges.checkpoint ()
        }

        PreCalculationResults (simulation, has, vertices, edges)
    }
}
