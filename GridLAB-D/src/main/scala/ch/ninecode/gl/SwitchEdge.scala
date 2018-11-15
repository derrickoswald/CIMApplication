package ch.ninecode.gl

import ch.ninecode.model.Switch

case class SwitchEdge
(
    cn1: String,
    cn2: String,
    switch: Switch,
    fuse: Boolean
)
extends GLMEdge
{
    def id: String = switch.id

    /**
     * Emit a switch or fuse.
     *
     * @param generator the driver program
     * @return A switch string (.glm text) for this edge.
     */
    override def emit (generator: GLMGenerator): String =
    {
        val status = if (switch.normalOpen) "OPEN" else "CLOSED"
        var current = switch.ratedCurrent
        if (current <= 0)
            current = 9999.0 // ensure it doesn't trip immediately
        // also set mean_replacement_time because sometimes: WARNING  [INIT] : Fuse:SIG8494 has a negative or 0 mean replacement time - defaulting to 1 hour
        val fuse_details = if (fuse)
            """
            mean_replacement_time 3600.0;
            current_limit %sA;""".format (current)
        else
            ""

        """
        |        object %s
        |        {
        |            name "%s";
        |            phases %s;
        |            from "%s";
        |            to "%s";
        |            status "%s";%s
        |        };
        |""".stripMargin.format (if (fuse) "fuse" else "switch", id, if (generator.isSinglePhase) "AN" else "ABCN", cn1, cn2, status, fuse_details)
    }
}