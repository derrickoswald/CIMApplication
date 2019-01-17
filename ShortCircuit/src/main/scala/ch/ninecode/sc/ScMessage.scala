package ch.ninecode.sc

import ch.ninecode.gl.Complex

/**
 * Message sent between nodes in the short circuit GraphX Pregel algorithm.
 *
 * @param source        the feeding transformer
 * @param ref           the impedance from the transformer to the previous node
 * @param edge          the edge impedance
 * @param fuses         the list of fuses encountered on the path from the source to this node
 * @param previous_node the previous node mRID
 * @param errors        any errors encountered
 */
case class ScMessage
(
    source_id: String,
    source_impedance: Complex,
    ref: Impedanzen,
    edge: Impedanzen,
    fuses: Branch,
    previous_node: String,
    errors: List[ScError])
