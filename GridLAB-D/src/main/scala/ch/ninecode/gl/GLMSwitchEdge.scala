package ch.ninecode.gl

import ch.ninecode.net.SwitchData
import ch.ninecode.net.SwitchDetails
import ch.ninecode.model.Element
import ch.ninecode.model.Terminal
import ch.ninecode.net.SwitchEdge

case class GLMSwitchEdge (
    data: SwitchData
)
extends SwitchEdge (data)
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
        val status = if (closed) "CLOSED" else "OPEN"
        // ensure it doesn't trip immediately
        val current = if (ratedCurrent <= 0) 9999.0 else ratedCurrent

        // also set mean_replacement_time because sometimes: WARNING  [INIT] : Fuse:SIG8494 has a negative or 0 mean replacement time - defaulting to 1 hour
        val fuse_details = if (fuse)
            s"""
               |            mean_replacement_time 3600.0;
               |            current_limit ${current}A;""".stripMargin
        else
            ""
        s"""
          |        object ${if (fuse) "fuse" else "switch"}
          |        {
          |            name "$id";
          |            phases ${if (generator.isSinglePhase) "AN" else "ABCN"};
          |            from "$cn1";
          |            to "$cn2";
          |            status "$status";$fuse_details
          |        };
          |""".stripMargin
    }
}

object GLMSwitchEdge
{
    // ToDo: remove me
    def apply (
        cn1: String,
        cn2: String,
        switches: Iterable[Element]
    ): GLMSwitchEdge =
    {
        val t1 = Terminal (TopologicalNode = cn1)
        val t2 = Terminal (TopologicalNode = cn2)
        GLMSwitchEdge (SwitchData (switches.map (x => SwitchDetails (x, t1, t2, None))))
    }
}