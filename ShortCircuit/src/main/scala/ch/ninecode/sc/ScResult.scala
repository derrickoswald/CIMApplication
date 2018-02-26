package ch.ninecode.sc

/**
 * Short circuit results.
 *
 * @param node TopologicalNode mRID
 * @param equipment conducting equipment mRID
 * @param terminal number for equipment
 * @param tx the feeding transformer
 * @param prev the previous node
 * @param low_r aggregate positive sequence resistance from the source (primary of feeding transformer) to this node (Ω)
 * @param low_x aggregate positive sequence reactance from the source (primary of feeding transformer) to this node (Ω)
 * @param low_r0 aggregate zero sequence resistance from the source (primary of feeding transformer) to this node (Ω)
 * @param low_x0 aggregate zero sequence reactance from the source (primary of feeding transformer) to this node (Ω)
 * @param high_r aggregate positive sequence resistance from the source (primary of feeding transformer) to this node (Ω)
 * @param high_x aggregate positive sequence reactance from the source (primary of feeding transformer) to this node (Ω)
 * @param high_r0 aggregate zero sequence resistance from the source (primary of feeding transformer) to this node (Ω)
 * @param high_x0 aggregate zero sequence reactance from the source (primary of feeding transformer) to this node (Ω)
 * @param fuses list of fuse values from the source (primary of feeding transformer) to this node
 * @param errors errors encountered in processing
 * @param low_ik one phase short bolted short circuit current (A)
 * @param low_ik3pol three phase bolted short circuit current (A)
 * @param low_ip maximum aperiodic short-circuit current according to IEC 60909-0 (A)
 * @param low_sk short-circuit power at the point of common coupling (VA)
 * @param low_motor_3ph_max_low maximum motor power (3 phase) for repetition_rate<0.01/min (W)
 * @param low_motor_1ph_max_low maximum motor power (1 phase, line to neutral) for repetition_rate<0.01/min (W)
 * @param low_motor_l_l_max_low maximum motor power (1 phase, line to line) for repetition_rate<0.01/min (W)
 * @param low_motor_3ph_max_med maximum motor power (3 phase) for 0.01 ≤ repetition_rate < 0.1 /min (W)
 * @param low_motor_1ph_max_med maximum motor power (1 phase, line to neutral) for 0.01 ≤ repetition_rate < 0.1 /min (W)
 * @param low_motor_l_l_max_med maximum motor power (1 phase, line to line) for 0.01 ≤ repetition_rate < 0.1 /min (W)
 * @param high_ik one phase short bolted short circuit current (A)
 * @param high_ik3pol three phase bolted short circuit current (A)
 * @param high_ip maximum aperiodic short-circuit current according to IEC 60909-0 (A)
 * @param high_sk short-circuit power at the point of common coupling (VA)
 * @param high_motor_3ph_max_low maximum motor power (3 phase) for repetition_rate<0.01/min (W)
 * @param high_motor_1ph_max_low maximum motor power (1 phase, line to neutral) for repetition_rate<0.01/min (W)
 * @param high_motor_l_l_max_low maximum motor power (1 phase, line to line) for repetition_rate<0.01/min (W)
 * @param high_motor_3ph_max_med maximum motor power (3 phase) for 0.01 ≤ repetition_rate < 0.1 /min (W)
 * @param high_motor_1ph_max_med maximum motor power (1 phase, line to neutral) for 0.01 ≤ repetition_rate < 0.1 /min (W)
 * @param high_motor_l_l_max_med maximum motor power (1 phase, line to line) for 0.01 ≤ repetition_rate < 0.1 /min (W)
 */
case class ScResult (
    node: String,
    equipment: String,
    terminal: Int,
    tx: String,
    prev: String,
    low_r: Double,
    low_x: Double,
    low_r0: Double,
    low_x0: Double,
    high_r: Double,
    high_x: Double,
    high_r0: Double,
    high_x0: Double,
    fuses: List[Double],
    errors: List[String],
    low_ik: Double = 0.0,
    low_ik3pol: Double = 0.0,
    low_ip: Double = 0.0,
    low_sk: Double = 0.0,
    low_motor_3ph_max_low: Double = 0.0,
    low_motor_1ph_max_low: Double = 0.0,
    low_motor_l_l_max_low: Double = 0.0,
    low_motor_3ph_max_med: Double = 0.0,
    low_motor_1ph_max_med: Double = 0.0,
    low_motor_l_l_max_med: Double = 0.0,
    high_ik: Double = 0.0,
    high_ik3pol: Double = 0.0,
    high_ip: Double = 0.0,
    high_sk: Double = 0.0,
    high_motor_3ph_max_low: Double = 0.0,
    high_motor_1ph_max_low: Double = 0.0,
    high_motor_l_l_max_low: Double = 0.0,
    high_motor_3ph_max_med: Double = 0.0,
    high_motor_1ph_max_med: Double = 0.0,
    high_motor_l_l_max_med: Double = 0.0
)
{
    def csv: String =
        equipment + ";" + terminal + ";" + node + ";" + tx + ";" + (if (null != errors) errors.mkString (",") else "") + ";" +
        low_ik + ";" + low_ik3pol + ";" + low_ip + ";" + low_r + ";" + low_x + ";" + low_r0 + ";" + low_x0 + ";" + low_sk + ";" +
        low_motor_3ph_max_low + ";" + low_motor_1ph_max_low + ";" + low_motor_l_l_max_low + ";" + low_motor_3ph_max_med + ";" + low_motor_1ph_max_med + ";" + low_motor_l_l_max_med + ";" +
        (if (null == fuses) "" else fuses.mkString (",")) + ";" + FData.fuse (high_ik) + ";" + FData.fuseOK (high_ik, fuses) + ";" +
        high_ik + ";" + high_ik3pol + ";" + high_ip + ";" + high_r + ";" + high_x + ";" + high_r0 + ";" + high_x0 + ";" + high_sk
}

object ScResult
{
    val csv_header: String = "equipment;terminal;node;transformer;errors;ik;ik3pol;ip;r;x;r0;x0;sk;motor3phmax_low;motor1phmax_low;motorllmax_low;motor3phmax_med;motor1phmax_med;motorllmax_med;fuses;fusemax;fuseOK;ik;ik3pol;ip;r;x;r0;x0;sk"
}
