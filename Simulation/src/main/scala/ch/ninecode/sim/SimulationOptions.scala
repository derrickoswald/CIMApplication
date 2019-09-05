package ch.ninecode.sim

import org.apache.spark.storage.StorageLevel

object LogLevels extends Enumeration
{
    type LogLevels = Value
    val ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN = Value
}

case class SimulationOptions
(
    /**
     * False if either help or version requested (i.e. don't proceed with execution).
     */
    var valid: Boolean = true,

    /**
     * If <code>true</code>, don't call sys.exit().
     */
    unittest: Boolean = false,

    /**
     * If <code>true</code>, emit progress messages.
     */
    verbose: Boolean = false,

    /**
     * Spark master.
     */
    master: String = "",

    /**
     * Spark options.
     */
    options: Map[String, String] = Map (
        "spark.serializer" -> "org.apache.spark.serializer.KryoSerializer",
        "spark.ui.showConsoleProgress" -> "false"
    ),

    /**
     * Cassandra connection host.
     */
    host: String = "localhost",

    /**
     * Logging level.
     */
    log_level: LogLevels.Value = LogLevels.OFF,

    /**
     * Session RDD checkpoint directory.
     */
    checkpoint: String = "",

    /**
     * Working directory for executors.
     */
    workdir: String = "simulation/",

    /**
     * Storage level for RDD serialization.
     */
    storage_level: StorageLevel = StorageLevel.fromString ("MEMORY_AND_DISK_SER"),

    /**
     * Simulate in three phase flag.
     */
    three_phase: Boolean = false,

    /**
     * Convert single phase meter readings into three phase flag.
     */
    fake_three_phase: Boolean = false,

    /**
     * If <code>true</code>, keep glm and input/output files in workdir.
     */
    keep: Boolean = false,

    /**
     * Only perform simulation.
     */
    simulationonly: Boolean = false,

    /**
     * Only perform postprocessing.
     */
    postprocessonly: Boolean = false,

    /**
     * Simulation JSON files.
     */
    simulation: Seq[String] = Seq ()
)
