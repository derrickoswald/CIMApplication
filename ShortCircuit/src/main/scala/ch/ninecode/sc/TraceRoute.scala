package ch.ninecode.sc

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import scala.collection.mutable

class TraceRoute
{
    def make_directed (starting: Iterable[(String, Double)], branches: Iterable[Branch]): Iterable[Branch] =
    {
        def split (endings: Set[String]) (branch: Branch): (Option[Branch], Option[Branch]) = // (unordered, ordered)
        {
            if (endings.contains (branch.from))
                (None, Some (branch.reverse)) // flip direction
            else if (endings.contains (branch.to))
                (None, Some (branch)) // already directed correctly
            else
                (Some (branch), None)
        }

        def kcl (branches: Iterable[Branch]) (start: (String, Double)): (Iterable[Branch], Iterable[(String, Double)]) =
        {
            val fanout = branches.filter (_.to == start._1)
            val ok = Math.abs (start._2 - fanout.map (_.current).sum) < 0.1
            if (ok)
                (fanout, fanout.map (x ⇒ (x.from, x.current)))
            else
                (Iterable(), Iterable(start))
        }

        val (un, or) = branches.map (split (starting.map (_._1).toSet)).unzip
        val unordered: Iterable[Branch] = un.flatten
        val ordered: Iterable[Branch] = or.flatten
        if (ordered.isEmpty)
            unordered
        else
        {
            val step: Iterable[(Iterable[Branch], Iterable[(String, Double)])] = starting.map (kcl (ordered))
            val good: Iterable[Branch] = step.flatMap (_._1)
            val good_nodes = good.map (_.to).toSet
            val bad = ordered.filter (x ⇒ !good_nodes.contains (x.to))
            val start: Map[String, Double] = step.flatMap (_._2).groupBy (x ⇒ x._1).map (x ⇒ (x._1, x._2.map (_._2).sum))
            val next: Iterable[Branch] = unordered ++ bad
            val better = next.size < branches.size  || start.keys.toArray.sortWith (_ < _).mkString != starting.map (_._1).toArray.sortWith (_ < _).toString
            if (!better)
            {
                val log: Logger = LoggerFactory.getLogger (getClass)
                log.error ("cannot make branches directed (%s branches cannot be resolved)".format (next.size))
                if (log.isDebugEnabled)
                    log.debug ("start %s: branches: %s".format (start.map (y ⇒ "" + y._1 + "@" + y._2 + "A").mkString (","), next.mkString (",")))
            }
            if (next.isEmpty)
                good
            else
                good ++ make_directed (start, next)
        }
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
        make_directed (Iterable(("source", undirected.find (x ⇒ x._2.to == "source").map (_._2.current).getOrElse (0.0))), undirected.values)
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
        {
//            // check for series elements reverse direction
//            val series = for
//            {
//                branch ← network
//                buddies = network.filter (x ⇒ branch != x && (branch.from == x.from || branch.to == x.to))
//                parallel = network.filter (x ⇒ branch.from == x.from)
//                if buddies.size == 1 && parallel.size == 1 && parallel.head == branch
//                buddy = buddies.head
//            }
//            yield (branch, buddy)
//            if (series.nonEmpty)
//            {
//                val pair = series.head
//                val flip = Math.abs (pair._2.current - pair._1.current) > 0.1
//                // only do one reduction at a time... I'm not smart enough to figure out how to do it in bulk
//                (true, Seq (pair._2.add_in_series (if (flip) pair._1.reverse else pair._1)) ++ network.filter (x ⇒ pair._1 != x && pair._2 != x))
//            }
//            else
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
        // check each node using Kirchoff's Current Law (KCL)
        def kcl (input: Double, output: Double, fixed: Iterable[Branch], undecided: Iterable[Branch]): (Boolean, Iterable[Branch]) =
        {
            // we need only test either from or to since all elements have the same from and to
            val currents = fixed.map (- _.current) ++ undecided.map (- _.current)
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
                    (false, List())
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
            val arranged = kcl (input, output, List(), set)
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
     * @param data the recorder data as tuples of node id, encoded equipment mRID and current
     * @return
     */
    def traceroute (start: String, data: Iterable[(String, String, Double)]): Seq[Branch] =
    {
        // print out the data
        println ("""traceroute ("%s", List (%s))""".format (start, data.map (x ⇒ """("%s", "%s", %s )""".format (x._1, x._2, x._3)).mkString (",")))

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
