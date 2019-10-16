package ch.ninecode.ts

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

import ch.ninecode.ts.LogLevels.LogLevels
import ch.ninecode.ts.Operations.Operations

/**
 * Options for time series processing.
 *
 * @param valid         <code>false</code> if either help or version requested (i.e. don't proceed with execution).
 * @param unittest      If <code>true</code>, don't call sys.exit().
 * @param master        Spark master.
 * @param spark_options Spark options.
 * @param storage_level Storage level for RDD serialization.
 * @param log_level     Logging level.
 * @param host          Cassandra connection host.
 * @param port          Cassandra nativeTransportPort port.
 * @param keyspace      Cassandra keyspace.
 * @param model_file    File name for model save/load.
 * @param operation     The operation to perform: statistical analysis, model building, time series synthesis.
 * @param tree_depth    The depth of the generated decision tree, or array for hyperparameter tuning.
 * @param bins          The maximum number of bins for discretizing continuous features, or array for hyperparameter tuning.
 * @param info          The minimum information gain for a split to be considered at a tree node, or array for hyperparameter tuning.
 * @param seed          A random number seed.
 * @param synthesis     The name, id, or mRID for the synthesized time series.
 * @param start         The starting time for the synthesis.
 * @param end           The ending time for the time series.
 * @param period        The number of milliseconds between synthesized readings.
 * @param yearly_kWh    The energy used by the synthesized time series per year (kWh).
 */
case class TimeSeriesOptions
(
    var valid: Boolean = true,
    unittest: Boolean = false,
    master: String = "",
    spark_options: Map[String, String] = Map (),
    storage_level: String = "MEMORY_AND_DISK_SER",
    log_level: LogLevels = LogLevels.OFF,
    host: String = "localhost",
    port: Int = 9042,
    keyspace: String = "cimapplication",
    model_file: String = "models/myDecisionTreeRegressorModel",
    operation: Operations = Operations.Synthesize,
    tree_depth: Array[Int] = Array(8),
    bins: Array[Int] = Array(32),
    info: Array[Double] = Array(0.0),
    seed: Long = -1L,
    synthesis: String = "HASFAKE",
    start: Calendar = { val d = "2017-07-19T00:00:00.000+0000"; val c = Calendar.getInstance (TimeZone.getTimeZone ("GMT")); c.setTimeInMillis (new SimpleDateFormat ("yyyy-MM-dd'T'HH:mm:ss.SSSZ").parse (d).getTime); c },
    end: Calendar = { val d = "2018-03-31T23:45:00.000+0000"; val c = Calendar.getInstance (TimeZone.getTimeZone ("GMT")); c.setTimeInMillis (new SimpleDateFormat ("yyyy-MM-dd'T'HH:mm:ss.SSSZ").parse (d).getTime); c },
    period: Int = 900000,
    yearly_kWh: Double = 7200.0
)
