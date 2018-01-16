package ch.ninecode.sc

import scala.math._

object MaximumStartingCurrent
{
    /**
     * Maximum voltage change for events with a repetition rate: r < 0.01 /min in the low voltage network
     */
    val dmax_low_rep = 0.06

    /**
     * Maximum voltage change for events with a repetition rate: 0.01 ≤ r < 0.1 /min in the low voltage network
     */
    val dmax_medium_rep = 0.03

    /**
     * Calculate the maximum symetrical 3 phase motor power at the point of common coupling.
     *
     * @param network_short_circuit_power network short-circuit power at the point of common coupling (VA)
     * @param network_impedance network resistance and reactance at the point of common coupling (Ω)
     * @param motor_power_factor nte cosine of the motor starting current-voltage phase angle (dimensionless)
     * @param motor_starting_current_ratio ratio between the motor starting current and rated current (dimensionless)
     * @param dmax use <code>dmax_medium_rep</code> for a repetition rate: 0.01 ≤ r < 0.1 /min or <code>dmax_low_rep</code> for a repetition rate: r < 0.01 /min
     * @return the maximum motor power rating that falls within the DACHCZ limit for network voltage change,
     *         this can be converted to maximum current by dividing by root 3 times the nominal voltage
     */
    def max_power_3_phase_motor (
        network_short_circuit_power: Double,
        network_impedance: Complex,
        motor_power_factor: Double = cos (30),
        motor_starting_current_ratio: Double = 5.0,
        dmax: Double = dmax_low_rep
        ): Double =
    {
        val phin = network_impedance.angle
        val phim = acos (motor_power_factor)
        val pmax = Math.abs (dmax * network_short_circuit_power / cos (phin - phim))
        pmax / motor_starting_current_ratio
    }
}
