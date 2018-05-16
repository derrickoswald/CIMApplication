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
 * @param players list of the GridLAB-D players - these are queried from Cassandra measured_value_by_day and written to .csv player files
 * @param recorders list of GridLAB-D recorders - these .csv recorder files are stored into Cassandra simulated_value_by_day
 * @param directory the directory to write the .glm, players and recorders
 */
case class SimulationTrafoKreis (
    simulation: String,
    island: String,
    transformer: TransformerSet,
    raw_nodes: Iterable[GLMNode],
    raw_edges: Iterable[Iterable[GLMEdge]],
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
                    node.id_seq,
                    node.equipment,
                    node.position,
                    node.voltage,
                    players.filter (_.parent == raw.id),
                    recorders.filter (_.parent == raw.id)
                )
            }
        )

    // add players and recorders to the raw edges
    val edges: Iterable[Iterable[SimulationEdge]] =
        raw_edges.map (
            raw ⇒
            {
                val edges: Iterable[SimulationEdge] = raw.asInstanceOf[Iterable[SimulationEdge]]
                // eliminate duplicate recorders by grouping by property - no two recorders for one gridlabd edge should record the same property, right?
                val ids = edges.map (_.id).toArray
                var these_recorders = recorders.filter (r ⇒ ids.contains (r.parent)).groupBy (_.property).values.map (_.find (_.parent == ids(0)).get).toArray
                // I'm not sure if we should also group for players - the use case of attaching multiple PV installations says no
                // var these_players = players.filter (r ⇒ ids.contains (r.parent)).groupBy (_.property).values.map (_.find (_.parent == ids(0)).get).toArray
                edges.map (
                    edge ⇒
                    {
                        val ret = SimulationEdge (
                            edge.id_equ,
                            edge.id_cn_1,
                            edge.id_cn_2,
                            edge.element,
                            edge.position,
                            players.filter (_.parent == edge.id), // these_players,
                            these_recorders
                        )
                        // only add the recorders and players to the first edge
                        these_recorders = Array()
                        // these_players = Array ()
                        ret
                    }
                )
            }
        )
}