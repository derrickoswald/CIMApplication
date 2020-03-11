package ch.ninecode.sc

import ch.ninecode.util.Complex

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

    // voltage ratio between from and to node = vto / vfrom
    def voltageRatio: Double

    def z (in: Impedanzen): Impedanzen

    def contents: Iterable[Branch]

    /**
     * Check if a fuse would blow given the short circuit power and the network of cables and fuses.
     *
     * @param ik short-circuit current to check against
     * @return tuple with a boolean <code>true</code> if the network would change because of the short circuit, or <code>false</code> if it would not,
     *         and either the remaining network with the change, or the original network if there would be no change
     */
    def checkFuses (ik: Double): (Boolean, Option[Branch])
}

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

    def reverse: Branch = SimpleBranch (to, from, current, mRID, name, rating, z)

    def ratios: Iterable[(Double, Branch)] = List((1.0, this))

    def voltageRatio: Double = 1.0

    def z (in: Impedanzen): Impedanzen = in + z

    def contents: Iterable[SimpleBranch] = Iterable (this)

    def checkFuses (ik: Double): (Boolean, Option[Branch]) =
    {
        if (isFuse)
        {
            val _rating = rating.getOrElse (Double.MaxValue)
            if (0.0 == _rating)
                (false, None)
            else
                if (FData.fuse (ik, this) >= _rating)
                    (true, None)
                else
                    (false, Some (this))
        }
        else
            (false, Some (this))
    }
}

/**
 * A transformer branch.
 *
 * @param from    the 'from' node
 * @param to      the 'to' node
 * @param current the current through this branch in the GridLAB-D experiment
 * @param mRID    the mRID of the CIM element
 * @param name    the name of the CIM element
 * @param pu      the per unit impedance
 */
case class TransformerBranch (override val from: String, override val to: String, override val current: Double, mRID: String, name: String,
                              s: Double, vfrom: Double, vto: Double, pu: Complex) extends Branch (from, to, current)
{
    override def toString: String = """TransformerBranch ("%s" ⇒ "%s" %sA %s %s)""".format (
        from,
        to,
        current,
        mRID,
        if (null != name) name else "")

    def asString: String = "%s".format (mRID)

    def asFuse: String = ""

    def asId: String = ""

    def seq: Seq[TransformerBranch] = Seq (this)

    def iter: Iterable[TransformerBranch] = Iterable (this)

    def isFuse: Boolean = false

    def lastFuses: Iterable[Branch] = Seq ()

    def justFuses: Option[Branch] = None

    def reverse: Branch = TransformerBranch (to, from, current, mRID, name, s, vto, vfrom, pu)

    def ratios: Iterable[(Double, Branch)] = List((1.0, this))

    def voltageRatio: Double = vto / vfrom

    def contents: Iterable[TransformerBranch] = Iterable (this)

    def checkFuses (ik: Double): (Boolean, Option[Branch]) =
    {
        (false, Some (this))
    }

    def z (in: Impedanzen): Impedanzen =
    {
        val base_ohms = vto * vto / s
        val secondary_z = pu * base_ohms
        val ratio = vto / vfrom
        val ratio2 = ratio * ratio
        // use r0=r1 & x0=x1 for trafos, assume the temperature effects are negligible
        in * ratio2 + Impedanzen (secondary_z, secondary_z, secondary_z, secondary_z)
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

    def iter: Iterable[Branch] = Iterable(this)

    def lastFuses: Iterable[Branch] = series.last.lastFuses

    def justFuses: Option[Branch] =
    {
        val fuses = series.flatMap (_.justFuses)
        if (1 == fuses.size)
            fuses.head match
            {
                case b: SimpleBranch =>
                    Some (SimpleBranch (this.from, this.to, this.current, b.mRID, b.name, b.rating, b.z))
                case p: ParallelBranch =>
                    Some (ParallelBranch (this.from, this.to, this.current, p.parallel))
                case s: SeriesBranch =>
                    Some (SeriesBranch (this.from, this.to, this.current, s.series))
                case c: ComplexBranch =>
                    Some (ComplexBranch (this.from, this.to, this.current, c.basket))
            }
        else
            if (fuses.nonEmpty)
                Some (SeriesBranch (this.from, this.to, this.current, fuses))
            else
                None
    }

    def reverse: Branch = SeriesBranch (to, from, current, series.reverse.map (_.reverse))

    def ratios: Iterable[(Double, Branch)] = series.last.ratios

    def voltageRatio: Double = seq.foldLeft (1.0) ((v, branch) ⇒ v * branch.voltageRatio)

    def z (in: Impedanzen): Impedanzen = seq.foldLeft (in) ((z, branch) ⇒ branch.z (z))

    def contents: Iterable[Branch] = this.series

    def checkFuses (ik: Double): (Boolean, Option[Branch]) =
    {
        // check if the last fuse blows, ToDo: not quite right, another series element could blow
        def fuses (arg: (Boolean, Option[Branch])): Option[(Boolean, Option[Branch])] =
        {
            val (blown, br) = arg
            if (blown)
                Some (arg)
            else
                br match
                {
                    case Some (branch) =>
                        branch.justFuses match
                        {
                            case Some (b) => Some ((blown, Some (b)))
                            case None => None
                        }
                    case None => Some (arg)
                }
        }
        val new_series: Seq[(Boolean, Option[Branch])] = series.map (_.checkFuses (ik)).flatMap (fuses)
        if (0 < new_series.size)
        {
            val blows = new_series.last._1
            if (blows)
                (blows, None)
            else
                (false, Some (this))
        }
        else
            (false, Some (this))
    }
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
        if (1 == fuses.size)
            fuses.head match
            {
                case b: SimpleBranch =>
                    Some (SimpleBranch (this.from, this.to, this.current, b.mRID, b.name, b.rating, b.z))
                case p: ParallelBranch =>
                    Some (ParallelBranch (this.from, this.to, this.current, p.parallel))
                case s: SeriesBranch =>
                    Some (SeriesBranch (this.from, this.to, this.current, s.series))
                case c: ComplexBranch =>
                    Some (ComplexBranch (this.from, this.to, this.current, c.basket))
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
            val impedance = z (Impedanzen ()).impedanz_low.modulus
            if (0.0 != impedance)
                parallel.map (branch ⇒ (impedance / branch.z (Impedanzen ()).impedanz_low.modulus, branch)) // ToDo: use vector math
            else
                parallel.map (x ⇒ (1.0 / parallel.size, x)) // split equally
        }
        else
            parallel.map (x ⇒ (x.current / sum, x))
    }

    // assume each branch has unity voltage ratio or the same voltage ratio
    def voltageRatio: Double = parallel.head.iter.foldLeft (1.0) ((v, branch) ⇒ v * branch.voltageRatio)

    def z (in: Impedanzen): Impedanzen =
    {
        val pz = parallel.map (_.z (Impedanzen ()))
        in + pz.tail.foldLeft (pz.head) ((z, bz) ⇒ bz.parallel (z))
    }

    def contents: Iterable[Branch] = parallel

    def checkFuses (ik: Double): (Boolean, Option[Branch]) =
    {
        val new_parallel = ratios.map (
            pair =>
            {
                val (fraction, branch) = pair
                val current = fraction * Math.abs (ik)
                if (current.isNaN)
                    (false, None)
                else
                    branch.checkFuses (current)
            }
        )
        val blows = new_parallel.exists (_._1)
        if (blows)
        {
            val remaining = new_parallel.flatMap (_._2)
            if (remaining.nonEmpty)
                (blows, Some (ParallelBranch (from, to, current, remaining)))
            else
                (blows, None)
        }
        else
            (false, Some (this))
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
        if (1 == fuses.length)
            fuses.head match
            {
                case b: SimpleBranch =>
                    Some (SimpleBranch (this.from, this.to, this.current, b.mRID, b.name, b.rating, b.z))
                case p: ParallelBranch =>
                    Some (ParallelBranch (this.from, this.to, this.current, p.parallel))
                case s: SeriesBranch =>
                    Some (SeriesBranch (this.from, this.to, this.current, s.series))
                case c: ComplexBranch =>
                    Some (ComplexBranch (this.from, this.to, this.current, c.basket))
            }
        else
            if (fuses.nonEmpty)
                Some (ComplexBranch (this.from, this.to, this.current, fuses))
            else
                None
    }

    def reverse: Branch = ComplexBranch (to, from, current, basket.map (_.reverse))

    def ratios: Iterable[(Double, Branch)] = basket.map (x ⇒ (x.current / current, x))

    def voltageRatio: Double = basket.foldLeft (1.0) ((v, branch) ⇒ v * branch.voltageRatio)

    /**
     * NOTE: this is totally wrong. It just puts a upper and lower bound on the actual impedance.
     * @return a fake impedance value
     */
    def z (in: Impedanzen): Impedanzen =
    {
        // compute the lowest impedance as three parallel branch circuits (this will be used for the low temperature values)
        var low_impedance = Impedanzen (0.0, 0.0, 0.0, 0.0)
        val start_branches = basket.filter (_.from == from)
        if (0 != start_branches.length) low_impedance = low_impedance + (if (1 < start_branches.length) ParallelBranch (from, to, 0.0, start_branches) else SeriesBranch (from, to, 0.0, start_branches)).z (in)
        val middle_branches = basket.filter (x ⇒ x.from != from && x.to != to)
        if (0 != middle_branches.length) low_impedance = low_impedance + (if (1 < middle_branches.length) ParallelBranch (from, to, 0.0, middle_branches) else SeriesBranch (from, to, 0.0, middle_branches)).z (in)
        val end_branches = basket.filter (_.to == to)
        if (0 != end_branches.length) low_impedance = low_impedance + (if (1 < end_branches.length) ParallelBranch (from, to, 0.0, end_branches) else SeriesBranch (from, to, 0.0, end_branches)).z (in)
        // compute the highest impedance as series branches (this will be used for the high temperature values)
        val high_impedance = SeriesBranch (from, to, 0.0, basket).z (in)
        // take the worst case from both
        Impedanzen (low_impedance.impedanz_low, low_impedance.null_impedanz_low, high_impedance.impedanz_high, high_impedance.null_impedanz_high)
    }

    def contents: Iterable[Branch] = basket

    def checkFuses (ik: Double): (Boolean, Option[Branch]) =
    {
        val new_complex = ratios.map (
            pair =>
            {
                val (fraction, branch) = pair
                val current = fraction * Math.abs (ik)
                if (current.isNaN)
                    (false, None)
                else
                    branch.checkFuses (current)
            }
        )
        val blows = new_complex.exists (_._1)
        if (blows)
            (blows, Some (ComplexBranch (from, to, current, new_complex.flatMap (_._2).toArray)))
        else
            (false, Some (this))
    }
}

object Branch
{
    def apply (from: String, to: String, current: Double, mRID: String, name: String, rating: Option[Double]): SimpleBranch = SimpleBranch (from, to, current, mRID, name, rating)

    def apply (from: String, to: String, current: Double, mRID: String, name: String, s: Double, vfrom: Double, vto: Double, pu: Complex): TransformerBranch = TransformerBranch (from, to, current, mRID, name, s, vfrom, vto, pu)

    def apply (from: String, to: String, current: Double, series: Seq[Branch]): SeriesBranch = SeriesBranch (from, to, current, series)

    def apply (from: String, to: String, current: Double, parallel: Iterable[Branch]): ParallelBranch = ParallelBranch (from, to, current, parallel)

    def apply (from: String, to: String, current: Double, basket: Array[Branch]): ComplexBranch = ComplexBranch (from, to, current, basket)
}
