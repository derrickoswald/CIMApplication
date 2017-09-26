package ch.ninecode.ms

import ch.ninecode.gl.GLMNode
import ch.ninecode.gl.Graphable
import ch.ninecode.gl.TransformerSet

/**
 * Vertex data.
 *
 * @param id_seq ConnectivityNode or TopologicalNode MRID.
 * @param voltage Node voltage.
 * @param load Low voltage transformer attached to this node - if any.
 */
case class USTNode (
    id_seq: String,
    voltage: Double,
    load: TransformerSet) extends GLMNode with Graphable with Serializable
{
    override def id: String = id_seq
    override def nominal_voltage: Double = voltage
}
