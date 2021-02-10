package ch.ninecode.sc

import ch.ninecode.util.Graphable

/**
 * Vertex data for GraphX trace.
 *
 * @param id_seq           the node mRID
 * @param voltage          the node voltage
 * @param source_id        the feeding transformer
 * @param id_prev          the previous node
 * @param impedance        the impedance of the transformer secondary supplying this node
 * @param branches         the network encountered on the path from the source to this node
 * @param errors           the list of errors and warnings encountered
 */
@SuppressWarnings(Array("org.wartremover.warts.Null"))
case class ScNode (
    id_seq: String = "",
    voltage: Double = 0.0,
    source_id: String = null,
    id_prev: String = null,
    impedance: Impedanzen = null,
    branches: Branch = null,
    errors: List[ScError] = null)
    extends Graphable
{
    def fatalErrors: Boolean = (null != errors) && errors.exists(_.fatal)

    def invalidErrors: Boolean = (null != errors) && errors.exists(_.invalid)
}