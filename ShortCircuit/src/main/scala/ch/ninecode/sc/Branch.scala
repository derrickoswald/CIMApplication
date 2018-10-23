package ch.ninecode.sc

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
 * @param from
 * @param to
 * @param current
 * @param parallel
 */
abstract class Branch (val from: String, val to: String, val current: Double)
{
    def seq: Seq[Branch]
    def iter: Iterable[Branch]
    def justFuses: Option[Branch] // this branch, but only fuses
    def asString: String
    def asFuse: Seq[(String, Double)]

    def add_in_series (that: Branch): Branch =
    {
        if (to == that.from)
            Branch (from, that.to, current, this.seq ++ that.seq)
        else if (from == that.to)
            Branch (that.from, to, current, that.seq ++ this.seq)
        else if (to == that.to)
            Branch (from, that.from, current, this.seq ++ that.seq.reverse)
        else if (from == that.from)
            Branch (that.to, to, current, that.seq.reverse ++ this.seq)
        else
            throw new IllegalArgumentException ("branches are not in series (%s, %s)".format (this, that))
    }

    def add_in_parallel (those: Iterable[Branch]): Branch =
    {
        if (those.forall (x ⇒ to == x.to && from == x.from))
            Branch (from , to, current + those.map (_.current).sum, this.iter ++ those.flatMap (x ⇒ x.iter))
        else
            throw new IllegalArgumentException ("branches are not in parallel (%s, %s)".format (this, those.map (_.toString).mkString (", ")))
    }
}

case class SimpleBranch (override val from: String, override val to: String, override val current: Double, mRID: String, rating: Option[Double]) extends Branch (from, to, current)
{
    override def toString: String =  """SimpleBranch ("%s" ⇒ "%s" %sA %s%s)""".format (from, to, current, mRID, if (rating.isDefined) "@%s".format (rating.get) else "")
    def asString: String =  "%s%s".format (mRID, if (rating.isDefined) "@%s".format (rating.get) else "")
    def asFuse: Seq[(String, Double)] = Seq((mRID, rating.getOrElse (0.0)))
    def seq = Seq (this)
    def iter = Iterable (this)
    def isFuse: Boolean = rating.isDefined
    def justFuses: Option[Branch] = if (isFuse) Some (this) else None
}

case class SeriesBranch (override val from: String, override val to: String, override val current: Double, series: Seq[Branch]) extends Branch (from, to, current)
{
    override def toString: String =  """SeriesBranch ("%s" ⇒ "%s" %sA %s)""".format (from, to, current, series.map (_.toString).mkString ("+"))
    def asString: String =  """%s""".format (series.map (_.asString).mkString ("(", ",", ")"))
    def asFuse: Seq[(String, Double)] = series.flatMap (_.asFuse)
    def seq: Seq[Branch] = series
    def iter = Iterable (this)
    def justFuses: Option[Branch] =
    {
        val fuses = series.flatMap (_.justFuses)
        if ((fuses.size == 1) && fuses.head.isInstanceOf[SimpleBranch])
        {
            val branch = fuses.head.asInstanceOf[SimpleBranch]
            Some (SimpleBranch (this.from, this.to, this.current, branch.mRID, branch.rating))
        }
        else if (fuses.nonEmpty)
            Some (SeriesBranch (this.from, this.to, this.current, fuses))
        else
            None
    }
}

case class ParallelBranch (override val from: String, override val to: String, override val current: Double, parallel: Iterable[Branch]) extends Branch (from, to, current)
{
    override def toString: String =  """ParallelBranch ("%s" ⇒ "%s" %sA %s)""".format (from, to, current, parallel.map (_.toString).mkString ("[", ",", "]"))
    def asString: String =  """%s""".format (parallel.map (_.asString).mkString ("[", "||", "]"))
    def asFuse: Seq[(String, Double)] = parallel.flatMap (_.asFuse).toSeq
    def seq = Seq (this)
    def iter: Iterable[Branch] = parallel
    def justFuses: Option[Branch] =
    {
        val fuses = parallel.flatMap (_.justFuses)
        if ((fuses.size == 1) && fuses.head.isInstanceOf[SimpleBranch])
        {
            val branch = fuses.head.asInstanceOf[SimpleBranch]
            Some (SimpleBranch (this.from, this.to, this.current, branch.mRID, branch.rating)) // ToDo: parallel cables and fuses makes no sense, what's the current rating?
        }
        else if (fuses.nonEmpty)
            Some (ParallelBranch (this.from, this.to, this.current, fuses))
        else
            None
    }
}

object Branch
{
    def apply (from: String, to: String, current: Double, mRID: String, rating: Option[Double]) = SimpleBranch (from, to, current, mRID, rating)
    def apply (from: String, to: String, current: Double, series: Seq[Branch]) = SeriesBranch (from, to, current, series)
    def apply (from: String, to: String, current: Double, parallel: Iterable[Branch]) = ParallelBranch (from, to, current, parallel)
}

