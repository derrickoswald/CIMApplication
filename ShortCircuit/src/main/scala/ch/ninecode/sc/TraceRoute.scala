package ch.ninecode.sc

import scala.collection.mutable

class TraceRoute
{
    /**
     * Convert the starting data structure into branches (mRID with both nodes).
     *
     * @param data raw recorder data for the experiment as tuples of node id, encoded equipment mRID and current
     * @return a simple electrical branch for each path
     */
    def toBranches (start: String, data: Iterable[(String, String, Double)]): Iterable[SimpleBranch] =
    {
        def split (endings: Set[String]) (branch: SimpleBranch): (Option[SimpleBranch], Option[SimpleBranch], Option[String]) = // (unordered, ordered, next)
        {
            if (endings.contains (branch.from))
                (None, Some (Branch (branch.to, branch.from, branch.current, branch.mRID, branch.rating)), Some (branch.to)) // flip direction
            else if (endings.contains (branch.to))
                (None, Some (branch), Some (branch.from)) // already directed correctly
            else
                (Some (branch), None, None)
        }

        def make_directed (starting: Set[String], branches: Iterable[SimpleBranch]): Iterable[SimpleBranch] =
        {
            val (un, or, next) = branches.map (split (starting)).unzip3
            val unordered = un.flatten
            val ordered = or.flatten
            if (ordered.isEmpty || unordered.isEmpty)
                ordered
            else
                ordered ++ make_directed (next.flatten.toSet, unordered)
        }

        // check each node using Kirchoff's Current Law (KCL)
        def kcl_violations (branches: Iterable[SimpleBranch]): Set[(String, Iterable[(String, Double)])] =
        {
            for
            {
                node:String ← branches.flatMap (x ⇒ List (x.from, x.to)).filter (node ⇒ (node != start) && (node != "source")).toSet
                in =  for { branch ← branches if branch.to   == node } yield (branch.mRID,   branch.current)
                out = for { branch ← branches if branch.from == node } yield (branch.mRID, - branch.current)
                both = in ++ out
                sum = both.map (_._2).sum
                if Math.abs (sum) > 0.1
            }
            yield (node, both)
        }

        def find_candidates (delinquents: Set[(String, Iterable[(String, Double)])], branches: Iterable[SimpleBranch]): Set[String] =
        {
            delinquents.flatMap (_._2.map (_._1)).filter (candidate ⇒ !branches.exists (y ⇒ y.mRID == candidate && (y.to == "source" || y.from == start)))
        }


        def better (tried: Set[String], candidates: Set[String], branches: Iterable[SimpleBranch]): Option[Iterable[SimpleBranch]] =
        {
            if (candidates.nonEmpty)
            {
                val candidate = candidates.head
                val flipped = branches.map (x ⇒ if (x.mRID != candidate) x else SimpleBranch (x.to, x.from, x.current, x.mRID, x.rating))
                val delinquents = kcl_violations (flipped)
                if (delinquents.isEmpty)
                    Some (flipped)
                else
                {
                    val done = tried ++ Seq (candidate)
                    val depth_first = better (done, find_candidates (delinquents, branches).diff (done), flipped)
                    depth_first match
                    {
                        case Some (arrangement) ⇒ Some (arrangement)
                        case None ⇒ better (done, candidates.tail, branches)
                    }
                }
            }
            else
                None
        }

        def best (branches: Iterable[SimpleBranch]): Iterable[SimpleBranch] =
        {
            val delinquents = kcl_violations (branches)
            if (delinquents.nonEmpty)
                better (Set (), find_candidates (delinquents, branches), branches).getOrElse (branches)
            else
                branches
        }

        // create unordered branches
        val undirected = mutable.Map[String, SimpleBranch]()
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
        val branches = make_directed (Set("source"), undirected.values)
        // flip branches that violate KCL
        best (branches)
    }

    /**
     * Reduce series connected elements.
     *
     * @param network the current network to be reduced
     * @return the reduced network with one pair of series elements converted to a series branch
     */
    def reduce_series (network: Iterable[Branch]): (Boolean, Iterable[Branch]) = // (reduced?, network)
    {
        // check for series elements forward direction
        val series = for
        {
            branch ← network
            buddies = network.filter (x ⇒ branch.to == x.from)
            parallel = network.filter (x ⇒ branch.to == x.to)
            if buddies.size == 1 && parallel.size == 1 && parallel.head == branch
            buddy = buddies.head
        }
        yield (branch, buddy)
        if (series.nonEmpty)
            // only do one reduction at a time... I'm not smart enough to figure out how to do it in bulk
            (true, Seq (series.head._1.add_in_series (series.head._2)) ++ network.filter (x ⇒ series.head._1 != x && series.head._2 != x))
        else
        {
            // check for series elements forward direction
            val series = for
            {
                branch ← network
                buddies = network.filter (x ⇒ branch.from == x.to)
                parallel = network.filter (x ⇒ branch.from == x.from)
                if buddies.size == 1 && parallel.size == 1 && parallel.head == branch
                buddy = buddies.head
            }
            yield (branch, buddy)
            if (series.nonEmpty)
                // only do one reduction at a time... I'm not smart enough to figure out how to do it in bulk
                (true, Seq (series.head._1.add_in_series (series.head._2)) ++ network.filter (x ⇒ series.head._1 != x && series.head._2 != x))
            else
                (false, network)
        }
    }

    /**
     * Reduce parallel connected elements.
     *
     * @param network the current network to be reduced
     * @return the reduced network with one pair of parallel elements converted to a parallel branch
     */
    def reduce_parallel (network: Iterable[Branch]): (Boolean, Iterable[Branch]) = // (reduced?, network)
    {
        // check for parallel elements
        val parallel: Iterable[(Branch, Iterable[Branch])] = for
        {
            branch ← network
            buddies = network.filter (x ⇒ (branch.from == x.from) && (branch.to == x.to) && (branch != x))
            if buddies.nonEmpty
        }
        yield (branch, buddies)
        if (parallel.nonEmpty)
            // only do one reduction at a time... I'm not smart enough to figure out how to do it in bulk
            (true, Seq (parallel.head._1.add_in_parallel (parallel.head._2)) ++ network.filter (x ⇒ parallel.head._1 != x && !parallel.head._2.toSeq.contains (x)))
        else
            (false, network)
    }

    /**
     * Convert the raw short circuit current data into fuse specificity lists.
     *
     * @param start the starting node id for which the fuse specificity is required
     * @param data the recorder data as tuples of node id, encoded equipment mRID and current
     * @return
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
