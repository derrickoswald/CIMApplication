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
     * @return the maximum motor power rating that falls within the DACHCZ limit for short-term network voltage change,
     *         for both a repetition rate: r < 0.01 /min and 0.01 ≤ r < 0.1 /min in that order
     *         this can be converted to maximum current by dividing by root 3 times the nominal voltage
     */
    def max_power_3_phase_motor (
        network_short_circuit_power: Double,
        network_impedance: Complex,
        motor_power_factor: Double = cos (60),
        motor_starting_current_ratio: Double = 5.0
        ): (Double, Double) =
    {
        val phin = network_impedance.angle
        val phim = acos (motor_power_factor)
        val pmax = Math.abs (network_short_circuit_power / cos (phin - phim)) / motor_starting_current_ratio
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
     * @param motor_starting_current_ratio ratio between the motor starting current and rated current (dimensionless)
     *                                     typical values range from 3.0 to 8.0
     * @return the maximum motor power rating that falls within the DACHCZ limit for short-term network voltage change,
     *         for both a repetition rate: r < 0.01 /min and 0.01 ≤ r < 0.1 /min in that order
     *         this can be converted to maximum current by dividing by the nominal line to neutral voltage
     */
    def max_power_1_phase_line_to_neutral_motor (
        network_short_circuit_power: Double,
        network_impedance: Complex,
        motor_power_factor: Double = cos (60),
        motor_starting_current_ratio: Double = 5.0
        ): (Double, Double) =
    {
        val dd = max_power_3_phase_motor (network_short_circuit_power, network_impedance, motor_power_factor, motor_starting_current_ratio)
        (dd._1 / 6.0, dd._2 / 6.0)
    }

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
     * @return the maximum motor power rating that falls within the DACHCZ limit for short-term network voltage change,
     *         for both a repetition rate: r < 0.01 /min and 0.01 ≤ r < 0.1 /min in that order
     *         this can be converted to maximum current by dividing by the nominal line to line voltage
     */
    def max_power_1_phase_line_to_line_motor (
        network_short_circuit_power: Double,
        network_impedance: Complex,
        motor_power_factor: Double = cos (60),
        motor_starting_current_ratio: Double = 5.0
        ): (Double, Double) =
    {
        val root3 = sqrt (3.0)
        val thirty = Pi / 6.0
        val sixty = Pi / 3.0
        val phin = network_impedance.angle
        val phim = acos (motor_power_factor)

        val temp_low = dmax_low_rep / root3 * network_short_circuit_power
        val pmax_line_neutral_low = min (
            abs (temp_low / cos (phin - (phim - thirty))),
            abs (temp_low / cos (phin - (phim + thirty))))
        val pmax_line_line_low = min (
            dmax_low_rep / 2.0 * network_short_circuit_power / cos (phin - phim),
            min (
                dmax_low_rep * network_short_circuit_power / cos (phin - (phim + sixty)),
                dmax_low_rep * network_short_circuit_power / cos (phin - (phim - sixty)))
        )
        val pmax_low = abs (min (pmax_line_neutral_low, pmax_line_line_low))

        val temp_med = dmax_medium_rep / root3 * network_short_circuit_power
        val pmax_line_neutral_med = min (
            abs (temp_med / cos (phin - (phim - thirty))),
            abs (temp_med / cos (phin - (phim + thirty))))
        val pmax_line_line_med = min (
            dmax_medium_rep / 2.0 * network_short_circuit_power / cos (phin - phim),
            min (
                dmax_medium_rep * network_short_circuit_power / cos (phin - (phim + sixty)),
                dmax_medium_rep * network_short_circuit_power / cos (phin - (phim - sixty)))
        )
        val pmax_med = abs (min (pmax_line_neutral_med, pmax_line_line_med))
        (pmax_low / motor_starting_current_ratio, pmax_med / motor_starting_current_ratio)
    }
}
