package ch.ninecode.mv

import ch.ninecode.gl.GLMEdge

case class FeederArea (
    feeder: String,
    station: String,
    number: String,
    description: String,
    nodes: Iterable[FeederNode],
    edges: Iterable[GLMEdge])
