package ch.ninecode.gl

import org.apache.spark.rdd.RDD
import org.apache.spark.graphx.EdgeDirection
import org.apache.spark.graphx.EdgeTriplet
import org.apache.spark.graphx.Graph
import org.apache.spark.graphx.Graph.graphToGraphOps
import org.apache.spark.graphx.VertexId
import org.slf4j.LoggerFactory
import org.slf4j.Logger

case class Trace (initial: Graph[PreNode, PreEdge])
{
    val log: Logger = LoggerFactory.getLogger (getClass)

    def vertexProgram (starting_nodes: Array[VertexId])(id: VertexId, v: Boolean, message: Boolean): Boolean =
    {
        if (message)
            true
        else
        // on the first pass through the Pregel algorithm all nodes get a false message
        // if this node is in in the list of starting nodes, set the vertex data to true
            starting_nodes.contains (id)
    }

    def sendMessage (triplet: EdgeTriplet[Boolean, PreEdge]): Iterator[(VertexId, Boolean)] =
    {
        var ret: Iterator[(VertexId, Boolean)] = Iterator.empty

        if (triplet.srcAttr && !triplet.dstAttr) // see if a message is needed
            if (triplet.attr.connected)
                ret = Iterator ((triplet.dstId, true))

        if (!triplet.srcAttr && triplet.dstAttr) // see if a message is needed in reverse
            if (triplet.attr.connected)
                ret = Iterator ((triplet.srcId, true))

        ret
    }

    def mergeMessage (a: Boolean, b: Boolean): Boolean = a || b

    // discard leaf edges that aren't transformers
    def keep (arg: Tuple3[PreEdge, Option[PreNode], Option[PreNode]]): Boolean =
    {
        arg match
        {
            case (_, Some (_), Some (_)) => // connected on both ends (not a leaf): keep
                true
            case (_, Some (_), None) | (_, None, Some (_)) => // connected on only one end (a leaf)
                val edge = arg._1
                edge.v1 != edge.v2 // keep if it spans a voltage change (a transformer)
            case (_, None, None) => // not connected (can't happen): discard
                false
        }
    }

    // trace the graph with the Pregel algorithm
    def run (starting_nodes: Array[VertexId]): (RDD[PreNode], RDD[PreEdge]) =
    {
        log.info ("trace([" + starting_nodes.mkString (",") + "]) begin")
        val begin = System.nanoTime ()

        // make a similar graph with boolean values as vertex data (false = not traced, true = traced)
        val binary = initial.mapVertices
        { case (_, _) => false }

        // perform the trace, marking all traced nodes true
        val graph = binary.pregel [Boolean](false, 10000, EdgeDirection.Either)(
            vertexProgram (starting_nodes),
            sendMessage,
            mergeMessage
        )

        // get the list of traced vertices
        val touched = graph.vertices.filter (_._2)
        val traced_nodes = touched.join (initial.vertices).map ((x) => (x._1, x._2._2))

        // get the list of edges
        val edges = initial.edges.map (_.attr)

        // OK, this is subtle, edges that stop the trace (open switches, open fuses, transformers, etc.)
        // have one node that isn't in the traced_nodes RDD.
        //
        // There are two ways to handle this, add in the missing nodes
        // or remove the edges that have only one vertex in the trace.
        //
        // With the "add in the missing nodes" method, GridLAB-D fails with the message:
        //    "Newton-Raphson method is unable to converge to a solution at this operation point"
        // because of open switches that are on the edge of the graph (removing the switches and the
        // singly connected edge node allows the system to be solved).
        //
        // But, removing all singly-connected edges also removes transformers,
        // meaning there is no high voltage node to use as a primary slack bus.
        // Sure, a secondary low voltage slack bus could be used - effectively
        // ignoring the capability of the transformer - but instead we selectively remove
        // singly-connected edges that are not transformers.

        // get the edges with their connected nodes
        val one = edges.keyBy (x => x.vertex_id (x.cn1)).leftOuterJoin (traced_nodes).values
        val two = one.keyBy (x => x._1.vertex_id (x._1.cn2)).leftOuterJoin (traced_nodes).values.map (x => (x._1._1, x._1._2, x._2))

        // filter out non-transformer leaf edges
        val traced_edges = two.filter (keep).map (_._1)

        // create a more complete list of traced nodes using the edge list
        val all_traced_nodes = traced_edges.keyBy (_.cn1).union (traced_edges.keyBy (_.cn2)).join (initial.vertices.values.keyBy (_.id)).reduceByKey ((a, b) ⇒ a).values.values

        val read = System.nanoTime ()
        log.info ("trace([" + starting_nodes.mkString (",") + "]) end " + ((read - begin) / 1e9) + " seconds")

        (all_traced_nodes, traced_edges)
    }
}
