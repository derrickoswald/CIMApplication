package ch.ninecode.on

import ch.ninecode.gl.GLMEdge

/**
 * The feeder data.
 *
 * @param feeder the mRID of the feeder
 * @param metadata information about the feeder, such as station name, connector number, description and the source CIM element
 * @param nodes the nodes belonging to the feeder (this includes the adjacent "one-switch-away" feeder nodes)
 * @param edges the edges belonging to the feeder (this includes the adjacent "one-switch-away" feeder edges)
 */
case class FeederArea (
    feeder: String,
    metadata: FeederMetadata,
    nodes: Iterable[FeederNode],
    edges: Iterable[GLMEdge])
