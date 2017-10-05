package ch.ninecode.gl

/**
 * Vertex data.
 * @param id_seq ConnectivityNode or TopologicalNode MRID.
 * @param voltage Node voltage.
 */
case class PreNode(
    id_seq: String,
    voltage: Double) extends GLMNode with Graphable with Serializable
{
    override def id: String = id_seq
    override def nominal_voltage: Double = voltage
}
