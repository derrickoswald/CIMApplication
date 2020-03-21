package ch.ninecode.sim

import ch.ninecode.gl.GLMEdge
import ch.ninecode.gl.GLMGenerator
import ch.ninecode.net.LoadFlowEdge

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
    players: Iterable[SimulationPlayer],
    recorders: Iterable[SimulationRecorder]
)
extends LoadFlowEdge (
    rawedge.id,
    rawedge.cn1,
    rawedge.cn2
)
with GLMEdge
{
    override def emit (generator: GLMGenerator): String = rawedge.emit (generator)
    override def toString: String = s"$id $cn1⇒$cn2"
}

