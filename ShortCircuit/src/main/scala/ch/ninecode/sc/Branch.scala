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

    def asId: String

    def lastFuses: Iterable[Branch]

    def reverse: Branch

    def add_in_series (that: Branch): Branch =
    {
        val i = Math.max (current, that.current)
        if (to == that.from)
            Branch (from, that.to, i, this.seq ++ that.seq)
        else
            if (from == that.to)
                Branch (that.from, to, i, that.seq ++ this.seq)
            else
                if (to == that.to)
                    Branch (from, that.from, i, this.seq ++ that.seq.reverse)
                else
                    if (from == that.from)
                        Branch (that.to, to, i, that.seq.reverse ++ this.seq)
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

    def z: Impedanzen

    def fuseThatBlows (ik: Double): Option[Branch]
}

/**
 * A simple two terminal element branch.
 *
 * @param from    the 'from' node
 * @param to      the 'to' node
 * @param current the current through this branch in the GridLAB-D experiment
 * @param mRID    the mRID of the CIM element
 * @param rating  the current rating if it is a fuse
 * @param z       the positive and zero sequence impedances at the operational temperatures
 */
case class SimpleBranch (override val from: String, override val to: String, override val current: Double, mRID: String, name: String, rating: Option[Double] = None,
     z: Impedanzen = Impedanzen (0.0, 0.0, 0.0, 0.0)) extends Branch (from, to, current)
{
    override def toString: String = """SimpleBranch ("%s" ⇒ "%s" %sA %s%s %s)""".format (
        from,
        to,
        current,
        mRID,
        if (rating.isDefined) "@%s".format (rating.get) else "",
        if (null != name) name else "")

    def asString: String = "%s%s".format (mRID, if (rating.isDefined) "@%s".format (rating.get) else "")

    def asFuse: String = rating.getOrElse (0.0).toInt.toString

    def asId: String = if (rating.isDefined) mRID else ""

    def seq: Seq[SimpleBranch] = Seq (this)

    def iter: Iterable[SimpleBranch] = Iterable (this)

    def isFuse: Boolean = rating.isDefined

    def lastFuses: Iterable[Branch] = Seq (this)

    def justFuses: Option[Branch] = if (isFuse) Some (this) else None

    def reverse: Branch = SimpleBranch (to, from, current, mRID, name, rating)

    def ratios: Iterable[(Double, Branch)] = List((1.0, this))

    def fuseThatBlows (ik: Double): Option[Branch] =
    {
        val _rating = rating.getOrElse (Double.MaxValue)
        if (0.0 == _rating)
            None
        else
            if (FData.fuse (ik, this) >= _rating)
                Some (this)
            else
                None
    }
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

    def asId: String = series.map (_.asId).mkString ("(", ",", ")")

    def seq: Seq[Branch] = series

    def iter: Iterable[SeriesBranch] = Iterable (this)

    def lastFuses: Iterable[Branch] = series.last.lastFuses

    def justFuses: Option[Branch] =
    {
        val fuses = series.flatMap (_.justFuses)
        if ((fuses.size == 1) && fuses.head.isInstanceOf [SimpleBranch])
        {
            val branch = fuses.head.asInstanceOf [SimpleBranch]
            Some (SimpleBranch (this.from, this.to, this.current, branch.mRID, branch.name, branch.rating))
        }
        else
            if (fuses.nonEmpty)
                Some (SeriesBranch (this.from, this.to, this.current, fuses))
            else
                None
    }

    def reverse: Branch = SeriesBranch (to, from, current, series.reverse.map (_.reverse))

    def ratios: Iterable[(Double, Branch)] = series.last.ratios

    def z: Impedanzen = seq.foldRight (Impedanzen (0.0, 0.0, 0.0, 0.0)) ((branch, z) ⇒ branch.z + z)

    def fuseThatBlows (ik: Double): Option[Branch] =
        series.last.fuseThatBlows (ik) // only check the last fuse to limit the outage impact
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

    def asId: String = parallel.map (_.asId).mkString ("[", ",", "]")

    def seq: Seq[ParallelBranch] = Seq (this)

    def iter: Iterable[Branch] = parallel

    def lastFuses: Iterable[Branch] = parallel.flatMap (_.lastFuses)

    def justFuses: Option[Branch] =
    {
        val fuses = parallel.flatMap (_.justFuses)
        if ((fuses.size == 1) && fuses.head.isInstanceOf [SimpleBranch])
        {
            val branch = fuses.head.asInstanceOf [SimpleBranch]
            Some (SimpleBranch (this.from, this.to, this.current, branch.mRID, branch.name, branch.rating)) // ToDo: parallel cables and fuses makes no sense, what's the current rating?
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
        {
            // use impedances
            val impedance = z.impedanz_low.modulus
            if (0.0 != impedance)
                parallel.map (branch ⇒ (impedance / branch.z.impedanz_low.modulus, branch)) // ToDo: use vector math
            else
                parallel.map (x ⇒ (1.0 / parallel.size, x)) // split equally
        }
        else
            parallel.map (x ⇒ (x.current / sum, x))
    }

    def z: Impedanzen = parallel.tail.foldRight (parallel.head.z) ((branch, z) ⇒ branch.z.parallel (z))

    def fuseThatBlows (ik: Double): Option[Branch] =
    {
        val dead = ratios.map (
            pair =>
            {
                val current = pair._1 * Math.abs (ik)
                if (current.isNaN)
                    None
                else
                    pair._2.fuseThatBlows (current)
            }
        ).toArray.flatten
        if (0 == dead.length)
            None
        else
        {
            // remove the branch from the parallel set and try again
            // Note: the fuse blowing would mean recomputing the whole network based on impedance
            // but we cheat and only look at this small section
            // ToDo: recompute entire branch
            val remaining = parallel.filter (!dead.contains(_))
            if (0 == remaining.size)
                Some (this)
            else if (1 == remaining.size)
                remaining.head.fuseThatBlows (ik) match
                {
                    case Some (_) => Some (this)
                    case _ => None
                }
            else
                this.copy (parallel = remaining).fuseThatBlows (ik) match
                {
                    case Some (_) => Some (this)
                    case _ => None
                }
        }
    }
}

/**
 * A group of elements too complex to reduce to a combination of series and parallel branches.
 *
 * @param from     the 'from' node
 * @param to       the 'to' node
 * @param current  the current through this branch in the GridLAB-D experiment
 * @param basket   the branches in no particular order
 */
case class ComplexBranch (override val from: String, override val to: String, override val current: Double, basket: Array[Branch]) extends Branch (from, to, current)
{
    override def toString: String = """ComplexBranch ("%s" ⇒ "%s" %sA %s)""".format (from, to, current, basket.map (_.toString).mkString ("[", ",", "]"))

    def asString: String = basket.map (_.asString).mkString ("{", ",", "}")

    def asFuse: String = basket.map (_.asFuse).mkString ("{", ",", "}")

    def asId: String = basket.map (_.asId).mkString ("{", ",", "}")

    def seq: Seq[ComplexBranch] = Seq (this)

    def iter: Iterable[Branch] = basket

    def lastFuses: Iterable[Branch] =
    {
        justFuses match { case Some (fuses) ⇒ Seq(fuses) case None ⇒ Seq() }
    }

    def justFuses: Option[Branch] =
    {
        val fuses = basket.flatMap (_.justFuses)
        if ((fuses.length == 1) && fuses.head.isInstanceOf [SimpleBranch])
        {
            val branch = fuses.head.asInstanceOf [SimpleBranch]
            Some (SimpleBranch (this.from, this.to, this.current, branch.mRID, branch.name, branch.rating)) // ToDo: parallel cables and fuses makes no sense, what's the current rating?
        }
        else
            if (fuses.nonEmpty)
                Some (ComplexBranch (this.from, this.to, this.current, fuses))
            else
                None
    }

    def reverse: Branch = ComplexBranch (to, from, current, basket.map (_.reverse))

    def ratios: Iterable[(Double, Branch)] =
    {
        basket.map (x ⇒ (x.current / current, x))
    }

    /**
     * NOTE: this is totally wrong. It just puts a upper and lower bound on the actual impedance.
     * @return a fake impedance value
     */
    def z: Impedanzen =
    {
        // compute the lowest impedance as three parallel branch circuits (this will be used for the low temperature values)
        var low_impedance = Impedanzen (0.0, 0.0, 0.0, 0.0)
        val start_branches = basket.filter (_.from == from)
        if (0 != start_branches.length) low_impedance = low_impedance + (if (1 < start_branches.length) ParallelBranch (from, to, 0.0, start_branches) else SeriesBranch (from, to, 0.0, start_branches)).z
        val middle_branches = basket.filter (x ⇒ x.from != from && x.to != to)
        if (0 != middle_branches.length) low_impedance = low_impedance + (if (1 < middle_branches.length) ParallelBranch (from, to, 0.0, middle_branches) else SeriesBranch (from, to, 0.0, middle_branches)).z
        val end_branches = basket.filter (_.to == to)
        if (0 != end_branches.length) low_impedance = low_impedance + (if (1 < end_branches.length) ParallelBranch (from, to, 0.0, end_branches) else SeriesBranch (from, to, 0.0, end_branches)).z
        // compute the highest impedance as series branches (this will be used for the high temperature values)
        var high_impedance = SeriesBranch (from, to, 0.0, basket).z
        // take the worst case from both
        Impedanzen (low_impedance.impedanz_low, low_impedance.null_impedanz_low, high_impedance.impedanz_high, high_impedance.null_impedanz_high)
    }

    def fuseThatBlows (ik: Double): Option[Branch] =
    {
        // cheat here and say any fuse that blows is good enough
        // ToDo: handle complex network
        val dead = ratios.map (
            pair =>
            {
                val current = pair._1 * Math.abs (ik)
                if (current.isNaN)
                    None
                else
                    pair._2.fuseThatBlows (current)
            }
        ).toArray.flatten
        if (0 == dead.length)
            None
        else
            Some (dead(0))
    }
}

object Branch
{
    def apply (from: String, to: String, current: Double, mRID: String, name: String, rating: Option[Double]) = SimpleBranch (from, to, current, mRID, name, rating)

    def apply (from: String, to: String, current: Double, series: Seq[Branch]) = SeriesBranch (from, to, current, series)

    def apply (from: String, to: String, current: Double, parallel: Iterable[Branch]) = ParallelBranch (from, to, current, parallel)

    def apply (from: String, to: String, current: Double, basket: Array[Branch]) = ComplexBranch (from, to, current, basket)
}

