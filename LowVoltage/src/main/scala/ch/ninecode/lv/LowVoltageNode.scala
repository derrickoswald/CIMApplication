package ch.ninecode.lv

import ch.ninecode.gl.GLMNode
import ch.ninecode.net.LoadFlowNode

/**
 * Vertex data.
 *
 * @param id               TopologicalNode mRID
 * @param nominal_voltage  node voltage (V)
 * @param equipment        ConductingEquipment mRID
 * @param isEnergyConsumer <code>true</code> if this node is an EnergyConsumer
 */
case class LowVoltageNode
(
    override val id: String,
    override val nominal_voltage: Double,
    equipment: String,
    isEnergyConsumer: Boolean
)
    extends LoadFlowNode (id, nominal_voltage) with GLMNode
{
    override def toString: String = s"$id $equipment (${nominal_voltage}V)${if (isEnergyConsumer) " EnergyConsumer" else ""}"
}
