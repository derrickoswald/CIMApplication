package ch.ninecode.np

import ch.ninecode.cim.CIMTopologyOptions
import ch.ninecode.cim.ForceTrue
import ch.ninecode.cim.Unforced
import ch.ninecode.util.CIMAble
import ch.ninecode.util.CIMReaderOptions
import ch.ninecode.util.MainOptions
import ch.ninecode.util.Mainable
import ch.ninecode.util.SparkOptions
import ch.ninecode.util.Sparkable

/**
 * Options for the NetworkParameter programs.
 *
 * @param main_options            main() program options
 * @param spark_options           Spark session options
 * @param cim_options             CIMReader options
 * @param verbose                 if <code>true</code> output informational messages
 * @param export                  save processed CIM rdf as this file name
 * @param available_power_csv     available power at station file
 * @param station_transformer_csv mapping between station and transformer file
 */
case class NetworkParametersOptions (
    var main_options: MainOptions = MainOptions(),
    var spark_options: SparkOptions = SparkOptions(),
    var cim_options: CIMReaderOptions = CIMReaderOptions(
        topology = true,
        topology_options = CIMTopologyOptions(
            identify_islands = true,
            force_retain_switches = Unforced,
            force_retain_fuses = ForceTrue,
            debug = true),
        dedup = true),
    verbose: Boolean = false,
    export: String = "",
    available_power_csv: String = "",
    station_transformer_csv: String = ""
) extends Mainable with Sparkable with CIMAble
