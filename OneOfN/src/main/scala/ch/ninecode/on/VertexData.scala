package ch.ninecode.on

/**
 * Vertex data for feeder processing.
 *
 * @param id      the mRID of the island
 * @param sources the source feeders for the island (if any)
 * @param feeders the feeders connected to this island
 */
case class VertexData (id: String = "", sources: Set[String] = Set [String](), feeders: Set[String] = Set [String]())

