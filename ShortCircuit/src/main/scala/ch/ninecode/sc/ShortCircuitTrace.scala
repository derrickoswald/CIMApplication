package ch.ninecode.sc

import scala.util.control.Breaks._
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import org.apache.spark.graphx.EdgeDirection
import org.apache.spark.graphx.EdgeTriplet
import org.apache.spark.graphx.Graph
import org.apache.spark.graphx.VertexId
import org.apache.spark.sql.SparkSession

import ch.ninecode.cim.CIMRDD
import ch.ninecode.model._

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
        initial.pregel (ScMessage (null, null, null, null, null, null), 10000, EdgeDirection.Either) (vprog, sendMessage, mergeMessage)
    }
        
    // do the Pregel algorithm
    def vprog (id: VertexId, v: ScNode, message: ScMessage): ScNode =
    {
        if (null == message.source) // handle the initial message by keeping the same vertex node
            v
        else
        {
            val errors = ScError.combine_errors (v.errors, message.errors, options.messagemax)
            val z = if ((null != message.ref) && (null != message.edge)) message.ref + message.edge else v.impedance
            val fuses = if (null != message.fuses) message.fuses else v.fuses
            v.copy (source = message.source, id_prev = message.previous_node, impedance = z, fuses = fuses, errors = errors)
        }
    }
    
    def mergeMessage (a: ScMessage, b: ScMessage): ScMessage =
    {
        if (a.previous_node != b.previous_node)
        {
            var error = List(ScError (true, true, "non-radial network detected from %s to %s".format (a.previous_node, b.previous_node)))
            log.error (error.head.message)
            var fuses: List[List[(String, Double)]] = null
            if (a.fuses == b.fuses)
                fuses = a.fuses
            else if ((null != a.fuses) && (null != b.fuses) && a.fuses.dropRight(1) == b.fuses.dropRight(1))
            {
                val comb_fuse = a.fuses.last ::: b.fuses.last
                fuses = a.fuses.dropRight(1) :+ comb_fuse.distinct
            }
            else 
            {
                // TODO
                val fuse_error = List(ScError (true, true, "different fuse paths detected from %s to %s".format (a.previous_node, b.previous_node)))
                log.error (fuse_error.head.message)
                error = ScError.combine_errors(error, fuse_error, options.messagemax)
            }
            a.copy (fuses = fuses, errors = ScError.combine_errors (a.errors, ScError.combine_errors (b.errors, error, options.messagemax), options.messagemax))
        }
        else
        {
            val parallel =
                if ((null != a.edge) && (null != b.edge))
                    a.edge.parallel (b.edge)
                else if (null != a.edge)
                    a.edge
                else
                    b.edge
            val warning = ScError (false, false, "reinforcement detected from %s".format (a.previous_node))
            a.copy (edge = parallel, errors = ScError.combine_errors (a.errors, ScError.combine_errors (b.errors, List (warning), options.messagemax), options.messagemax))
        }
    }
        
    def propagateFusesOnly (triplet: EdgeTriplet[ScNode, ScEdge]): Iterator[(VertexId, ScMessage)] = 
    {
        val src = triplet.srcAttr
        val dst = triplet.dstAttr
        if (triplet.attr.element.isInstanceOf[Fuse])
        {
            if (triplet.attr.shouldContinueTo (triplet.dstAttr))
            {
                if (src.fuses != null && src.fuses.last.map(_._1).contains(triplet.attr.element.id))
                {
                    if (src.fuses.dropRight(1) == dst.fuses || (dst.fuses == null && src.fuses.dropRight(1).isEmpty))
                        Iterator.empty
                    else 
                    {
                        val fuses = triplet.attr.fusesTo (dst.fuses)
                        Iterator((triplet.srcId, ScMessage (dst.source, null, null, fuses, dst.id_seq, dst.errors)))
                    }
                }
                else if (dst.fuses != null && dst.fuses.last.map(_._1).contains(triplet.attr.element.id))
                {
                    if (dst.fuses.dropRight(1) == src.fuses || (src.fuses == null && dst.fuses.dropRight(1).isEmpty))
                        Iterator.empty
                    else 
                    {
                        val fuses = triplet.attr.fusesTo (src.fuses)
                        Iterator((triplet.dstId, ScMessage (src.source, null, null, fuses, src.id_seq, src.errors)))
                    }
                }
                else 
                {
                    // TODO
                    log.error ("shouldn't happen")
                    Iterator.empty
                }
            }
            else
            {
                    Iterator.empty
            }
        }
        else if (src.fatalErrors && dst.fatalErrors)
            if (src.fuses == dst.fuses)
                Iterator.empty

            else if ((null != src.fuses) && (null != dst.fuses) && src.fuses.dropRight(1) == dst.fuses.dropRight(1))
            {
                val comb_fuse = src.fuses.last ::: dst.fuses.last
                val fuses = src.fuses.dropRight(1) :+ comb_fuse.distinct
                Iterator(
                    (triplet.dstId, ScMessage (dst.source, null, null, fuses, src.id_seq, null)),
                    (triplet.srcId, ScMessage (src.source, null, null, fuses, dst.id_seq, null))
                )                    
            }
            else if ((null != src.fuses) && (null != dst.fuses) && src.fuses.length == dst.fuses.length)
            {
                var r: Iterator[(VertexId, ScMessage)] = Iterator.empty
                breakable {
                    for (i <- 1 to src.fuses.length)
                    {
                        if (src.fuses.drop(i-1).head.length > dst.fuses.drop(i-1).head.length)
                        {
                            val fuses = src.fuses.slice(0,i) ::: dst.fuses.drop(i)
                            r = Iterator((triplet.dstId, ScMessage (src.source, null, null, fuses, src.id_seq, null)))
                            break
                        }
                        else if (src.fuses.drop(i-1).head.length < dst.fuses.drop(i-1).head.length)
                        {
                            val fuses = dst.fuses.slice(0,i) ::: src.fuses.drop(i)
                            r = Iterator((triplet.srcId, ScMessage (dst.source, null, null, fuses, dst.id_seq, null)))
                            break
                        } 
                        else 
                        {
                            // continue
                            log.error("fuses ok")
                        }
                    }
                }
                r
            }
            else
            {                     
                val fuse_error = ScError (true, true, "different fuse paths detected from %s to %s".format (src.id_seq, dst.id_seq))
                log.error (fuse_error.message)
                Iterator(
                    (triplet.dstId, ScMessage (dst.source, null, null, null, src.id_seq, List (fuse_error))),
                    (triplet.srcId, ScMessage (src.source, null, null, null, dst.id_seq, List (fuse_error)))
                )
            }
        else if (src.fatalErrors)
        {
            val fuses = triplet.attr.fusesTo (src.fuses)
            Iterator(
                (triplet.dstId, ScMessage (dst.source, null, null, fuses, src.id_seq, src.errors))
            )
        }
        else if (dst.fatalErrors)
        {
            val fuses = triplet.attr.fusesTo (dst.fuses)
            Iterator(
                (triplet.srcId, ScMessage (src.source, null, null, fuses, dst.id_seq, dst.errors))
            )
        }
        else 
        {
            // TODO
            log.error ("shouldn't happen")
            Iterator.empty
        }
    }

    def handleMesh (triplet: EdgeTriplet[ScNode, ScEdge]): Iterator[(VertexId, ScMessage)] =
    {
        val src = triplet.srcAttr
        val dst = triplet.dstAttr
        if (src.fatalErrors || dst.fatalErrors)
        {
            propagateFusesOnly(triplet)
        }
        else if ((src.id_prev == dst.id_seq) || (dst.id_prev == src.id_seq)) // reinforcement
            Iterator.empty
        else
        {
            // check if the non-null impedance difference matches what we expect for this cable
            triplet.attr.element match
            {
                case _: ACLineSegment ⇒
                    val diff = src.impedance - dst.impedance
                    val expected = triplet.attr.impedanceTo ("not important")
                    val isequal = Math.abs (!diff.impedanz_low - !expected.impedanz_low) < 1e-6 && Math.abs (!diff.null_impedanz_low - !expected.null_impedanz_low) < 1e-6
                    if (isequal)
                        Iterator.empty
                    else
                    {
                        val error = ScError (true, true, "non-radial network detected through %s".format (triplet.attr.id_equ))
                        log.error (error.message)

                        val fuses =
                            if (null != src.fuses)
                                if (null != dst.fuses)
                                    src.fuses.dropRight(1) :+ (src.fuses.last ::: dst.fuses.last)
                                else
                                    src.fuses
                            else
                                if (null != dst.fuses)
                                    dst.fuses
                                else
                                    null

                        // neither node has a fatal error yet, send a message to both to mark them with a fatal error
                        Iterator (
                            (triplet.dstId, ScMessage (dst.source, null, null, fuses, src.id_seq, List (error))),
                            (triplet.srcId, ScMessage (src.source, null, null, fuses, dst.id_seq, List (error)))
                        )
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
        else if (triplet.srcAttr.impedance != null && triplet.dstAttr.impedance == null)
            if (triplet.attr.shouldContinueTo (triplet.dstAttr))
            {
                val from = triplet.attr.impedanceFrom (triplet.dstAttr.id_seq, triplet.srcAttr.impedance)
                val to = triplet.attr.impedanceTo (triplet.dstAttr.id_seq)
                val fuses = triplet.attr.fusesTo (triplet.srcAttr.fuses)
                val errors = triplet.attr.hasIssues (triplet.dstAttr.id_seq, triplet.srcAttr.errors, options.messagemax)
                Iterator ((triplet.dstId, ScMessage (triplet.srcAttr.source, from, to, fuses, triplet.srcAttr.id_seq, errors)))
            }
            else
                Iterator.empty
        else if (triplet.dstAttr.impedance != null && triplet.srcAttr.impedance == null)
            if (triplet.attr.shouldContinueTo (triplet.srcAttr))
            {
                val from = triplet.attr.impedanceFrom (triplet.srcAttr.id_seq, triplet.dstAttr.impedance)
                val to = triplet.attr.impedanceTo (triplet.srcAttr.id_seq)
                val fuses = triplet.attr.fusesTo (triplet.dstAttr.fuses)
                val errors = triplet.attr.hasIssues (triplet.srcAttr.id_seq, triplet.dstAttr.errors, options.messagemax)
                Iterator ((triplet.srcId, ScMessage (triplet.dstAttr.source, from, to, fuses, triplet.dstAttr.id_seq, errors)))
            }
            else
                Iterator.empty
        else
            Iterator.empty
        x
    }    
}