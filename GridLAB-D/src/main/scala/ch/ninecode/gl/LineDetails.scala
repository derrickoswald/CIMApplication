package ch.ninecode.gl

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.model._

/**
 * One element of a line between two nodes.
 *
 * @param line the ACLineSegment for this element
 * @param terminal1 associated Terminal one
 * @param terminal2 associated Terminal two
 * @param per_length_impedance impedance description on a per meter basis
 * @param wire_info asset information for this line segment
 */
final case class LineDetails (
    line: ACLineSegment,
    terminal1: Terminal,
    terminal2: Terminal,
    per_length_impedance: Option[Element],
    wire_info: Option[Element])
{
    import LineDetails._

    lazy val log: Logger = LoggerFactory.getLogger (getClass)

    /**
     * Get the conducting equipment voltage.
     *
     * @param mapping the mapping between BaseVoltage and voltage
     * @return the voltage, or 0.0 if it was not found (V)
     */
    def v (implicit mapping: Array[(String, Double)]): Double =
    {
        mapping.find (_._1 == line.Conductor.ConductingEquipment.BaseVoltage) match
        {
            case Some ((_, v)) => v
            case _ => 0.0
        }
    }

    /**
     * Emit a warning message if the default impedance is being used.
     *
     * @param message method returning the warning message
     */
    def maybe_warn (message: () => String): Unit = if (EMIT_WARNING_WHEN_DEFAULT) log.warn (message ())

    /**
     * Determine if the bitfield is set for the given mask.
     *
     * @param mask single bit mask to check.
     * @return <code>true</code> if the bit is set, <code>false</code> otherwise.
     */
    def isSet (mask: Int): Boolean = 0 != (line.bitfields (mask / 32) & (1 << (mask % 32)))

    /**
     * Predicate to determine if the <code>perLengthImpedance</code> method would return default values.
     *
     * @return <code>true</code> if the <code>perLengthImpedance</code> uses a default value, <code>false</code> otherwise.
     */
    def perLengthImpedanceIsDefault: Boolean =
    {
        per_length_impedance match
        {
            case Some (_: PerLengthSequenceImpedance) =>
                false
            case Some (_: PerLengthPhaseImpedance) =>
                true
            case Some (_: Element) =>
                true
            case None =>
                if (PROPERTIES_ARE_ERRONEOUSLY_PER_KM && (isSet (r1Mask) || isSet (x1Mask) || isSet (r0Mask) || isSet (x0Mask)))
                    false
                else
                    true
        }
    }

    /**
     * Per length impedance of this line, as found in the CIM file.
     *
     * The PerLengthSequenceImpedance is assumed to be per meter at the <code>CIM_BASE_TEMPERATURE</code>.
     * Where there is no PerLengthSequenceImpedance associated with the line, this returns default values,
     * except when <code>PROPERTIES_ARE_ERRONEOUSLY_PER_KM</code> is <code>true</code> in which case any
     * r, x and r0, x0 values of the ACLineSegment are interpreted as per length values on a kilometer basis.
     *
     * @return the positive and zero sequence impedances (Ω/m) at the temperature implicit in the CIM file
     */
    def perLengthImpedance: Sequences =
    {
        per_length_impedance match
        {
            case Some (seq: PerLengthSequenceImpedance) =>
                Sequences (Complex (seq.r, seq.x), Complex (seq.r0, seq.x0))
            case Some (phased: PerLengthPhaseImpedance) =>
                maybe_warn (() => s"ACLineSegment ${line.id} PerLengthPhaseImpedance ${phased.id} is not supported, using default impedance $DEFAULT_PER_LENGTH_IMPEDANCE /m")
                DEFAULT_PER_LENGTH_IMPEDANCE
            case Some (element: Element) =>
                maybe_warn (() => s"ACLineSegment ${line.id} unrecognized PerLengthImpedance class ${element.id}, using default impedance $DEFAULT_PER_LENGTH_IMPEDANCE /m")
                DEFAULT_PER_LENGTH_IMPEDANCE
            case None =>
                if (PROPERTIES_ARE_ERRONEOUSLY_PER_KM && (isSet (r1Mask) || isSet (x1Mask) || isSet (r0Mask) || isSet (x0Mask)))
                {
                    val z1 = Complex (line.r, line.x)
                    val z0 = Complex (line.r0, line.x0)
                    Sequences (z1 / 1000.0, z0 / 1000.0)
                }
                else
                {
                    maybe_warn (() => s"ACLineSegment ${line.id} using default impedance $DEFAULT_PER_LENGTH_IMPEDANCE /m")
                    DEFAULT_PER_LENGTH_IMPEDANCE
                }
        }
    }

    /**
     * Temperature adjusted resistance.
     *
     * @param r           the given resistance (Ω)
     * @param temperature target temperature (°C)
     * @param base        current temperature for the given resistance (°C)
     * @return the temperature compensated resistance (Ω)
     */
    def resistanceAt (r: Double, temperature: Double = CIM_BASE_TEMPERATURE, base: Double = CIM_BASE_TEMPERATURE): Double = (1.0 + (ALPHA * (temperature - base))) * r

    /**
     * Temperature adjusted per length impedance.
     *
     * @param temperature target temperature (°C)
     * @param base        current temperature for the given resistance (°C)
     * @return the temperature compensated per length positive and zero sequence impedance (Ω/m),
     *         and a flag indicating if this value is the default because no per length impedance was found
     */
    def perLengthImpedanceAt (temperature: Double = CIM_BASE_TEMPERATURE, base: Double = CIM_BASE_TEMPERATURE): Sequences =
    {
        val z = perLengthImpedance
        Sequences (
            Complex (resistanceAt (z.z1.re, temperature, base), z.z1.im),
            Complex (resistanceAt (z.z0.re, temperature, base), z.z0.im))
    }

    /**
     * Temperature adjusted impedance.
     *
     * @param temperature target temperature (°C)
     * @param base        current temperature for the given resistance (°C)
     * @return the temperature compensated positive and zero sequence impedance (Ω)
     */
    def impedanceAt (temperature: Double = CIM_BASE_TEMPERATURE, base: Double = CIM_BASE_TEMPERATURE): Sequences =
        perLengthImpedanceAt (temperature, base) * line.Conductor.len

    /**
     * Impedance not temperature adjusted.
     *
     * @return the positive and zero sequence impedance (Ω) at the temperature implicit in the CIM file.
     */
    def impedance: Sequences = perLengthImpedance * line.Conductor.len

    /** @return a summary string for this line */
    override def toString: String = s"${line.id} z=${impedance.toString}"
}

/**
 * Constants for the LineDetails class.
 * Some values, such as impedances can be set here globally (in this static Java Object).
 */
object LineDetails
{
    /**
     * Per meter positive sequence impedance corresponding to GKN 3x16rm/16 1/0.6 kV.
     */
    lazy val DEFAULT_Z1_SMALL: Complex = Complex (1.12e-3, 0.075e-3)

    /**
     * Per meter zero sequence impedance corresponding to GKN 3x16rm/16 1/0.6 kV.
     */
    lazy val DEFAULT_Z0_SMALL: Complex = Complex (4.48e-3, 0.3e-3)

    /**
     * Per meter positive sequence impedance corresponding to GKN 3x95se/95 1/0.6 kV.
     */
    lazy val DEFAULT_Z1_MEDIUM: Complex = Complex (0.190e-3, 0.070e-3)

    /**
     * Per meter zero sequence impedance corresponding to GKN 3x95se/95 1/0.6 kV.
     */
    lazy val DEFAULT_Z0_MEDIUM: Complex = Complex (0.760e-3, 0.28e-3)

    /**
     * Per meter positive sequence impedance corresponding to GKN 3x240se/240 1/0.6 kV.
     */
    lazy val DEFAULT_Z1_LARGE: Complex = Complex (0.0767e-3, 0.069e-3)

    /**
     * Per meter zero sequence impedance corresponding to GKN 3x240se/240 1/0.6 kV.
     */
    lazy val DEFAULT_Z0_LARGE: Complex = Complex (0.307e-3, 0.276e-3)

    /**
     * Default per meter sequence impedances.
     * Used if the ACLineSegment has no PerLengthImpedance specified and the properties are not set directly.
     * @example The default value corresponds to a medium sized cable (GKN 3x95se/95 1/0.6 kV), but this can easily
     * be set programmatically to small (GKN 3x16rm/16 1/0.6 kV), or large (GKN 3x240se/240 1/0.6 kV) cables sizes,
     * like so:
     * LineDetails.DEFAULT_PER_LENGTH_IMPEDANCE = Sequences (LineDetails.DEFAULT_Z1_SMALL, LineDetails.DEFAULT_Z0_SMALL)
     * LineDetails.DEFAULT_PER_LENGTH_IMPEDANCE = Sequences (LineDetails.DEFAULT_Z1_LARGE, LineDetails.DEFAULT_Z0_LARGE)
     * One can also change it to a bespoke value like so:
     * LineDetails.DEFAULT_PER_LENGTH_IMPEDANCE = Sequences (Complex (r1, x1), Complex (r0, x0))
     */
    lazy val DEFAULT_PER_LENGTH_IMPEDANCE: Sequences = Sequences (DEFAULT_Z1_MEDIUM, DEFAULT_Z0_MEDIUM)

    /**
     * Flag to emit a warning message in the log when a default impedance is used.
     */
    var EMIT_WARNING_WHEN_DEFAULT = true

    /**
     * Kludge for erroneous CIM files where the r, x, r0, and x0 properties are per kilometer values.
     * In early versions of our CIM files, the ACLineSegment properties were erroneously populated with per kilometer
     * values instead of the correct total values. This flag is used only when there is no associated
     * PerLengthImpedance object and the r or x property is set. When <code>true</code>, r, x abd r0, x0 are assumed to be
     * the per length sequence impedance of the line on a per kilometer basis.
     */
    var PROPERTIES_ARE_ERRONEOUSLY_PER_KM = true

    /**
     * Index of r field in ACLineSegment bitmask.
     */
    lazy val r1Mask: Int = ACLineSegment.fields.indexOf ("r")

    /**
     *  Index of x field in ACLineSegment bitmask.
     */
    lazy val x1Mask: Int = ACLineSegment.fields.indexOf ("x")

    /**
     * Index of r0 field in ACLineSegment bitmask.
     */
    lazy val r0Mask: Int = ACLineSegment.fields.indexOf ("r0")

    /**
     *  Index of x0 field in ACLineSegment bitmask.
     */
    lazy val x0Mask: Int = ACLineSegment.fields.indexOf ("x0")

    /**
     * Temperature (°C) of resistance values in the CIM file.
     */
    var CIM_BASE_TEMPERATURE: Double = 20.0

    /**
     * Temperature coefficient of resistance.
     *
     * A compromise between copper (0.00393) and aluminum (0.00403) /°C
     *
     * It's complicated (http://nvlpubs.nist.gov/nistpubs/bulletin/07/nbsbulletinv7n1p71_A2b.pdf) and depends on the
     * alloy and how the wire is drawn and worked, e.g.
     * "good commercial copper furnished for use as electrical conductors, the average deviation of C from the mean value
     * 0.00393<sub>8</sub> is only o.ooooo<sub>8</sub>, or 0.2%. Also, when the conductivity and temperature coefficient
     * are altered by annealing or hard-drawing, C has been found to remain constant within the experimental error."
     */
    val ALPHA: Double = 0.004
}
