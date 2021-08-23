package ch.ninecode.sc.branch

import ch.ninecode.sc.Impedanzen
import ch.ninecode.sc.ShortCircuitOptions

/**
 * A group of series connected elements.
 *
 * @param from    the 'from' node
 * @param to      the 'to' node
 * @param current the current through this branch in the GridLAB-D experiment
 * @param series  the branches in series, in order
 */
case class SeriesBranch (
    override val from: String,
    override val to: String,
    override val current: Double,
    series: Seq[Branch]) extends Branch(from, to, current)
{
    override def toString: String =
        s"""SeriesBranch ("$from" ⇒ "$to" ${current}A ${series.map(_.toString).mkString("+")})"""

    def asString: String = series.map(_.asString).mkString("(", ",", ")")

    def asFuse: String = series.map(_.asFuse).mkString("(", ",", ")")

    def asId: String = series.map(_.asId).mkString("(", ",", ")")

    def seq: Seq[Branch] = series

    def iter: Iterable[Branch] = Iterable(this)

    def lastFuses: Iterable[Branch] =
    {
        series.lastOption match
        {
            case Some(last) => last.lastFuses
            case _ => Seq()
        }
    }

    def justFuses: Option[Branch] =
    {
        val fuses = series.flatMap(_.justFuses)
        fuses.toList match
        {
            case Nil =>
                None
            case single :: Nil =>
                single match
                {
                    case b: SimpleBranch =>
                        Some(SimpleBranch(from, to, current, b.mRID, b.name, b.rating, b.standard, b.z))
                    case p: ParallelBranch =>
                        Some(ParallelBranch(from, to, current, p.parallel))
                    case s: SeriesBranch =>
                        Some(SeriesBranch(from, to, current, s.series))
                    case c: ComplexBranch =>
                        Some(ComplexBranch(Array(from), to, current, c.basket))
                }
            case _ =>
                Some(SeriesBranch(from, to, current, fuses))
        }
    }

    def reverse: Branch = SeriesBranch(to, from, current, series.reverse.map(_.reverse))

    def ratios: Iterable[(Double, Branch)] =
    {
        series.lastOption match
        {
            case Some(last) => last.ratios
            case _ => Seq()
        }
    }

    def voltageRatio: Double = seq.foldLeft(1.0)((v, branch) => v * branch.voltageRatio)

    def z (in: Impedanzen): Impedanzen = seq.foldLeft(in)((z, branch) => branch.z(z))

    def getTransformerBranch: Option[TransformerBranch] =
    {
        series.headOption match
        {
            case Some(trafo) => trafo.getTransformerBranch
            case _ => None
        }
    }

    def contents: Iterable[Branch] = series

    def checkFuses (ik: Double, options: ShortCircuitOptions): (Boolean, Option[Branch]) =
    {
        // check if the last fuse blows, ToDo: not quite right, another series element could blow
        def fuses (arg: (Boolean, Option[Branch])): Option[(Boolean, Option[Branch])] =
        {
            val (blown, br) = arg
            if (blown)
                Some(arg)
            else
                br match
                {
                    case Some(branch) =>
                        branch.justFuses match
                        {
                            case Some(b) => Some((blown, Some(b)))
                            case None => None
                        }
                    case None => Some(arg)
                }
        }

        val new_series: List[(Boolean, Option[Branch])] = series.map(_.checkFuses(ik, options)).flatMap(fuses).toList
        new_series match
        {
            case Nil =>
                (false, Some(this))
            case _ :+ last =>
                val blows = last._1
                if (blows)
                    (blows, None)
                else
                    (false, Some(this))
        }
    }
}
