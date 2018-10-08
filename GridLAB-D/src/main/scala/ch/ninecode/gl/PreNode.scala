package ch.ninecode.gl

/**
 * Vertex data.
 *
 * @param id ConnectivityNode or TopologicalNode MRID.
 * @param nominal_voltage Node voltage.
 * @param problem Any error message associated with the node.
 */
case class PreNode (
    id: String,
    nominal_voltage: Double,
    problem: String)
extends GLMNode
