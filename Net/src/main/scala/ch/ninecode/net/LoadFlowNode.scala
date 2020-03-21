package ch.ninecode.net

import ch.ninecode.util.Graphable

/**
 * Generic load-flow node.
 *
 * Base class for extension by load-flow engine implementing classes.
 *
 * @param _id              unique node identifier, usually the mRID of the associated CIM TopologicalNode
 * @param _nominal_voltage nominal voltage of the node (V)
 */
class LoadFlowNode
(
    _id: String,
    _nominal_voltage: Double
)
extends Graphable
with Serializable
{
    def id: String = _id
    def nominal_voltage: Double = _nominal_voltage
}

