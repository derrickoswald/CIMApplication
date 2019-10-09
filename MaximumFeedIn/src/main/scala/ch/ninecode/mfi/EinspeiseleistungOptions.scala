package ch.ninecode.mfi

import scala.collection._

/**
 * Logging level enumeration.
 */
object LogLevels extends Enumeration
{
    type LogLevels = Value
    val ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN = Value
    def toLog4j (level: Value): org.apache.log4j.Level =
        level match {
            case ALL   => org.apache.log4j.Level.ALL
            case DEBUG => org.apache.log4j.Level.DEBUG
            case ERROR => org.apache.log4j.Level.ERROR
            case FATAL => org.apache.log4j.Level.FATAL
            case INFO  => org.apache.log4j.Level.INFO
            case OFF   => org.apache.log4j.Level.ALL
            case TRACE => org.apache.log4j.Level.ALL
            case WARN  => org.apache.log4j.Level.WARN
        }
}

/**
 * Options for the Einspeiseleistung calculation.
 *
 * @param valid              <code>false</code> if either help or version requested (i.e. don't proceed with execution).
 * @param unittest           If <code>true</code>, don't call sys.exit().
 * @param master             Spark master, e.g. spark://host:port, mesos://host:port, yarn, or local[*].
 * @param spark_options      Spark options.
 * @param storage            Storage level for RDD serialization.
 * @param dedup              Perform de-duplication on (striped) input files.
 * @param log_level          Logging level.
 * @param checkpoint_dir     Checkpoint directory on HDFS, e.g. hdfs://...
 * @param verbose            If <code>true</code> turns on the INFO logging if it was not on. Default <code>false</code>.
 * @param cim_reader_options Options to the CIMReader, such as <code>ch.ninecode.cim.do_deduplication</code>.
 * @param three              If <code>true</code> uses three-phase calculations. Default <code>false</code> - single phase caclulations.
 * @param precalculation     If <code>true</code> performs only the precalculation and stores the results in the database.
 * @param trafos             The list of transformers to process. Default is an empty list which means all low voltage transformers in the input file(s) are processeed.
 * @param export_only        If <code>true</code> only generates the GridLAB-D .glm files without simulating them. Default <code>false</code>.
 * @param all                If <code>true</code> forces all house connections to be processed, rather than just the ones with a changed photo-voltaic installation. Default <code>false</code>.
 * @param erase              If <code>true</code> deletes the generated GridLAB-D .glm files and player and recorder files. Default <code>false</code>.
 * @param simulation         The prior simulation number to use in determining the transformers to process. Default -1 - use either the trafos list if specified or all low voltage transformers.
 * @param reference          The prior simulation number to determine if the photo-voltaic installation status is changed. Default -1 - use the current precalculation simulation.
 * @param delta              The difference threshold to determine if the maximum feed-in power has changed between precalculations. Default 1.0e-6.
 * @param precalc_factor     The scale factor to apply to precalculation maximum values - which is used as an upper bound for the stepped simulation calculation. Default 1.5.
 * @param cosphi             The maximum feed-in power factor, i.e. the power factor for new photo-voltaic installations, +lagging, -leading. Default 1.0.
 * @param voltage_threshold  the voltage threshold for the feeder of the house under test. Default 3.0 (3%).
 * @param voltage_threshold2 the voltage threshold to be used for neighboring feeders of the house under test. Default 3.0 (3%).
 * @param ignore_other       Whether to check cable currents on neighboring feeders of the house under test. Default false.
 * @param cable_impedance_limit Cables with a R1 value higher than this are not calculated with GridLAB-D, the reason is bad performance in GridLAB-D for very high impedance values. Default 5.0.
 * @param workdir            The shared directory (among Spark executors) to use for staging GridLAB-D simulations. Each simulation is created in a subdirectory of this directory.
 * @param outputfile         The name of the SQLite database results file.
 * @param files              The list of input CIM files (RDF).
 */
case class EinspeiseleistungOptions
(
    var valid: Boolean = true,
    unittest: Boolean = false,
    master: String = "",
    spark_options: Map[String, String] = Map (
        "spark.graphx.pregel.checkpointInterval" → "8",
        "spark.serializer" → "org.apache.spark.serializer.KryoSerializer",
        "spark.ui.showConsoleProgress" → "false"
    ),
    storage: String = "MEMORY_AND_DISK_SER",
    dedup: Boolean = false,
    log_level: LogLevels.Value = LogLevels.OFF,
    checkpoint_dir: String = "",
    verbose: Boolean = false,
    cim_reader_options: Iterable[(String, String)] = new immutable.HashMap[String, String](),
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
    workdir: String = "",
    outputfile: String = "simulation/results.db",
    files: Seq[String] = Seq ()
)
