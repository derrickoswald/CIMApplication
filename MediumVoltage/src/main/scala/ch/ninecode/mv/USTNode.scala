package ch.ninecode.mv

import ch.ninecode.gl.GLMNode
import ch.ninecode.gl.TransformerSet

/**
 * Vertex data.
 *
 * @param id ConnectivityNode or TopologicalNode MRID.
 * @param nominal_voltage Node voltage.
 * @param load Low voltage transformer attached to this node - if any.
 */
case class USTNode (
    id: String,
    nominal_voltage: Double,
    load: TransformerSet)
extends GLMNode
