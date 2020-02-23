package ch.ninecode.gl

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

