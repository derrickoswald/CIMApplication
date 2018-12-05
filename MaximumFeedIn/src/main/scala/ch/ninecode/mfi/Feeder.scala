package ch.ninecode.mfi

import org.apache.spark.graphx.VertexId

/**
 * Start of feeder branch.
 *
 * @param node      The VertexId for the associated node.
 * @param feeder_id The mRID of the feeder source (abgang).
 */
case class Feeder (node: VertexId, feeder_id: String)
