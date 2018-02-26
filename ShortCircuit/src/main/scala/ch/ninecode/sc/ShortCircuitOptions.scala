package ch.ninecode.sc

/**
 * Short circuit calculation options.
 *
 * @param verbose flag to output progress and interesting values
 * @param default_supply_network_short_circuit_power available short circuit power (at transformer primary) to be used if no equivalent injection is found (VA)
 * @param default_supply_network_short_circuit_impedance short circuit impedance to be used if no equivalent injection is found (Ω)
 * @param default_transformer_power_rating default transformer maximum power rating to be applied if a transformer has no ratedS specified (VA)
 * @param default_transformer_impedance characteristic impedance to be applied if a transformer has no r or x specified (Ω)
 * @param base_temperature temperature of elements in the input CIM file (°C)
 * @param low_temperature low temperature for lowest resistance (maximum fault level) calculations (used for rating equipment) (°C)
 * @param high_temperature high temperature for highest resistance (minimum fault level) calculations (used for protections settings) (°C)
 * @param cmax voltage factor for maximum fault level (used for rating equipment), IEC60909 specifies 1.05 for voltages < 1kV, 1.1 for voltages > 1kV (dimensionless)
 * @param cmin voltage factor for minimum fault level (used for protections settings), IEC60909 specifies 0.95 for voltages < 1kV, 1.0 for voltages > 1kV (dimensionless)
 * @param cosphi power factor of (motor) load (dimensionless)
 * @param trafos file name of transformer names to process
 * @param workdir shared directory (HDFS or NFS share) for intermediate results
 */
case class ShortCircuitOptions (
    verbose: Boolean = true,
    default_supply_network_short_circuit_power: Double = 200.0e6,
    default_supply_network_short_circuit_impedance: Complex = Complex (0.437785783, -1.202806555),
    default_transformer_power_rating: Double = 630000,
    default_transformer_impedance: Complex = Complex (0.005899999998374999, 0.039562482211875),
    base_temperature: Double = 20.0,
    low_temperature: Double = 60.0,
    high_temperature: Double = 90.0,
    cmax: Double = 1.0,
    cmin: Double = 0.90,
    cosphi: Double = 0.5,
    trafos: String = "",
    workdir: String = "")
