package ch.ninecode.sc

import java.net.URI

import javax.json.Json
import javax.json.JsonObjectBuilder

import ch.ninecode.cim.CIMTopologyOptions
import ch.ninecode.cim.ForceTrue
import ch.ninecode.util.CIMAble
import ch.ninecode.util.CIMReaderOptions
import ch.ninecode.util.Complex
import ch.ninecode.util.MainOptions
import ch.ninecode.util.Mainable
import ch.ninecode.util.SparkOptions
import ch.ninecode.util.Sparkable

/**
 * Short circuit calculation options.
 *
 * @param main_options                        main() program options
 * @param spark_options                       Spark session options
 * @param cim_options                         CIMReader options
 * @param id                                  run id on input, primary key for database on output
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
    var main_options: MainOptions = MainOptions(),
    var spark_options: SparkOptions = SparkOptions(),
    var cim_options: CIMReaderOptions = CIMReaderOptions(
        topology = true,
        topology_options = CIMTopologyOptions(
            identify_islands = true,
            force_retain_fuses = ForceTrue)),
    id: String = "",
    verbose: Boolean = true,
    description: String = "",
    default_short_circuit_power_max: Double = 200.0e6,
    default_short_circuit_impedance_max: Complex = Complex(0.437785783, -1.202806555),
    default_short_circuit_angle_max: Double = Double.NaN,
    default_short_circuit_power_min: Double = 100.0e6,
    default_short_circuit_impedance_min: Complex = Complex(0.875571570, -2.405613110),
    default_short_circuit_angle_min: Double = Double.NaN,
    default_transformer_power_rating: Double = 630000,
    default_transformer_impedance: Complex = Complex(0.005899999998374999, 0.039562482211875),
    base_temperature: Double = 20.0,
    low_temperature: Double = 60.0,
    high_temperature: Double = 90.0,
    cmax: Double = 1.0,
    cmin: Double = 0.90,
    worstcasepf: Boolean = true,
    cosphi: Double = 0.5,
    fuse_table: FuseData = FuseData(Array(FuseTable.default)),
    messagemax: Int = 5,
    batchsize: Long = 10000,
    trafos: String = "",
    cable_impedance_limit: Double = 5.0,
    workdir: String = "",
    calculate_public_lighting: Boolean = false,
    output: ShortCircuitOutputType.Value = ShortCircuitOutputType.SQLite,
    outputfile: String = "results/shortcircuit.db",
    host: String = "localhost",
    port: Int = 9042,
    keyspace: String = "cimapplication",
    replication: Int = 1
) extends Mainable with Sparkable with CIMAble
{
    def derive_work_dir (files: Seq[String]): String =
    {
        files.toList match
        {
            case paths :: _ =>
                val file = paths.split(",")(0).replace(" ", "%20")
                val uri = new URI(file)
                val scheme = uri.getScheme
                val auth = if (null == uri.getAuthority) "" else uri.getAuthority
                if (null == scheme)
                    "/simulation/"
                else
                    s"$scheme://$auth/simulation/"
            case _ =>
                "/simulation/"
        }
    }

    /**
     * Get user specified directory or generate a working directory matching the files.
     */
    def getWorkDir: String = if ("" != workdir) workdir else derive_work_dir(cim_options.files)

    /**
     * Make a complex value into JSON.
     *
     * @param value the comlex value to encode
     * @return a JSON object with complex value
     */
    def complexAsJSON (value: Complex): JsonObjectBuilder =
    {
        Json.createObjectBuilder
            .add("re", value.re)
            .add("im", value.im)
    }

    /**
     * JSON representation of the options.
     *
     * @return a JSON object with the parameters as properties
     */
    def asJSON: JsonObjectBuilder =
    {
        Json.createObjectBuilder
            .add("id", id)
            .add("verbose", verbose)
            .add("description", description)
            .add("default_short_circuit_power_max", complexAsJSON(default_short_circuit_power_max))
            .add("default_short_circuit_impedance_max", complexAsJSON(default_short_circuit_impedance_max))
            .add("default_short_circuit_power_min", complexAsJSON(default_short_circuit_power_min))
            .add("default_short_circuit_impedance_min", complexAsJSON(default_short_circuit_impedance_min))
            .add("default_transformer_power_rating", default_transformer_power_rating)
            .add("default_transformer_impedance", complexAsJSON(default_transformer_impedance))
            .add("base_temperature", base_temperature)
            .add("low_temperature", low_temperature)
            .add("high_temperature", high_temperature)
            .add("cmax", cmax)
            .add("cmin", cmin)
            .add("worstcasepf", worstcasepf)
            .add("cosphi", cosphi)
            .add("fuse_table", fuse_table.toString)
            .add("messagemax", messagemax)
            .add("batchsize", batchsize)
            .add("trafos", trafos)
            .add("cable_impedance_limit", cable_impedance_limit)
            .add("workdir", workdir)
            .add("calculate_public_lighting", calculate_public_lighting)
            .add("output", output.toString)
            .add("outputfile", outputfile)
            .add("host", host)
            .add("port", port)
            .add("keyspace", keyspace)
            .add("replication", replication)
    }
}
