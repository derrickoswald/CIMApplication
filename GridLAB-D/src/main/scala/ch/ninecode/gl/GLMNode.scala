package ch.ninecode.gl

/**
 * Basic properties of a node.
 */
trait GLMNode extends Serializable
{
    /**
     * The unique node identifier.
     *
     * @return The ID of the node (the mRID of the ConnectivityNode or TopologicalNode).
     */
    def id: String

    /**
     * The nominal voltage of the node.
     *
     * @return The voltage of the node (volts).
     */
    def nominal_voltage: Double
}
