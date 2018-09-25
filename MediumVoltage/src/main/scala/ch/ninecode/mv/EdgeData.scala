package ch.ninecode.mv

/**
 * Edge data for feeder processing.
 *
 * Each edge represents an open switch that separates different TopologicalIsland.
 *
 * @param id the mRID of the edge
 * @param island1 mRID of the island on one side
 * @param island2 mRID of the island on the other side
 */
case class EdgeData (id: String, island1: String, island2: String)
