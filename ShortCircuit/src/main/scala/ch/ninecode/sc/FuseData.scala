package ch.ninecode.sc

case class FuseData (
    Tables: Array[FuseTable]
)
{
    assert (null != Tables && 0 != Tables.length, "no fuse tables")

    override def toString: String = s"""[${ Tables.map (_.toString).mkString (",") }]"""

    /**
     * Find the recommended fuse for the given I<sub>k</sub>.
     *
     * Based on the supplied standard, find the highest rated fuse allowed for the given short circuit current.
     *
     * @param ik short circuit current (A)
     * @param standard the standard to apply
     * @return the recommended fuse rating (A)
     */
    def fuse (ik: Double, standard: String): Double =
    {
        if (ik.isNaN)
            Tables(0).Table.last.Rating
        else
        {
            Tables.find (_.Standard == standard) match
            {
                case Some (table) =>
                    table.Table.filter (_.Ik <= Math.abs (ik)).last.Rating
                case None =>
                    Tables(0).Table.filter (_.Ik <= Math.abs (ik)).last.Rating
            }
        }
    }
}
