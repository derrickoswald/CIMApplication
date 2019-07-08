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
 * @param simulation  the primary key for the simulation as stored in the simulation table.
 * @param island      the name of the TopologicalIsland for the low voltage side of the transformer
 * @param transformer the transformer (or set of ganged transformers) servicing the area
 * @param raw_nodes   topological nodes for the simulation
 * @param raw_edges   topological edges for the simulation
 * @param start_time  the simulation starting time
 * @param finish_time the simulation ending time
 * @param players     list of the GridLAB-D players - these are queried from Cassandra measured_value and written to .csv player files
 * @param recorders   list of GridLAB-D recorders - these .csv recorder files are stored into Cassandra simulated_value
 * @param directory   the directory to write the .glm, players and recorders
 */
case class SimulationTrafoKreis
(
    simulation: String,
    island: String,
    transformer: TransformerSet,
    raw_nodes: Iterable[GLMNode],
    raw_edges: Iterable[GLMEdge],
    start_time: Calendar,
    finish_time: Calendar,
    players: Iterable[SimulationPlayer],
    recorders: Iterable[SimulationRecorder],
    directory: String)
{
    val name: String = transformer.transformer_name
    val swing_nodes: Array[SwingNode] = Array (SwingNode (transformer.node0, transformer.v0, transformer.transformer_name))

    // add players and recorders to the raw nodes
    val nodes: Iterable[SimulationNode] =
        raw_nodes.map (
            raw ⇒
            {
                val node = raw.asInstanceOf [SimulationNode]
                val my_players = players.filter (_.parent == raw.id)
                // for power recorders of nodes with players, it has to be attached to the player, not the node
                // the query has been altered to make the parent name for these nodes have the form <mrid>_load_object
                val my_player_objects = my_players.map (_.name + "_object").toArray
                val my_recorders = recorders.filter (x ⇒ x.parent == raw.id || my_player_objects.contains (x.parent))
                SimulationNode (
                    node.id,
                    node.nominal_voltage,
                    node.equipment,
                    node.world_position,
                    node.schematic_position,
                    my_players,
                    my_recorders
                )
            }
        )

    // add players and recorders to the raw edges
    val edges: Iterable[SimulationEdge] =
        raw_edges.map (
            raw ⇒
            {
                val edge: SimulationEdge = raw.asInstanceOf [SimulationEdge]
                val id = edge.id
                var these_recorders = recorders.filter (_.parent == id)
                var these_players = players.filter (_.parent == id)
                SimulationEdge (
                    edge.cn1,
                    edge.cn2,
                    edge.rawedge,
                    edge.world_position,
                    edge.schematic_position,
                    these_players,
                    these_recorders
                )
            }
        )
}