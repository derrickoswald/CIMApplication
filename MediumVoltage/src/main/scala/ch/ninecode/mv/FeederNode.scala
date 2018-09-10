package ch.ninecode.mv

import ch.ninecode.gl.GLMNode
import ch.ninecode.model.Element


/**
 * Vertex data.
 *
 * @param id ConnectivityNode or TopologicalNode MRID.
 * @param nominal_voltage Node voltage.
 */
case class FeederNode (
    id: String,
    feeder: Element,
    nominal_voltage: Double)
extends GLMNode

object FeederNode
{
    /**
     * Create a GMLNode.
     *
     * @param elements the elements attached to this node
     * @param id the id of the TopologicalNode
     * @param nominal_voltage the nominal voltage of the node (V)
     * @return a type of node
     */
    def toFeederNode (elements: Iterable[Element], id: String, nominal_voltage: Double): FeederNode =
    {
        FeederNode (id, if (null != elements) elements.head else null, nominal_voltage)
    }
}

