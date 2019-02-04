package ch.ninecode.sc

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import scala.collection.mutable

/**
 * Organize the simple branches of an experiment into a single series or parallel branch.
 *
 * Proceeds by orienting the branches by current flow (current flows in the direction from⇒to),
 * then aggregating the simple branches into increasingly simplified series and parallel branches.
 */
class TraceRoute
{
    val log: Logger = LoggerFactory.getLogger (getClass)

    /**
     * Orient each branch according to current flow.
     *
     * Beginning at the branch to the "source" follow the trail of currents, splitting and joining at parallel
     * nodes, to always ensure that Kirchhoff's current law (currents in and out of node must sum to zero).
     *
     * @param starting the starting nodes with source/sink current
     * @param branches the list of simple branches to orient
     * @return the oriented branch list
     */
    def make_directed (starting: Iterable[(String, Double)], branches: Iterable[Branch]): Iterable[Branch] =
    {
        /**
         * Split the branches into two options, those not terminating on the starting nodes, and those that do
         *
         * @param ending the node to look for the starting nodes in
         * @param branch the branch to test
         * @return a 2tuple of, the option of branch not terminating on endings and the option that it does
         */
        def split (ending: String)(branch: Branch): (Option[Branch], Option[Branch]) = // (unordered, ordered)
        {
            if (ending == branch.from)
                (None, Some (branch.reverse)) // flip direction
            else
                if (ending == branch.to)
                    (None, Some (branch)) // already directed correctly
                else
                    (Some (branch), None)
        }

        /**
         * Apply Kirchhoff's current law to branches terminating on the starting node.
         *
         * @param branches the list of branches to check
         * @param ordered  the branches that have already been ordered
         * @param start    the node and current at the 'to' end of the branches of interest
         * @return a 2tuple of, if successful, the branches terminating on the node and a list of from nodes
         *         or if not successful the original start node and current
         */
        def kcl (branches: Iterable[Branch], ordered: Iterable[Branch], start: (String, Double)): (Iterable[Branch], Iterable[(String, Double)]) =
        {
            val fan_in = branches.filter (_.to == start._1)
            val i_in = fan_in.map (x ⇒ Math.abs (x.current)).sum
            val fan_out = ordered.filter (_.from == start._1)
            val i_out = fan_out.map (x ⇒ Math.abs (x.current)).sum
            val ok =
                if (i_out > 0.0)
                    Math.abs (i_out - i_in) < 0.1 // ToDo: this 0.1 value is a heuristic value determined by testing, maybe a parameter is needed
                else
                    Math.abs (start._2 - i_in) < 0.1
            if (ok)
                (fan_in, fan_in.map (x ⇒ (x.from, x.current)))
            else
                (Iterable (), Iterable (start))
        }

        var ret: Iterable[Branch] = Seq ()
        var start: Map[String, Double] = starting.toMap
        var next = branches
        var done = false
        do
        {
            done = false
            var new_start = new mutable.HashMap[String, Double]()
            var new_next = next
            for (node ← start)
            {
                val (un, or) = new_next.map (split (node._1)).unzip
                val unordered: Iterable[Branch] = un.flatten
                val ordered: Iterable[Branch] = or.flatten
                val step: (Iterable[Branch], Iterable[(String, Double)]) = kcl (ordered, ret, node)
                val good = step._1
                val good_nodes = good.map (_.to).toSet
                val bad = ordered.filter (x ⇒ !good_nodes.contains (x.to))
                ret = ret ++ good
                new_start ++= step._2
                new_next = unordered ++ bad
            }

            val new_starts = new_start.groupBy (x ⇒ x._1).map (x ⇒ (x._1, x._2.values.sum))
            val better = new_next.size < next.size || (new_starts.keys.toArray.sortWith (_ < _).mkString != start.keys.toArray.sortWith (_ < _).mkString)
            if (!better)
            {
                log.error ("cannot make branches directed (%s branches cannot be resolved)".format (new_next.size))
                if (log.isDebugEnabled)
                    log.debug ("start %s: branches: %s".format (start.map (y ⇒ "" + y._1 + "@" + y._2 + "A").mkString (","), new_next.mkString (",")))
            }
            if (new_next.isEmpty || !better)
                done = true
            else
            {
                start = new_starts
                next = new_next
                done = false
            }
        }
        while (!done)
        ret
    }

    /**
     * Convert the starting data structure into branches (mRID with both nodes).
     *
     * @param data raw recorder data for the experiment as tuples of node id, encoded equipment mRID and current
     * @return a simple electrical branch for each path
     */
    def toBranches (start: String, data: Iterable[(String, String, Double)]): Iterable[Branch] =
    {
        // create unordered branches
        val undirected = mutable.Map [String, SimpleBranch]()

        def get_mrid (element: String): String =
        {
            val index = element.indexOf ("$")
            if (-1 == index) element else element.substring (0, index)
        }

        def get_rating (element: String): Option[Double] =
        {
            val index = element.indexOf ("$")
            if (-1 == index) None else Some (element.substring (index + 1).toDouble)
        }

        def make_branch (element: (String, String, Double)): Unit =
        {
            val from = element._1
            val mrid = get_mrid (element._2)
            val current = element._3
            val rating = get_rating (element._2)
            if (!undirected.contains (mrid))
                undirected (mrid) = Branch (from, data.find (y ⇒ get_mrid (y._2) == mrid && y._1 != from).map (_._1).getOrElse ("source"), current, mrid, rating)
        }

        data.foreach (make_branch)
        // walk backwards from ending node
        make_directed (Iterable (("source", undirected.find (x ⇒ x._2.to == "source").map (_._2.current).getOrElse (0.0))), undirected.values)
    }

    /**
     * Reduce series connected elements.
     *
     * @param network the current network to be reduced
     * @return the reduced network with one pair of series elements converted to a series branch
     */
    def reduce_series (network: Iterable[Branch]): (Boolean, Iterable[Branch]) = // (reduced?, network)
    {
        // check for series elements
        val series = for
        {
            branch ← network
            buddies = network.filter (x ⇒ branch.to == x.from)
            parallel = network.filter (x ⇒ branch != x && branch.to == x.to)
            if buddies.size == 1 && parallel.isEmpty
            buddy = buddies.head
        }
            yield (branch, buddy)
        if (series.nonEmpty)
        {
            // only do one reduction at a time... I'm not smart enough to figure out how to do it in bulk
            val pair = series.head
            val rest = network.filter (x ⇒ pair._1 != x && pair._2 != x)
            val flip = Math.abs (pair._1.current - pair._2.current) > 0.1
            (true, Seq (pair._1.add_in_series (if (flip) pair._2.reverse else pair._2)) ++ rest)
        }
        else
            (false, network)
    }

    /**
     * Reduce parallel connected elements.
     *
     * @param network the current network to be reduced
     * @return the reduced network with one pair of parallel elements converted to a parallel branch
     */
    def reduce_parallel (network: Iterable[Branch]): (Boolean, Iterable[Branch]) = // (reduced?, network)
    {
        // check each node using Kirchoff's Current Law (KCL)
        def kcl (input: Double, output: Double, fixed: Iterable[Branch], undecided: Iterable[Branch]): (Boolean, Iterable[Branch]) =
        {
            // we need only test either from or to since all elements have the same from and to
            val currents = fixed.map (-_.current) ++ undecided.map (-_.current)
            if ((Math.abs (input + currents.sum) < 0.1) || (Math.abs (output + currents.sum) < 0.1))
                (true, fixed ++ undecided)
            else
            {
                // try adding reversed elements
                val ok = for
                    {
                    test ← undecided
                    solid = fixed.toList :+ test.reverse
                    rest = undecided.filter (_ != test)
                    result = kcl (input, output, solid, rest)
                    if result._1
                }
                    yield solid ++ rest
                if (ok.isEmpty)
                    (false, List ())
                else
                    (true, ok.head)
            }
        }

        // check for parallel elements
        val parallel: Iterable[Iterable[Branch]] =
            for
                {
                branch: Branch ← network
                buddies: Iterable[Branch] = network.filter (x ⇒ ((branch.from == x.from) && (branch.to == x.to)) && (branch != x))
                if buddies.nonEmpty
            }
                yield buddies ++ Seq (branch)
        if (parallel.nonEmpty)
        {
            // only do one reduction at a time... I'm not smart enough to figure out how to do it in bulk
            val set: Iterable[Branch] = parallel.head
            // alter set to satisfy KCL
            val input = network.filter (_.to == set.head.from).map (_.current).sum
            val output = network.filter (_.from == set.head.to).map (_.current).sum
            val arranged = kcl (input, output, List (), set)
            if (arranged._1)
                (true, Seq (arranged._2.head.add_in_parallel (arranged._2.tail)) ++ network.filter (x ⇒ !set.toSeq.contains (x)))
            else
                (false, network)
        }
        else
            (false, network)
    }

    /**
     * Convert the raw short circuit current data into fuse specificity lists.
     *
     * @param start the starting node id for which the fuse specificity is required
     * @param data  the recorder data as tuples of node id, encoded equipment mRID and current
     * @return the single equivalent branch
     */
    def traceroute (start: String, data: Iterable[(String, String, Double)]): Seq[Branch] =
    {
        // print out the data
        // println ("""traceroute ("%s", List (%s))""".format (start, data.map (x ⇒ """("%s", "%s", %s )""".format (x._1, x._2, x._3)).mkString (",")))

        var network: Iterable[Branch] = toBranches (start, data)
        // print out the starting branches
        // println ("raw:\n" + network.mkString ("\n"))

        // step by step reduce the network to a single branch through series and parallel reductions
        var done = false
        do
        {
            val (modified, net) = reduce_series (network)
            network = net
            done = !modified
            if (done)
            {
                val (modified, net) = reduce_parallel (network)
                network = net
                done = !modified
            }
        }
        while (!done)
        // println ("reduced:\n" + network.mkString ("\n"))

        // convert the network into a set of fuses by removing all cables and transformers
        network.toSeq.flatMap (_.justFuses)
    }
}
