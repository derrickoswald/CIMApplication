package ch.ninecode.sc

/**
 * Short circuit results.
 *
 * @param node TopologicalNode mRID
 * @param equipment conducting equipment mRID
 * @param terminal number for equipment
 * @param tx the feeding transformer
 * @param prev the previous node
 * @param r aggregate positive sequence resistance from the source (primary of feeding transformer) to this node (Ω)
 * @param x aggregate positive sequence reactance from the source (primary of feeding transformer) to this node (Ω)
 * @param r0 aggregate zero sequence resistance from the source (primary of feeding transformer) to this node (Ω)
 * @param x0 aggregate zero sequence reactance from the source (primary of feeding transformer) to this node (Ω)
 * @param fuses list of fuse values from the source (primary of feeding transformer) to this node
 * @param errors errors encountered in processing
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
case class ScResult (
    node: String,
    equipment: String,
    terminal: Int,
    tx: String,
    prev: String,
    r: Double,
    x: Double,
    r0: Double,
    x0: Double,
    fuses: List[Double],
    errors: List[String],
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
{
    def csv: String =
        equipment + ";" + terminal + ";" + tx + ";" + ik + ";" + ik3pol + ";" + ip + ";" + r + ";" + r0 + ";" + x + ";" + x0 + ";" + sk + ";" +
        (if (null == fuses) "" else fuses.mkString (",")) + ";" + FData.fuse (ik) + ";" + FData.fuseOK (ik, fuses) + ";" +
        (if (null != errors) errors.mkString (",") else "") + ";" +
        motor_3ph_max_low + ";" + motor_1ph_max_low + ";" + motor_l_l_max_low + ";" + motor_3ph_max_med + ";" + motor_1ph_max_med + ";" + motor_l_l_max_med
}

object ScResult
{
    val csv_header: String = "equipment;terminal;transformer;ik;ik3pol;ip;r;x;r0;x0;sk;fuses;fusemax;fuseOK;errors;motor3phmax_low;motor1phmax_low;motorllmax_low;motor3phmax_med;motor1phmax_med;motorllmax_med"
}
