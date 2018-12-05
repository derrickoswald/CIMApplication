package ch.ninecode.sc

/**
 * Short circuit intermediate results.
 *
 * @param ik           one phase short bolted short circuit current (A)
 * @param ik3pol       three phase bolted short circuit current (A)
 * @param ip           maximum aperiodic short-circuit current according to IEC 60909-0 (A)
 * @param sk           short-circuit power at the point of common coupling (VA)
 * @param imax_3ph_low maximum inrush current (3 phase) for repetition_rate<0.01/min (A)
 * @param imax_1ph_low maximum inrush current (1 phase, line to neutral) for repetition_rate<0.01/min (A)
 * @param imax_2ph_low maximum inrush current (1 phase, line to line) for repetition_rate<0.01/min (A)
 * @param imax_3ph_med maximum inrush current (3 phase) for 0.01 ≤ repetition_rate < 0.1 /min (A)
 * @param imax_1ph_med maximum inrush current (1 phase, line to neutral) for 0.01 ≤ repetition_rate < 0.1 /min (A)
 * @param imax_2ph_med maximum inrush current (1 phase, line to line) for 0.01 ≤ repetition_rate < 0.1 /min (A)
 */
case class ScIntermediate
(
    ik: Double = 0.0,
    ik3pol: Double = 0.0,
    ip: Double = 0.0,
    sk: Double = 0.0,
    imax_3ph_low: Double = 0.0,
    imax_1ph_low: Double = 0.0,
    imax_2ph_low: Double = 0.0,
    imax_3ph_med: Double = 0.0,
    imax_1ph_med: Double = 0.0,
    imax_2ph_med: Double = 0.0)
