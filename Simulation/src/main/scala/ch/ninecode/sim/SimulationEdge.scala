package ch.ninecode.sim

import ch.ninecode.gl.GLMEdge

/**
 * Edge data.
 *
 * @param id        ConductingEquipment MRID.
 * @param cn1       Terminal 1 ConnectivityNode or TopologicalNode MRID.
 * @param cn2       Terminal 2 ConnectivityNode or TopologicalNode MRID.
 * @param position  The array of (x,y) coordinates of the PositionPoints of the ConductingEquipment, or <code>null</code> if none.
 * @param players   Players attached to this edge - if any.
 * @param recorders Recorders attached to this edge - if any.
 */
case class SimulationEdge
(
    id: String,
    cn1: String,
    cn2: String,
    rawedge: GLMEdge,
    position: Iterable[(Double, Double)],
    players: Iterable[SimulationPlayer] = null,
    recorders: Iterable[SimulationRecorder] = null
) extends GLMEdge


