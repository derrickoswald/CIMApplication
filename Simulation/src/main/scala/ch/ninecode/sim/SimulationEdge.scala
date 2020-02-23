package ch.ninecode.sim

import ch.ninecode.gl.GLMEdge

/**
 * Edge data.
 *
 * @param world_position The array of (x,y) coordinates of the PositionPoints of the ConductingEquipment in world coordinates, or <code>null</code> if none.
 * @param schematic_position The array of (x,y) coordinates of the PositionPoints of the ConductingEquipment in schematic coordinates, or <code>null</code> if none.
 * @param players   Players attached to this edge - if any.
 * @param recorders Recorders attached to this edge - if any.
 */
case class SimulationEdge
(
    rawedge: GLMEdge,
    world_position: Iterable[(Double, Double)],
    schematic_position: Iterable[(Double, Double)],
    players: Iterable[SimulationPlayer] = null,
    recorders: Iterable[SimulationRecorder] = null
) extends GLMEdge
{
    val id: String = rawedge.id
    val cn1: String = rawedge.cn1
    val cn2: String = rawedge.cn2
    override def toString: String =
    {
        "%s %s⇒%s".format (id, cn1, cn2)
    }
}

