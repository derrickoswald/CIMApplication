package ch.ninecode.on

import ch.ninecode.gl.GLMNode
import ch.ninecode.model.Element
import ch.ninecode.net.LoadFlowNode

/**
 * Vertex data.
 *
 * @param _id             ConnectivityNode or TopologicalNode mRID.
 * @param nominal_voltage Node voltage.
 */
case class FeederNode
(
    _id: String,
    override val nominal_voltage: Double,
    feeder: Element)
extends LoadFlowNode (if (null == feeder) _id else feeder.id, nominal_voltage)
with GLMNode

object FeederNode
{
    def apply (_id: String, feeder: Element, nominal_voltage: Double): FeederNode =
        FeederNode (_id, nominal_voltage, feeder)

    /**
     * Create a GLMNode.
     *
     * @param element         the element attached to this node
     * @param id              the id of the TopologicalNode
     * @param nominal_voltage the nominal voltage of the node (V)
     * @return a type of node
     */
    def apply (element: Option[Element], id: String, nominal_voltage: Double): FeederNode =
        FeederNode (id, element.orNull, nominal_voltage)
}

