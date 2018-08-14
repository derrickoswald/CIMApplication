package ch.ninecode.gl

import ch.ninecode.model.Switch

case class SwitchEdge
(
    node1: String,
    node2: String,
    switch: Switch,
    fuse: Boolean
)
extends GLMEdge
{
    def id: String = switch.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID

    /**
     * The node id connected to the first terminal.
     *
     * @return The ID of the ConnectivityNode or TopologicalNode reference by the Terminal with sequence number 1.
     */
    def cn1: String = node1

    /**
     * The node id connected to the second terminal.
     *
     * @return The ID of the ConnectivityNode or TopologicalNode reference by the Terminal with sequence number 2.
     */
    def cn2: String = node2

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