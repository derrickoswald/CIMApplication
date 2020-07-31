package ch.ninecode.gl

import ch.ninecode.net.LoadFlowNode

/**
 * Vertex data.
 *
 * @param id              ConnectivityNode or TopologicalNode MRID.
 * @param nominal_voltage Node voltage.
 * @param problem         Any error message associated with the node.
 */
case class PreNode
(
    override val id: String,
    override val nominal_voltage: Double,
    problem: String = "")
extends LoadFlowNode (id, nominal_voltage)
