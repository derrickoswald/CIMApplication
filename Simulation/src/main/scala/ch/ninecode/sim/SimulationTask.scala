package ch.ninecode.sim

import java.util.Calendar

import ch.ninecode.gl.GLMEdge
import ch.ninecode.gl.GLMNode

case class SimulationTask
(
    island: String,
    start: Calendar,
    end: Calendar,
    nodes: Iterable[GLMNode],
    edges: Iterable[Iterable[GLMEdge]],
    players: Array[SimulationPlayer],
    recorders: Array[SimulationRecorder]
)
{

}
