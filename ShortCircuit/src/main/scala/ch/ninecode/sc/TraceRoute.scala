package ch.ninecode.sc

import scala.collection.mutable

class TraceRoute
{
    /**
     * Convert the starting data structure into branches (mRID with both nodes).
     *
     * @param data
     * @return
     */
    def toBranches (data: Iterable[(String, String, Double)]): Iterable[Branch] =
    {
        def split (endings: Set[String])(branch: Branch): (Option[Branch], Option[Branch], Option[String]) = // (unordered, ordered, next)
            if (endings.contains (branch.from))
                (None, Some (Branch (branch.to, branch.from, branch.current, branch.mRID)), Some (branch.to)) // flip direction
            else if (endings.contains (branch.to))
                (None, Some (branch), Some (branch.from)) // already directed correctly
            else
                (Some (branch), None, None)

        def make_directed (starting: Set[String], branches: Iterable[Branch]): Iterable[Branch] =
        {
            val (un, or, next) = branches.map (split (starting)).unzip3
            val unordered = un.flatten
            val ordered = or.flatten
            if (ordered.isEmpty || unordered.isEmpty)
                ordered
            else
                ordered ++ make_directed (next.flatten.toSet, unordered)
        }

        // create unordered branches
        val undirected = mutable.Map[String, Branch]()
        data.foreach (x ⇒ if (!undirected.contains (x._2)) undirected (x._2) = Branch (x._1, data.find (y ⇒ y._2 == x._2 && y._1 != x._1).map (_._1).getOrElse ("source"), x._3, x._2))
        // walk backwards from ending node
        make_directed (Set("source"), undirected.values)
    }

    /**
     * Reduce series connected elements.
     *
     * @param network
     * @return
     */
    def reduce_series (network: Iterable[Branch]): (Boolean, Iterable[Branch]) = // (reduced?, network)
    {
        // check for series elements
        val series = for
            {
            branch ← network
            buddies = network.filter (x ⇒ (branch.to == x.from) || ((branch.to == x.to) && (branch != x)))
            if buddies.size == 1
            buddy = buddies.head
            if branch.to == buddy.from
        }
            yield (branch, buddy)
        if (series.nonEmpty)
        // only do one reduction at a time... I'm not smart enough to figure out how to do it in bulk
            (true, Seq (series.head._1.add_in_series (series.head._2)) ++ network.filter (x ⇒ series.head._1 != x && series.head._2 != x))
        else
            (false, network)
    }

    /**
     * Reduce parallel connected elements.
     *
     * @param network
     * @return
     */
    def reduce_parallel (network: Iterable[Branch]): (Boolean, Iterable[Branch]) = // (reduced?, network)
    {
        // check for parallel elements
        val parallel: Iterable[(Branch, Iterable[Branch])] = for
            {
            branch ← network
            buddies = network.filter (x ⇒ (branch.from == x.from) && (branch.to == x.to) && (branch != x))
            if buddies.size > 0
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
     * @param start
     * @param data
     * @return
     */
    def traceroute (start: String, data: Iterable[(String, String, Double)]): Seq[Branch] =
    {
        // print out the data
        // println ("""traceroute ("%s", List (%s))""".format (start, data.map (x ⇒ """("%s", "%s", %s )""".format (x._1, x._2, x._3)).mkString (",")))

        var network = toBranches (data)
        // print out the starting branches
        // println (network.mkString ("\n"))

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

        network.toSeq
    }
}
