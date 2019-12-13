package ch.ninecode.sim

import org.apache.spark.storage.StorageLevel

object LogLevels extends Enumeration
{
    type LogLevels = Value
    val ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN = Value
}

/**
 *
 * @param valid False if either help or version requested (i.e. don't proceed with execution).
 * @param unittest If <code>true</code>, don't call sys.exit().
 * @param verbose If <code>true</code>, emit progress messages.
 * @param master Spark master.
 * @param options Spark options.
 * @param host Cassandra connection host.
 * @param port Cassandra connection port.
 * @param log_level Logging level.
 * @param checkpoint Session RDD checkpoint directory.
 * @param workdir  Working directory for executors.
 * @param storage_level Storage level for RDD serialization.
 * @param three_phase If <code>true</code>, simulate in three phase flag.
 * @param fake_three_phase If <code>true</code>, convert single phase meter readings into three phase.
 * @param keep If <code>true</code>, keep glm and input/output files in workdir.
 * @param simulationonly If <code>true</code>, only perform simulation, not postprocessing.
 * @param postprocessonly If <code>true</code>, only perform postprocessing, not simulation.
 * @param simulation Simulation JSON files.
 */
case class SimulationOptions
(
    var valid: Boolean = true,
    unittest: Boolean = false,
    verbose: Boolean = false,
    master: String = "",
    options: Map[String, String] = Map (
        "spark.serializer" -> "org.apache.spark.serializer.KryoSerializer",
        "spark.ui.showConsoleProgress" -> "false",
        "spark.sql.warehouse.dir" -> "file:///tmp/"
    ),
    host: String = "localhost",
    port: Int = 9042,
    log_level: LogLevels.Value = LogLevels.OFF,
    checkpoint: String = "",
    workdir: String = "simulation/",
    storage_level: StorageLevel = StorageLevel.fromString ("MEMORY_AND_DISK_SER"),
    three_phase: Boolean = false,
    fake_three_phase: Boolean = false,
    keep: Boolean = false,
    simulationonly: Boolean = false,
    postprocessonly: Boolean = false,
    simulation: Seq[String] = Seq ()
)
