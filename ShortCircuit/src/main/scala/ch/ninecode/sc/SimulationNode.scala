package ch.ninecode.sc

import ch.ninecode.gl.GLMNode

/**
 * Vertex data.
 *
 * @param id TopologicalNode mRID.
 * @param nominal_voltage Node voltage (V).
 * @param equipment ConductingEquipment mRID.
 * @param psrtype The type of node according to the power system resource type.
 */
case class SimulationNode (
    id: String,
    nominal_voltage: Double,
    equipment: String,
    psrtype: String
)
extends GLMNode

