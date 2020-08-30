package ch.ninecode.gl

import ch.ninecode.net.LoadFlowEdge

/**
 * An edge in the GLM file.
 */
trait GLMEdge extends LoadFlowEdge
{
    /**
     * Return the .glm text for the edge, by default, make a link.
     *
     * @param generator The generator object with details on what/how to generate the .glm.
     * @return The string value to be included in the .glm file for this edge.
     */
    def emit (generator: GLMGenerator): String =
        s"""
           |        object link
           |        {
           |            name "$id";
           |            phases ${if (generator.isSinglePhase) "AN" else "ABCN"};
           |            from "$cn1";
           |            to "$cn2";
           |        };
           |""".stripMargin
}

