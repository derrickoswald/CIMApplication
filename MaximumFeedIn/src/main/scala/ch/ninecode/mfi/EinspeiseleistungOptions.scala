package ch.ninecode.mfi

import java.net.URI

import javax.json.Json
import javax.json.JsonObjectBuilder

import ch.ninecode.cim.CIMTopologyOptions
import ch.ninecode.cim.ForceTrue
import ch.ninecode.util.CIMAble
import ch.ninecode.util.CIMReaderOptions
import ch.ninecode.util.CassandraOptions
import ch.ninecode.util.Cassandraable
import ch.ninecode.util.MainOptions
import ch.ninecode.util.Mainable
import ch.ninecode.util.SparkOptions
import ch.ninecode.util.Sparkable

/**
 * Options for the maximum feed-in calculation.
 *
 * @param main_options          main() program options
 * @param spark_options         Spark session options
 * @param cim_options           CIMReader options
 * @param cassandra_options     Cassandra options
 * @param id                    run id on input, primary key for database on output
 * @param checkpoint_dir        Checkpoint directory on HDFS, e.g. hdfs://...
 * @param workdir               The shared directory (among Spark executors) to use for staging GridLAB-D simulations. Each simulation is created in a subdirectory of this directory.
 * @param verbose               If <code>true</code> turns on the INFO logging if it was not on. Default <code>false</code>.
 * @param three                 If <code>true</code> uses three-phase calculations. Default <code>false</code> - single phase calculations.
 * @param precalculation        If <code>true</code> performs only the precalculation and stores the results in the database.
 * @param trafos                The list of transformers to process. Default is an empty list which means all low voltage transformers in the input file(s) are processeed.
 * @param export_only           If <code>true</code> only generates the GridLAB-D .glm files without simulating them. Default <code>false</code>.
 * @param all                   If <code>true</code> forces all house connections to be analysed with load flow, rather than just non-radial networks or the ones with a changed photo-voltaic installation. Default <code>false</code>.
 * @param erase                 If <code>true</code> deletes the generated GridLAB-D .glm files and player and recorder files. Default <code>false</code>.
 * @param simulation            The prior simulation number to use in determining the transformers to process. Default -1 - use either the trafos list if specified or all low voltage transformers.
 * @param reference             The prior simulation number to determine if the photo-voltaic installation status is changed. Default -1 - use the current precalculation simulation.
 * @param delta                 The difference threshold to determine if the maximum feed-in power has changed between precalculations. Default 1.0e-6.
 * @param precalc_factor        The scale factor to apply to precalculation maximum values - which is used as an upper bound for the stepped simulation calculation. Default 1.5.
 * @param cosphi                The maximum feed-in power factor, i.e. the power factor for new photo-voltaic installations, +lagging, -leading. Default 1.0.
 * @param voltage_threshold     the voltage threshold for the feeder of the house under test. Default 3.0 (3%).
 * @param voltage_threshold2    the voltage threshold to be used for neighboring feeders of the house under test. Default 3.0 (3%).
 * @param ignore_other          Whether to check cable currents on neighboring feeders of the house under test. Default false.
 * @param cable_impedance_limit Cables with a R1 value higher than this are not calculated with GridLAB-D, the reason is bad performance in GridLAB-D for very high impedance values (Ω). Default 5.0.
 * @param base_temperature      Temperature of elements in the input CIM file (°C).
 * @param sim_temperature       Temperature at which the simulation should be done (°C).
 * @param outputfile            The name of the SQLite database results file.
 * @param keyspace              The Cassandra keyspace to store results.
 * @param replication           The Cassandra keyspace replication if it needs to be created.
 */
case class EinspeiseleistungOptions
(
    var main_options: MainOptions = MainOptions(),
    var spark_options: SparkOptions = SparkOptions(),
    var cim_options: CIMReaderOptions = CIMReaderOptions(
        topology = true,
        topology_options = CIMTopologyOptions(
            identify_islands = true,
            force_retain_switches = ForceTrue,
            force_retain_fuses = ForceTrue
        )
    ),
    var cassandra_options: CassandraOptions = CassandraOptions(),
    id: String = "",
    checkpoint_dir: String = "",
    workdir: String = "",
    verbose: Boolean = false,
    three: Boolean = false,
    precalculation: Boolean = false,
    trafos: String = "",
    export_only: Boolean = false,
    all: Boolean = false,
    erase: Boolean = false,
    simulation: Int = -1,
    reference: Int = -1,
    delta: Double = 1e-6,
    precalc_factor: Double = 2.5,
    cosphi: Double = 1.0,
    voltage_threshold: Double = 3.0,
    voltage_threshold2: Double = 3.0,
    ignore_other: Boolean = false,
    cable_impedance_limit: Double = 5.0,
    base_temperature: Double = 20.0,
    sim_temperature: Double = 20.0,
    output: MaximumFeedInOutputType.Value = MaximumFeedInOutputType.SQLite,
    outputfile: String = "simulation/results.db",
    keyspace: String = "cimapplication",
    replication: Int = 1
) extends Mainable with Sparkable with CIMAble with Cassandraable
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
                    "simulation/"
                else
                    s"$scheme://$auth/simulation/"
            case _ =>
                "simulation/"
        }
    }

    /**
     * Get user specified directory or generate a working directory matching the files.
     */
    def getWorkDir: String = if ("" != workdir) workdir else derive_work_dir(cim_options.files)

    /**
     * JSON representation of the options.
     *
     * @return a JSON object with the parameters as properties
     */
    def asJSON: JsonObjectBuilder =
    {
        Json.createObjectBuilder
            .add("id", id)
            .add("checkpoint_dir", checkpoint_dir)
            .add("workdir", workdir)
            .add("verbose", verbose)
            .add("three", three)
            .add("precalculation", precalculation)
            .add("trafos", trafos)
            .add("export_only", export_only)
            .add("all", all)
            .add("erase", erase)
            .add("simulation", simulation)
            .add("reference", reference)
            .add("delta", delta)
            .add("precalc_factor", precalc_factor)
            .add("cosphi", cosphi)
            .add("voltage_threshold", voltage_threshold)
            .add("voltage_threshold2", voltage_threshold2)
            .add("ignore_other", ignore_other)
            .add("cable_impedance_limit", cable_impedance_limit)
            .add("base_temperature", base_temperature)
            .add("sim_temperature", sim_temperature)
            .add("output", output.toString)
            .add("outputfile", outputfile)
            .add("keyspace", keyspace)
            .add("replication", replication)
    }
}
