package ch.ninecode.sc

/**
 * Vertex data for GraphX trace.
 *
 * @param id_seq The node mRID
 * @param voltage The node voltage
 * @param source the feeding transformer
 * @param impedance the impedance from the transformer to this node
 */
case class ScNode (
    id_seq: String,
    voltage: Double,
    source: String,
    impedance: Impedanzen)
extends
    Graphable
