package ch.ninecode.sc

import ch.ninecode.util.Complex

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
@SuppressWarnings(Array("org.wartremover.warts.Null"))
case class ScMessage
(
    source_id: String = null,
    source_impedance: Complex = null,
    ref: Impedanzen = null,
    edge: Impedanzen = null,
    fuses: Branch = null,
    previous_node: String = null,
    errors: List[ScError] = null)
{
    def asString: String =
    {
        val z = if ((null != ref) && (null != edge)) ref + edge else Impedanzen(0.0, 0.0, 0.0, 0.0)
        "[%s(%sΩ) %s(%sΩ)+%sΩ=%sΩ %s %s]".format(
            source_id,
            source_impedance,
            previous_node,
            if (null != ref) ref.impedanz_high else "",
            if (null != edge) edge.impedanz_high else "",
            z,
            if (null != fuses) fuses.asString else "",
            if (null != errors) errors.mkString("{", ",", "}") else "")
    }
}