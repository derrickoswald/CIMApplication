package ch.ninecode.sim

import ch.ninecode.gl.GLMNode
import ch.ninecode.gl.Graphable

/**
 * Vertex data.
 *
 * @param id              TopologicalNode mRID.
 * @param nominal_voltage Node voltage (V).
 * @param equipment       ConductingEquipment mRID.
 * @param position        The (x,y) coordinates of the PositionPoint of the ConductingEquipment, or <code>null</code> if none.
 * @param players         Players attached to this node - if any.
 * @param recorders       Recorders attached to this node - if any.
 */
case class SimulationNode
(
    id: String,
    nominal_voltage: Double,
    equipment: String,
    position: (Double, Double) = null,
    players: Array[SimulationPlayer] = null,
    recorders: Array[SimulationRecorder] = null
) extends GLMNode
