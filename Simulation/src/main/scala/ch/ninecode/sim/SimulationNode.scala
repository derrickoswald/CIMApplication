package ch.ninecode.sim

import ch.ninecode.gl.GLMNode

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
    id: String,
    nominal_voltage: Double,
    equipment: String,
    world_position: (Double, Double),
    schematic_position: (Double, Double),
    players: Iterable[SimulationPlayer] = null,
    recorders: Iterable[SimulationRecorder] = null
) extends GLMNode
{
    override def toString: String =
    {
        "%s %s (%sV)".format (id, equipment, nominal_voltage)
    }
}
