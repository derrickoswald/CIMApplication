package ch.ninecode.sim

import java.sql.Timestamp

import javax.json.JsonObject

import scala.reflect.runtime.universe.TypeTag

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.udf
import org.apache.spark.storage.StorageLevel
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import com.datastax.driver.core.ConsistencyLevel
import com.datastax.spark.connector._
import com.datastax.spark.connector.SomeColumns
import com.datastax.spark.connector.writer.WriteConf

// Measurements needed from GridLAB-D recorders:
//   - PowerTransformer (N6) apparent power [Scheinleistung (S)] (VA)
//   - PowerTransformer (N6) current [Strom (I)] (A)
//   - PowerTransformer (N6) power factor [cosφ]
//   - BusbarSection (with container PSRType_DistributionBox) voltage [Spannung (U)] (V)
//   - BusbarSection (with container PSRType_DistributionBox) apparent power [Scheinleistung (S)] (VA)
//   - BusbarSection (with container PSRType_DistributionBox) power factor [cosφ]
//   - EnergyConsumer (with PSRType_HouseService) voltage [Spannung (U)] (V)
//   - EnergyConsumer (with PSRType_HouseService) apparent power [Scheinleistung (S)] (VA), negative means feed-in
//   - EnergyConsumer (with PSRType_HouseService) power factor [cosφ]
//   - ACLineSegment (N7) current [Strom (I)] (A)

trait Evented
{
    // database schema
    //          simulation mrid   type    start_time end_time,  ratio,  severity, message
    type Event = (String, String, String, Timestamp, Timestamp, Double, Int, String)
}

/**
 * Event trigger.
 *
 * Base class that contains details about the condition that triggers an event.
 *
 * @param `type` The type of value, which corresponds to the <code>type</code> column of the simulated_value Cassandra table.
 * @param severity The severity of the event.
 * @param reference The reference value, which corresponds to the query in the <code>key_value</code> Cassandra table.
 * @param default The reference default value if it is not found in the <code>properties</code> column. (same units as simulated_value records for the type)
 * @param ratio The ratio between the value and reference required to trigger the event.
 * @param duration The continuous duration that the ratio must hold for the event to be registered. (mSec)
 */
abstract class Trigger (
        val `type`: String,
        val severity: Int,
        val reference: String,
        val default: Double,
        val ratio: Double,
        val duration: Int)
    extends Serializable
{
    /**
     * Required zero arg constructor for serializability.
     */
    def this () = this ("", 0, "", 0.0, 0.0, 0)

    /**
     * Comparison function generator.
     * @param nominal The nominal value, e.g. ratedVoltage, ratedCurrent or ratedS
     * @return A function that can determine if a value causes the trigger to fire.
     */
    def comparator (nominal: Double): Double ⇒ Boolean

    /**
     * Message describing the event.
     *
     * @param millis The number of milliseconds the ratio has held for (according to the comparator).
     * @return A suitable message for the <code>type</code> column of the simulation_event Cassandra table.
     */
    def message (millis: Int): String
}

/**
 * Over ratio event trigger.
 *
 * Triggers an event when the value exceeds a threshold.
 *
 * @param `type` The type of value, which corresponds to the <code>type</code> column of the simulated_value Cassandra table.
 * @param severity The severity of the event.
 * @param reference The reference value, which corresponds to the property in the <code>properties</code> column of the geojson_XXX Cassandra table.
 * @param default The reference default value if it is not found in the <code>properties</code> column. (same units as simulated_value records for the type)
 * @param ratio The ratio between the value and reference above which the event will be triggered.
 * @param duration The continuous duration that the ratio must be exceeded for the event to be registered (mSec).
 */
case class HighTrigger (
        override val `type`: String,
        override val severity: Int,
        override val reference: String,
        override val default: Double,
        override val ratio: Double,
        override val duration: Int)
    extends Trigger (`type`, severity, reference, default, ratio, duration)
{
    def hi (threshold: Double)(value:Double): Boolean = value > threshold
    def comparator (nominal: Double): Double ⇒ Boolean = hi (ratio * nominal)
    def message (millis: Int): String = "%s exceeds %s%% threshold for %s milliseconds".format (`type`, ratio * 100.0, millis)
}

/**
 * Under ratio event trigger.
 *
 * Triggers an event when the value subceeds a threshold.
 *
 * @param `type` The type of value, which corresponds to the <code>type</code> column of the simulated_value Cassandra table.
 * @param severity The severity of the event.
 * @param reference The reference value, which corresponds to the property in the <code>properties</code> column of the geojson_XXX Cassandra table.
 * @param default The reference default value if it is not found in the <code>properties</code> column. (same units as simulated_value records for the type)
 * @param ratio The ratio between the value and reference below which the event will be triggered.
 * @param duration The continuous duration that the ratio must be subceeded for the event to be registered (mSec).
 */
case class LowTrigger (
        override val `type`: String,
        override val severity: Int,
        override val reference: String,
        override val default: Double,
        override val ratio: Double,
        override val duration: Int)
    extends Trigger (`type`, severity, reference, default, ratio, duration)
{
    def lo (threshold: Double)(value:Double): Boolean = value < threshold
    def comparator (nominal: Double): Double ⇒ Boolean = lo (ratio * nominal)
    def message (millis: Int): String = "%s subceeds %s%% threshold for %s milliseconds".format (`type`, ratio * 100.0, millis)
}

/**
 * Checks for events in a sequence of values.
 *
 * @param simulation the simulation id
 * @param mrid the mRID of the element being checked
 * @param trigger the description of the trigger for an event
 * @param limit the threshold value for the trigger
 */
case class Checker (simulation: String, mrid: String, trigger: Trigger, limit: Double) extends Evented
{
    var ret: List[Event] = Nil

    var start = 0L                         // starting time
    var end = 0L                           // ending time
    var timeout: Int = Int.MaxValue        // count-down
    val predicate: Double ⇒ Boolean = trigger.comparator (limit)

    def next (time: Long, period: Int, current: Double): Unit =
    {
        if (predicate (current))
        {
            if (0L == start)
            {
                // start counting
                start = time
                end = time + period
                timeout = trigger.duration - period
            }
            else
            {
                if (time == end)
                {
                    // consecutive periods
                    end = time + period
                    timeout = timeout - period
                }
                else
                {
                    // non consecutive periods
                    // emit an event if the duration has elapsed
                    if (timeout <= 0L)
                        ret = (simulation, mrid, trigger.`type`, new Timestamp (start), new Timestamp (end), trigger.ratio, trigger.severity, trigger.message (trigger.duration - timeout)) :: ret
                    // start counting again
                    start = time
                    end = time + period
                    timeout = trigger.duration - period
                }
            }
        }
        else
        {
            // emit an event if the duration has elapsed
            if (timeout <= 0L)
                ret = (simulation, mrid, trigger.`type`, new Timestamp (start), new Timestamp (end), trigger.ratio, trigger.severity, trigger.message (trigger.duration - timeout)) :: ret
            start = 0L
            end = 0L
            timeout = Int.MaxValue
        }
    }

    def gimme: List[Event] =
    {
        // emit an event if the duration has elapsed
        if (0L != start && timeout <= 0L)
        {
            ret = (simulation, mrid, trigger.`type`, new Timestamp (start), new Timestamp (end), trigger.ratio, trigger.severity, trigger.message (trigger.duration - timeout)) :: ret
            start = 0L // don't do it again
            end = 0L
            timeout = Int.MaxValue
        }
        ret
    }
}

/**
 * Worker bee for the SimulationEvents class.
 *
 * Needed this class to get around the 'not serializable' exception if the code is directly in SimulationEvents.
 *
 * @param spark The Spark session
 * @param storage_level The storage level for persist
 */
case class DoubleChecker (spark: SparkSession, storage_level: StorageLevel = StorageLevel.fromString ("MEMORY_AND_DISK_SER"))
    extends Evented
{
    def filterFor (thresholds: Iterable[_ <: Trigger], column: String, nominal: String): String =
    {
        var minratio = Double.MaxValue
        var maxratio = Double.MinValue
        thresholds.foreach
        {
            case h: HighTrigger ⇒ if (h.ratio < minratio) minratio = h.ratio
            case l: LowTrigger ⇒ if (l.ratio > maxratio) maxratio = l.ratio
        }
        val filters = Array (
            if (minratio != Double.MaxValue) Some ("%s > (%s * %s)".format (column, minratio, nominal)) else None,
            if (maxratio != Double.MinValue) Some ("%s < (%s * %s)".format (column, maxratio, nominal)) else None)
        // NOTE: Cassandra has no 'OR' clause capability because it is brain dead, so be aware that thresholds with both filters will fail
        filters.flatten.mkString (" or ")
    }

    def check (simulation: String, thresholds: Iterable[_ <: Trigger]) (stuff: Iterable[(String, Timestamp, Int, Double, Double)]): Iterable[Event] =
    {
        // all the mRID and threshold values are the same
        val mrid = stuff.head._1
        val threshold = stuff.head._5

        // pare down the data and sort by time
        val values = stuff.map (x ⇒ (x._2.getTime, x._3, x._4)).toArray.sortWith (_._1 < _._1)

        val checkers = thresholds.map (x ⇒ Checker (simulation, mrid, x, threshold))
        for (i ← values.indices)
        {
            val time = values(i)._1
            val period = values(i)._2
            val value = values(i)._3
            checkers.foreach (x ⇒ x.next (time, period, value))
        }
        checkers.flatMap (_.gimme)
    }

    def isEmpty[T] (rdd: RDD[T]): Boolean =
    {
        0 == rdd.take (1).length
    }

    def isEmpty (dataframe: DataFrame): Boolean =
    {
        0 == dataframe.take (1).length
    }

    def save (events: RDD[Event]) (implicit access: SimulationCassandraAccess): Unit =
    {
        if (!isEmpty (events))
        {
            val columns = SomeColumns ("simulation", "mrid", "type", "start_time", "end_time", "ratio", "severity", "message")
            val configuration = WriteConf.fromSparkConf (spark.sparkContext.getConf).copy (consistencyLevel = ConsistencyLevel.ANY)
            events.saveToCassandra (access.output_keyspace, "simulation_event", columns, configuration)
        }
    }

    def checkFor (thresholds: Iterable[Trigger]) (implicit access: SimulationCassandraAccess) : Unit =
    {
        // all the threshold types, geometry tables, reference and default are the same
        val `type` = thresholds.head.`type`
        val reference = thresholds.head.reference
        val default = thresholds.head.default.toString

        def magnitude[Type_x: TypeTag, Type_y: TypeTag] = udf [Double, Double, Double]((x: Double, y: Double) => Math.sqrt (x * x + y * y))
        def getReference[Type_x: TypeTag] = udf [Double, Map[String, String]]((map: Map[String, String]) => map.getOrElse (reference, default).toDouble)

        val simulated_values = access.raw_values (`type`)
        val keyvalues = access.key_value (reference)

        val values = simulated_values
            .withColumn ("value", magnitude [Double, Double].apply (simulated_values ("real_a"), simulated_values ("imag_a")))
            .drop ("real_a", "imag_a")
            .join (
                keyvalues
                    .withColumn ("reference", keyvalues.col ("value").cast ("double"))
                    .drop ("value"),
                Seq ("mrid"))

        // values.printSchema ()
        // values.show(5)
        // values.explain (true)
        if (!isEmpty (values)) // ToDo: change to Dataset.isEmpty when Spark version > 2.4.0
        {
            val mrid = values.schema.fieldIndex ("mrid")
            val period = values.schema.fieldIndex ("period")
            val time = values.schema.fieldIndex ("time")
            val value = values.schema.fieldIndex ("value")
            val ref = values.schema.fieldIndex ("reference")

            // two stages here, one for HighTrigger and one for LowTrigger
            val highs = thresholds.flatMap (
                {
                    case h: HighTrigger ⇒ Some (h)
                    case _: LowTrigger ⇒ None
                }
            )
            val lows = thresholds.flatMap (
                {
                    case _: HighTrigger ⇒ None
                    case l: LowTrigger ⇒ Some (l)
                }
            )

            val highEvents = if (0 < highs.size)
                values.filter (filterFor (highs, "value", "reference"))
                    .rdd.map (row ⇒ (row.getString (mrid), row.getTimestamp (time), row.getInt (period), row.getDouble (value), row.getDouble (ref)))
                    .groupBy (_._1).values.flatMap (check (access.simulation, highs))
            else
                spark.sparkContext.emptyRDD[Event]

            val lowEvents = if (0 < lows.size)
                values.filter (filterFor (lows, "value", "reference"))
                    .rdd.map (row ⇒ (row.getString (mrid), row.getTimestamp (time), row.getInt (period), row.getDouble (value), row.getDouble (ref)))
                    .groupBy (_._1).values.flatMap (check (access.simulation, lows))
            else
                spark.sparkContext.emptyRDD[Event]

            save (highEvents.union (lowEvents))
        }
        keyvalues.unpersist (false)
        simulated_values.unpersist (false)
    }
}

/**
 * Find significant events in the simulation.
 *
 * Scan the results after running a simulation to locate various threshold events.
 *
 * @param triggers The list of trigger thresholds to check for.
 * @param spark   The Spark session
 * @param options The simulation options. Note: Currently only the verbose and storage_level options are used.
 */
case class SimulationEvents (triggers: Iterable[Trigger]) (spark: SparkSession, options: SimulationOptions)
    extends SimulationPostProcessor (spark, options)
{
    if (options.verbose) org.apache.log4j.LogManager.getLogger (getClass.getName).setLevel (org.apache.log4j.Level.INFO)
    val log: Logger = LoggerFactory.getLogger (getClass)

    def run (implicit access: SimulationCassandraAccess): Unit =
    {
        // organize the triggers by type, table, reference and default
        val sets = triggers.groupBy (trigger ⇒ (trigger.`type`, trigger.reference, trigger.default)).values.toArray
        log.info ("""checking for events in %s (input keyspace: %s, output keyspace: %s)""".format (access.simulation, access.input_keyspace, access.output_keyspace))

        for (i ← sets.indices)
        {
            val thresholds = sets(i)
            val `type` = thresholds.head.`type`
            log.info ("""%s events""".format (`type`))
            DoubleChecker (spark, options.storage_level).checkFor (thresholds)
            log.info ("""%s deviation events saved to %s.simulation_event""".format (`type`, access.output_keyspace))
        }
    }
}

/**
 * Parser for "event" post processor.
 */
object SimulationEvents extends SimulationPostProcessorParser
{
    val STANDARD_TRIGGERS: Iterable[Trigger] = List[Trigger] (
        // voltage exceeds ±10% of nominal = red, voltage exceeds ±6%=orange
        HighTrigger ("voltage", 1, "ratedVoltage", 400.0, 1.06, 15 * 60 * 1000),
         LowTrigger ("voltage", 1, "ratedVoltage", 400.0, 0.94, 15 * 60 * 1000),
        HighTrigger ("voltage", 2, "ratedVoltage", 400.0, 1.10, 15 * 60 * 1000),
         LowTrigger ("voltage", 2, "ratedVoltage", 400.0, 0.90, 15 * 60 * 1000),

        // current >75% and >14h within 24h = orange
        // current >90% and >3h within 24h = red
        // current >110% for 15 minutes or more = red
        HighTrigger ("current", 1, "ratedCurrent", 100.0, 0.75, 14 * 60 * 60 * 1000),
        HighTrigger ("current", 2, "ratedCurrent", 100.0, 0.90,  3 * 60 * 60 * 1000),
        HighTrigger ("current", 2, "ratedCurrent", 100.0, 1.10,      15 * 60 * 1000),

        // power >75% and >14h within 24h = orange
        // power >90% and >3h within 24h = red
        // power >110% for 15 minutes or more = red
        HighTrigger ("power", 1, "ratedS", 630.0, 0.75, 14 * 60 * 60 * 1000),
        HighTrigger ("power", 2, "ratedS", 630.0, 0.90,  3 * 60 * 60 * 1000),
        HighTrigger ("power", 2, "ratedS", 630.0, 1.10,      15 * 60 * 1000)
    )


    def cls: String = "event"

    /**
     * Generates a JSON parser to populate a processor.
     * @return A method that will return an instance of a post processor given the postprocessing element of a JSON.
     */
    def parser (): JsonObject ⇒ (SparkSession, SimulationOptions) ⇒ SimulationPostProcessor =
        post ⇒
        {

            val triggers = if (post.containsKey ("thresholds"))
            {
                val thresholds = post.getJsonArray ("thresholds")
                for (i ← 0 until thresholds.size)
                    yield
                        {
                            val threshold = thresholds.getJsonObject (i)
                            val trigger = threshold.getString ("trigger", "high")
                            val `type` = threshold.getString ("type", "voltage")
                            val severity = threshold.getInt ("severity", 1)
                            val table = threshold.getString ("table", "geojson_points")
                            val reference = threshold.getString ("reference", "ratedS")
                            val default = threshold.getJsonNumber ("default").doubleValue ()
                            val ratio = threshold.getJsonNumber ("ratio").doubleValue ()
                            val duration = threshold.getInt ("duration", 900000)
                            trigger match
                            {
                                case "high" ⇒ HighTrigger (`type`, severity, reference, default, ratio, duration)
                                case "low" ⇒ LowTrigger (`type`, severity, reference, default, ratio, duration)
                                case _ ⇒ null
                            }
                        }
            }
            else
                STANDARD_TRIGGERS

            SimulationEvents (triggers) (_: SparkSession, _: SimulationOptions)
        }

}
