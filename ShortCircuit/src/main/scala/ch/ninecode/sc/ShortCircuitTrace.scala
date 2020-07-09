package ch.ninecode.sc

import ch.ninecode.cim.CIMRDD
import ch.ninecode.model._
import org.apache.spark.graphx.EdgeDirection
import org.apache.spark.graphx.EdgeTriplet
import org.apache.spark.graphx.Graph
import org.apache.spark.graphx.VertexId
import org.apache.spark.sql.SparkSession
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Short circuit calculation tracing.
 * Uses GraphX to trace the topology and generate the short circuit results at each node.
 *
 * @param session the Spark session
 * @param options options for short-circuit processing
 */
case class ShortCircuitTrace (session: SparkSession, options: ShortCircuitOptions) extends CIMRDD with Serializable
{
    implicit val spark: SparkSession = session
    implicit val log: Logger = LoggerFactory.getLogger (getClass)

    def trace (initial: Graph[ScNode, ScEdge]): Graph[ScNode, ScEdge] =
    {
        log.info ("tracing")
        initial.pregel (ScMessage (null, null, null, null, null, null, null), 10000, EdgeDirection.Either)(vprog, sendMessage, mergeMessage)
    }

    // do the Pregel algorithm
    def vprog (id: VertexId, v: ScNode, message: ScMessage): ScNode =
    {
        if (null == message.source_id) // handle the initial message by keeping the same vertex node
        v
        else
        {
            val errors = ScError.combine_errors (v.errors, message.errors, options.messagemax)
            val z = if ((null != message.ref) && (null != message.edge)) message.ref + message.edge else v.impedance
            val branches = if (null != message.fuses) message.fuses else v.branches
            v.copy (source_id = message.source_id, source_impedance = message.source_impedance, id_prev = message.previous_node, impedance = z, branches = branches, errors = errors)
        }
    }

    def mergeMessage (a: ScMessage, b: ScMessage): ScMessage =
    {
        if (a.previous_node != b.previous_node)
        {
            val error = List (ScError (fatal = true, invalid = true, "non-radial network detected from %s to %s".format (a.previous_node, b.previous_node)))
            log.error (error.head.message)
            a.copy (errors = ScError.combine_errors (a.errors, ScError.combine_errors (b.errors, error, options.messagemax), options.messagemax))
        }
        else
        {
            val parallel =
                if ((null != a.edge) && (null != b.edge))
                    a.edge.parallel (b.edge)
                else
                    if (null != a.edge)
                        a.edge
                    else
                        b.edge
            val warning = ScError (fatal = false, invalid = false, "reinforcement detected from %s".format (a.previous_node))
            a.copy (edge = parallel, errors = ScError.combine_errors (a.errors, ScError.combine_errors (b.errors, List (warning), options.messagemax), options.messagemax))
        }
    }

    def handleMesh (triplet: EdgeTriplet[ScNode, ScEdge]): Iterator[(VertexId, ScMessage)] =
    {
        val src = triplet.srcAttr
        val dst = triplet.dstAttr
        val edge = triplet.attr
        if ((src.id_prev == dst.id_seq) || (dst.id_prev == src.id_seq)) // reinforcement
            Iterator.empty
        else if (!edge.shouldContinueTo (dst, options.calculate_public_lighting)) // boundary switch ?
            Iterator.empty
        else
        {
            // check if the non-null impedance difference matches what we expect for this cable
            edge.element match
            {
                case _: ACLineSegment ⇒
                    val diff = src.impedance - dst.impedance
                    val expected = edge.impedanceTo ("not important")
                    val isequal = Math.abs (!diff.impedanz_low - !expected.impedanz_low) < 1e-6 && Math.abs (!diff.null_impedanz_low - !expected.null_impedanz_low) < 1e-6
                    if (isequal)
                        Iterator.empty
                    else
                    {
                        val error = ScError (fatal = true, invalid = true, s"non-radial network detected through ${edge.id_equ}")
                        log.error (error.message)
                        if (!src.fatalErrors && !dst.fatalErrors)
                            // neither node has a fatal error yet, send a message to both to mark them with a fatal error
                            Iterator (
                                (triplet.dstId, ScMessage (dst.source_id, dst.source_impedance, null, null, src.branches, src.id_seq, List (error))),
                                (triplet.srcId, ScMessage (src.source_id, dst.source_impedance, null, null, src.branches, dst.id_seq, List (error)))
                            )
                        else
                            Iterator.empty
                    }
                case _: PowerTransformer =>
                    val diff = src.impedance - dst.impedance
                    val expected = edge.impedanceTo (dst.id_seq)
                    val isequal = Math.abs (!diff.impedanz_low - !expected.impedanz_low) < 1e-6 && Math.abs (!diff.null_impedanz_low - !expected.null_impedanz_low) < 1e-6
                    if (isequal)
                        Iterator.empty
                    else
                    {
                        val error = ScError (fatal = true, invalid = true, s"non-radial network detected through ${edge.id_equ}")
                        log.error (error.message)
                        if (!src.fatalErrors && !dst.fatalErrors)
                            // neither node has a fatal error yet, send a message to both to mark them with a fatal error
                            Iterator (
                                (triplet.dstId, ScMessage (dst.source_id, dst.source_impedance, null, null, src.branches, src.id_seq, List (error))),
                                (triplet.srcId, ScMessage (src.source_id, dst.source_impedance, null, null, src.branches, dst.id_seq, List (error)))
                            )
                        else
                            Iterator.empty
                    }
                case _: Switch | _: Fuse =>
                    val isequal = Math.abs (!src.impedance.impedanz_low - !dst.impedance.impedanz_low) < 1e-6 && Math.abs (!src.impedance.null_impedanz_low - !dst.impedance.null_impedanz_low) < 1e-6
                    if (isequal)
                        Iterator.empty
                    else
                    {
                        val error = ScError (fatal = true, invalid = true, s"non-radial network detected through ${edge.id_equ}")
                        log.error (error.message)
                        if (!src.fatalErrors && !dst.fatalErrors)
                            // neither node has a fatal error yet, send a message to both to mark them with a fatal error
                            Iterator (
                                (triplet.dstId, ScMessage (dst.source_id, dst.source_impedance, null, null, src.branches, src.id_seq, List (error))),
                                (triplet.srcId, ScMessage (src.source_id, dst.source_impedance, null, null, src.branches, dst.id_seq, List (error)))
                            )
                        else
                            Iterator.empty
                    }
                case _ ⇒
                    Iterator.empty
            }
        }
    }

    def sendMessage (triplet: EdgeTriplet[ScNode, ScEdge]): Iterator[(VertexId, ScMessage)] =
    {
        val x =
            if (triplet.srcAttr.impedance != null && triplet.dstAttr.impedance != null)
                handleMesh (triplet)
            else
                if (triplet.srcAttr.impedance != null && triplet.dstAttr.impedance == null)
                    if (triplet.attr.shouldContinueTo (triplet.dstAttr, options.calculate_public_lighting))
                    {
                        val from = triplet.attr.impedanceFrom (triplet.dstAttr.id_seq, triplet.srcAttr.impedance)
                        val to = triplet.attr.impedanceTo (triplet.dstAttr.id_seq)
                        val branches = triplet.attr.fusesTo (triplet.srcAttr.branches)
                        val errors = triplet.attr.hasIssues (triplet.srcAttr.errors, options)
                        val message = ScMessage (triplet.srcAttr.source_id, triplet.srcAttr.source_impedance, from, to, branches, triplet.srcAttr.id_seq, errors)
                        if (log.isDebugEnabled)
                            log.debug ("%s <-- %s".format (triplet.dstAttr.id_seq, message.asString))
                        Iterator ((triplet.dstId, message))
                    }
                    else
                        Iterator.empty
                else
                    if (triplet.dstAttr.impedance != null && triplet.srcAttr.impedance == null)
                        if (triplet.attr.shouldContinueTo (triplet.srcAttr, options.calculate_public_lighting))
                        {
                            val from = triplet.attr.impedanceFrom (triplet.srcAttr.id_seq, triplet.dstAttr.impedance)
                            val to = triplet.attr.impedanceTo (triplet.srcAttr.id_seq)
                            val branches = triplet.attr.fusesTo (triplet.dstAttr.branches)
                            val errors = triplet.attr.hasIssues (triplet.dstAttr.errors, options)
                            val message = ScMessage (triplet.dstAttr.source_id, triplet.dstAttr.source_impedance, from, to, branches, triplet.dstAttr.id_seq, errors)
                            if (log.isDebugEnabled)
                                log.debug ("%s <-- %s".format (triplet.srcAttr.id_seq, message.asString))
                            Iterator ((triplet.srcId, message))
                        }
                        else
                            Iterator.empty
                    else
                        Iterator.empty
        x
    }
}