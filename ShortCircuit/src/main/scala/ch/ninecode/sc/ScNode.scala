package ch.ninecode.sc

import ch.ninecode.util.Complex
import ch.ninecode.util.Graphable

/**
 * Vertex data for GraphX trace.
 *
 * @param id_seq           the node mRID
 * @param voltage          the node voltage
 * @param source_id        the feeding transformer
 * @param source_impedance the feeding transformer impedance at the secondary (Ω)
 * @param id_prev          the previous node
 * @param impedance        the impedance of the transformer secondary supplying this node
 * @param branches         the network encountered on the path from the source to this node
 * @param errors           the list of errors and warnings encountered
 */
case class ScNode (
    id_seq: String,
    voltage: Double,
    source_id: String,
    source_impedance: Complex,
    id_prev: String,
    impedance: Impedanzen,
    branches: Branch,
    errors: List[ScError])
    extends Graphable
{
    def fatalErrors: Boolean = (null != errors) && errors.exists (_.fatal)

    def invalidErrors: Boolean = (null != errors) && errors.exists (_.invalid)
}