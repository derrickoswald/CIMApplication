package ch.ninecode.mfi

import java.util.Calendar

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.gl.PreEdge
import ch.ninecode.net.TransformerIsland
import ch.ninecode.net.TransformerData

/**
 * A work package for gridlab simulation.
 *
 * @param start                  Starting time to be used in the simulation.
 * @param trafo                  The island name [concatenated transformer(s) (or ganged transformers)] = the simulation name.
 * @param transformers           The feeding transformers (or ganged transformers).
 * @param nodes                  The nodes in the transformer service area.
 * @param edges                  The edges in the transformer service area.
 * @param houses                 The house connections in the transformer service area.
 * @param options                Options for calculations.
 * @param subtransmission_trafos The list of subtransmission transformers to be matched for edge creation.
 */
case class Trafokreis
(
    start: Calendar,
    trafo: String,
    transformers: TransformerIsland,
    nodes: Iterable[PowerFeedingNode],
    edges: Iterable[PreEdge],
    houses: Iterable[MaxPowerFeedingNodeEEA],
    options: EinspeiseleistungOptions,
    subtransmission_trafos: Array[TransformerData] = Array()
)
{
    val log: Logger = LoggerFactory.getLogger(getClass)

    val window: Int = 3 * 60 // window size in simulated seconds per experiment

    val step: Double = 10000.0

    def limit (node: MaxPowerFeedingNodeEEA): Double =
    {
        val margin = if (node.reason == "non-radial network" || node.reason == "transformer limit")
            1.5
        else
            options.precalc_factor // check up to this factor (1.5 == 50%) over the precalculated value
        math.ceil(node.max_power_feeding * margin / step) * step // limit as ceiling(d*margin%) in thousands
    }

    def significant (h: MaxPowerFeedingNodeEEA): Boolean =
        h.psr_type == "PSRType_HouseService" &&
            // only do houses where we know it's more than a kilowatt or it's zero because of a three winding transformer
            (h.max_power_feeding > 1000.0 || 0 != h.reason.indexOf("transformer windings for edge"))

    @SuppressWarnings(Array("org.wartremover.warts.TraversableOps"))
    def best (nodes: Iterable[MaxPowerFeedingNodeEEA]): String =
    {
        // heuristic that node name starts with the mRID of the element
        nodes.find(x => x.id_seq.startsWith(x.mrid)) match
        {
            case Some(x) =>
                x.mrid
            case None =>
                nodes.head.mrid
        }
    }

    @SuppressWarnings(Array("org.wartremover.warts.TraversableOps"))
    def gen_exp (h: (Iterable[MaxPowerFeedingNodeEEA], Int)): Experiment =
    {
        val (nodes, index) = h // (precalc_nodes, experiment #)
        val a_node = nodes.head
        val feeder = a_node.feeder
        val node = a_node.id_seq // the node under test
        val house = best(nodes) // the house under test (could be multiple houses per node)
        val houses = nodes.map(_.mrid).toList // all the houses attached to the node
        val max = nodes.map(limit).max // upper kilowatt limit to test
        val interval = 5 // seconds per step
        val steps = window / interval - 2 // total possible number of steps in the experiment (need 0 input on both ends, hence -2)
        val riser = if (steps * step >= max) step else math.ceil(max / steps / step) * step // limit as ceiling(minimum step size) in thousands
        Experiment(trafo, feeder, node, house, houses, start_time, index, window, interval, 0, max, riser) // in 5 second intervals go from 0 to max in steps of <1000>
    }

    // generate experiments
    val experiments: Array[Experiment] = houses.filter(significant).groupBy(_.id_seq).values.zipWithIndex.map(gen_exp).toArray

    def name: String = trafo

    def start_time: Calendar = start

    /**
     * Calendar duplication utility function.
     *
     * @param c The Calendar value to be cloned.
     */
    @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
    def dup (c: Calendar): Calendar = c.clone().asInstanceOf[Calendar]

    def finish_time: Calendar =
    {
        val t = dup(start_time)
        t.add(Calendar.SECOND, experiments.length * window)
        t
    }
}
