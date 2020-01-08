package ch.ninecode.gl

import ch.ninecode.model._

case class SwitchEdge
(
    cn1: String,
    cn2: String,
    switches: Iterable[Element]
)
    extends GLMEdge
{
    def id: String = switches.map (_.id).toArray.sortWith (_ < _).mkString ("_")

    def toSwitch (element: Element): Switch =
    {
        element match
        {
            case s: Switch ⇒ s.asInstanceOf [Switch]
            case c: Cut ⇒ c.asInstanceOf [Cut].Switch
            case d: Disconnector ⇒ d.asInstanceOf [Disconnector].Switch
            case f: Fuse ⇒ f.asInstanceOf [Fuse].Switch
            case g: GroundDisconnector ⇒ g.asInstanceOf [GroundDisconnector].Switch
            case j: Jumper ⇒ j.asInstanceOf [Jumper].Switch
            case m: MktSwitch ⇒ m.asInstanceOf [MktSwitch].Switch
            case p: ProtectedSwitch ⇒ p.asInstanceOf [ProtectedSwitch].Switch
            case b: Breaker ⇒ b.asInstanceOf [Breaker].ProtectedSwitch.Switch
            case l: LoadBreakSwitch ⇒ l.asInstanceOf [LoadBreakSwitch].ProtectedSwitch.Switch
            case r: Recloser ⇒ r.asInstanceOf [Recloser].ProtectedSwitch.Switch
            case s: Sectionaliser ⇒ s.asInstanceOf [Sectionaliser].Switch
            case _ ⇒
                println ("non-switch (%s:%s) in SwitchEdge".format (element.getClass, element.id))
                null.asInstanceOf [Switch]
        }
    }

    def normalOpen: Boolean =
        switches.forall (x ⇒ toSwitch (x).normalOpen)

    def ratedCurrent: Double =
        switches.map (x ⇒ toSwitch (x).ratedCurrent).min

    def fuse: Boolean =
        switches.forall ( { case f: Fuse ⇒ true case _ ⇒ false })

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