package ch.ninecode.sc

/**
 * Fuse table break point.
 *
 * The required optimal fuse is the largest fuse where the short circuit current
 * is less than or equal to the maximum Ik of one of these Amp objects.
 * (_.Ik <= Math.abs (ik))
 *
 * @param Ik     maximum allowed short circuit current for this fuse
 * @param Rating fuse rating (A)
 */
case class Amp (
    Ik: Double,
    Rating: Double)
{
    override def toString: String = s"""{ ik: $Ik, rating: $Rating }"""
}

