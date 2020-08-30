package ch.ninecode.sim

import ch.ninecode.gl.GLMEdge
import ch.ninecode.gl.GLMGenerator
import ch.ninecode.net.LoadFlowEdge

/**
 * Edge data.
 *
 * @param rawedge            The underlying edge object.
 * @param world_position     The array of (x,y) coordinates of the PositionPoints of the ConductingEquipment in world coordinates, or <code>null</code> if none.
 * @param schematic_position The array of (x,y) coordinates of the PositionPoints of the ConductingEquipment in schematic coordinates, or <code>null</code> if none.
 * @param players            Players attached to this edge - if any.
 * @param recorders          Recorders attached to this edge - if any.
 */
case class SimulationEdge
(
    rawedge: GLMEdge,
    world_position: Iterable[(Double, Double)] = Seq (),
    schematic_position: Iterable[(Double, Double)] = Seq (),
    players: Iterable[SimulationPlayer] = Iterable (),
    recorders: Iterable[SimulationRecorder] = Iterable ()
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

