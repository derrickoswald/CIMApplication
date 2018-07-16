package ch.ninecode.sc

import ch.ninecode.gl.Graphable

/**
 * Vertex data for GraphX trace.
 *
 * @param id_seq the node mRID
 * @param voltage the node voltage
 * @param source the feeding transformer
 * @param id_prev the previous node
 * @param impedance the impedance from the transformer to this node
 * @param fuses the list of fuses encountered on the path from the source to this node
 * @param errors the list of errors and warnings encountered
 */
case class ScNode (
    id_seq: String,
    voltage: Double,
    source: String,
    id_prev: String,
    impedance: Impedanzen,
    fuses: List[Double],
    errors: List[ScError])
extends
    Graphable
{
    def fatalErrors: Boolean = (null != errors) && errors.exists (_.fatal)
}