package ch.ninecode.on

import ch.ninecode.gl.GLMEdge
import ch.ninecode.gl.GLMGenerator
import ch.ninecode.model.Switch

case class PlayerSwitchEdge
(
    cn1: String,
    cn2: String,
    switch: Switch,
    fuse: Boolean
)
extends GLMEdge
{
    override def id: String = switch.id

    /**
     * Emit a switch or fuse.
     *
     * @param generator the driver program
     * @return A switch string (.glm text) for this edge.
     */
    override def emit (generator: GLMGenerator): String =
    {
        val obj = if (fuse) "fuse" else "switch"
        var current = switch.ratedCurrent
        if (current <= 0)
            current = 9999.0 // ensure it doesn't trip immediately
        // also set mean_replacement_time because sometimes: WARNING  [INIT] : Fuse:SIG8494 has a negative or 0 mean replacement time - defaulting to 1 hour
        val details = if (fuse)
            """
            mean_replacement_time 3600.0;
            current_limit %sA;
            object player
            {
                property "status";
                file "input_data/%s.csv";
            };""".format (current, id)
        else
            """
            object player
            {
                property "status";
                file "input_data/%s.csv";
            };""".format (id)

        """
        |        object %s
        |        {
        |            name "%s";
        |            phases %s;
        |            from "%s";
        |            to "%s";%s
        |        };
        |""".stripMargin.format (obj, id, if (generator.isSinglePhase) "AN" else "ABCN", cn1, cn2, details)
    }
}

