package ch.ninecode.gl

import ch.ninecode.net.LoadFlowNode

/**
 * A node in the GLM file.
 */
trait GLMNode extends LoadFlowNode
{
    /**
     * Return the .glm text for the node, by default make a meter.
     *
     * @return The string value to be included in the .glm file for this node.
     */
    def emit (generator: GLMGenerator): String =
        s"""
           |        object meter
           |        {
           |            name "$id";
           |            phases ${if (generator.isSinglePhase) "AN" else "ABCN"};
           |            bustype PQ;
           |            nominal_voltage ${nominal_voltage}V;
           |        };
           |""".stripMargin
}

