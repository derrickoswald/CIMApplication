package ch.ninecode.gl

import ch.ninecode.model.Element

/**
 * Basic properties of an edge.
 */
trait GLMEdge extends Serializable
{
    /**
     * The unique edge identifier.
     *
     * @return The ID of the edge (the mRID of the electrical element).
     */
    def id: String

    /**
     * The node id connected to the first terminal.
     *
     * @return The ID of the ConnectivityNode or TopologicalNode reference by the Terminal with sequence number 1.
     */
    def cn1: String

    /**
     * The node id connected to the second terminal.
     *
     * @return The ID of the ConnectivityNode or TopologicalNode reference by the Terminal with sequence number 2.
     */
    def cn2: String

    /**
     * The CIM model object for this edge.
     *
     * @return The instance of the CIM model object that is this edge, e.g. Switch.
     */
    def el: Element
}
