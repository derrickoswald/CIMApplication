package ch.ninecode.sim

import ch.ninecode.gl.GLMEdge

/**
 * Edge data.
 *
 * @param cn1       Terminal 1 ConnectivityNode or TopologicalNode MRID.
 * @param cn2       Terminal 2 ConnectivityNode or TopologicalNode MRID.
 * @param world_position The array of (x,y) coordinates of the PositionPoints of the ConductingEquipment in world coordinates, or <code>null</code> if none.
 * @param schematic_position The array of (x,y) coordinates of the PositionPoints of the ConductingEquipment in schematic coordinates, or <code>null</code> if none.
 * @param players   Players attached to this edge - if any.
 * @param recorders Recorders attached to this edge - if any.
 */
case class SimulationEdge
(
    cn1: String,
    cn2: String,
    rawedge: GLMEdge,
    world_position: Iterable[(Double, Double)],
    schematic_position: Iterable[(Double, Double)],
    players: Iterable[SimulationPlayer] = null,
    recorders: Iterable[SimulationRecorder] = null
) extends GLMEdge
{
    def id: String = rawedge.id
}

