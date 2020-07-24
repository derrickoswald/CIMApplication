package ch.ninecode.sim

import ch.ninecode.gl.GLMNode
import ch.ninecode.net.LoadFlowNode

/**
 * Vertex data.
 *
 * @param id              TopologicalNode mRID.
 * @param nominal_voltage Node voltage (V).
 * @param equipment       ConductingEquipment mRID.
 * @param world_position  The (x,y) coordinates of the PositionPoint of the ConductingEquipment in world coordinates, or <code>null</code> if none.
 * @param schematic_position The (x,y) coordinates of the PositionPoint of the ConductingEquipment in schematic coordinates, or <code>null</code> if none.
 * @param players         Players attached to this node - if any.
 * @param recorders       Recorders attached to this node - if any.
 */
case class SimulationNode
(
    override val id: String,
    override val nominal_voltage: Double,
    equipment: String,
    world_position: Iterable[(Double, Double)] = Seq (),
    schematic_position: Iterable[(Double, Double)] = Seq (),
    players: Iterable[SimulationPlayer] = null,
    recorders: Iterable[SimulationRecorder] = null
)
extends LoadFlowNode (
    id,
    nominal_voltage
)
with GLMNode
{
    override def toString: String = s"$id $equipment (${nominal_voltage}V)"
}
