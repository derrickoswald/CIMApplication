package ch.ninecode.sc.branch

import ch.ninecode.sc.Impedanzen
import ch.ninecode.sc.ScBranches
import ch.ninecode.sc.ShortCircuitOptions

/**
 * A group of elements too complex to reduce to a combination of series and parallel branches.
 *
 * @param trafo_hv_nodes all trafo hv pin nodes as the 'from' node,
 * @param to             the 'to' node
 * @param current        the current through this branch in the GridLAB-D experiment
 * @param basket         the branches in no particular order
 */
case class ComplexBranch (
    trafo_hv_nodes: Array[String],
    override val to: String,
    override val current: Double,
    basket: Array[Branch]) extends Branch(trafo_hv_nodes.mkString("_"), to, current)
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
        var lastFuseBranches = Set[Branch]()
        val directs: Iterable[Branch] = basket.filter(b => to == b.to || to == b.from)
        for (direct <- directs)
        {
            lastFuseBranches ++= getLastFuseBranches(direct, basket)
        }
        lastFuseBranches
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
                    Some(ComplexBranch(trafo_hv_nodes, to, current, c.basket))
            }
        else
            if (fuses.nonEmpty)
                Some(ComplexBranch(trafo_hv_nodes, to, current, fuses))
            else
                None
    }

    override def justLastFuses: Iterable[Branch] =
    {
        lastFuses.toList.flatMap(_.justLastFuses)
    }

    def reverse: Branch = ComplexBranch(trafo_hv_nodes, to, current, basket.map(_.reverse))

    private def getLastFuseBranches (branch: Branch, branches: Iterable[Branch]): Set[Branch] =
    {
        if (branch.justFuses.size < 1)
        {
            val newBranches: Iterable[Branch] = branches.filter(branch.from == _.to)
            val newFuseBranches: Iterable[Branch] = newBranches.flatMap((b: Branch) => getLastFuseBranches(b, branches))
            newFuseBranches.toSet
        } else
        {
            Set(branch)
        }
    }

    def ratios: Iterable[(Double, Branch)] =
    {
        val justFuses = justLastFuses.toList
        val current = justFuses.map(_.current).sum
        justFuses.map(x => (x.current / current, x))
    }

    def voltageRatio: Double = basket.foldLeft(1.0)((v, branch) => v * branch.voltageRatio)

    /**
     * @return a fake impedance value
     */
    def z (in: Impedanzen): Impedanzen =
    {
        val impedance: Impedanzen = NodalAdmittanceMatrix(basket, trafo_hv_nodes, to).getImpedanceForComplexBranches

        val prev_impedance = getTransformerBranch match
        {
            case Some(trafoBranch) => trafoBranch.convert_impedance(in)
            case _ => in
        }

        impedance + prev_impedance
    }

    def getTransformerBranch: Option[TransformerBranch] =
    {
        basket.flatMap(_.getTransformerBranch).headOption
    }

    def contents: Iterable[Branch] = basket

    def checkFuses (ik: Double, options: ShortCircuitOptions): (Boolean, Option[Branch]) =
    {
        val justFuses = lastFuses.toList
        val current = justFuses.map(_.current).sum
        val ratio = justFuses.map(x => (x.current / current, x))
        val new_complex: Iterable[(Boolean, Option[Branch])] = ratio.map(
            (pair: (Double, Branch)) =>
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
        {
            val new_branches = new_complex.flatMap(_._2)
            if (new_branches.isEmpty)
            {
                // Stop if just lastfuse branches are empty
                (blows, None)
            } else {
                // Continue by considering again the whole basket but without the blown branches
                val branchWithoutFuses = basket.filter(!lastFuses.toList.contains(_))
                val new_branch: Option[Branch] = new ScBranches().reduce_branches(new_branches ++ branchWithoutFuses, trafo_hv_nodes, to)
                (blows, new_branch)
            }
        } else {
            (false, Some(this))
        }
    }
}
