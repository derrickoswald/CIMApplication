package ch.ninecode.sc

/**
 * Message sent between nodes in the short circuit GraphX Pregel algorithm.
 *
 * @param source the feeding transformer
 * @param ref the impedance from the transformer to the previous node
 * @param edge the edge impedance
 * @param previous_node the previous node mRID
 * @param errors any errors encountered
 */
case class ScMessage (
    source: String,
    ref: Impedanzen,
    edge: Impedanzen,
    previous_node: String,
    errors: List[ScError])
