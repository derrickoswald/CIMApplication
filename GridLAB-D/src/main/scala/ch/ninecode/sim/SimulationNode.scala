package ch.ninecode.sim

import ch.ninecode.gl.GLMNode
import ch.ninecode.gl.Graphable

/**
 * Vertex data.
 *
 * @param id_seq ConnectivityNode or TopologicalNode mRID.
 * @param voltage Node voltage.
 * @param players Players attached to this node - if any.
 * @param recorders Recorders attached to this node - if any.
 */
case class SimulationNode (
   id_seq: String,
   voltage: Double,
   players: Array[SimulationPlayer],
   recorders: Array[SimulationRecorder]
) extends GLMNode with Graphable
{
    override def id: String = id_seq
    override def nominal_voltage: Double = voltage
}