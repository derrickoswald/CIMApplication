package ch.ninecode.gl

/**
 * Edge data for transformer service area processing.
 *
 * @param id          the mRID of the edge element
 * @param isConnected <code>true</code> if there is a connection between the islands, i.e. a closed switch,
 *                    which means the islands are in the same transformer service area
 */
case class EdgeData (id: String, isConnected: Boolean)
