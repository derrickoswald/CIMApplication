package ch.ninecode.net;

/**
 * Switch data.
 *
 * Switches of various types between two nodes.
 *
 * @param switches the switch details, their terminals, and switch information (if any).
 *                 all corresponding Terminal must be connected to the same TopologicalNode objects
 */
final case class SwitchData (switches: Iterable[SwitchDetails])
{
    // there should be at least one switch
    require (switches != null, "no SwitchDetails")
    require (switches.nonEmpty, "no switches in SwitchDetails sequence")

    /**
     * Get typical switch details.
     *
     * @return the first switch
     */
    @SuppressWarnings (Array ("org.wartremover.warts.TraversableOps"))
    def aSwitch: SwitchDetails = switches.head

    /** @return the mRID of the TopologicalNode for one end of the switches */
    lazy val node0: String = switches.map (_.terminal1.TopologicalNode).foldLeft ("")(
        (n1, n2) => if ("" == n1) n2 else if (n1 == n2) n1 else n1 /* ToDo: log error */
    )

    /** @return the mRID of the TopologicalNode for the other end of the switches */
    lazy val node1: String = switches.map (_.terminal2.TopologicalNode).foldLeft ("")(
        (n1, n2) => if ("" == n1) n2 else if (n1 == n2) n1 else n1 /* ToDo: log error */
    )

    /**
     * Get the closed status of the switches.
     *
     * @return <code>true</code> if any switch is closed, <code>false</code> otherwise
     */
    def closed: Boolean = switches.exists (_.closed)

    /** @return a summary string for the switches */
    override def toString: String = s"""${switches.map (_.toString).mkString ("||")} from $node0 to $node1"""
}
