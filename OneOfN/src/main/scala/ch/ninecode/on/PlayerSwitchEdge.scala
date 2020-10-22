package ch.ninecode.on

import ch.ninecode.gl.GLMEdge
import ch.ninecode.gl.GLMGenerator
import ch.ninecode.model.Switch
import ch.ninecode.net.LoadFlowEdge

case class PlayerSwitchEdge
(
    override val cn1: String,
    override val cn2: String,
    switch: Switch,
    fuse: Boolean
)
    extends LoadFlowEdge(switch.id, cn1, cn2)
        with GLMEdge
{
    /**
     * Emit a switch or fuse.
     *
     * @param generator the driver program
     * @return A switch string (.glm text) for this edge.
     */
    override def emit (generator: GLMGenerator): String =
    {
        val obj = if (fuse) "fuse" else "switch"
        // ensure it doesn't trip immediately
        val current = if (switch.ratedCurrent <= 0) 9999.0 else switch.ratedCurrent
        // also set mean_replacement_time because sometimes: WARNING  [INIT] : Fuse:SIG8494 has a negative or 0 mean replacement time - defaulting to 1 hour
        val details = if (fuse)
            s"""
               |            mean_replacement_time 3600.0;
               |            current_limit ${current}A;
               |            object player
               |            {
               |                property "status";
               |                file "input_data/$id.csv";
               |            };""".stripMargin
        else
            s"""
               |            object player
               |            {
               |                property "status";
               |                file "input_data/$id.csv";
               |            };""".stripMargin

        s"""
           |        object $obj
           |        {
           |            name "$id";
           |            phases ${if (generator.isSinglePhase) "AN" else "ABCN"};
           |            from "$cn1";
           |            to "$cn2";$details
           |        };
           |""".stripMargin
    }
}

