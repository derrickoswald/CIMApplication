package ch.ninecode.net

;

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.model.ACLineSegment
import ch.ninecode.model.Element
import ch.ninecode.model.PerLengthPhaseImpedance
import ch.ninecode.model.PerLengthSequenceImpedance
import ch.ninecode.model.Terminal
import ch.ninecode.util.Complex
import ch.ninecode.util.Sequences

/**
 * One element of a line between two nodes.
 *
 * @param line                 the ACLineSegment for this element
 * @param terminal1            associated Terminal one
 * @param terminal2            associated Terminal two
 * @param per_length_impedance impedance description on a per meter basis
 * @param wire_info            asset information for this line segment
 */
final case class LineDetails (
    line: ACLineSegment,
    terminal1: Terminal,
    terminal2: Terminal,
    per_length_impedance: Option[Element],
    wire_info: Option[Element],
    CIMBaseTemperature: Double = LineDetails.CIM_BASE_TEMPERATURE,
    Alpha: Double = LineDetails.ALPHA)
{

    import LineDetails._

    /**
     * Predicate to determine if the <code>perLengthImpedance</code> method would return default values.
     *
     * @return <code>true</code> if the <code>perLengthImpedance</code> uses a default value, <code>false</code> otherwise.
     */
    lazy val perLengthImpedanceIsDefault: Boolean = checkIfPerLengthImpedanceIsDefault (this)

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
    lazy val perLengthImpedance: Sequences = getPerLengthImpedance (this)

    /**
     * Temperature adjusted resistance.
     *
     * @param r           the given resistance (Ω)
     * @param temperature target temperature (°C)
     * @param base        current temperature for the given resistance (°C)
     * @return the temperature compensated resistance (Ω)
     */
    def resistanceAt (r: Double, temperature: Double = CIMBaseTemperature, base: Double =
    CIMBaseTemperature): Double = (1.0 + (Alpha * (temperature - base))) * r

    /**
     * Temperature adjusted per length impedance.
     *
     * @param temperature target temperature (°C)
     * @param base        current temperature for the given resistance (°C)
     * @return the temperature compensated per length positive and zero sequence impedance (Ω/m),
     *         and a flag indicating if this value is the default because no per length impedance was found
     */
    def perLengthImpedanceAt (temperature: Double = CIMBaseTemperature, base: Double = CIMBaseTemperature): Sequences =
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
    def impedanceAt (temperature: Double = CIMBaseTemperature, base: Double = CIMBaseTemperature): Sequences =
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
    lazy val log: Logger = LoggerFactory.getLogger (LineDetails.getClass)

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
     *
     * @example The default value corresponds to a medium sized cable (GKN 3x95se/95 1/0.6 kV), but this can easily
     *          be set programmatically to small (GKN 3x16rm/16 1/0.6 kV), or large (GKN 3x240se/240 1/0.6 kV) cables sizes,
     *          like so:
     *          LineDetails.DEFAULT_PER_LENGTH_IMPEDANCE = Sequences (LineDetails.DEFAULT_Z1_SMALL, LineDetails.DEFAULT_Z0_SMALL)
     *          LineDetails.DEFAULT_PER_LENGTH_IMPEDANCE = Sequences (LineDetails.DEFAULT_Z1_LARGE, LineDetails.DEFAULT_Z0_LARGE)
     *          One can also change it to a bespoke value like so:
     *          LineDetails.DEFAULT_PER_LENGTH_IMPEDANCE = Sequences (Complex (r1, x1), Complex (r0, x0))
     */
    var DEFAULT_PER_LENGTH_IMPEDANCE: Sequences = Sequences (DEFAULT_Z1_MEDIUM, DEFAULT_Z0_MEDIUM)

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
     * Index of x field in ACLineSegment bitmask.
     */
    lazy val x1Mask: Int = ACLineSegment.fields.indexOf ("x")

    /**
     * Index of r0 field in ACLineSegment bitmask.
     */
    lazy val r0Mask: Int = ACLineSegment.fields.indexOf ("r0")

    /**
     * Index of x0 field in ACLineSegment bitmask.
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
    var ALPHA: Double = 0.004

    /**
     * Emit a warning message if the default impedance is being used.
     *
     * @param message method returning the warning message
     */
    def maybe_warn (message: () => String): Unit = if (EMIT_WARNING_WHEN_DEFAULT) log.warn (message ())

    /**
     * Predicate to determine if the ACLineSegment has values for r, x, r0, or x0.
     *
     * @param details the line details to check
     * @return
     */
    def hasRX (details: LineDetails): Boolean =
    {
        /**
         * Determine if the bitfield is set for the given mask.
         *
         * @param mask single bit mask to check.
         * @return <code>true</code> if the bit is set, <code>false</code> otherwise.
         */
        def isSet (mask: Int): Boolean = 0 != (details.line.bitfields (mask / 32) & (1 << (mask % 32)))

        isSet (r1Mask) || isSet (x1Mask) || isSet (r0Mask) || isSet (x0Mask)
    }

    /**
     * Predicate to determine if the <code>perLengthImpedance</code> method would return default values.
     *
     * @return <code>true</code> if the <code>perLengthImpedance</code> uses a default value, <code>false</code> otherwise.
     */
    def checkIfPerLengthImpedanceIsDefault (details: LineDetails): Boolean =
    {
        details.per_length_impedance match
        {
            case Some (_: PerLengthSequenceImpedance) =>
                false
            case Some (_: PerLengthPhaseImpedance) =>
                true
            case Some (_: Element) =>
                true
            case None =>
                if (PROPERTIES_ARE_ERRONEOUSLY_PER_KM && hasRX (details))
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
    def getPerLengthImpedance (details: LineDetails): Sequences =
    {
        details.per_length_impedance match
        {
            case Some (seq: PerLengthSequenceImpedance) =>
                Sequences (Complex (seq.r, seq.x), Complex (seq.r0, seq.x0))
            case Some (phased: PerLengthPhaseImpedance) =>
                maybe_warn (() => s"ACLineSegment ${details.line.id} PerLengthPhaseImpedance ${phased.id} is not supported, using default impedance $DEFAULT_PER_LENGTH_IMPEDANCE Ω/m")
                DEFAULT_PER_LENGTH_IMPEDANCE
            case Some (element: Element) =>
                maybe_warn (() => s"ACLineSegment ${details.line.id} unrecognized PerLengthImpedance class ${element.id}, using default impedance $DEFAULT_PER_LENGTH_IMPEDANCE Ω/m")
                DEFAULT_PER_LENGTH_IMPEDANCE
            case None =>
                if (PROPERTIES_ARE_ERRONEOUSLY_PER_KM && hasRX (details))
                {
                    val z1 = Complex (details.line.r, details.line.x)
                    val z0 = Complex (details.line.r0, details.line.x0)
                    Sequences (z1 / 1000.0, z0 / 1000.0)
                }
                else
                {
                    maybe_warn (() => s"ACLineSegment ${details.line.id} using default impedance ${DEFAULT_PER_LENGTH_IMPEDANCE} Ω/m")
                    DEFAULT_PER_LENGTH_IMPEDANCE
                }
        }
    }

    /**
     * Defaults for physical constants included in the closure sent to executors.
     *
     * @param DefaultPerLengthImpedance            the supplied per meter impedance if the ACLineSegment doesn't have one
     * @param EmitWarningWhenDefault               the flag to show a warning message when a cable has no per length impedance
     *                                             (or it is invalid or an un-supported subclass)
     * @param PropertiesAreErroneouslyPerKilometer the flag indicating r and x properties are actually per kilometer values
     * @param CIMBaseTemperature                   the temperature of the per unit resistance values found in the CIM file
     * @param Alpha                                the temperature coefficient of resistance used to calculate the resistance at a temperature other than the above
     */
    case class StaticLineDetails (
        DefaultPerLengthImpedance: Sequences = DEFAULT_PER_LENGTH_IMPEDANCE,
        EmitWarningWhenDefault: Boolean = EMIT_WARNING_WHEN_DEFAULT,
        PropertiesAreErroneouslyPerKilometer: Boolean = PROPERTIES_ARE_ERRONEOUSLY_PER_KM,
        CIMBaseTemperature: Double = CIM_BASE_TEMPERATURE,
        Alpha: Double = ALPHA
    )

    def apply (
        line: ACLineSegment,
        terminal1: Terminal,
        terminal2: Terminal,
        per_length_impedance: Option[Element],
        wire_info: Option[Element])
        (implicit static_line_details: LineDetails.StaticLineDetails): LineDetails =
    {
        DEFAULT_PER_LENGTH_IMPEDANCE = static_line_details.DefaultPerLengthImpedance
        EMIT_WARNING_WHEN_DEFAULT = static_line_details.EmitWarningWhenDefault
        PROPERTIES_ARE_ERRONEOUSLY_PER_KM = static_line_details.PropertiesAreErroneouslyPerKilometer
        CIM_BASE_TEMPERATURE = static_line_details.CIMBaseTemperature
        ALPHA = static_line_details.Alpha
        LineDetails (line, terminal1, terminal2, per_length_impedance, wire_info, CIM_BASE_TEMPERATURE, ALPHA)
    }
}