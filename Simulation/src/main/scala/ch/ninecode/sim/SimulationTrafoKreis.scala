package ch.ninecode.sim

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
 * @param raw_nodes topological nodes for the simulation
 * @param raw_edges topological edges for the simulation
 * @param start_time the simulatuon starting time
 * @param finish_time the simulation ending time
 * @param players list of the GridLAB-D players - these are queried from Cassandra measured_value and written to .csv player files
 * @param recorders list of GridLAB-D recorders - these .csv recorder files are stored into Cassandra simulated_value
 * @param directory the directory to write the .glm, players and recorders
 */
case class SimulationTrafoKreis (
    simulation: String,
    island: String,
    transformer: TransformerSet,
    raw_nodes: Iterable[GLMNode],
    raw_edges: Iterable[GLMEdge],
    start_time: Calendar,
    finish_time: Calendar,
    players: Array[SimulationPlayer],
    recorders: Array[SimulationRecorder],
    directory: String)
{
    val name: String = transformer.transformer_name
    val swing_nodes: Array[SwingNode] = Array (SwingNode (transformer.node0, transformer.v0, transformer.transformer_name))

    // add players and recorders to the raw nodes
    val nodes: Iterable[SimulationNode] =
        raw_nodes.map (
            raw ⇒
            {
                val node = raw.asInstanceOf[SimulationNode]
                SimulationNode (
                    node.id,
                    node.nominal_voltage,
                    node.equipment,
                    node.position,
                    players.filter (_.parent == raw.id),
                    recorders.filter (_.parent == raw.id)
                )
            }
        )

    // add players and recorders to the raw edges
    val edges: Iterable[SimulationEdge] =
        raw_edges.map (
            raw ⇒
            {
                val edge: SimulationEdge = raw.asInstanceOf[SimulationEdge]
                val id = edge.id
                var these_recorders = recorders.filter (_.parent == id) // keep only recorders for primary edge
                var these_players = players.filter (_.parent == id)
                SimulationEdge (
                    edge.id,
                    edge.cn1,
                    edge.cn2,
                    edge.rawedge,
                    edge.position,
                    these_players,
                    these_recorders
                )
            }
        )
}