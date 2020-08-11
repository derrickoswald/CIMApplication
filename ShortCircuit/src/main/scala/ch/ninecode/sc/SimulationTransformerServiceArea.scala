package ch.ninecode.sc

import java.util.Calendar

import ch.ninecode.gl.GLMEdge
import ch.ninecode.net.TransformerIsland
import ch.ninecode.util.Complex

/**
 * A container for a simulation piece of work.
 *
 * Includes the information necessary to perform a simulation (of a transformer service area - a trafokreis).
 *
 * @param island     the transformer island for the low voltage side of the transformer
 * @param nodes      topological nodes for the simulation
 * @param edges      topological edges for the simulation
 * @param start_time the simulation starting time
 * @param directory  the directory to write the .glm, players and recorders
 */
case class SimulationTransformerServiceArea
(
    island: TransformerIsland,
    nodes: Iterable[SimulationNode],
    edges: Iterable[GLMEdge],
    start_time: Calendar,
    directory: String) extends Serializable
{
    val name: String = island.island_name

    // experiment only on houses and busbars
    def keep (node: SimulationNode): Boolean = node.consumer || node.busbar

    // generate experiments as 5 seconds short circuit (100Ω) at each node
    lazy val experiments: Array[ScExperiment] = nodes.filter (keep).zipWithIndex // (node, index)
        .map (
            x =>
            {
                val (node, index) = x
                ScExperiment (name, node.id, node.equipment, start_time, index, 5, node.nominal_voltage, Complex (100.0))
            }
        ).toArray

    /**
     * Calendar duplication utility function.
     *
     * @param c The Calendar value to be cloned.
     */
    def dup (c: Calendar): Calendar = c.clone ().asInstanceOf [Calendar]

    val finish_time: Calendar =
    {
        val t = dup (start_time)
        t.add (Calendar.SECOND, 5 * (experiments.length + 1))
        t
    }
}
