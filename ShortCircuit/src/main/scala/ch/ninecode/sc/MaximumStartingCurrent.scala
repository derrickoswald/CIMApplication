package ch.ninecode.sc

import scala.math.abs
import scala.math.acos
import scala.math.cos
import scala.math.min
import scala.math.sqrt
import scala.math.Pi

import ch.ninecode.util.Complex

object MaximumStartingCurrent
{
    /**
     * Maximum short-term voltage change for events with a repetition rate: r < 0.01 /min in the low voltage network
     */
    val dmax_low_rep = 0.06

    /**
     * Maximum short-term voltage change for events with a repetition rate: 0.01 ≤ r < 0.1 /min in the low voltage network
     */
    val dmax_medium_rep = 0.03

    /**
     * Compute the cos term for the maximum current calculation.
     *
     * @param network_impedance network resistance and reactance at the point of common coupling (Ω)
     * @param options           providing power factor values for calculation
     * @return The angle between the device inrush current and the network impedance, or worst case 1.0 if the options choose that.
     */
    def costerm (network_impedance: Complex, options: ShortCircuitOptions): Double =
    {
        if (options.worstcasepf)
            1.0
        else
        {
            val phin = network_impedance.angle
            val phim = acos (options.cosphi)
            cos (phin - phim)
        }
    }

    /**
     * Calculate the maximum symmetrical 3 phase motor current at the point of common coupling.
     *
     * @param network_short_circuit_power network short-circuit power at the point of common coupling (VA)
     * @param network_impedance           network resistance and reactance at the point of common coupling (Ω)
     * @param voltage                     network voltage at the point of common couplig (V)
     * @param options                     providing power factor values for calculation
     * @return the maximum motor inrush current that falls within the DACHCZ limit for short-term network voltage change,
     *         for both a repetition rate: r < 0.01 /min and 0.01 ≤ r < 0.1 /min in that order
     */
    def max_current_3_phase (
        network_short_circuit_power: Double,
        network_impedance: Complex,
        voltage: Double,
        options: ShortCircuitOptions
    ): (Double, Double) =
    {
        val root3 = sqrt (3.0)
        val pmax = Math.abs (network_short_circuit_power / (root3 * costerm (network_impedance, options)))
        (dmax_low_rep * pmax / voltage, dmax_medium_rep * pmax / voltage)
    }

    /**
     * Calculate the maximum single phase motor current connected line-to-neutral at the point of common coupling.
     * For example, a 230V motor connected between L1 and neutral.
     *
     * @param network_short_circuit_power network short-circuit power at the point of common coupling (VA)
     * @param network_impedance           network resistance and reactance at the point of common coupling (Ω)
     * @param voltage                     network voltage at the point of common couplig (V)
     * @param options                     providing power factor values for calculation
     * @return the maximum motor inrush current that falls within the DACHCZ limit for short-term network voltage change,
     *         for both a repetition rate: r < 0.01 /min and 0.01 ≤ r < 0.1 /min in that order
     */
    def max_current_1_phase (
        network_short_circuit_power: Double,
        network_impedance: Complex,
        voltage: Double,
        options: ShortCircuitOptions
    ): (Double, Double) =
    {
        val root3 = sqrt (3.0)
        val pmax = Math.abs (network_short_circuit_power * root3 / (6.0 * costerm (network_impedance, options)))
        (dmax_low_rep * pmax / voltage, dmax_medium_rep * pmax / voltage)
    }

    /**
     * Calculate the maximum single phase motor current connected line-to-line at the point of common coupling.
     * For example, a 400V motor connected between L1 and L2.
     *
     * @param network_short_circuit_power network short-circuit power at the point of common coupling (VA)
     * @param network_impedance           network resistance and reactance at the point of common coupling (Ω)
     * @param voltage                     network voltage at the point of common couplig (V)
     * @param options                     providing power factor values for calculation
     * @return the maximum motor inrush current that falls within the DACHCZ limit for short-term network voltage change,
     *         for both a repetition rate: r < 0.01 /min and 0.01 ≤ r < 0.1 /min in that order
     */
    def max_current_2_phase (
        network_short_circuit_power: Double,
        network_impedance: Complex,
        voltage: Double,
        options: ShortCircuitOptions
    ): (Double, Double) =
    {
        val root3 = sqrt (3.0)
        val thirty = Pi / 6.0
        val sixty = Pi / 3.0
        val phin = network_impedance.angle
        val phim = if (options.worstcasepf) phin else acos (options.cosphi)

        val temp = 1.0 / root3 * network_short_circuit_power
        val pmax_line_neutral = min (
            abs (temp / cos (phin - (phim - thirty))), // dL1−N
            abs (temp / cos (phin - (phim + thirty)))) // dL2−N
        val pmax_line_line = min (
            abs (1.0 / 2.0 * network_short_circuit_power / cos (phin - phim)), // dL1−L2
            min (
                abs (network_short_circuit_power / cos (phin - (phim + sixty))), // dL2−L3
                abs (network_short_circuit_power / cos (phin - (phim - sixty)))) // dL3−L1
        )
        val pmax = min (pmax_line_neutral, pmax_line_line)

        (dmax_low_rep * pmax / voltage, dmax_medium_rep * pmax / voltage)
    }
}
