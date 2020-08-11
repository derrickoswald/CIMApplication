package ch.ninecode.sc

import ch.ninecode.gl.Complex

/**
 * Message sent between nodes in the short circuit GraphX Pregel algorithm.
 *
 * @param source_id        the feeding transformer
 * @param source_impedance the source impedance at the transformer secondary (Ω)
 * @param ref              the impedance from the transformer to the previous node
 * @param edge             the edge impedance
 * @param fuses            the list of fuses encountered on the path from the source to this node
 * @param previous_node    the previous node mRID
 * @param errors           any errors encountered
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
{
    def asString: String =
    {
        val z = if ((null != ref) && (null != edge)) ref + edge else Complex (0.0)
        "[%s(%sΩ) %s(%sΩ)+%sΩ=%sΩ %s %s]".format (
            source_id,
            source_impedance,
            previous_node,
            if (null != ref) ref.impedanz_high else "",
            if (null != edge) edge.impedanz_high else "",
            z,
            if (null != fuses) fuses.asString else "",
            if (null != errors) errors.mkString ("{", ",", "}") else "")
    }
}