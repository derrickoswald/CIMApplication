package ch.ninecode.sc

import scala.util.control.Breaks.break

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.sc.branch.Branch
import ch.ninecode.sc.branch.ComplexBranch

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
        trafo_hv_nodes: Array[String],
        mrid: String): Option[Branch] =
    {
        // reduce the tree to (hopefully) one branch spanning from start to end
        var family = graph_edges
        var count = family.size
        do
        {
            count = family.size
            family = family.filter(no_stubs(family, trafo_hv_nodes, mrid))
        }
        while (count != family.size)

        var branches: Iterable[Branch] = reduce(family, trafo_hv_nodes, mrid)

        if (branches.size > 1)
        {
            log.info(s"complex branch network from ${trafo_hv_nodes.mkString(",")} to ${mrid}")
            val directs: Iterable[Branch] = branches.filter(b => mrid == b.to || mrid == b.from)
            val current = directs.map(_.current).sum
            branches = List(ComplexBranch(trafo_hv_nodes, mrid, current, branches.toArray))
        }

        branches.headOption
    }


    def get_all_nodes (branches: Iterable[Branch]): Set[String] =
    {
        branches.map(_.to).toSet ++ branches.map(_.from).toSet
    }

    /**
     * Reduce series connected elements.
     *
     * @param network     the current network to be reduced
     * @param trafo_nodes the list of starting trafo nodes
     * @return the reduced network with one pair of series elements converted to a series branch
     */
    def reduce_series (network: Iterable[Branch], mrid: String): (Boolean, Iterable[Branch]) = // (reduced?, network)
    {
        def is_reducable: String => Boolean = (node: String) =>
        {
            network.count(_.to == node) == 1 && network.count(_.from == node) == 1
        }

        def get_reduced_series (starting_branch: Branch, branches: Iterable[Branch], reducable_nodes: Set[String]): Branch =
        {
            var reduced_series_branch = starting_branch
            do
            {
                branches.find(_.to == reduced_series_branch.from) match
                {
                    case Some(branch) =>
                    {
                        reduced_series_branch = reduced_series_branch.add_in_series(branch)
                    }
                    case None =>
                    {
                        log.error(s"Trying to reduce ${reduced_series_branch} failed. Because no branch found going to ${reduced_series_branch.from}. MRID: ${mrid}")
                        break
                    }
                }
            } while (reducable_nodes.contains(reduced_series_branch.from))
            reduced_series_branch
        }

        val all_nodes = get_all_nodes(network)
        val reducable_nodes = all_nodes.filter(is_reducable)
        val intersection_nodes_to_process = all_nodes -- reducable_nodes

        val new_network = intersection_nodes_to_process.flatMap((intersection_node) =>
        {
            var results: Set[Branch] = Set()
            for (starting_branch <- network.filter(_.to == intersection_node))
            {
                val new_series_branch = if (reducable_nodes.contains(starting_branch.from))
                {
                    get_reduced_series(starting_branch, network, reducable_nodes)
                } else
                {
                    starting_branch
                }
                results ++= Set(new_series_branch)
            }
            results
        })
        (new_network.size != network.size, new_network)
    }

    /**
     * Reduce parallel connected elements.
     *
     * @param network the current network to be reduced
     * @return the reduced network with one pair of parallel elements converted to a parallel branch
     */
    @SuppressWarnings(Array("org.wartremover.warts.TraversableOps"))
    def reduce_parallel (network: Iterable[Branch], trafo_nodes: Array[String]): (Boolean, Iterable[Branch]) = // (reduced?, network)
    {
        def has_trafo_end (branch_end: String, x_end: String, trafo_nodes: Array[String]) =
        {
            trafo_nodes.contains(branch_end) && trafo_nodes.contains(x_end)
        }

        def trafo_parallel_condtion (branch: Branch, x: Branch, trafo_nodes: Array[String]): Boolean =
        {
            (has_trafo_end(branch.from, x.from, trafo_nodes) && branch.hasSameTo(x)) ||
                (has_trafo_end(branch.to, x.to, trafo_nodes) && branch.hasSameFrom(x))
        }

        def parallel_branches_filter (branch: Branch): Branch => Boolean =
        {
            x => (branch.isParallelTo(x) || trafo_parallel_condtion(branch, x, trafo_nodes)) && (branch != x)
        }

        // check for parallel elements
        val parallel: Iterable[Iterable[Branch]] = for
            {
            branch <- network
            buddies = network.filter(parallel_branches_filter(branch))
            if buddies.nonEmpty
        }
            yield buddies ++ Seq(branch)
        parallel.headOption match
        {
            case Some(set) =>
                if (set.tail.forall(x => set.head.isParallelTo(x)))
                // only do one reduction at a time... I'm not smart enough to figure out how to do it in bulk
                    (true, Seq(set.head.add_in_parallel(set.tail)) ++ network.filter(x => !set.toSeq.contains(x)))
                else
                {
                    val head = set.head
                    val tail = set.tail
                    val from = (Iterable[String](head.from) ++ tail.map(_.from)).toList.sorted.mkString("_")
                    val trafo_parallel_branch = Seq(Branch(from, head.to, head.current + tail.map(_.current).sum, head.iter ++ tail.flatMap(x => x.iter)))
                    (true, trafo_parallel_branch ++ network.filter(x => !set.toSeq.contains(x)))
                }
            case None =>
                (false, network)
        }
    }

    def reduce (branches: Iterable[Branch], trafo_nodes: Array[String], mrid: String): Iterable[Branch] =
    {
        // step by step reduce the network to a single branch through series and parallel reductions
        var network = branches
        var networkChanged = false
        var branchFlipped = false
        val maxIter = branches.size * 10
        var iterationStep = 0
        do
        {
            val series_result = reduce_series(network, mrid)
            val series_modified = series_result._1
            network = series_result._2
            val parallel_result = reduce_parallel(network, trafo_nodes)
            val parallel_modified = parallel_result._1
            network = parallel_result._2
            networkChanged = series_modified || parallel_modified
            if (!networkChanged && network.size > 1)
            {
                // no changes in last iteration, but still more than 1 branch --> try to reverse fuse branches
                val branches_with_unclear_direction = network.filter(_.current == 0.0)
                for (branch <- branches_with_unclear_direction)
                {
                    val has_connection_on_from = network.count(_.from == branch.from) > 0
                    val has_connection_on_to = network.count(_.to == branch.to) > 0
                    if (has_connection_on_from && has_connection_on_to)
                    {
                        network = network.filter(_ != branch)
                        network = Seq(branch.reverse) ++ network
                        branchFlipped = true
                    }
                }

            }
            iterationStep = iterationStep + 1
        }
        while (networkChanged || (branchFlipped && iterationStep < maxIter))

        network
    }
}
