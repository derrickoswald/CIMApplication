package ch.ninecode.np

/**
 * Gather transformer details from query results.
 *
 * @param transformer the transformer name
 * @param station the station the transformer is in
 * @param phases the phases present on the terminal
 * @param connectivity_node the ConnectivityNode mRID
 * @param topological_node the TopologicalNode mRID
 * @param x the longitude
 * @param y the latitude
 */
case class TransformerDetails (
    transformer: String,
    station: String,
    voltage: String,
    phases: String,
    connectivity_node: String,
    topological_node: String,
    x: Double,
    y: Double
)
