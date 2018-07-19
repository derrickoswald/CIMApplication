package ch.ninecode.sc

import ch.ninecode.gl.GLMNode
import ch.ninecode.gl.Graphable

/**
 * Vertex data.
 *
 * @param id_seq TopologicalNode mRID.
 * @param equipment ConductingEquipment mRID.
 * @param voltage Node voltage (V).
 */
case class SimulationNode (
    id_seq: String,
    equipment: String,
    voltage: Double,
    psrtype: String
) extends GLMNode with Graphable
{
    override def id: String = id_seq
    override def nominal_voltage: Double = voltage
}

