package ch.ninecode.sc

import ch.ninecode.gl.GLMNode

/**
 * Vertex data.
 *
 * @param id TopologicalNode mRID.
 * @param equipment ConductingEquipment mRID.
 * @param nominal_voltage Node voltage (V).
 * @param psrtype The type of node according to the power system resource type.
 */
case class SimulationNode (
    id: String,
    equipment: String,
    nominal_voltage: Double,
    psrtype: String
)
extends GLMNode

