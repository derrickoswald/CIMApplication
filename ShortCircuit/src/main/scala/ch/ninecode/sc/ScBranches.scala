package ch.ninecode.sc

import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ScBranches
{
    implicit val log: Logger = LoggerFactory.getLogger(getClass)

    def connectedTo (branch: Branch, edges: Iterable[Branch]): Boolean =
    {
        edges.exists(edge => edge != branch && (branch.to == edge.to || branch.to == edge.from))
    }

    def connectedFrom (branch: Branch, edges: Iterable[Branch]): Boolean =
    {
        edges.exists(edge => edge != branch && (branch.from == edge.to || branch.from == edge.from))
    }

    // eliminate branches in the tree than only have one end connected - except for the starting and ending node
    def no_stubs (edges: Iterable[Branch], start: Array[String], end: String)(branch: Branch): Boolean =
    {
        val to = connectedTo(branch, edges)
        val from = connectedFrom(branch, edges)
        val connected = to && from
        val into = start.contains(branch.to)
        val infrom = start.contains(branch.from)

        connected ||
            (end == branch.to && (infrom || from)) ||
            (end == branch.from && (into || to)) ||
            (infrom && to) ||
            (into && from)
    }

    def reduce_branches (
        graph_edges: Iterable[Branch],
        lvnodes: Array[String],
        experiment: ScExperiment): Iterable[Branch] =
    {
        // reduce the tree to (hopefully) one branch spanning from start to end
        var family = graph_edges
        var count = family.size
        do
        {
            count = family.size
            family = family.filter(no_stubs(family, lvnodes, experiment.mrid))
        }
        while (count != family.size)

        val branches: Iterable[Branch] = reduce(family, lvnodes, experiment.mrid)
        branches
    }

    /**
     * Reduce series connected elements.
     *
     * @param network     the current network to be reduced
     * @param trafo_nodes the list of starting trafo nodes
     * @return the reduced network with one pair of series elements converted to a series branch
     */
    def reduce_series (network: Iterable[Branch], trafo_nodes: Array[String], mrid: String): (Boolean, Iterable[Branch]) = // (reduced?, network)
    {
        // check for series elements, eliminate making a series connection across the house or trafo
        val prepend =
            for
                {
                branch <- network
                house = branch.from == mrid
                if !house
                trafo = trafo_nodes.contains(branch.from)
                if !trafo
                buddies = network.filter(x => (branch.from == x.to) || (branch.from == x.from && branch != x))
                if buddies.size == 1
                buddy :: _ = buddies
            }
                yield (branch, buddy)

        val append =
            for
                {
                branch <- network
                house = branch.to == mrid
                if !house
                trafo = trafo_nodes.contains(branch.to)
                if !trafo
                buddies = network.filter(x => (branch.to == x.from) || (branch.to == x.to && branch != x))
                if buddies.size == 1
                buddy :: _ = buddies
            }
                yield (branch, buddy)

        val series = prepend ++ append

        series match
        {
            case (branch, buddy) :: _ =>
                // only do one reduction at a time... I'm not smart enough to figure out how to do it in bulk
                val rest = network.filter(x => branch != x && buddy != x)
                val new_series = if (buddy.to == branch.from)
                    buddy.add_in_series(branch)
                else
                    if (branch.to == buddy.from)
                        branch.add_in_series(buddy)
                    else
                    // choose the simplest element to reverse
                        branch match
                        {
                            case _: SimpleBranch =>
                                buddy.add_in_series(branch.reverse)
                            case _: TransformerBranch =>
                                buddy.add_in_series(branch.reverse)
                            case _ =>
                                buddy.reverse.add_in_series(branch)
                        }
                (true, Seq(new_series) ++ rest)
            case _ =>
                (false, network)
        }
    }

    /**
     * Reduce parallel connected elements.
     *
     * @param network the current network to be reduced
     * @return the reduced network with one pair of parallel elements converted to a parallel branch
     */
    @SuppressWarnings(Array("org.wartremover.warts.TraversableOps"))
    def reduce_parallel (network: Iterable[Branch]): (Boolean, Iterable[Branch]) = // (reduced?, network)
    {
        // check for parallel elements
        val parallel = for
            {
            branch <- network
            buddies = network.filter(x => ((branch.from == x.from) && (branch.to == x.to)) && (branch != x))
            if buddies.nonEmpty
        }
            yield buddies ++ Seq(branch)
        parallel match
        {
            case set :: _ =>
                // only do one reduction at a time... I'm not smart enough to figure out how to do it in bulk
                (true, Seq(set.head.add_in_parallel(set.tail)) ++ network.filter(x => !set.toSeq.contains(x)))
            case _ =>
                (false, network)
        }
    }

    def reduce (branches: Iterable[Branch], trafo_nodes: Array[String], mrid: String): Iterable[Branch] =
    {
        // step by step reduce the network to a single branch through series and parallel reductions
        var done = false
        var network: Iterable[Branch] = branches
        do
        {
            val (modified, net) = reduce_series(network, trafo_nodes, mrid)
            network = net
            done = !modified
            if (done)
            {
                val (modified, net) = reduce_parallel(network)
                network = net
                done = !modified
                // check that all branches start from the transformer
                if (done)
                    if (!network.forall(x => trafo_nodes.contains(x.from)))
                    {
                        val max = network.map(_.current).foldLeft(Double.MinValue)((x: Double, y: Double) => if (x > y) x else y)
                        val significant = max * 0.01 // 1% of the maximum current
                        val filtered = network.filter(x =>
                            (x.current > significant)
                                || trafo_nodes.contains(x.from)
                                || trafo_nodes.contains(x.to)
                                || (x.from == mrid)
                                || (x.to == mrid))
                        if (filtered.size < network.size)
                        {
                            done = false
                            network = filtered
                        }
                    }
            }
        }
        while (!done)

        network
    }
}
