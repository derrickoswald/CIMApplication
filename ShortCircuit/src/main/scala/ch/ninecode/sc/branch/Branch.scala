package ch.ninecode.sc.branch

import ch.ninecode.sc.Impedanzen
import ch.ninecode.sc.ShortCircuitOptions
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
                        throw new IllegalArgumentException(s"branches are not in series ($this, $that)")
    }

    @SuppressWarnings(Array("org.wartremover.warts.Throw"))
    def add_in_parallel (those: Iterable[Branch]): Branch =
    {
        if (those.forall(x => to == x.to && from == x.from))
            Branch(from, to, current + those.map(_.current).sum, this.iter ++ those.flatMap(x => x.iter))
        else
        {
            val exceptionMsg = s"branches are not in parallel ($this, ${those.map(_.toString).mkString(", ")})"
            throw new IllegalArgumentException(exceptionMsg)
        }
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
     * @return tuple with a boolean <code>true</code> if the network would change because of the short circuit,
     *         or <code>false</code> if it would not, and either the remaining network with the change,
     *         or the original network if there would be no change
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

object Branch
{
    def apply (from: String, to: String, current: Double, mRID: String, name: String, standard: String, rating: Option[Double]): SimpleBranch = SimpleBranch(from, to, current, mRID, name, rating, standard)

    def apply (from: String, to: String, current: Double, mRID: String, name: String, s: Double, vfrom: Double, vto: Double, pu: Complex): TransformerBranch = TransformerBranch(from, to, current, mRID, name, s, vfrom, vto, pu, None)

    def apply (from: String, to: String, current: Double, series: Seq[Branch]): SeriesBranch = SeriesBranch(from, to, current, series)

    def apply (from: String, to: String, current: Double, parallel: Iterable[Branch]): ParallelBranch = ParallelBranch(from, to, current, parallel)

    def apply (trafo_hv_nodes: Array[String], to: String, current: Double, basket: Array[Branch]): ComplexBranch = ComplexBranch(trafo_hv_nodes, to, current, basket)
}