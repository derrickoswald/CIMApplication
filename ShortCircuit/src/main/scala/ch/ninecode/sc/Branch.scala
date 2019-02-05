package ch.ninecode.sc

import ch.ninecode.gl.Complex

/**
 * A circuit branch.
 *
 * A two terminal device (or equivalent) with incoming (from) and outgoing (to) nodes,
 * a corresponding current from the load-flow simulation, and either
 * - mRID is populated with the value of the mRID of a simple two terminal device or
 * - series is populated with (two or more) sequential series connected branches, or
 * - parallel is populated with (two or more) parallel branches.
 *
 * We would like the JSON representation (of a Branch) to be something like:
 * var circuit =
 * {
 *     "series":
 *     [
 *         "a series branch mRID",
 *         "another series branch mRID",
 *         {
 *             "parallel":
 *             [
 *                 "a single element in a parallel branch",
 *                 {
 *                     "series":
 *                     [
 *                         "one element in series",
 *                         "two element in series"
 *                     ]
 *                 },
 *                "a further element in parallel"
 *             ]
 *         },
 *         "a final element in series"
 *     ]
 * }
 *
 * Note that the series and parallel constructs alternate, and so the labels are redundant
 * if the reader knows that they alternate starting with a series sequence.
 *
 * @param from    the 'from' node
 * @param to      the 'to' node
 * @param current the current through this branch in the GridLAB-D experiment
 */
abstract class Branch (val from: String, val to: String, val current: Double)
{
    def seq: Seq[Branch]

    def iter: Iterable[Branch]

    // this branch, but only fuses
    def justFuses: Option[Branch]

    def asString: String

    def asFuse: String

    def lastFuses: Iterable[Branch]

    def reverse: Branch

    def add_in_series (that: Branch): Branch =
    {
        if (to == that.from)
            Branch (from, that.to, current, this.seq ++ that.seq)
        else
            if (from == that.to)
                Branch (that.from, to, current, that.seq ++ this.seq)
            else
                if (to == that.to)
                    Branch (from, that.from, current, this.seq ++ that.seq.reverse)
                else
                    if (from == that.from)
                        Branch (that.to, to, current, that.seq.reverse ++ this.seq)
                    else
                        throw new IllegalArgumentException ("branches are not in series (%s, %s)".format (this, that))
    }

    def add_in_parallel (those: Iterable[Branch]): Branch =
    {
        if (those.forall (x ⇒ to == x.to && from == x.from))
            Branch (from, to, current + those.map (_.current).sum, this.iter ++ those.flatMap (x ⇒ x.iter))
        else
            throw new IllegalArgumentException ("branches are not in parallel (%s, %s)".format (this, those.map (_.toString).mkString (", ")))
    }

    // ratios of currents with the branch that the portion applies to
    def ratios: Iterable[(Double, Branch)]

    def z1: Complex
    def z0: Complex
}

/**
 * A simple two terminal element branch.
 *
 * @param from    the 'from' node
 * @param to      the 'to' node
 * @param current the current through this branch in the GridLAB-D experiment
 * @param mRID    the mRID of the CIM element
 * @param rating  the current rating if it is a fuse
 */
case class SimpleBranch (override val from: String, override val to: String, override val current: Double, mRID: String, rating: Option[Double] = None,
     z1: Complex = 0.0, z0: Complex = 0.0) extends Branch (from, to, current)
{
    override def toString: String = """SimpleBranch ("%s" ⇒ "%s" %sA %s%s)""".format (from, to, current, mRID, if (rating.isDefined) "@%s".format (rating.get) else "")

    def asString: String = "%s%s".format (mRID, if (rating.isDefined) "@%s".format (rating.get) else "")

    def asFuse: String = rating.getOrElse (0.0).toInt.toString

    def seq: Seq[SimpleBranch] = Seq (this)

    def iter: Iterable[SimpleBranch] = Iterable (this)

    def isFuse: Boolean = rating.isDefined

    def lastFuses: Iterable[Branch] = Seq (this)

    def justFuses: Option[Branch] = if (isFuse) Some (this) else None

    def reverse: Branch = SimpleBranch (to, from, current, mRID, rating)

    def ratios: Iterable[(Double, Branch)] = List((1.0, this))
}

/**
 * A group of series connected elements.
 *
 * @param from    the 'from' node
 * @param to      the 'to' node
 * @param current the current through this branch in the GridLAB-D experiment
 * @param series  the branches in series, in order
 */
case class SeriesBranch (override val from: String, override val to: String, override val current: Double, series: Seq[Branch]) extends Branch (from, to, current)
{
    override def toString: String = """SeriesBranch ("%s" ⇒ "%s" %sA %s)""".format (from, to, current, series.map (_.toString).mkString ("+"))

    def asString: String = series.map (_.asString).mkString ("(", ",", ")")

    def asFuse: String = series.map (_.asFuse).mkString ("(", ",", ")")

    def seq: Seq[Branch] = series

    def iter: Iterable[SeriesBranch] = Iterable (this)

    def lastFuses: Iterable[Branch] = series.last.lastFuses

    def justFuses: Option[Branch] =
    {
        val fuses = series.flatMap (_.justFuses)
        if ((fuses.size == 1) && fuses.head.isInstanceOf [SimpleBranch])
        {
            val branch = fuses.head.asInstanceOf [SimpleBranch]
            Some (SimpleBranch (this.from, this.to, this.current, branch.mRID, branch.rating))
        }
        else
            if (fuses.nonEmpty)
                Some (SeriesBranch (this.from, this.to, this.current, fuses))
            else
                None
    }

    def reverse: Branch = SeriesBranch (to, from, current, series.reverse.map (_.reverse))

    def ratios: Iterable[(Double, Branch)] = series.last.ratios

    def z1: Complex = seq.foldRight (Complex (0.0)) ((a, b) ⇒ b + a.z1)

    def z0: Complex = seq.foldRight (Complex (0.0)) ((a, b) ⇒ b + a.z0)
}

/**
 * A group of elements in parallel.
 *
 * @param from     the 'from' node
 * @param to       the 'to' node
 * @param current  the current through this branch in the GridLAB-D experiment
 * @param parallel the branches in parallel, in no particular order
 */
case class ParallelBranch (override val from: String, override val to: String, override val current: Double, parallel: Iterable[Branch]) extends Branch (from, to, current)
{
    override def toString: String = """ParallelBranch ("%s" ⇒ "%s" %sA %s)""".format (from, to, current, parallel.map (_.toString).mkString ("[", ",", "]"))

    def asString: String = parallel.map (_.asString).mkString ("[", ",", "]")

    def asFuse: String = parallel.map (_.asFuse).mkString ("[", ",", "]")

    def seq: Seq[ParallelBranch] = Seq (this)

    def iter: Iterable[Branch] = parallel

    def lastFuses: Iterable[Branch] = parallel.flatMap (_.lastFuses)

    def justFuses: Option[Branch] =
    {
        val fuses = parallel.flatMap (_.justFuses)
        if ((fuses.size == 1) && fuses.head.isInstanceOf [SimpleBranch])
        {
            val branch = fuses.head.asInstanceOf [SimpleBranch]
            Some (SimpleBranch (this.from, this.to, this.current, branch.mRID, branch.rating)) // ToDo: parallel cables and fuses makes no sense, what's the current rating?
        }
        else
            if (fuses.nonEmpty)
                Some (ParallelBranch (this.from, this.to, this.current, fuses))
            else
                None
    }

    def reverse: Branch = ParallelBranch (to, from, current, parallel.map (_.reverse))

    def ratios: Iterable[(Double, Branch)] =
    {
        val currents = parallel.map (_.current)
        val sum = currents.sum
        if (0.0 == sum)
            parallel.map (x ⇒ (1.0 / parallel.size, x)) // split equally
        else
            parallel.map (x ⇒ (x.current / sum, x))
    }

    def z1: Complex = parallel.tail.foldRight (parallel.head.z1) ((a, b) ⇒ b.parallel_impedanz (a.z1))

    def z0: Complex = parallel.tail.foldRight (parallel.head.z0) ((a, b) ⇒ b.parallel_impedanz (a.z0))
}

object Branch
{
    def apply (from: String, to: String, current: Double, mRID: String, rating: Option[Double]) = SimpleBranch (from, to, current, mRID, rating)

    def apply (from: String, to: String, current: Double, series: Seq[Branch]) = SeriesBranch (from, to, current, series)

    def apply (from: String, to: String, current: Double, parallel: Iterable[Branch]) = ParallelBranch (from, to, current, parallel)
}

