package ch.ninecode.sc.branch

import ch.ninecode.sc.Impedanzen
import ch.ninecode.sc.ShortCircuitOptions

/**
 * A group of elements in parallel.
 *
 * @param from     the 'from' node
 * @param to       the 'to' node
 * @param current  the current through this branch in the GridLAB-D experiment
 * @param parallel the branches in parallel, in no particular order
 */
case class ParallelBranch (
    override val from: String,
    override val to: String,
    override val current: Double,
    parallel: Iterable[Branch]) extends Branch(from, to, current)
{
    override def toString: String =
        s"""ParallelBranch ("$from" ⇒ "$to" ${current}A ${parallel.map(_.toString).mkString("[", ",", "]")})"""

    def asString: String = parallel.map(_.asString).mkString("[", ",", "]")

    def asFuse: String = parallel.map(_.asFuse).mkString("[", ",", "]")

    def asId: String = parallel.map(_.asId).mkString("[", ",", "]")

    def seq: Seq[ParallelBranch] = Seq(this)

    def iter: Iterable[Branch] = parallel

    def lastFuses: Iterable[Branch] = parallel.flatMap(_.lastFuses)

    def justFuses: Option[Branch] =
    {
        val fuses = parallel.flatMap(_.justFuses)
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
                Some(ParallelBranch(from, to, current, fuses))
        }
    }

    def reverse: Branch = ParallelBranch(to, from, current, parallel.map(_.reverse))

    def ratios: Iterable[(Double, Branch)] =
    {
        val currents = parallel.map(_.current)
        val sum = currents.sum
        if (0.0 == sum)
        {
            // use impedances
            val impedance = z(Impedanzen()).impedanz_low.modulus
            if (0.0 != impedance)
                // ToDo: use vector math
                parallel.map(branch => (impedance / branch.z(Impedanzen()).impedanz_low.modulus, branch))
            else
                parallel.map(x => (1.0 / parallel.size, x)) // split equally
        }
        else
            parallel.map(x => (x.current / sum, x))
    }

    // assume each branch has unity voltage ratio or the same voltage ratio
    @SuppressWarnings(Array("org.wartremover.warts.TraversableOps"))
    def voltageRatio: Double = parallel.head.iter.foldLeft(1.0)((v, branch) => v * branch.voltageRatio)

    def z (in: Impedanzen): Impedanzen =
    {
        val prev_impedance = getTransformerBranch match
        {
            case Some(trafoBranch) => trafoBranch.convert_impedance(in)
            case _ => in
        }

        val pz = parallel.map(_.z(Impedanzen()))
        pz.reduceOption((z1, z2) => z1.parallel(z2)) match
        {
            case Some(z) => prev_impedance + z
            case None => prev_impedance
        }
    }

    def getTransformerBranch: Option[TransformerBranch] =
    {
        parallel.headOption match
        {
            case Some(branch) =>
                branch match
                {
                    case trafo: TransformerBranch => Option(trafo)
                    case series: SeriesBranch => series.getTransformerBranch
                    case _ => None
                }
            case None => None
        }
    }

    def contents: Iterable[Branch] = parallel

    def checkFuses (ik: Double, options: ShortCircuitOptions): (Boolean, Option[Branch]) =
    {
        val new_parallel = ratios.map(
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
        val blows = new_parallel.exists(_._1)
        if (blows)
        {
            val remaining = new_parallel.flatMap(_._2)
            if (remaining.nonEmpty)
                (blows, Some(ParallelBranch(from, to, current, remaining)))
            else
                (blows, None)
        }
        else
            (false, Some(this))
    }
}
