package ch.ninecode.mfi

import org.apache.spark.graphx.VertexId

import ch.ninecode.util.Graphable

/**
 * Start of a feeder branch.
 *
 * @param feeder_id the mRID of the feeder conducting equipment (abgang)
 * @param node      the associated topological node
 * @param control   the mRID of the controlling Switch or Fuse
 */
case class Feeder (
    feeder_id: String,
    node: String,
    control: String
)
    extends Graphable
{
    lazy val vertex: VertexId = vertex_id (node)
}
