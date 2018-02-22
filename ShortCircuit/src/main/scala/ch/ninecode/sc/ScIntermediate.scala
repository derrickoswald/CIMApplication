package ch.ninecode.sc

/**
 * Short circuit intermediate results.
 *
 * @param ik one phase short bolted circuit current (A)
 * @param ik3pol three phase bolted short circuit current (A)
 * @param ip maximum aperiodic short-circuit current according to IEC 60909-0 (A)
 * @param sk short-circuit power at the point of common coupling (VA)
 * @param motor_3ph_max_low maximum (continuous) motor power (3 phase) for pf=cos(60), inrush=5x, repetition_rate<0.01/min (W)
 * @param motor_1ph_max_low maximum (continuous) motor power (1 phase, line to neutral) for pf=cos(60), inrush=5x, repetition_rate<0.01/min (W)
 * @param motor_l_l_max_low maximum (continuous) motor power (1 phase, line to line) for pf=cos(60), inrush=5x, repetition_rate<0.01/min (W)
 * @param motor_3ph_max_med maximum (continuous) motor power (3 phase) for pf=cos(60), inrush=5x, 0.01 ≤ repetition_rate < 0.1 /min (W)
 * @param motor_1ph_max_med maximum (continuous) motor power (1 phase, line to neutral) for pf=cos(60), inrush=5x, 0.01 ≤ repetition_rate < 0.1 /min (W)
 * @param motor_l_l_max_med maximum (continuous) motor power (1 phase, line to line) for pf=cos(60), inrush=5x, 0.01 ≤ repetition_rate < 0.1 /min (W)
 */
case class ScIntermediate (
    ik: Double = 0.0,
    ik3pol: Double = 0.0,
    ip: Double = 0.0,
    sk: Double = 0.0,
    motor_3ph_max_low: Double = 0.0,
    motor_1ph_max_low: Double = 0.0,
    motor_l_l_max_low: Double = 0.0,
    motor_3ph_max_med: Double = 0.0,
    motor_1ph_max_med: Double = 0.0,
    motor_l_l_max_med: Double = 0.0)
