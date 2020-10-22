package ch.ninecode.on

import ch.ninecode.gl.GLMNode
import ch.ninecode.model.Element
import ch.ninecode.net.LoadFlowNode

/**
 * Vertex data.
 *
 * @param _id             ConnectivityNode or TopologicalNode mRID.
 * @param nominal_voltage Node voltage.
 * @param feeder          Element attached to this node, if any.
 */
case class FeederNode
(
    _id: String,
    override val nominal_voltage: Double,
    feeder: Option[Element])
    extends LoadFlowNode(
        feeder match
        {
            case Some(element) => element.id
            case _ => _id
        },
        nominal_voltage)
        with GLMNode

