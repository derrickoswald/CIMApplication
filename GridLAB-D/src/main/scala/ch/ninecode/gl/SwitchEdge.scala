package ch.ninecode.gl

import ch.ninecode.net.SwitchData
import ch.ninecode.net.SwitchDetails
import ch.ninecode.model._

case class SwitchEdge
(
    data: SwitchData
)
    extends GLMEdge
{
    def id: String = data.switches.map (_.element.id).toArray.sortWith (_ < _).mkString ("_")

    def cn1: String = data.node0

    def cn2: String = data.node1

    def normalOpen: Boolean =
        data.switches.forall (x => x.asSwitch.normalOpen)

    def ratedCurrent: Double =
        data.switches.map (x => x.asSwitch.ratedCurrent).min

    def fuse: Boolean =
        data.switches.forall (_.fuse)

    /**
     * Emit a switch or fuse.
     *
     * @param generator the driver program
     * @return A switch string (.glm text) for this edge.
     */
    override def emit (generator: GLMGenerator): String =
    {
        val status = if (normalOpen) "OPEN" else "CLOSED"
        var current = ratedCurrent
        if (current <= 0)
            current = 9999.0 // ensure it doesn't trip immediately

        // also set mean_replacement_time because sometimes: WARNING  [INIT] : Fuse:SIG8494 has a negative or 0 mean replacement time - defaulting to 1 hour
        val fuse_details = if (fuse)
            """
               |            mean_replacement_time 3600.0;
               |            current_limit %sA;""".format (current).stripMargin
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

object SwitchEdge
{
    // ToDo: remove me
    def apply (
        cn1: String,
        cn2: String,
        switches: Iterable[Element]
    ): SwitchEdge =
    {
        val t1 = Terminal (TopologicalNode = cn1)
        val t2 = Terminal (TopologicalNode = cn2)
        SwitchEdge (SwitchData (switches.map (x => SwitchDetails (x, t1, t2, None))))
    }
}