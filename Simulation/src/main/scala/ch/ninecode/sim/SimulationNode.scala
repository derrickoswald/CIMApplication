package ch.ninecode.sim

import ch.ninecode.gl.GLMNode
import ch.ninecode.gl.Graphable

/**
 * Vertex data.
 *
 * @param id TopologicalNode mRID.
 * @param equipment ConductingEquipment mRID.
 * @param position The (x,y) coordinates of the PositionPoint of the ConductingEquipment, or <code>null</code> if none.
 * @param nominal_voltage Node voltage (V).
 * @param players Players attached to this node - if any.
 * @param recorders Recorders attached to this node - if any.
 */
case class SimulationNode (
   id: String,
   equipment: String,
   position: (Double, Double),
   nominal_voltage: Double,
   players: Array[SimulationPlayer] = null,
   recorders: Array[SimulationRecorder] = null
)
extends
    GLMNode
