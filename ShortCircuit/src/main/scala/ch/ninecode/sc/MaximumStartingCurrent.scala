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
     * @param motor_starting_current_ratio ratio between the motor starting current and rated current (dimensionless)
     *                                     typical values range from 3.0 to 8.0
     * @param dmax use <code>dmax_medium_rep</code> for a repetition rate: 0.01 ≤ r < 0.1 /min or <code>dmax_low_rep</code> for a repetition rate: r < 0.01 /min
     * @return the maximum motor power rating that falls within the DACHCZ limit for short-term network voltage change,
     *         this can be converted to maximum current by dividing by root 3 times the nominal voltage
     */
    def max_power_3_phase_motor (
        network_short_circuit_power: Double,
        network_impedance: Complex,
        motor_power_factor: Double = cos (60),
        motor_starting_current_ratio: Double = 5.0,
        dmax: Double = dmax_low_rep
        ): Double =
    {
        val phin = network_impedance.angle
        val phim = acos (motor_power_factor)
        val pmax = Math.abs (dmax * network_short_circuit_power / cos (phin - phim))
        pmax / motor_starting_current_ratio
    }

    /**
     * Calculate the maximum single phase motor power connected line-to-neutral at the point of common coupling.
     * For example, a 230V motor connected between L1 and neutral.
     *
     * @param network_short_circuit_power network short-circuit power at the point of common coupling (VA)
     * @param network_impedance network resistance and reactance at the point of common coupling (Ω)
     * @param motor_power_factor the cosine of the motor starting current-voltage phase angle (dimensionless)
     *                           typical values range from 0.2 (φ = 78°) to 0.6 (φ = 53°) during startup
     * @param motor_starting_current_ratio ratio between the motor starting current and rated current (dimensionless)
     *                                     typical values range from 3.0 to 8.0
     * @param dmax use <code>dmax_medium_rep</code> for a repetition rate: 0.01 ≤ r < 0.1 /min or <code>dmax_low_rep</code> for a repetition rate: r < 0.01 /min
     * @return the maximum motor power rating that falls within the DACHCZ limit for short-term network voltage change,
     *         this can be converted to maximum current by dividing by the nominal line to neutral voltage
     */
    def max_power_1_phase_line_to_neutral_motor (
        network_short_circuit_power: Double,
        network_impedance: Complex,
        motor_power_factor: Double = cos (60),
        motor_starting_current_ratio: Double = 5.0,
        dmax: Double = dmax_low_rep
        ): Double = max_power_3_phase_motor (network_short_circuit_power, network_impedance, motor_power_factor, motor_starting_current_ratio, dmax) / 6.0

    /**
     * Calculate the maximum single phase motor power connected line-to-line at the point of common coupling.
     * For example, a 400V motor connected between L1 and L2.
     *
     * @param network_short_circuit_power network short-circuit power at the point of common coupling (VA)
     * @param network_impedance network resistance and reactance at the point of common coupling (Ω)
     * @param motor_power_factor the cosine of the motor starting current-voltage phase angle (dimensionless)
     *                           typical values range from 0.2 (φ = 78°) to 0.6 (φ = 53°) during startup
     * @param motor_starting_current_ratio ratio between the motor starting current and rated current (dimensionless)
     *                                     typical values range from 3.0 to 8.0
     * @param dmax use <code>dmax_medium_rep</code> for a repetition rate: 0.01 ≤ r < 0.1 /min or <code>dmax_low_rep</code> for a repetition rate: r < 0.01 /min
     * @return the maximum motor power rating that falls within the DACHCZ limit for short-term network voltage change,
     *         this can be converted to maximum current by dividing by the nominal line to line voltage
     */
    def max_power_1_phase_line_to_line_motor (
        network_short_circuit_power: Double,
        network_impedance: Complex,
        motor_power_factor: Double = cos (60),
        motor_starting_current_ratio: Double = 5.0,
        dmax: Double = dmax_low_rep
        ): Double =
    {
        val root3 = sqrt (3.0)
        val thirty = Pi / 6.0
        val sixty = Pi / 3.0
        val phin = network_impedance.angle
        val phim = acos (motor_power_factor)
        val temp = dmax / root3 * network_short_circuit_power
        val pmax_line_neutral = min (
            abs (temp / cos (phin - (phim - thirty))),
            abs (temp / cos (phin - (phim + thirty))))
        val pmax_line_line = min (
            dmax / 2.0 * network_short_circuit_power / cos (phin - phim),
            min (
                dmax * network_short_circuit_power / cos (phin - (phim + sixty)),
                dmax * network_short_circuit_power / cos (phin - (phim - sixty)))
        )
        val pmax = abs (min (pmax_line_neutral, pmax_line_line))
        pmax / motor_starting_current_ratio
    }
}
