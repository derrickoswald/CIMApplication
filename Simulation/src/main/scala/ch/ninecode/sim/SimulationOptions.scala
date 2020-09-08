package ch.ninecode.sim

import ch.ninecode.cim.CIMTopologyOptions
import ch.ninecode.util.CIMAble
import ch.ninecode.util.CIMReaderOptions
import ch.ninecode.util.MainOptions
import ch.ninecode.util.Mainable
import ch.ninecode.util.SparkOptions
import ch.ninecode.util.Sparkable

/**
 * Options for Simulation.
 *
 * @param main_options          main() program options
 * @param spark_options         Spark session options
 * @param cim_options           CIMReader options
 * @param verbose               If <code>true</code>, emit progress messages.
 * @param host                  Cassandra connection host.
 * @param port                  Cassandra connection port.
 * @param workdir               Working directory for executors.
 * @param three_phase           If <code>true</code>, simulate in three phase flag.
 * @param fake_three_phase      If <code>true</code>, convert single phase meter readings into three phase.
 * @param keep                  If <code>true</code>, keep glm and input/output files in workdir.
 * @param simulationonly        If <code>true</code>, only perform simulation, not postprocessing.
 * @param postprocessonly       If <code>true</code>, only perform postprocessing, not simulation.
 * @param cable_impedance_limit cables with a R1 value higher than this are not calculated with gridlab,
 *                              the reason is bad performance in gridlab with too high impedance values
 * @param simulation            Simulation JSON files.
 */
case class SimulationOptions
(
    var main_options: MainOptions = MainOptions (),
    var spark_options: SparkOptions = SparkOptions (),
    var cim_options: CIMReaderOptions = CIMReaderOptions (
        topology = true,
        topology_options = CIMTopologyOptions (identify_islands = true)),
    verbose: Boolean = false,
    host: String = "localhost",
    port: Int = 9042,
    workdir: String = "simulation/",
    three_phase: Boolean = false,
    fake_three_phase: Boolean = false,
    keep: Boolean = false,
    simulationonly: Boolean = false,
    postprocessonly: Boolean = false,
    cable_impedance_limit: Double = 5.0,
    simulation: Seq[String] = Seq ()
) extends Mainable with Sparkable with CIMAble
