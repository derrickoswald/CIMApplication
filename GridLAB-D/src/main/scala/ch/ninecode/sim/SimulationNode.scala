package ch.ninecode.sim

import ch.ninecode.gl.GLMNode
import ch.ninecode.gl.Graphable

/**
 * Vertex data.
 *
 * @param id_seq TopologicalNode mRID.
 * @param equipment ConductingEquipment mRID.
 * @param position The (x,y) coordinates of the PositionPoint of the ConductingEquipment, or <code>null</code> if none.
 * @param voltage Node voltage (V).
 * @param players Players attached to this node - if any.
 * @param recorders Recorders attached to this node - if any.
 */
case class SimulationNode (
   id_seq: String,
   equipment: String,
   position: (Double, Double),
   voltage: Double,
   players: Array[SimulationPlayer] = null,
   recorders: Array[SimulationRecorder] = null
) extends GLMNode with Graphable
{
    override def id: String = id_seq
    override def nominal_voltage: Double = voltage
}