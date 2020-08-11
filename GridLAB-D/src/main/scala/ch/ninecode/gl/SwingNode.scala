package ch.ninecode.gl

import ch.ninecode.net.LoadFlowNode

case class SwingNode (
    override val id: String,
    override val nominal_voltage: Double,
    name: String)
    extends LoadFlowNode (id, nominal_voltage)
        with GLMNode
