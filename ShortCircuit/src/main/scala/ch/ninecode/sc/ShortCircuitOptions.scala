package ch.ninecode.sc

/**
 * Short circuit calculation options.
 *
 * @param verbose flag to output progress and interesting values
 * @param csv_file file of available short circuit power at substations
 * @param default_supply_network_short_circuit_power available short circuit power to be used if no corresponding substation is found (MVA)
 * @param default_supply_network_short_circuit_angle short circuit power angle to be used if no corresponding substation is found (degrees)
 * @param trafos file name of transformer names to process
 * @param workdir shared directory (HDFS or NFS share) for intermediate results
 */
case class ShortCircuitOptions (
    verbose: Boolean = true,
    csv_file: String = "",
    default_supply_network_short_circuit_power: Double = 200.0,
    default_supply_network_short_circuit_angle: Double = -70.0,
    trafos: String = "",
    workdir: String = "")
