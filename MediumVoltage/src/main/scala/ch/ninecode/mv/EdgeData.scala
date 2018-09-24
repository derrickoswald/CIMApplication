package ch.ninecode.mv

/**
 * Edge data for feeder processing.
 *
 * @param id the mRID of the edge
 * @param isConnected <code>true</code> if there is a connection between the nodes, i.e. a closed switch,
 *        which means the nodes are part of the same feeder
 */
case class EdgeData (id: String, isConnected: Boolean)
