package ch.ninecode.lv

object LogLevels extends Enumeration
{
    type LogLevels = Value
    val ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN = Value
}

/**
 * Low voltage GLM generation options.
 *
 * @param valid              <code>false</code> if either help or version requested (i.e. don't proceed with execution).
 * @param unittest           If <code>true</code> don't call sys.exit().
 * @param verbose            If <code>true</code> turns on the INFO logging if it was not on. Default <code>false</code>.
 * @param master             Spark master, e.g. spark://host:port, mesos://host:port, yarn, or local[*].
 * @param spark_options      Spark options.
 * @param cim_reader_options Options to the CIMReader, such as <code>ch.ninecode.cim.do_deduplication</code>.
 * @param storage            Storage level for RDD serialization.
 * @param log_level          Logging level.
 * @param dedup              Perform de-duplication on (striped) input files.
 * @param three              If <code>true</code> generate three phase .glm files, else one phase.
 * @param trafos             The list of transformers to process. Default is an empty list which means all low voltage transformers in the input file(s) are processeed.
 * @param workdir            The shared directory (among Spark executors) to use for staging GridLAB-D simulations. Each simulation is created in a subdirectory of this directory.
 * @param checkpoint_dir     Checkpoint directory on HDFS, e.g. hdfs://...
 * @param files              The list of input CIM files (RDF).
 */
case class LowVoltageOptions
(
    var valid: Boolean = true,
    unittest: Boolean = false,
    verbose: Boolean = false,
    master: String = "local[*]",
    spark_options: Map[String, String] = Map (
        "spark.graphx.pregel.checkpointInterval" -> "8",
        "spark.serializer" -> "org.apache.spark.serializer.KryoSerializer",
        "spark.ui.showConsoleProgress" -> "false"
    ),
    cim_reader_options: Map[String, String] = Map[String, String](
        "ch.ninecode.cim.do_topo" -> "true",
        "ch.ninecode.cim.do_topo_islands" -> "true",
        "ch.ninecode.cim.force_retain_switches" -> "Unforced",
        "ch.ninecode.cim.force_retain_fuses" -> "Unforced",
        "ch.ninecode.cim.force_switch_separate_islands" -> "Unforced",
        "ch.ninecode.cim.force_fuse_separate_islands" -> "Unforced",
        "ch.ninecode.cim.default_switch_open_state" -> "false",
        "StorageLevel" -> "MEMORY_AND_DISK_SER"
    ),
    storage: String = "MEMORY_AND_DISK_SER",
    log_level: LogLevels.Value = LogLevels.OFF,
    dedup: Boolean = false,
    three: Boolean = false,
    trafos: String = "",
    workdir: String = "",
    checkpoint_dir: String = "",
    files: Seq[String] = Seq ()
)
