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
import ch.ninecode.gl.Complex
import ch.ninecode.gl.PV
import ch.ninecode.gl.PreEdge
import ch.ninecode.gl.PreNode
import ch.ninecode.gl.TransformerSet
import ch.ninecode.model.ACLineSegment
import ch.ninecode.model.BaseVoltage
import ch.ninecode.model.ConductingEquipment
import ch.ninecode.model.Connector
import ch.ninecode.model.Element
import ch.ninecode.model.Terminal

class PowerFeeding (session: SparkSession, storage_level: StorageLevel = StorageLevel.MEMORY_AND_DISK_SER) extends CIMRDD with Serializable
{
    implicit val spark: SparkSession = session
    implicit val log: Logger = LoggerFactory.getLogger (getClass)

    // return length, resistance and maximum current for an edge
    def line_details (edge: PreEdge): (Double, Complex, Double) =
    {
        edge.element match
        {
            case line: ACLineSegment ⇒ (line.Conductor.len / 1000.0, Complex (line.r, line.x), edge.ratedCurrent)
            case _ ⇒ (0.0, 0.0, Double.PositiveInfinity)
        }
    }

    def vertexProgram (id: VertexId, v: PowerFeedingNode, message: PowerFeedingNode): PowerFeedingNode =
    {
        if (message.sum_z.re > v.sum_z.re || message.min_ir < v.min_ir || message.hasIssues) message else v
    }

    def sendMessage (triplet: EdgeTriplet[PowerFeedingNode, PreEdge]): Iterator[(VertexId, PowerFeedingNode)] =
    {
        if ((null != triplet.srcAttr.source_obj) || (null != triplet.dstAttr.source_obj))
            if (triplet.attr.connected)
                if (triplet.srcAttr.source_obj != null && triplet.dstAttr.source_obj == null)
                {
                    val (dist_km, z, ir) = line_details (triplet.attr)
                    val sum_z = triplet.srcAttr.sum_z + z * dist_km
                    val min_ir = math.min (triplet.srcAttr.min_ir, ir)
                    val feeder = if (null != triplet.dstAttr.feeder) triplet.dstAttr.feeder else triplet.srcAttr.feeder
                    val problem = if (triplet.srcAttr.hasIssues) triplet.srcAttr.problem else if (triplet.dstAttr.hasIssues) triplet.dstAttr.problem else if (null != triplet.attr.problem) triplet.attr.problem else triplet.srcAttr.problem
                    val message = PowerFeedingNode (triplet.dstAttr.id, triplet.dstAttr.nominal_voltage, triplet.srcAttr.source_obj, feeder, sum_z, min_ir, problem)
                    if (log.isDebugEnabled)
                        log.debug ("%s <-- %s".format (triplet.dstId.toString, message.asString))
                    Iterator ((triplet.dstId, message))
                }
                else
                    if (triplet.srcAttr.source_obj == null && triplet.dstAttr.source_obj != null)
                    {
                        val (dist_km, z, ir) = line_details (triplet.attr)
                        val sum_z = triplet.dstAttr.sum_z + z * dist_km
                        val min_ir = math.min (triplet.dstAttr.min_ir, ir)
                        val feeder = if (null != triplet.srcAttr.feeder) triplet.srcAttr.feeder else triplet.dstAttr.feeder
                        val problem = if (triplet.dstAttr.hasIssues) triplet.dstAttr.problem else if (triplet.srcAttr.hasIssues) triplet.srcAttr.problem else if (null != triplet.attr.problem) triplet.attr.problem else triplet.dstAttr.problem
                        val message = PowerFeedingNode (triplet.srcAttr.id, triplet.srcAttr.nominal_voltage, triplet.dstAttr.source_obj, feeder, sum_z, min_ir, problem)
                        if (log.isDebugEnabled)
                            log.debug ("%s <-- %s".format (triplet.srcId.toString, message.asString))
                        Iterator ((triplet.srcId, message))
                    }
                    else
                        if (triplet.srcAttr.source_obj != triplet.dstAttr.source_obj)
                        {
                            if (triplet.srcAttr.hasIssues && !triplet.dstAttr.hasIssues)
                                Iterator ((triplet.dstId, triplet.dstAttr.copy (problem = triplet.srcAttr.problem)))
                            else if (!triplet.srcAttr.hasIssues && triplet.dstAttr.hasIssues)
                                Iterator ((triplet.srcId, triplet.srcAttr.copy (problem = triplet.dstAttr.problem)))
                            else
                                Iterator.empty
                        }
                        else
                            Iterator.empty
            else
                Iterator.empty
        else
            Iterator.empty
    }

    def mergeMessage (a: PowerFeedingNode, b: PowerFeedingNode): PowerFeedingNode =
    {
        val node = a.copy (problem = if (a.hasIssues) a.problem else if (b.hasIssues) b.problem else "non-radial network")
        if (log.isDebugEnabled)
            log.debug ("merge %s & %s".format (a.asString, b.asString))
        node
    }

    def trace (initial: Graph[PreNode, PreEdge], starting_nodes: RDD[StartingTrafo], feeders: RDD[Feeder]): Graph[PowerFeedingNode, PreEdge] =
    {
        log.info ("trace")

        // create the initial Graph with PowerFeedingNode vertices
        def add_feeder (id: VertexId, v: PreNode, feeder: Option[Feeder]): PowerFeedingNode =
            PowerFeedingNode (v.id, v.nominal_voltage, null.asInstanceOf [StartingTrafo], feeder.orNull, Double.NegativeInfinity, Double.PositiveInfinity, v.problem)

        val pregraph = initial.outerJoinVertices (feeders.keyBy (_.node))(add_feeder)

        def starting_map (id: VertexId, v: PowerFeedingNode, trafo: Option[StartingTrafo]): PowerFeedingNode =
            if (trafo.isDefined)
                v.copy (source_obj = trafo.get, sum_z = 0.0)
            else
                v

        val graph = pregraph.outerJoinVertices (starting_nodes.keyBy (_.nsPin))(starting_map).persist (storage_level)

        // run Pregel
        val default_message = PowerFeedingNode (null, 0, null.asInstanceOf [StartingTrafo], null, Double.NegativeInfinity, Double.PositiveInfinity, null)
        graph.pregel [PowerFeedingNode](default_message, 10000, EdgeDirection.Either)(
            vertexProgram,
            sendMessage,
            mergeMessage
        )
    }

    def calc_max_feeding_power (options: EinspeiseleistungOptions) (args: (PowerFeedingNode, Option[(String, String)])): MaxPowerFeedingNodeEEA =
    {
        val node: PowerFeedingNode = args._1
        val mrid = args._2.map (_._1).orNull
        val psrtype = args._2.map (_._2).orNull
        val z = node.sum_z
        val v = node.nominal_voltage
        val min_ir = node.min_ir
        val trafo_id = node.source_obj.trafo_id
        val feeder_id = if (null != node.feeder) node.feeder.feeder_id else null
        val trafo_ratedS = node.source_obj.ratedS
        val trafo_z = node.source_obj.z.re
        val z_summe = math.sqrt (3) * z + trafo_z
        val threshold = options.voltage_threshold / 100.0

        val solver = SmaxSolver (threshold, options.cosphi)
        val p_max_u = solver.solve (v, z_summe).modulus
        val p_max_i = math.sqrt (3) * min_ir * (v + z_summe.modulus * min_ir)
        val (p_max, reason, details) =
            if (null != node.problem)
                (trafo_ratedS, node.problem, null)
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
            .filter (_._2._1.size == 1).map (x ⇒ (x._2._1.head.TopologicalNode, (x._2._2.id, x._2._2.Equipment.PowerSystemResource.PSRType)))
        houses.keyBy (_.id).leftOuterJoin (psrtype).values.map (calc_max_feeding_power (options))
    }

    def trafo_mapping (transformers: TransformerSet): StartingTrafo =
    {
        val pn = PreNode ("", 0.0, null)
        val v0 = pn.vertex_id (transformers.node0)
        val v1 = pn.vertex_id (transformers.node1)
        val ratedS = transformers.power_rating
        val impedance = transformers.total_impedance_per_unit._1
        StartingTrafo (v0, v1, transformers.transformer_name, impedance, ratedS)
    }

    /**
     * The RDD of feeder objects.
     *
     * @return The RDD of feeders.
     */
    def feeders: RDD[Feeder] =
    {
        // get the list of N7 voltages and allowed power system resource types
        // ToDo: fix this 1000V multiplier
        val low_voltages = getOrElse [BaseVoltage].filter (x ⇒ x.nominalVoltage <= 1.0).map (_.id).collect
        val allowed_PSRTypes = Array ("PSRType_Substation", "PSRType_TransformerStation")

        def isFeeder (element: Element): Boolean =
            element match
            {
                case c: Connector ⇒
                    low_voltages.contains (c.ConductingEquipment.BaseVoltage) &&
                        allowed_PSRTypes.contains (c.ConductingEquipment.Equipment.PowerSystemResource.PSRType)
                case _ ⇒ false
            }

        // get the list of M7 level feeders in substations
        val pn = PreNode ("", 0.0, null)
        val ret = getOrElse [Element]("Elements").keyBy (_.id).join (getOrElse [Terminal].keyBy (_.ConductingEquipment)).values
            .flatMap (a ⇒ if (isFeeder (a._1)) List (Feeder (pn.vertex_id (a._2.TopologicalNode), a._1.id)) else List ())

        ret.persist (storage_level)
        ret.name = "Feeders"
        ret
    }

    def threshold_calculation (initial: Graph[PreNode, PreEdge], sdata: RDD[(String, Iterable[PV])], transformers: RDD[TransformerSet], options: EinspeiseleistungOptions): PreCalculationResults =
    {

        val graph = trace (initial, transformers.map (trafo_mapping), feeders)
        val house_nodes = get_threshold_per_has (graph.vertices.values.filter (_.source_obj != null), options)
        val traced_house_nodes_EEA = house_nodes.keyBy (_.id_seq).leftOuterJoin (sdata).values

        // update each element in the transform er service area with bad value (just choose the first)
        val problem_trafos = graph.vertices.values.filter (x ⇒ x.source_obj != null && x.hasIssues).keyBy (_.source_obj).groupByKey.map (x ⇒ (x._1.trafo_id, x._2.head.problem))
        val has = traced_house_nodes_EEA.map (
            node =>
            {
                node._2 match
                {
                    case Some (eea) ⇒
                        node._1.copy (eea = eea)
                    case None ⇒
                        node._1
                }
            }
        )
        .keyBy (_.source_obj).leftOuterJoin (problem_trafos).values.map (
            arg ⇒
            {
                arg._2 match
                {
                    case Some (problem) ⇒ arg._1.copy (max_power_feeding = 0.0, reason = problem, details = null)
                    case None ⇒ arg._1
                }
            }
        )
        .persist (storage_level)

        val simulation = Database.store_precalculation ("Threshold Precalculation", Calendar.getInstance ())(has)
        log.info ("the simulation number is " + simulation)

        def mapGraphEdges (triplet: EdgeTriplet[PowerFeedingNode, PreEdge]): (String, PreEdge) =
        {
            val source = triplet.srcAttr.source_obj
            val target = triplet.dstAttr.source_obj

            var ret = (null.asInstanceOf [String], triplet.attr)
            if (source != null && target != null && source.trafo_id != null && target.trafo_id != null)
            {
                val source_trafo_id = source.trafo_id
                val target_trafo_id = target.trafo_id
                if (source_trafo_id == target_trafo_id)
                    ret = (source_trafo_id, triplet.attr)
            }
            ret
        }

        val vertices = graph.vertices.values
        val edges = graph.triplets.map (mapGraphEdges)

        vertices.persist (storage_level)
        edges.persist (storage_level)
        if (session.sparkContext.getCheckpointDir.isDefined)
        {
            has.checkpoint (); vertices.checkpoint (); edges.checkpoint ()
        }

        PreCalculationResults (simulation, has, vertices, edges)
    }
}
