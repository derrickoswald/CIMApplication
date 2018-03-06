package ch.ninecode.sc

/**
 * Message sent between nodes in the short circuit GraphX Pregel algorithm.
 *
 * @param source the feeding transformer
 * @param ref the impedance from the transformer to the previous node
 * @param edge the edge impedance
 * @param fuses the list of fuses encountered on the path from the source to this node
 * @param previous_node the previous node mRID
 * @param errors any errors encountered
 */
case class ScMessage (
    source: String,
    ref: Impedanzen,
    edge: Impedanzen,
    fuses: List[Double],
    previous_node: String,
    errors: List[ScError])
{
    def combine_errors (list1: List[ScError], list2: List[ScError], max: Int): List[ScError] =
    {
        if (null == list1)
            list2
        else
            if (null == list2)
                list1
            else // both not null
            {
                // eliminate duplicates
                val l0 = list2.filter (!list1.contains (_))
                // prefer the fatal ones
                val l1 = (list1.filter (_.fatal) ++ l0.filter (_.fatal)).take (max)
                // add existing non-fatal messages
                val l2 = (l1 ++ list1.filter (!_.fatal)).take (max)
                // add any new non-fatal messages that fit
                val l3 = (l2 ++ l0.filter (!_.fatal)).take (max)
                if (0 == l3.length) null else l3
            }
    }
}
