package ch.ninecode.sc

import ch.ninecode.gl.Complex

/**
 * Short circuit calculation options.
 *
 * @param verbose                             flag to output progress and interesting values
 * @param description                         text describing this program execution
 * @param default_short_circuit_power_max     maximum available short circuit power (at transformer primary) to be used if no equivalent injection is found (VA)
 * @param default_short_circuit_impedance_max short circuit impedance to be used under maximum conditions if no equivalent injection is found (Ω)
 * @param default_short_circuit_angle_max     short circuit angle to be used under maximum conditions if no equivalent injection is found, overrides impedance if specified (°)
 * @param default_short_circuit_power_min     minimum available short circuit power (at transformer primary) to be used if no equivalent injection is found (VA)
 * @param default_short_circuit_impedance_min short circuit impedance to be used under minimum conditions if no equivalent injection is found (Ω)
 * @param default_short_circuit_angle_min     short circuit angle to be used under minimum conditions if no equivalent injection is found, overrides impedance if specified (°)
 * @param default_transformer_power_rating    default transformer maximum power rating to be applied if a transformer has no ratedS specified (VA)
 * @param default_transformer_impedance       characteristic impedance to be applied if a transformer has no r or x specified (Ω)
 * @param base_temperature                    temperature of elements in the input CIM file (°C)
 * @param low_temperature                     low temperature for lowest resistance (maximum fault level) calculations (used for rating equipment) (°C)
 * @param high_temperature                    high temperature for highest resistance (minimum fault level) calculations (used for protections settings) (°C)
 * @param cmax                                voltage factor for maximum fault level (used for rating equipment), IEC60909 specifies 1.05 for voltages < 1kV, 1.1 for voltages > 1kV (dimensionless)
 * @param cmin                                voltage factor for minimum fault level (used for protections settings), IEC60909 specifies 0.95 for voltages < 1kV, 1.0 for voltages > 1kV (dimensionless)
 * @param worstcasepf                         assume worst case motor power factor (cos term = 1.0, ignore cosphi)
 * @param cosphi                              power factor of (motor) load e.g. cos (60), the cosine of the motor starting current-voltage phase angle, typical values range from 0.2 (φ = 78°) to 0.6 (φ = 53°) during startup (dimensionless)
 * @param fuse_table                          recommended fuse sizing table choice, 1,2 or 3; table 1 ranges from 65A⇒25 to 2400A⇒630, table 2 ranges from 28A⇒10 to 2500A⇒630, while table 3 is based on DIN (as in table 1) or SEV which ranges from 200A⇒60 to 1150A⇒400
 * @param messagemax                          mximum number of warning and error messages to keep for each node
 * @param batchsize                           size of result collections for driver database writes
 * @param trafos                              file name of transformer names to process
 * @param cable_impedance_limit               cables with a R1 value higher than this are not calculated with gridlab, the reason is bad performance in gridlab with too high impedance values
 * @param workdir                             shared directory (HDFS or NFS share) for intermediate results
 * @param calculate_public_lighting           whether to consider public lighting in the shortcircuit calculations
 */
case class ShortCircuitOptions
(
    verbose: Boolean = true,
    description: String = "",
    default_short_circuit_power_max: Double = 200.0e6,
    default_short_circuit_impedance_max: Complex = Complex (0.437785783, -1.202806555),
    default_short_circuit_angle_max: Double = Double.NaN,
    default_short_circuit_power_min: Double = 100.0e6,
    default_short_circuit_impedance_min: Complex = Complex (0.875571570, -2.405613110),
    default_short_circuit_angle_min: Double = Double.NaN,
    default_transformer_power_rating: Double = 630000,
    default_transformer_impedance: Complex = Complex (0.005899999998374999, 0.039562482211875),
    base_temperature: Double = 20.0,
    low_temperature: Double = 60.0,
    high_temperature: Double = 90.0,
    cmax: Double = 1.0,
    cmin: Double = 0.90,
    worstcasepf: Boolean = true,
    cosphi: Double = 0.5,
    fuse_table: Int = 1,
    messagemax: Int = 5,
    batchsize: Long = 10000,
    trafos: String = "",
    cable_impedance_limit: Double = 5.0,
    workdir: String = "",
    calculate_public_lighting: Boolean = false
)
