package ch.ninecode.mfi

import java.util.Calendar

import ch.ninecode.gl.PreEdge
import ch.ninecode.gl.TransformerData
import ch.ninecode.gl.TransformerSet
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * A work package for gridlab simulation.
 *
 * @param start        Starting time to be used in the simulation.
 * @param trafo        The transformer (or ganged transformers) name to be used as the name of the simulation.
 * @param transformers The feeding transformer (or ganged transformers).
 * @param nodes        The nodes in the transformer service area.
 * @param edges        The edges in the transformer service area.
 * @param houses       The house connections in the transformer service area.
 * @param options      Options for calculations.
 */
case class Trafokreis
(
    start: Calendar,
    trafo: String,
    transformers: TransformerSet,
    nodes: Iterable[PowerFeedingNode],
    edges: Iterable[PreEdge],
    houses: Iterable[MaxPowerFeedingNodeEEA],
    options: EinspeiseleistungOptions,
    subtransmission_trafos: Array[TransformerData] = Array()
)
{
    val log: Logger = LoggerFactory.getLogger (getClass)

    val window: Int = 3 * 60 // window size in simulated seconds per experiment

    val step: Double = 10000.0

    def limit (node: MaxPowerFeedingNodeEEA): Double =
    {
        val margin = if ((node.reason == "non-radial network") || (node.reason == "transformer limit"))
            1.5
        else
            options.precalc_factor // check up to this factor (1.5 == 50%) over the precalculated value
        math.ceil (node.max_power_feeding * margin / step) * step // limit as ceiling(d*margin%) in thousands
    }

    def significant (h: MaxPowerFeedingNodeEEA): Boolean =
        h.psr_type == "PSRType_HouseService" &&
        // only do houses where we know it's more than a kilowatt or it's zero because of a three winding transformer
        (h.max_power_feeding > 1000.0 || 0 != h.reason.indexOf ("transformer windings for edge"))

    def gen_exp (h: (MaxPowerFeedingNodeEEA, Int)): Experiment =
    {
        val feeder = h._1.feeder
        val node = h._1.id_seq // the node under test
        val house = h._1.mrid // the house under test (could be multiple houses per node)
        val index = h._2 // experiment #
        val max = limit (h._1) // upper kilowatt limit to test
        val interval = 5 // seconds per step
        val steps = window / interval - 2 // total possible number of steps in the experiment (need 0 input on both ends, hence -2)
        val riser = if (steps * step >= max) step else math.ceil (max / steps / step) * step // limit as ceiling(minimum step size) in thousands
        Experiment (trafo, feeder, node, house, start_time, index, window, interval, 0, max, riser) // in 5 second intervals go from 0 to max in steps of <1000>
    }

    // generate experiments
    val experiments: Array[Experiment] = houses.filter (significant).zipWithIndex.map (gen_exp).toArray

    def name: String = trafo

    def start_time: Calendar = start

    def finish_time: Calendar =
    {
        val t = start_time.clone ().asInstanceOf [Calendar]
        t.add (Calendar.SECOND, experiments.length * window)
        t
    }

    def swing_node: String = transformers.node0

    def swing_node_voltage: Double = transformers.v0
}
