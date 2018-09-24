package ch.ninecode.mv

/**
 * Vertex data for feeder processing.
 *
 * @param id the mRID of the node
 * @param feeders the feeders connected to this node
 */
case class VertexData (id: String, feeders: Set[String])

