package ch.ninecode.ts

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

import org.apache.spark.storage.StorageLevel

import ch.ninecode.ts.Operations.Operations
import ch.ninecode.util.MainOptions
import ch.ninecode.util.Mainable
import ch.ninecode.util.SparkOptions
import ch.ninecode.util.Sparkable

/**
 * Options for time series processing.
 *
 * @param main_options  main() program options
 * @param spark_options Spark session options
 * @param storage       the RDD storage level
 * @param host          Cassandra connection host.
 * @param port          Cassandra nativeTransportPort port.
 * @param keyspace      Cassandra keyspace.
 * @param replication   Cassandra keyspace replication factor only if the keyspace is created.
 * @param model_file    File name for model save/load.
 * @param meta_file     File name for metadata.
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
 * @param classes       The class types and counts for synthesis using meta data, e.g. "Apartment=4, General=1".
 */
case class TimeSeriesOptions
(
    var main_options: MainOptions = MainOptions(),
    var spark_options: SparkOptions = SparkOptions(),
    storage: StorageLevel = StorageLevel.MEMORY_AND_DISK_SER,
    host: String = "localhost",
    port: Int = 9042,
    replication: Int = 1,
    keyspace: String = "cimapplication",
    model_file: String = "models/myDecisionTreeRegressorModel",
    meta_file: String = "meta_utf8.csv",
    operation: Operations = Operations.Synthesize,
    tree_depth: Array[Int] = Array(15),
    bins: Array[Int] = Array(80),
    info: Array[Double] = Array(0.0),
    seed: Long = -1L,
    synthesis: String = "HASFAKE",
    start: Calendar =
    {
        val d = "2017-07-19T00:00:00.000+0000";
        val c = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        c.setTimeInMillis(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").parse(d).getTime);
        c
    },
    end: Calendar =
    {
        val d = "2018-03-31T23:45:00.000+0000";
        val c = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        c.setTimeInMillis(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").parse(d).getTime);
        c
    },
    period: Int = 900000,
    yearly_kWh: Double = 7200.0,
    classes: Map[String, Int] = Map()
) extends Mainable with Sparkable
{
    /**
     * Convert storage level to a string.
     *
     * @return a String that would generate level from StorageLevel.fromString
     */
    def storageAsString: String =
    {
        storage match
        {
            case StorageLevel.NONE => "NONE"
            case StorageLevel.DISK_ONLY => "DISK_ONLY"
            case StorageLevel.DISK_ONLY_2 => "DISK_ONLY_2"
            case StorageLevel.MEMORY_ONLY => "MEMORY_ONLY"
            case StorageLevel.MEMORY_ONLY_2 => "MEMORY_ONLY_2"
            case StorageLevel.MEMORY_ONLY_SER => "MEMORY_ONLY_SER"
            case StorageLevel.MEMORY_ONLY_SER_2 => "MEMORY_ONLY_SER_2"
            case StorageLevel.MEMORY_AND_DISK => "MEMORY_AND_DISK"
            case StorageLevel.MEMORY_AND_DISK_2 => "MEMORY_AND_DISK_2"
            case StorageLevel.MEMORY_AND_DISK_SER => "MEMORY_AND_DISK_SER"
            case StorageLevel.MEMORY_AND_DISK_SER_2 => "MEMORY_AND_DISK_SER_2"
            case StorageLevel.OFF_HEAP => "OFF_HEAP"
            case _ => ""
        }
    }
}