package ch.ninecode.sc.branch

import ch.ninecode.sc.Impedanzen
import ch.ninecode.sc.ShortCircuitOptions

/**
 * A group of elements too complex to reduce to a combination of series and parallel branches.
 *
 * @param from    the 'from' node
 * @param to      the 'to' node
 * @param current the current through this branch in the GridLAB-D experiment
 * @param basket  the branches in no particular order
 */
case class ComplexBranch (
    override val from: String,
    override val to: String,
    override val current: Double,
    basket: Array[Branch]) extends Branch(from, to, current)
{
    override def toString: String =
        s"""ComplexBranch ("$from" ⇒ "$to" ${current}A ${basket.map(_.toString).mkString("[", ",", "]")})"""

    def asString: String = basket.map(_.asString).mkString("{", ",", "}")

    def asFuse: String = basket.map(_.asFuse).mkString("{", ",", "}")

    def asId: String = basket.map(_.asId).mkString("{", ",", "}")

    def seq: Seq[ComplexBranch] = Seq(this)

    def iter: Iterable[Branch] = basket

    def lastFuses: Iterable[Branch] =
    {
        justFuses match
        {
            case Some(fuses) => Seq(fuses)
            case None => Seq()
        }
    }

    def justFuses: Option[Branch] =
    {
        val fuses = basket.flatMap(_.justFuses)
        if (1 == fuses.length)
            fuses.head match
            {
                case b: SimpleBranch =>
                    Some(SimpleBranch(from, to, current, b.mRID, b.name, b.rating, b.standard, b.z))
                case p: ParallelBranch =>
                    Some(ParallelBranch(from, to, current, p.parallel))
                case s: SeriesBranch =>
                    Some(SeriesBranch(from, to, current, s.series))
                case c: ComplexBranch =>
                    Some(ComplexBranch(from, to, current, c.basket))
            }
        else
            if (fuses.nonEmpty)
                Some(ComplexBranch(from, to, current, fuses))
            else
                None
    }

    def reverse: Branch = ComplexBranch(to, from, current, basket.map(_.reverse))

    def ratios: Iterable[(Double, Branch)] = basket.map(x => (x.current / current, x))

    def voltageRatio: Double = basket.foldLeft(1.0)((v, branch) => v * branch.voltageRatio)

    /**
     * @return a fake impedance value
     */
    def z (in: Impedanzen): Impedanzen =
    {
        Impedanzen(0.0, 0.0, 0.0, 0.0)
    }

    def contents: Iterable[Branch] = basket

    def checkFuses (ik: Double, options: ShortCircuitOptions): (Boolean, Option[Branch]) =
    {
        val new_complex = ratios.map(
            pair =>
            {
                val (fraction, branch) = pair
                val current = fraction * Math.abs(ik)
                if (current.isNaN)
                    (false, None)
                else
                    branch.checkFuses(current, options)
            }
        )
        val blows = new_complex.exists(_._1)
        if (blows)
            (blows, Some(ComplexBranch(from, to, current, new_complex.flatMap(_._2).toArray)))
        else
            (false, Some(this))
    }
}
