package ch.ninecode.sc

import ch.ninecode.gl.GLMNode

/**
 * Vertex data.
 *
 * @param id              TopologicalNode mRID.
 * @param nominal_voltage Node voltage (V).
 * @param equipment       ConductingEquipment mRID.
 * @param consumer        Flag indicating the node is an EnergyConsumer.
 * @param busbar          Flag indicating the node is a BusbarSection.
 */
case class SimulationNode
(
    id: String,
    nominal_voltage: Double,
    equipment: String,
    consumer: Boolean,
    busbar: Boolean
)
    extends GLMNode

