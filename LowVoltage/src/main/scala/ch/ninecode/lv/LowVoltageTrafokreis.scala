package ch.ninecode.lv

import java.util.Calendar

import ch.ninecode.gl.GLMEdge
import ch.ninecode.net.TransformerIsland

/**
 * A work package for gridlab simulation.
 *
 * @param start        Starting time to be used in the simulation.
 * @param transformers The feeding transformers (or ganged transformers).
 * @param nodes        The nodes in the transformer service area.
 * @param edges        The edges in the transformer service area.
 */
case class LowVoltageTrafokreis
(
    start: Calendar,
    transformers: TransformerIsland,
    nodes: Iterable[LowVoltageNode],
    edges: Iterable[GLMEdge]
)
{
    lazy val name: String = transformers.island_name
}