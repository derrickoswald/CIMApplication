package ch.ninecode.sim

import java.util.Calendar

import ch.ninecode.net.LoadFlowEdge
import ch.ninecode.net.LoadFlowNode

/**
 * A work unit to be simulated.
 *
 * @param transformer The name of the transformer (set) that this task simulates.
 *                    Used to name the GridLAB-D model directory and comment within the .glm.
 * @param island      The TopologicalIsland mRID composing the .glm file.
 * @param start       The start time of the simulation.
 * @param end         The end time of the simulation.
 * @param nodes       The nodes to add to the .glm file.
 * @param edges       The edges to add to the .glm file.
 * @param players     The players to be used in the .glm file.
 * @param recorders   The recorders to be included in the .glm file.
 */
case class SimulationTask
(
    transformer: String,
    island: String,
    start: Calendar,
    end: Calendar,
    nodes: Iterable[SimulationNode],
    edges: Iterable[SimulationEdge],
    players: Iterable[SimulationPlayer],
    recorders: Iterable[SimulationRecorder]
)

