package ch.ninecode.ts

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

import org.apache.spark.storage.StorageLevel
import org.json4s.Formats
import org.json4s.JsonAST.JLong
import org.json4s.JsonAST.JString

import ch.ninecode.ts.Operations.Operations
import ch.ninecode.util.CassandraOptions
import ch.ninecode.util.Cassandraable
import ch.ninecode.util.JSON
import ch.ninecode.util.JSONAble
import ch.ninecode.util.JSONCustomSerializer
import ch.ninecode.util.MainOptions
import ch.ninecode.util.Mainable
import ch.ninecode.util.SparkOptions
import ch.ninecode.util.Sparkable

/**
 * Options for time series processing.
 *
 * @param main_options      main() program options
 * @param spark_options     Spark session options
 * @param cassandra_options Cassandra options
 * @param storage           the RDD storage level
 * @param keyspace          Cassandra keyspace.
 * @param replication       Cassandra keyspace replication factor only if the keyspace is created.
 * @param model_file        File name for model save/load.
 * @param meta_file         File name for metadata.
 * @param operation         The operation to perform: statistical analysis, model building, time series synthesis.
 * @param tree_depth        The depth of the generated decision tree, or array for hyperparameter tuning.
 * @param bins              The maximum number of bins for discretizing continuous features, or array for hyperparameter tuning.
 * @param info              The minimum information gain for a split to be considered at a tree node, or array for hyperparameter tuning.
 * @param seed              A random number seed.
 * @param synthesis         The name, id, or mRID for the synthesized time series.
 * @param start             The starting time for the synthesis.
 * @param end               The ending time for the time series.
 * @param period            The number of milliseconds between synthesized readings.
 * @param yearly_kWh        The energy used by the synthesized time series per year (kWh).
 * @param classes           The class types and counts for synthesis using meta data, e.g. "Apartment=4, General=1".
 */
case class TimeSeriesOptions
(
    var main_options: MainOptions = MainOptions(),
    var spark_options: SparkOptions = SparkOptions(),
    var cassandra_options: CassandraOptions = CassandraOptions(),
    storage: StorageLevel = StorageLevel.MEMORY_AND_DISK_SER,
    keyspace: String = "cimapplication",
    replication: Int = 1,
    model_file: String = "models/myDecisionTreeRegressorModel",
    meta_file: String = "meta_utf8.csv",
    operation: Operations = Operations.Synthesize,
    tree_depth: Array[Int] = Array(15),
    bins: Array[Int] = Array(80),
    info: Array[Double] = Array(0.0),
    seed: Long = -1L,
    synthesis: String = "HASFAKE",
    start: Calendar = TimeSeriesOptions.parseCalendar("2017-07-19T00:00:00.000+0000"),
    end: Calendar =TimeSeriesOptions.parseCalendar("2018-03-31T23:45:00.000+0000"),
    period: Int = 900000,
    yearly_kWh: Double = 7200.0,
    classes: Map[String, Int] = Map()
) extends Mainable with Sparkable with Cassandraable with JSONAble[TimeSeriesOptions]
{
    def storageAsString: String = TimeSeriesOptions.storageLevelAsString (storage)

    /**
     * Output equivalent JSON options.
     */
    override def toJSON: String = TimeSeriesOptions.toJSON(this)

    /**
     * Create one of these option objects from JSON.
     *
     * @param text the JSON text
     * @return either an error message in Left or the options instance in Right
     */
    override def fromJSON (text: String): Either[String, TimeSeriesOptions] = TimeSeriesOptions.fromJSON(text)
}
object TimeSeriesOptions extends JSON[TimeSeriesOptions]
{
    /**
     * The name of the resource containing the JSON schema for the options.
     *
     * @return a resource name string for use by ClassLoader.getResourceAsStream
     */
    def schemaResourceName: String = "TimeSeriesOptionsSchema.json"
    def schemaUriMap: Map[String,String] = Map[String,String](
        "https://raw.githubusercontent.com/derrickoswald/CIMApplication/master/json-schema/TimeSeriesOptionsSchema.json" -> "resource:TimeSeriesOptionsSchema.json"
    ) ++ MainOptions.schemaUriMap ++ SparkOptions.schemaUriMap ++ CassandraOptions.schemaUriMap

    /**
     * Convert storage level to a string.
     *
     * @return a String that would generate level from StorageLevel.fromString
     */
    def storageLevelAsString (storage: StorageLevel): String =
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

    class StorageLevelSerializer extends JSONCustomSerializer[StorageLevel](
        (format: Formats) =>
            (
                {
                    case JString(s) => StorageLevel.fromString(s)
                },
                {
                    case x: StorageLevel => JString(storageLevelAsString(x))
                }
            )
    )

    class OperationsSerializer extends JSONCustomSerializer[Operations](
        (format: Formats) =>
            (
                {
                    case JString(s) => Operations.withName(s)
                },
                {
                    case x: Operations => JString(x.toString)
                }
            )
    )

    lazy val calendar_format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")

    def parseCalendar (s: String): Calendar =
    {
        val c = Calendar.getInstance(TimeZone.getTimeZone("GMT"))
        c.setTimeInMillis(calendar_format.parse(s).getTime)
        c
    }

    class CalendarSerializer extends JSONCustomSerializer[Calendar](
        (format: Formats) =>
            (
                {
                    case JString(s) => parseCalendar (s)
                    case JLong(l) =>
                        val c = Calendar.getInstance(TimeZone.getTimeZone("GMT"))
                        c.setTimeInMillis(l)
                        c
                },
                {
                    case x: Calendar =>
                        val d = calendar_format.format(x.getTime)
                        JString(d)
                }
            )
    )

    /**
     * The list of custom serializers for the options.
     */
    override def customSerializers: Seq[JSONCustomSerializer[_]] = List.concat(
        MainOptions.customSerializers,
        SparkOptions.customSerializers,
        CassandraOptions.customSerializers,
        List(new StorageLevelSerializer, new OperationsSerializer, new CalendarSerializer)
    )
}