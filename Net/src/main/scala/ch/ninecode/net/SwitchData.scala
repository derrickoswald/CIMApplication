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
    /** @return the mRID of the TopologicalNode for one end of the switches */
    def node0: String = switches.head.terminal1.TopologicalNode

    /** @return the mRID of the TopologicalNode for the other end of the switches */
    def node1: String = switches.head.terminal2.TopologicalNode

    /** @return a summary string for the switches */
    override def toString: String = s"""${switches.map (_.toString).mkString ("||")} from $node0 to $node1"""
}
