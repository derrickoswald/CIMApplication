package ch.ninecode.net

;

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
    // there should be at least one line
    require(lines != null, "no LineDetails")
    require(lines.nonEmpty, "no lines in LineDetails sequence")

    /**
     * Get typical line details.
     *
     * @return the first line
     */
    @SuppressWarnings(Array("org.wartremover.warts.TraversableOps"))
    def aLine: LineDetails = lines.head

    /** @return the mRID of the TopologicalNode for one end of the lines */
    lazy val node0: String = lines.map(_.terminal1.TopologicalNode).foldLeft("")(
        (n1, n2) => if ("" == n1) n2 else
            if (n1 == n2) n1 else n1 /* ToDo: log error */
    )

    /** @return the mRID of the TopologicalNode for the other end of the lines */
    lazy val node1: String = lines.map(_.terminal2.TopologicalNode).foldLeft("")(
        (n1, n2) => if ("" == n1) n2 else
            if (n1 == n2) n1 else n1 /* ToDo: log error */
    )

    /** @return a summary string for the lines */
    override def toString: String = s"""${lines.map(_.toString).mkString("||")} from $node0 to $node1"""

    /**
     * Calculate the parallel impedance at the CIM file temperature.
     *
     * @return the positive and zero sequence impedance between the two nodes
     */
    def perLengthImpedance: Sequences =
        lines.map(_.perLengthImpedance).foldLeft(Sequences())((x, y) => x + y.reciprocal).reciprocal

    /**
     * Calculate the parallel impedance at the specified temperature.
     *
     * @param temperature the temperature at which to calculate the impedance (°C)
     * @return the positive and zero sequence impedance between the two nodes
     */
    def perLengthImpedanceAt (temperature: Double = CIM_BASE_TEMPERATURE, base: Double = CIM_BASE_TEMPERATURE): Sequences =
        lines.map(_.perLengthImpedanceAt(temperature, base)).foldLeft(Sequences())((x, y) => x + y.reciprocal).reciprocal

    /**
     * Predicate to determine if the <code>perLengthImpedanceAt</code> method is using default impedance values.
     *
     * @return <code>true</code> if the <code>perLengthImpedanceAt</code> uses a default value, <code>false</code> otherwise.
     */
    def perLengthImpedanceIsDefault: Boolean =
        lines.exists(_.perLengthImpedanceIsDefault)
}
