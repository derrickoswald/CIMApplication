package ch.ninecode.sc

import org.apache.spark.graphx.EdgeDirection
import org.apache.spark.graphx.EdgeTriplet
import org.apache.spark.graphx.Graph
import org.apache.spark.graphx.VertexId
import org.apache.spark.sql.SparkSession
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.cim.CIMRDD
import ch.ninecode.model.ACLineSegment
import ch.ninecode.model.Fuse
import ch.ninecode.model.PowerTransformer
import ch.ninecode.model.Switch

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
    implicit val log: Logger = LoggerFactory.getLogger(getClass)

    def trace (initial: Graph[ScNode, ScEdge]): Graph[ScNode, ScEdge] =
    {
        log.info("tracing")
        initial.pregel(ScMessage(), 10000, EdgeDirection.Either)(vprog, sendMessage, mergeMessage)
    }

    // do the Pregel algorithm
    def vprog (id: VertexId, v: ScNode, message: ScMessage): ScNode =
    {
        if (null == message.source_id) // handle the initial message by keeping the same vertex node
            v
        else
        {
            val errors = ScError.combine_errors(v.errors, message.errors, options.messagemax)
            val z = if ((null != message.ref) && (null != message.edge)) message.ref + message.edge else v.impedance
            val branches = if (null != message.fuses) message.fuses else v.branches
            v.copy(source_id = message.source_id, id_prev = message.previous_node, impedance = z, branches = branches, errors = errors)
        }
    }

    def mergeMessage (a: ScMessage, b: ScMessage): ScMessage =
    {
        val text = s"non-radial network detected from ${a.previous_node} to ${b.previous_node}"
        log.info(text)
        val error = ScError(fatal = true, invalid = true, text)
        val error1 = ScError.combine_errors(b.errors, List(error), options.messagemax)
        val errors = ScError.combine_errors(a.errors, error1, options.messagemax)
        a.copy(errors = errors)
    }

    def handleMesh (triplet: EdgeTriplet[ScNode, ScEdge]): Iterator[(VertexId, ScMessage)] =
    {
        val src = triplet.srcAttr
        val dst = triplet.dstAttr
        val edge = triplet.attr
        if ((src.id_prev == dst.id_seq) || (dst.id_prev == src.id_seq)) // reinforcement
            Iterator.empty
        else
            if (!edge.shouldContinueTo(dst, options.calculate_public_lighting)) // boundary switch ?
                Iterator.empty
            else
            {
                // check if the non-null impedance difference matches what we expect for this cable
                edge.element match
                {
                    case _: ACLineSegment =>
                        val diff = src.impedance - dst.impedance
                        val expected = edge.impedanceTo("not important")
                        val isequal = Math.abs(!diff.impedanz_low - !expected.impedanz_low) < 1e-6 && Math.abs(!diff.null_impedanz_low - !expected.null_impedanz_low) < 1e-6
                        if (isequal)
                            Iterator.empty
                        else
                        {
                            val error = ScError(fatal = true, invalid = true, s"non-radial network detected through ${edge.id_equ}")
                            log.info(error.message)
                            if (!src.fatalErrors && !dst.fatalErrors)
                            // neither node has a fatal error yet, send a message to both to mark them with a fatal error
                                Iterator(
                                    (triplet.dstId, ScMessage(source_id = dst.source_id, fuses = src.branches, previous_node = src.id_seq, errors = List(error))),
                                    (triplet.srcId, ScMessage(source_id = src.source_id, fuses = src.branches, previous_node = dst.id_seq, errors = List(error)))
                                )
                            else
                                Iterator.empty
                        }
                    case _: PowerTransformer =>
                        val diff = src.impedance - dst.impedance
                        val expected = edge.impedanceTo(dst.id_seq)
                        val isequal = Math.abs(!diff.impedanz_low - !expected.impedanz_low) < 1e-6 && Math.abs(!diff.null_impedanz_low - !expected.null_impedanz_low) < 1e-6
                        if (isequal)
                            Iterator.empty
                        else
                        {
                            val error = ScError(fatal = true, invalid = true, s"non-radial network detected through ${edge.id_equ}")
                            log.info(error.message)
                            if (!src.fatalErrors && !dst.fatalErrors)
                            // neither node has a fatal error yet, send a message to both to mark them with a fatal error
                                Iterator(
                                    (triplet.dstId, ScMessage(source_id = dst.source_id, fuses = src.branches, previous_node = src.id_seq, errors = List(error))),
                                    (triplet.srcId, ScMessage(source_id = src.source_id, fuses = src.branches, previous_node = dst.id_seq, errors = List(error)))
                                )
                            else
                                Iterator.empty
                        }
                    case _: Switch | _: Fuse =>
                        val isequal = Math.abs(!src.impedance.impedanz_low - !dst.impedance.impedanz_low) < 1e-6 && Math.abs(!src.impedance.null_impedanz_low - !dst.impedance.null_impedanz_low) < 1e-6
                        if (isequal)
                            Iterator.empty
                        else
                        {
                            val error = ScError(fatal = true, invalid = true, s"non-radial network detected through ${edge.id_equ}")
                            log.info(error.message)
                            if (!src.fatalErrors && !dst.fatalErrors)
                            // neither node has a fatal error yet, send a message to both to mark them with a fatal error
                                Iterator(
                                    (triplet.dstId, ScMessage(source_id = dst.source_id, fuses = src.branches, previous_node = src.id_seq, errors = List(error))),
                                    (triplet.srcId, ScMessage(source_id = src.source_id, fuses = src.branches, previous_node = dst.id_seq, errors = List(error)))
                                )
                            else
                                Iterator.empty
                        }
                    case _ =>
                        Iterator.empty
                }
            }
    }

    def sendMessage (triplet: EdgeTriplet[ScNode, ScEdge]): Iterator[(VertexId, ScMessage)] =
    {
        val x =
            if (triplet.srcAttr.impedance != null && triplet.dstAttr.impedance != null)
                handleMesh(triplet)
            else
                if (triplet.srcAttr.impedance != null && triplet.dstAttr.impedance == null)
                    if (triplet.attr.shouldContinueTo(triplet.dstAttr, options.calculate_public_lighting))
                    {
                        val from = triplet.attr.impedanceFrom(triplet.dstAttr.id_seq, triplet.srcAttr.impedance)
                        val to = triplet.attr.impedanceTo(triplet.dstAttr.id_seq)
                        val branches = triplet.attr.fusesTo(triplet.srcAttr.branches, triplet.srcAttr.id_seq, options)
                        val errors = triplet.attr.hasIssues(triplet.srcAttr.errors, options)
                        val message = ScMessage(triplet.srcAttr.source_id, from, to, branches, triplet.srcAttr.id_seq, errors)
                        if (log.isDebugEnabled)
                            log.debug("%s <-- %s".format(triplet.dstAttr.id_seq, message.asString))
                        Iterator((triplet.dstId, message))
                    }
                    else
                        Iterator.empty
                else
                    if (triplet.dstAttr.impedance != null && triplet.srcAttr.impedance == null)
                        if (triplet.attr.shouldContinueTo(triplet.srcAttr, options.calculate_public_lighting))
                        {
                            val from = triplet.attr.impedanceFrom(triplet.srcAttr.id_seq, triplet.dstAttr.impedance)
                            val to = triplet.attr.impedanceTo(triplet.srcAttr.id_seq)
                            val branches = triplet.attr.fusesTo(triplet.dstAttr.branches, triplet.dstAttr.id_seq, options)
                            val errors = triplet.attr.hasIssues(triplet.dstAttr.errors, options)
                            val message = ScMessage(triplet.dstAttr.source_id, from, to, branches, triplet.dstAttr.id_seq, errors)
                            if (log.isDebugEnabled)
                                log.debug("%s <-- %s".format(triplet.srcAttr.id_seq, message.asString))
                            Iterator((triplet.srcId, message))
                        }
                        else
                            Iterator.empty
                    else
                        Iterator.empty
        x
    }
}