package ch.ninecode.sc

import scala.math._

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
     * Calculate the maximum symmetrical 3 phase motor power at the point of common coupling.
     *
     * @param network_short_circuit_power network short-circuit power at the point of common coupling (VA)
     * @param network_impedance network resistance and reactance at the point of common coupling (Ω)
     * @param motor_power_factor the cosine of the motor starting current-voltage phase angle (dimensionless)
     *                           typical values range from 0.2 (φ = 78°) to 0.6 (φ = 53°) during startup
     * @return the maximum motor power rating that falls within the DACHCZ limit for short-term network voltage change,
     *         for both a repetition rate: r < 0.01 /min and 0.01 ≤ r < 0.1 /min in that order
     *         this can be converted to maximum current by dividing by root 3 times the nominal voltage
     */
    def max_power_3_phase_motor (
        network_short_circuit_power: Double,
        network_impedance: Complex,
        motor_power_factor: Double = 1.0 // e.g. cos (60)
        ): (Double, Double) =
    {
        val root3 = sqrt (3.0)
        val phin = network_impedance.angle
        val phim = acos (motor_power_factor)
        val pmax = Math.abs (network_short_circuit_power / (root3 * cos (phin - phim)))
        (dmax_low_rep * pmax, dmax_medium_rep * pmax)
    }

    /**
     * Calculate the maximum single phase motor power connected line-to-neutral at the point of common coupling.
     * For example, a 230V motor connected between L1 and neutral.
     *
     * @param network_short_circuit_power network short-circuit power at the point of common coupling (VA)
     * @param network_impedance network resistance and reactance at the point of common coupling (Ω)
     * @param motor_power_factor the cosine of the motor starting current-voltage phase angle (dimensionless)
     *                           typical values range from 0.2 (φ = 78°) to 0.6 (φ = 53°) during startup

     * @return the maximum motor power rating that falls within the DACHCZ limit for short-term network voltage change,
     *         for both a repetition rate: r < 0.01 /min and 0.01 ≤ r < 0.1 /min in that order
     *         this can be converted to maximum current by dividing by the nominal line to neutral voltage
     */
    def max_power_1_phase_line_to_neutral_motor (
        network_short_circuit_power: Double,
        network_impedance: Complex,
        motor_power_factor: Double = 1.0 // e.g. cos (60)
        ): (Double, Double) =
    {
        val root3 = sqrt (3.0)
        val phin = network_impedance.angle
        val phim = acos (motor_power_factor)
        val pmax = Math.abs (network_short_circuit_power * root3 / (6.0 * cos (phin - phim)))
        (dmax_low_rep * pmax, dmax_medium_rep * pmax)
    }

    /**
     * Calculate the maximum single phase motor power connected line-to-line at the point of common coupling.
     * For example, a 400V motor connected between L1 and L2.
     *
     * @param network_short_circuit_power network short-circuit power at the point of common coupling (VA)
     * @param network_impedance network resistance and reactance at the point of common coupling (Ω)
     * @param motor_power_factor the cosine of the motor starting current-voltage phase angle (dimensionless)
     *                           typical values range from 0.2 (φ = 78°) to 0.6 (φ = 53°) during startup
     * @return the maximum motor power rating that falls within the DACHCZ limit for short-term network voltage change,
     *         for both a repetition rate: r < 0.01 /min and 0.01 ≤ r < 0.1 /min in that order
     *         this can be converted to maximum current by dividing by the nominal line to line voltage
     */
    def max_power_1_phase_line_to_line_motor (
        network_short_circuit_power: Double,
        network_impedance: Complex,
        motor_power_factor: Double = 1.0 // e.g. cos (60)
        ): (Double, Double) =
    {
        val root3 = sqrt (3.0)
        val thirty = Pi / 6.0
        val sixty = Pi / 3.0
        val phin = network_impedance.angle
        val phim = acos (motor_power_factor)

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

        (dmax_low_rep * pmax, dmax_medium_rep * pmax)
    }
}
