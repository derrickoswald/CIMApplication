package ch.ninecode.sc

/**
 * Errors encountered in processing.
 *
 * @param fatal if <code>true</code> processing should stop, otherwise it can proceed
 * @param invalid if <code>true</code> the results are invalid, otherwise the results are valid
 * @param message the error message
 */
case class ScError (
   fatal: Boolean,
   invalid: Boolean,
   message: String)
{
    override def toString: String = (if (fatal) "FATAL: " else if (invalid) "INVALID: " else "") + message
}

object ScError
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
                // also prefer invalid ones
                val l2 = (l1 ++ list1.filter (x => !x.fatal && x.invalid) ++ l0.filter (x => !x.fatal && x.invalid)).take (max)
                // add existing non-fatal & non-invalid messages
                val l3 = (l2 ++ list1.filter (x => !x.fatal && !x.invalid)).take (max)
                // add any new non-fatal & non-invalid messages that fit
                val l4 = (l3 ++ l0.filter (x => !x.fatal && !x.invalid)).take (max)
                if (0 == l4.length) null else l4
            }
    }
}