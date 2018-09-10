package ch.ninecode.mv

import ch.ninecode.gl.GLMEdge

case class FeederArea (
    feeder: String,
    nodes: Iterable[FeederNode],
    edges: Iterable[GLMEdge])
