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
 * "series":
 * [
 * "a series branch mRID",
 * "another series branch mRID",
 * {
 * "parallel":
 * [
 * "a single element in a parallel branch",
 * {
 * "series":
 * [
 * "one element in series",
 * "two element in series"
 * ]
 * },
 * "a further element in parallel"
 * ]
 * },
 * "a final element in series"
 * ]
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

    @SuppressWarnings(Array("org.wartremover.warts.Throw"))
    def add_in_series (that: Branch): Branch =
    {
        val i = Math.max(current, that.current)
        if (to == that.from)
            Branch(from, that.to, i, this.seq ++ that.seq)
        else
            if (from == that.to)
                Branch(that.from, to, i, that.seq ++ this.seq)
            else
                if (to == that.to)
                    Branch(from, that.from, i, this.seq ++ that.seq.reverse)
                else
                    if (from == that.from)
                        Branch(that.to, to, i, that.seq.reverse ++ this.seq)
                    else
                        throw new IllegalArgumentException(s"branches are not in series (${this}, ${that})")
    }

    @SuppressWarnings(Array("org.wartremover.warts.Throw"))
    def add_in_parallel (those: Iterable[Branch]): Branch =
    {
        if (those.forall(x => to == x.to && from == x.from))
            Branch(from, to, current + those.map(_.current).sum, this.iter ++ those.flatMap(x => x.iter))
        else
            throw new IllegalArgumentException(s"branches are not in parallel (${this}, ${those.map(_.toString).mkString(", ")})")
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
     * @param ik      short-circuit current to check against
     * @param options the options containing cmin and the fuse table
     * @return tuple with a boolean <code>true</code> if the network would change because of the short circuit, or <code>false</code> if it would not,
     *         and either the remaining network with the change, or the original network if there would be no change
     */
    def checkFuses (ik: Double, options: ShortCircuitOptions): (Boolean, Option[Branch])

    def hasSameFrom (other: Branch): Boolean =
    {
        from == other.from
    }

    def hasSameTo (other: Branch): Boolean =
    {
        to == other.to
    }

    def isParallelTo (other: Branch): Boolean =
    {
        this.hasSameFrom(other) && this.hasSameTo(other)
    }
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
    s: Double, vfrom: Double, vto: Double, pu: Complex, reference_impedanzen: Option[Impedanzen]) extends Branch(from, to, current)
{
    override def toString: String =
    {
        val n = name match
        {
            case s: String => s
            case _ => ""
        }
        s"""TransformerBranch ("$from" ⇒ "$to" ${current}A $mRID $n)"""
    }

    def asString: String = s"$mRID"

    def asFuse: String = ""

    def asId: String = ""

    def seq: Seq[TransformerBranch] = Seq(this)

    def iter: Iterable[TransformerBranch] = Iterable(this)

    def isFuse: Boolean = false

    def lastFuses: Iterable[Branch] = Seq()

    def justFuses: Option[Branch] = None

    def reverse: Branch = TransformerBranch(to, from, current, mRID, name, s, vto, vfrom, pu, reference_impedanzen)

    def ratios: Iterable[(Double, Branch)] = List((1.0, this))

    def voltageRatio: Double = vto / vfrom

    def contents: Iterable[TransformerBranch] = Iterable(this)

    def checkFuses (ik: Double, options: ShortCircuitOptions): (Boolean, Option[Branch]) =
    {
        (false, Some(this))
    }

    def z (in: Impedanzen): Impedanzen =
    {
        val base_ohms = vto * vto / s
        val secondary_z = pu * base_ohms
        val ratio = vto / vfrom
        val ratio2 = ratio * ratio

        val in_converted = in * ratio2
        val trafo_impedance = reference_impedanzen.getOrElse(Impedanzen(secondary_z, secondary_z, secondary_z, secondary_z))

        in_converted + trafo_impedance
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
case class SeriesBranch (override val from: String, override val to: String, override val current: Double, series: Seq[Branch]) extends Branch(from, to, current)
{
    override def toString: String = s"""SeriesBranch ("$from" ⇒ "$to" ${current}A ${series.map(_.toString).mkString("+")})"""

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
                        Some(ComplexBranch(from, to, current, c.basket))
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

/**
 * A group of elements in parallel.
 *
 * @param from     the 'from' node
 * @param to       the 'to' node
 * @param current  the current through this branch in the GridLAB-D experiment
 * @param parallel the branches in parallel, in no particular order
 */
case class ParallelBranch (override val from: String, override val to: String, override val current: Double, parallel: Iterable[Branch]) extends Branch(from, to, current)
{
    override def toString: String = s"""ParallelBranch ("$from" ⇒ "$to" ${current}A ${parallel.map(_.toString).mkString("[", ",", "]")})"""

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
                        Some(ComplexBranch(from, to, current, c.basket))
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
                parallel.map(branch => (impedance / branch.z(Impedanzen()).impedanz_low.modulus, branch)) // ToDo: use vector math
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
        val pz = parallel.map(_.z(Impedanzen()))
        pz.reduceOption((z1, z2) => z1.parallel(z2)) match
        {
            case Some(z) => in + z
            case None => in
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

/**
 * A group of elements too complex to reduce to a combination of series and parallel branches.
 *
 * @param from    the 'from' node
 * @param to      the 'to' node
 * @param current the current through this branch in the GridLAB-D experiment
 * @param basket  the branches in no particular order
 */
case class ComplexBranch (override val from: String, override val to: String, override val current: Double, basket: Array[Branch]) extends Branch(from, to, current)
{
    override def toString: String = s"""ComplexBranch ("$from" ⇒ "$to" ${current}A ${basket.map(_.toString).mkString("[", ",", "]")})"""

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
     * NOTE: this is totally wrong. It just puts a upper and lower bound on the actual impedance.
     *
     * @return a fake impedance value
     */
    def z (in: Impedanzen): Impedanzen =
    {
        // compute the lowest impedance as three parallel branch circuits (this will be used for the low temperature values)
        var low_impedance = Impedanzen(0.0, 0.0, 0.0, 0.0)
        val start_branches = basket.filter(_.from == from)
        if (0 != start_branches.length) low_impedance = low_impedance + (if (1 < start_branches.length) ParallelBranch(from, to, 0.0, start_branches) else SeriesBranch(from, to, 0.0, start_branches)).z(in)
        val middle_branches = basket.filter(x => x.from != from && x.to != to)
        if (0 != middle_branches.length) low_impedance = low_impedance + (if (1 < middle_branches.length) ParallelBranch(from, to, 0.0, middle_branches) else SeriesBranch(from, to, 0.0, middle_branches)).z(in)
        val end_branches = basket.filter(_.to == to)
        if (0 != end_branches.length) low_impedance = low_impedance + (if (1 < end_branches.length) ParallelBranch(from, to, 0.0, end_branches) else SeriesBranch(from, to, 0.0, end_branches)).z(in)
        // compute the highest impedance as series branches (this will be used for the high temperature values)
        val high_impedance = SeriesBranch(from, to, 0.0, basket).z(in)
        // take the worst case from both
        Impedanzen(low_impedance.impedanz_low, low_impedance.null_impedanz_low, high_impedance.impedanz_high, high_impedance.null_impedanz_high)
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

object Branch
{
    def apply (from: String, to: String, current: Double, mRID: String, name: String, standard: String, rating: Option[Double]): SimpleBranch = SimpleBranch(from, to, current, mRID, name, rating, standard)

    def apply (from: String, to: String, current: Double, mRID: String, name: String, s: Double, vfrom: Double, vto: Double, pu: Complex): TransformerBranch = TransformerBranch(from, to, current, mRID, name, s, vfrom, vto, pu, None)

    def apply (from: String, to: String, current: Double, series: Seq[Branch]): SeriesBranch = SeriesBranch(from, to, current, series)

    def apply (from: String, to: String, current: Double, parallel: Iterable[Branch]): ParallelBranch = ParallelBranch(from, to, current, parallel)

    def apply (from: String, to: String, current: Double, basket: Array[Branch]): ComplexBranch = ComplexBranch(from, to, current, basket)
}
