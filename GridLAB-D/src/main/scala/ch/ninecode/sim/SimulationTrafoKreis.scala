package ch.ninecode.sim

import java.util.Calendar

import ch.ninecode.gl.GLMEdge
import ch.ninecode.gl.GLMNode
import ch.ninecode.gl.SwingNode
import ch.ninecode.gl.TransformerSet

case class SimulationTrafoKreis (
    island: String,
    transformer: TransformerSet,
    raw_nodes: Iterable[GLMNode],
    raw_edges: Iterable[Iterable[GLMEdge]],
    start_time: Calendar,
    finish_time: Calendar,
    players: Array[SimulationPlayer],
    recorders: Array[SimulationRecorder])
{
    val name: String = transformer.transformer_name
    val swing_nodes: Array[SwingNode] = Array (SwingNode (transformer.node0, transformer.v0, transformer.transformer_name))

    // add players and recorders to the raw nodes
    val nodes: Iterable[SimulationNode] =
        raw_nodes.map (
            raw ⇒
                SimulationNode (
                    raw.id,
                    raw.nominal_voltage,
                    players.filter (_.parent == raw.id),
                    recorders.filter (_.parent == raw.id)
                )
        )

    // add players and recorders to the raw edges
    // ToDo: this is not right
    val edges: Iterable[Iterable[SimulationEdge]] =
        raw_edges.map (
            raw ⇒
                raw.map (
                    r ⇒
                        SimulationEdge (
                            r.id,
                            r.cn1,
                            r.cn2,
                            r.el,
                            players.filter (_.parent == r.id),
                            recorders.filter (_.parent == r.id)
                        )
                )
        )
}