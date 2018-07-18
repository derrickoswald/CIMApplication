package ch.ninecode.sc

import java.util.Calendar

import ch.ninecode.gl.GLMEdge
import ch.ninecode.gl.GLMNode
import ch.ninecode.gl.SwingNode
import ch.ninecode.gl.TransformerSet

/**
 * A container for a simulation piece of work.
 *
 * Includes the information necessary to perform a simulation (of a transformer service area - a trafokreis).
 *
 * @param simulation the primary key for the simulation as stored in the simulation table.
 * @param island the name of the TopologicalIsland for the low voltage side of the transformer
 * @param transformer the transformer (or set of ganged transformers) servicing the area
 * @param nodes topological nodes for the simulation
 * @param edges topological edges for the simulation
 * @param start_time the simulatuon starting time
 * @param finish_time the simulation ending time
 * @param directory the directory to write the .glm, players and recorders
 */
case class SimulationTransformerServiceArea (
    simulation: String,
    island: String,
    transformer: TransformerSet,
    nodes: Iterable[GLMNode],
    edges: Iterable[Iterable[GLMEdge]],
    start_time: Calendar,
    finish_time: Calendar,
    directory: String) extends Serializable
{
    val name: String = transformer.transformer_name
    val swing_nodes: Array[SwingNode] = Array (SwingNode (transformer.node0, transformer.v0, transformer.transformer_name))
}
