package ch.ninecode.sc

import scala.collection.mutable

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
 * @param mRID
 * @param parallel
 */
case class Branch (from: String, to: String, current: Double, mRID: String, series: Seq[Branch], parallel: Iterable[Branch])
{
    override def toString: String =  """Branch ("%s" ⇒ "%s" %sA %s)""".format (from, to, current, if (null == parallel) if (null == series) mRID else series.map (_.toString).mkString ("+") else parallel.map (_.toString).mkString ("[", ",", "]"))
    def contents: Seq[String] = mRID +: (if (null == parallel) Seq () else parallel.flatMap (_.contents).toSeq)
    def nodes: Seq[String] = Seq (from, to) ++ (if (null == parallel)  Seq() else parallel.flatMap (_.nodes))
    def add_in_series (that: Branch): Branch =
    {
        val this_seq = if (null == mRID && null == parallel) this.series else Seq (this)
        val that_seq = if (null == that.mRID && null == that.parallel) that.series else Seq (that)
        if (to == that.from)
            Branch (from, that.to, current, this_seq ++ that_seq)
        else if (from == that.to)
            Branch (that.from, to, current, that_seq ++ this_seq)
        else
            throw new IllegalArgumentException ("branches are not in series (%s, %s)".format (this, that))
    }
    def add_in_parallel (those: Iterable[Branch]): Branch =
    {
        val this_iter = if (null == mRID && null == series) this.parallel else Iterable (this)
        val those_iter = those.flatMap (x ⇒ if (null == x.mRID && null == x.series) x.parallel else List (x))
        if (those.forall (x ⇒ to == x.to && from == x.from))
            Branch (from , to, current + those.map (_.current).sum, this_iter ++ those_iter)
        else
            throw new IllegalArgumentException ("branches are not in parallel (%s, %s)".format (this, those.map (_.toString).mkString (", ")))
    }
}

object Branch
{
    def apply (from: String, to: String, current: Double, mRID: String) = new Branch (from: String, to: String, current: Double, mRID: String, null, null)
    def apply (from: String, to: String, current: Double, series: Seq[Branch]) = new Branch (from: String, to: String, current: Double, null, series, null)
    def apply (from: String, to: String, current: Double, parallel: Iterable[Branch]) = new Branch (from: String, to: String, current: Double, null, null, parallel)
}

