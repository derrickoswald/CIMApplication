package ch.ninecode.sc

/**
 * Short circuit calculation options.
 *
 * @param verbose flag to output progress and interesting values
 * @param default_supply_network_short_circuit_power available short circuit power to be used if no equivalent injection is found (VA)
 * @param default_supply_network_short_circuit_angle short circuit power angle to be used if no corresponding substation is found (degrees)
 * @param default_transformer_impedance characteristic impedance to be applied if a transformer has no r or x specified (Ω)
 * @param cmax voltage factor for maximum fault level (used for rating equipment), IEC60909 specifies 1.05 for voltages < 1kV, 1.1 for voltages > 1kV (dimensionless)
 * @param cmin voltage factor for minimum fault level (used for protections settings), IEC60909 specifies 0.95 for voltages < 1kV, 1.0 for voltages > 1kV (dimensionless)
 * @param cosphi power factor of (motor) load
 * @param starting_ratio surge current ratio of (motor) starting load
 * @param trafos file name of transformer names to process
 * @param workdir shared directory (HDFS or NFS share) for intermediate results
 */
case class ShortCircuitOptions (
    verbose: Boolean = true,
    default_supply_network_short_circuit_power: Double = 200.0e6,
    default_supply_network_short_circuit_angle: Double = -70.0,
    default_transformer_power_rating: Double = 630000,
    default_transformer_impedance: Complex = Complex (0.005899999998374999, 0.039562482211875),
    cmax: Double = 1.0,
    cmin: Double = 0.90,
    cosphi: Double = 0.5,
    starting_ratio: Double = 1.0,
    trafos: String = "",
    workdir: String = "")
