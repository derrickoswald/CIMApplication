package ch.ninecode.sc

import java.util.Calendar

import ch.ninecode.gl.Complex
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
 * @param directory the directory to write the .glm, players and recorders
 */
case class SimulationTransformerServiceArea (
    simulation: String,
    island: String,
    transformer: TransformerSet,
    nodes: Iterable[GLMNode],
    edges: Iterable[Iterable[GLMEdge]],
    start_time: Calendar,
    directory: String) extends Serializable
{
    val name: String = transformer.transformer_name
    val swing_nodes: Array[SwingNode] = Array (SwingNode (transformer.node0, transformer.v0, transformer.transformer_name))

    // we can filter out only house nodes based on PSRType if the CIM profile supports that
    val only_houses: Boolean = true
    val n: Iterable[GLMNode] = if (only_houses) nodes.filter (_.asInstanceOf[SimulationNode].psrtype == "PSRType_HouseService") else nodes

    // generate experiments as 5 seconds short circuits at each node
    lazy val experiments: Array[ScExperiment] = n.zipWithIndex // (node, index)
        .map (x ⇒ ScExperiment (simulation, x._1.asInstanceOf[SimulationNode].id_seq, start_time, x._2, 5, Complex (10.0, 0))).toArray

    /**
     * Calendar duplication utility function.
     * @param c The Calendar value to be cloned.
     */
    def dup(c: Calendar): Calendar = c.clone().asInstanceOf[Calendar]
    val finish_time: Calendar =  { val t = dup (start_time); t.add (Calendar.SECOND, 5 * (experiments.length + 1)); t }
}