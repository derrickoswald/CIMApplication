package ch.ninecode.sc

/**
 * Vertex data for GraphX trace.
 *
 * @param id_seq the node mRID
 * @param voltage the node voltage
 * @param source the feeding transformer
 * @param impedance the impedance from the transformer to this node
 * @param fuses the list of fuses encountered on the path from the source to this node
 */
case class ScNode (
    id_seq: String,
    voltage: Double,
    source: String,
    impedance: Impedanzen,
    fuses: List[Double],
    var errors: List[ScError])
extends
    Graphable
{
    def noFatalErrors: Boolean = (null == errors) || errors.forall (!_.fatal)
    def reinforcement: Boolean = (null != errors) && errors.exists (_.message.startsWith ("reinforcement"))
}