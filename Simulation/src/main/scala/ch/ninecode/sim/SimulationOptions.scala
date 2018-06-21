package ch.ninecode.sim

import ch.ninecode.sim.Main.LogLevels
import ch.ninecode.sim.Main.LogLevels.LogLevels

case class SimulationOptions
(
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
    options: Map[String,String] = Map(),

    /**
     * Cassandra connection host.
     */
    host: String = "localhost",

    /**
     * The Cassandra keyspace to save results to.
     */
    keyspace: String = "cimapplication",

    /**
     * The maximum number of statements to submit in one batch.
     * Note: java.lang.IllegalStateException: Batch statement cannot contain more than 65535 statements.
     */
    batchsize: Int = 65535,

    /**
     * Storage level for RDD serialization.
     */
    storage: String = "MEMORY_AND_DISK_SER",

    /**
     * Logging level.
     */
    log_level: LogLevels = LogLevels.OFF,

    /**
     * Session RDD checkpoint directory.
     */
    checkpoint: String = "",

    /**
     * Working directory for executors.
     */
    workdir: String = "/tmp/",

    /**
     * If <code>true</code>, keep glm and input/output files in workdir.
     */
    keep: Boolean = false,

    /**
     * Summarize mode.
     */
    summarize: Boolean = false,

    /**
     * Simulation JSON files.
     */
    simulation: Seq[String] = Seq ()
)
