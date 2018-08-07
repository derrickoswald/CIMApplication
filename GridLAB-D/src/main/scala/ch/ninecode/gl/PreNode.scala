package ch.ninecode.gl

/**
 * Vertex data.
 *
 * @param id ConnectivityNode or TopologicalNode MRID.
 * @param nominal_voltage Node voltage.
 */
case class PreNode (
    id: String,
    nominal_voltage: Double)
extends GLMNode
