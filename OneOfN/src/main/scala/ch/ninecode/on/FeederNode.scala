package ch.ninecode.on

import ch.ninecode.gl.GLMNode
import ch.ninecode.model.Element


/**
 * Vertex data.
 *
 * @param _id             ConnectivityNode or TopologicalNode mRID.
 * @param nominal_voltage Node voltage.
 */
case class FeederNode
(
    _id: String,
    feeder: Element,
    nominal_voltage: Double)
    extends GLMNode
{
    override def id: String = if (null == feeder) _id else feeder.id
}

object FeederNode
{
    /**
     * Create a GMLNode.
     *
     * @param elements        the elements attached to this node
     * @param id              the id of the TopologicalNode
     * @param nominal_voltage the nominal voltage of the node (V)
     * @return a type of node
     */
    def toFeederNode (elements: Iterable[Element], id: String, nominal_voltage: Double): FeederNode =
    {
        FeederNode (id, if (null == elements) null else elements.headOption.orNull, nominal_voltage)
    }
}

