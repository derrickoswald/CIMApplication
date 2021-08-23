package ch.ninecode.sc.branch

import ch.ninecode.sc.Impedanzen
import ch.ninecode.sc.ShortCircuitOptions

/**
 * A simple two terminal element branch.
 *
 * @param from    the 'from' node
 * @param to      the 'to' node
 * @param current the current through this branch in the GridLAB-D experiment
 * @param mRID    the mRID of the CIM element
 * @param name    the name of the CIM element
 * @param rating  the current rating if it is a fuse
 * @param z       the positive and zero sequence impedances at the operational temperatures
 */
case class SimpleBranch (override val from: String, override val to: String, override val current: Double, mRID: String,
    name: String, rating: Option[Double] = None, standard: String,
    z: Impedanzen = Impedanzen(0.0, 0.0, 0.0, 0.0)) extends Branch(from, to, current)
{
    override def toString: String =
    {
        val n = name match
        {
            case s: String => s
            case _ => ""
        }
        s"""SimpleBranch ("$from" ⇒ "$to" ${current}A $asString $n)"""
    }

    def ratingString: String =
    {
        rating match
        {
            case Some(amps) => s"@$amps"
            case _ => ""
        }
    }

    def asString: String = s"$mRID$ratingString[$standard]"

    def asFuse: String = rating.getOrElse(0.0).toInt.toString

    def asId: String = if (rating.isDefined) mRID else ""

    def seq: Seq[SimpleBranch] = Seq(this)

    def iter: Iterable[SimpleBranch] = Iterable(this)

    def isFuse: Boolean = rating.isDefined

    def lastFuses: Iterable[Branch] = Seq(this)

    def justFuses: Option[Branch] = if (isFuse) Some(this) else None

    def reverse: Branch = SimpleBranch(to, from, current, mRID, name, rating, standard, z)

    def ratios: Iterable[(Double, Branch)] = List((1.0, this))

    def voltageRatio: Double = 1.0

    def z (in: Impedanzen): Impedanzen = in + z

    def contents: Iterable[SimpleBranch] = Iterable(this)

    def getTransformerBranch: Option[TransformerBranch] = None

    def checkFuses (ik: Double, options: ShortCircuitOptions): (Boolean, Option[Branch]) =
    {
        if (isFuse)
        {
            val _rating = rating.getOrElse(Double.MaxValue)
            if (0.0 == _rating)
                (false, None)
            else
                if (options.fuse_table.fuse(ik, standard) >= _rating)
                    (true, None)
                else
                    (false, Some(this))
        }
        else
            (false, Some(this))
    }
}
