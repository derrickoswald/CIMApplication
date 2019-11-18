package ch.ninecode.gl

import ch.ninecode.gl.LineDetails.CIM_BASE_TEMPERATURE

/**
 * Line data.
 *
 * Composite line or lines between two nodes.
 *
 * @param lines the ACLineSegments, their terminals, per length impedances and wire information (if any).
 *              all Terminal must be connected to the same TopologicalNode objects
 */
final case class LineData (lines: Iterable[LineDetails])
{
    /** @return the mRID of the TopologicalNode for one end of the lines */
    def node0: String = lines.head.terminal1.TopologicalNode

    /** @return the mRID of the TopologicalNode for the other end of the lines */
    def node1: String = lines.head.terminal2.TopologicalNode

    /** @return a summary string for the lines */
    override def toString: String = s"${lines.map (_.toString).mkString ("||")} from $node0 to $node1"

    def perLengthImpedanceAt (temperature: Double = CIM_BASE_TEMPERATURE): Sequences =
        lines.map (_.perLengthImpedanceAt (temperature)).foldLeft (Sequences ()) ((x, y) ⇒ x + y.reciprocal).reciprocal

    def perLengthImpedanceIsDefault: Boolean =
        lines.exists (_.perLengthImpedanceIsDefault)
}
