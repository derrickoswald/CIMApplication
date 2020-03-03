package ch.ninecode.net;

import ch.ninecode.net.LineDetails.CIM_BASE_TEMPERATURE
import ch.ninecode.util.Sequences

/**
 * Line data.
 *
 * Composite line or lines between two nodes.
 *
 * @param lines the ACLineSegments, their terminals, per length impedances and wire information (if any).
 *              all corresponding Terminal must be connected to the same TopologicalNode objects
 */
final case class LineData (lines: Iterable[LineDetails])
{
    /** @return the mRID of the TopologicalNode for one end of the lines */
    def node0: String = lines.head.terminal1.TopologicalNode

    /** @return the mRID of the TopologicalNode for the other end of the lines */
    def node1: String = lines.head.terminal2.TopologicalNode

    /** @return a summary string for the lines */
    override def toString: String = s"""${lines.map (_.toString).mkString ("||")} from $node0 to $node1"""

    /**
     * Calculate the parallel impedance at the specified temperature.
     *
     * @param temperature the temperature at which to calculate the impedance (°C)
     * @return the positive and zero sequence impedance between the two nodes
     */
    def perLengthImpedanceAt (temperature: Double = CIM_BASE_TEMPERATURE): Sequences =
        lines.map (_.perLengthImpedanceAt (temperature)).foldLeft (Sequences ()) ((x, y) => x + y.reciprocal).reciprocal

    /**
     * Predicate to determine if the <code>perLengthImpedanceAt</code> method is using default impedance values.
     *
     * @return <code>true</code> if the <code>perLengthImpedanceAt</code> uses a default value, <code>false</code> otherwise.
     */
    def perLengthImpedanceIsDefault: Boolean =
        lines.exists (_.perLengthImpedanceIsDefault)
}
