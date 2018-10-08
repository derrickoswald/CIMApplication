package ch.ninecode.gl

import ch.ninecode.model.Element

/**
 * Basic properties of a node.
 */
trait GLMNode extends Graphable with Serializable
{
    /**
     * The unique node identifier.
     *
     * @return The ID of the node (the mRID of the ConnectivityNode or TopologicalNode).
     */
    def id: String

    /**
     * The nominal voltage of the node.
     *
     * @return The voltage of the node (volts).
     */
    def nominal_voltage: Double

    /**
     * Return the .glm text for the node.
     *
     * @param one_phase If <code>true</code>, emit a single phase node, otherwise emit a three phase node.
     * @return The string value to be included in the .glm file for this node.
     */
    def emit (generator: GLMGenerator): String =
        """
          |        object meter
          |        {
          |            name "%s";
          |            phases %s;
          |            bustype PQ;
          |            nominal_voltage %sV;
          |        };
          |""".stripMargin.format (id, if (generator.isSinglePhase) "AN" else "ABCN", nominal_voltage)
}

object GLMNode
{
    /**
     * Temporary measure until we figure out how to create subclasses of GMLNode from:
     *   - PreNode/PreEdge trace results
     *   - Island trace results
     *   - multi-island trace results
     * @param elements
     * @param id
     * @param cn2
     * @return a type of node
     */
    def toGLMNode (elements: Iterable[Element], id: String, nominal_voltage: Double): GLMNode =
    {
        PreNode (id, nominal_voltage, null)
    }
}
