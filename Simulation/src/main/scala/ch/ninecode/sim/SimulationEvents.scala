package ch.ninecode.sim

import java.sql.Date
import java.sql.Timestamp

import javax.json.JsonObject
import javax.json.JsonValue
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.reflect.runtime.universe.TypeTag

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.Row
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.expressions.UserDefinedFunction
import org.apache.spark.sql.functions
import org.apache.spark.sql.functions.udf
import org.apache.spark.sql.types.DateType
import org.apache.spark.storage.StorageLevel
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import com.datastax.oss.driver.api.core.ConsistencyLevel
import com.datastax.spark.connector._
import com.datastax.spark.connector.SomeColumns
import com.datastax.spark.connector.types.IntType
import com.datastax.spark.connector.types.UDTFieldDef
import com.datastax.spark.connector.types.UserDefinedType
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
 * @param `type`    The type of value, which corresponds to the <code>type</code> column of the simulated_value Cassandra table.
 * @param severity  The severity of the event.
 * @param reference The reference value, which corresponds to the query in the <code>key_value</code> Cassandra table.
 * @param default   The reference default value if it is not found in the <code>properties</code> column. (same units as simulated_value records for the type)
 * @param ratio     The ratio between the value and reference required to trigger the event.
 * @param duration  The continuous duration that the ratio must hold for the event to be registered. (mSec)
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
     *
     * @param nominal The nominal value, e.g. ratedVoltage, ratedCurrent or ratedS
     * @return A function that can determine if a value causes the trigger to fire.
     */
    def comparator (nominal: Double): Double => Boolean

    /**
     * Message describing the event.
     *
     * @param millis The number of milliseconds the ratio has held for (according to the comparator).
     * @return A suitable string for the <code>message</code> column of the simulation_event Cassandra table.
     */
    def message (millis: Int): String
}

/**
 * Over ratio event trigger.
 *
 * Triggers an event when the value exceeds a threshold.
 *
 * @param `type`    The type of value, which corresponds to the <code>type</code> column of the simulated_value Cassandra table.
 * @param severity  The severity of the event.
 * @param reference The reference value, which corresponds to the property in the <code>properties</code> column of the geojson_XXX Cassandra table.
 * @param default   The reference default value if it is not found in the <code>properties</code> column. (same units as simulated_value records for the type)
 * @param ratio     The ratio between the value and reference above which the event will be triggered.
 * @param duration  The continuous duration that the ratio must be exceeded for the event to be registered (mSec).
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
    def hi (threshold: Double)(value: Double): Boolean = value > threshold

    def comparator (nominal: Double): Double => Boolean = hi (ratio * nominal)

    def message (millis: Int): String = f"${`type`}%s exceeds ${ratio * 100.0}%3.1f%% threshold for ${millis / 1000}%d seconds"
}

/**
 * Under ratio event trigger.
 *
 * Triggers an event when the value subceeds a threshold.
 *
 * @param `type`    The type of value, which corresponds to the <code>type</code> column of the simulated_value Cassandra table.
 * @param severity  The severity of the event.
 * @param reference The reference value, which corresponds to the property in the <code>properties</code> column of the geojson_XXX Cassandra table.
 * @param default   The reference default value if it is not found in the <code>properties</code> column. (same units as simulated_value records for the type)
 * @param ratio     The ratio between the value and reference below which the event will be triggered.
 * @param duration  The continuous duration that the ratio must be subceeded for the event to be registered (mSec).
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
    def lo (threshold: Double)(value: Double): Boolean = value < threshold

    def comparator (nominal: Double): Double => Boolean = lo (ratio * nominal)

    def message (millis: Int): String = f"${`type`}%s subceeds ${ratio * 100.0}%3.1f%% threshold for ${millis / 1000}%d seconds"
}

/**
 * Checks for events in a sequence of values.
 *
 * @param simulation the simulation id
 * @param mrid       the mRID of the element being checked
 * @param trigger    the description of the trigger for an event
 * @param limit      the threshold value for the trigger
 */
case class Checker (simulation: String, mrid: String, trigger: Trigger, limit: Double) extends Evented
{
    var ret: List[Event] = Nil

    var start = 0L // starting time
    var end = 0L // ending time
    var timeout: Int = Int.MaxValue // count-down
    val predicate: Double => Boolean = trigger.comparator (limit)

    def add (): Unit =
    {
        if (timeout <= 0L)
        {
            val t0 = new Timestamp (start)
            val t1 = new Timestamp (end)
            val message = trigger.message (trigger.duration - timeout)
            ret = (simulation, mrid, trigger.`type`, t0, t1, trigger.ratio, trigger.severity, message) :: ret
        }
    }

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
                    add ()
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
            add ()
            start = 0L
            end = 0L
            timeout = Int.MaxValue
        }
    }

    def finish: List[Event] =
    {
        // emit an event if the duration has elapsed
        if (0L != start && timeout <= 0L)
        {
            add ()
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
 * @param spark         The Spark session
 * @param storage_level The storage level for persist
 * @param three_phase   <code>true</code> if three phase checking is to be done
 */
case class DoubleChecker (spark: SparkSession, storage_level: StorageLevel = StorageLevel.fromString ("MEMORY_AND_DISK_SER"), three_phase: Boolean)
    extends Evented
{
    /*
     * Not used since SQL filters are no longer pushed down to Cassandra (since 3.0.0beta).
     * Generate a combined filter taking the 'best' case of a number of thresholds.
     *
     * Used to bulk reduce the number of simulated values than must be checked individually over time.
     * This filter is intended to be 'pushed down' to the Cassandra cluster and return
     * only the records that might cause one of the thresholds to trigger.
     *
     * @param thresholds list of 'all' high (or low) thresholds, but not both at the same time.
     * @param column     the column to filter on
     * @param nominal    the nominal or reference value the threshold ratio applies to
     * @return a filter string suitable for 'push down'
     *
    def filterFor (thresholds: Iterable[_ <: Trigger], column: String, nominal: String): String =
    {
        var minratio = Double.MaxValue
        var maxratio = Double.MinValue
        thresholds.foreach
        {
            case h: HighTrigger => if (h.ratio < minratio) minratio = h.ratio
            case l: LowTrigger => if (l.ratio > maxratio) maxratio = l.ratio
        }
        val filters = Array (
            if (minratio != Double.MaxValue) Some (s"$column > ($minratio * $nominal)") else None,
            if (maxratio != Double.MinValue) Some (s"$column < ($maxratio * $nominal)") else None)
        // NOTE: Cassandra has no 'OR' clause capability because it is brain dead, so be aware that thresholds with both filters will fail
        filters.flatten.mkString (" or ")
    }
    */

    /**
     * Generate a combined filter taking the 'best' case of a number of thresholds.
     *
     * Used to bulk reduce the number of simulated values than must be checked individually over time.
     * This replaces an SQL filter that is no longer pushed down to Cassandra (since 3.0.0beta).
     *
     * @param thresholds list of 'all' high (or low) thresholds, but not both at the same time.
     * @param column     the column to filter on
     * @param nominal    the nominal or reference value the threshold ratio applies to
     * @return a filter method for each row
     */
    def filterFor (thresholds: Iterable[_ <: Trigger], column: Int, nominal: Int): Row => Boolean =
    {
        var minratio = Double.MaxValue
        var maxratio = Double.MinValue
        thresholds.foreach
        {
            case h: HighTrigger => if (h.ratio < minratio) minratio = h.ratio
            case l: LowTrigger => if (l.ratio > maxratio) maxratio = l.ratio
        }

        def kernel (dohi: Boolean, dolo: Boolean)(row: Row) =
        {
            val value = row.getDouble (column)
            val reference = row.getDouble (nominal)
            val above = dohi && value > (minratio * reference)
            val below = dolo && value < (maxratio * reference)
            above || below
        }

        kernel (minratio != Double.MaxValue, maxratio != Double.MinValue)
    }

    /**
     * Get mRID and threshold.
     *
     * @param data simulated values
     *             (all the mRID and threshold values are the same)
     * @return (mRID, threshold)
     */
    @SuppressWarnings (Array ("org.wartremover.warts.TraversableOps"))
    def getConstants (data: Iterable[(String, Timestamp, Int, Double, Double)]): (String, Double) =
    {
        val first = data.head
        (first._1, first._5)
    }

    /**
     * Perform checking over time periods.
     *
     * Arrange the ('best' case) prefiltered values in time order and call stateful Checker(s) for each
     * value in time order. The events (if any) are then extracted from the Checker(s).
     *
     * @param simulation the simulation id
     * @param triggers   the list of business rule triggers
     * @param data       the raw data that passes the 'best' case filtering
     * @return a list of business rule events, if any
     */
    def check (simulation: String, triggers: Iterable[_ <: Trigger])(data: Iterable[(String, Timestamp, Int, Double, Double)]): Iterable[Event] =
    {
        val (mrid, threshold) = getConstants (data)

        // pare down the data and sort by time
        val values = data.map (x => (x._2.getTime, x._3, x._4)).toArray.sortWith (_._1 < _._1)

        val checkers = triggers.map (x => Checker (simulation, mrid, x, threshold))
        for (i <- values.indices)
        {
            val time = values (i)._1
            val period = values (i)._2
            val value = values (i)._3
            checkers.foreach (x => x.next (time, period, value))
        }
        checkers.flatMap (_.finish)
    }

    /**
     * Save the events, if any, to Cassandra.
     *
     * @param events the detected events
     * @param access a Cassandra helper class
     */
    def save (events: RDD[Event])(implicit access: SimulationCassandraAccess): Unit =
    {
        val columns = SomeColumns ("simulation", "mrid", "type", "start_time", "end_time", "ratio", "severity", "message")
        val configuration = WriteConf.fromSparkConf (spark.sparkContext.getConf).copy (consistencyLevel = ConsistencyLevel.ANY)
        events.saveToCassandra (access.output_keyspace, "simulation_event", columns, configuration)
    }

    /**
     * Get the type and reference value.
     *
     * @param triggers trigger defeinitions
     *                 (all the threshold types, geometry tables, reference and default are the same)
     * @return (mRID, threshold)
     */
    @SuppressWarnings (Array ("org.wartremover.warts.TraversableOps"))
    def getTriggerDetails (triggers: Iterable[Trigger]): (String, String) =
    {
        val first = triggers.head
        (first.`type`, first.reference)
    }

    def mag (re: Double, im: Double): Double =
    {
        Math.sqrt (re * re + im * im)
    }

    /**
     * Compute the magnitude of real and imaginary components.
     */
    def magnitude[Type_x: TypeTag, Type_y: TypeTag]: UserDefinedFunction =
        udf [Double, Double, Double]((re: Double, im: Double) => mag (re, im))

    /**
     * Compute the maximum magnitude of three phases.
     */
    def maximum_magnitude[Type_a: TypeTag, Type_b: TypeTag, Type_c: TypeTag, Type_d: TypeTag, Type_e: TypeTag, Type_f: TypeTag]: UserDefinedFunction =
        udf [Double, Double, Double, Double, Double, Double, Double](
            (a_r: Double, a_i: Double, b_r: Double, b_i: Double, c_r: Double, c_i: Double) =>
            {
                val a = mag (a_r, a_i)
                val b = mag (b_r, b_i)
                val c = mag (c_r, c_i)
                Math.max (a, Math.max (b, c))
            }
        )

    /**
     * Compute the minimum magnitude of three phases.
     */
    def minimum_magnitude[Type_a: TypeTag, Type_b: TypeTag, Type_c: TypeTag, Type_d: TypeTag, Type_e: TypeTag, Type_f: TypeTag]: UserDefinedFunction =
        udf [Double, Double, Double, Double, Double, Double, Double](
            (a_r: Double, a_i: Double, b_r: Double, b_i: Double, c_r: Double, c_i: Double) =>
            {
                val a = mag (a_r, a_i)
                val b = mag (b_r, b_i)
                val c = mag (c_r, c_i)
                Math.min (a, Math.min (b, c))
            }
        )

    /**
     * Convert between single phase reference and three phase reference.
     */
    def per_phase[Type_x: TypeTag]: UserDefinedFunction =
        udf [Double, Double]((x: Double) => x / Math.sqrt (3.0))

    /**
     * Apply a set of thresholds (with the same type, table, reference and default) to the data.
     *
     * @param triggers the trigger thresholds in the set
     * @param access   a Cassandra helper class
     */
    def checkFor (triggers: Iterable[Trigger])(implicit access: SimulationCassandraAccess): Unit =
    {
        val (typ, reference) = getTriggerDetails (triggers)

        val to_drop = if (three_phase)
            Seq ("simulation", "type", "units")
        else
            Seq ("simulation", "type", "real_b", "imag_b", "real_c", "imag_c", "units")
        val simulated_values = access.raw_values (typ, to_drop)
        val keyvalues = access.key_value (reference)

        val values = if (three_phase)
        {
            val references = keyvalues
                .withColumn ("reference", per_phase [Double].apply (keyvalues.col ("value").cast ("double")))
                .drop ("value")
            simulated_values
                .withColumn ("value_max", maximum_magnitude [Double, Double, Double, Double, Double, Double].apply (
                    functions.col ("real_a"), functions.col ("imag_a"), functions.col ("real_b"), functions.col ("imag_b"), functions.col ("real_c"), functions.col ("imag_c")))
                .withColumn ("value_min", minimum_magnitude [Double, Double, Double, Double, Double, Double].apply (
                    functions.col ("real_a"), functions.col ("imag_a"), functions.col ("real_b"), functions.col ("imag_b"), functions.col ("real_c"), functions.col ("imag_c")))
                .drop ("real_a", "imag_a", "real_b", "imag_b", "real_c", "imag_c")
                .join (references, Seq ("mrid"))
        }
        else
        {
            val references = keyvalues
                .withColumn ("reference", keyvalues.col ("value").cast ("double"))
                .drop ("value")
            simulated_values
                .withColumn ("value1", magnitude [Double, Double].apply (simulated_values ("real_a"), simulated_values ("imag_a")))
                .drop ("value", "real_a", "imag_a")
                .join (references, Seq ("mrid"))
        }

        val values_rdd = values.rdd.persist (storage_level)

        val mrid = values.schema.fieldIndex ("mrid")
        val period = values.schema.fieldIndex ("period")
        val time = values.schema.fieldIndex ("time")
        val val_max = if (three_phase) "value_max" else "value1"
        val val_min = if (three_phase) "value_min" else "value1"
        val value_max = values.schema.fieldIndex (val_max)
        val value_min = values.schema.fieldIndex (val_min)
        val ref = values.schema.fieldIndex ("reference")

        // two stages here, one for HighTrigger and one for LowTrigger
        val (highs, lows) = triggers.partition ({ case _: HighTrigger => true case _ => false })

        // for exceeding threshold checks, use the minimum of the three phases (worst case), or just the value (single phase case)
        val highEvents = if (0 < highs.size)
            values_rdd
                .filter (filterFor (highs, value_min, ref))
                .map (row => (row.getString (mrid), row.getTimestamp (time), row.getInt (period), row.getDouble (value_min), row.getDouble (ref)))
                .groupBy (_._1).values.flatMap (check (access.simulation, highs))
        else
            spark.sparkContext.emptyRDD [Event]

        // for subceeding threshold checks, use the maximum of the three phases (worst case), or just the value (single phase case)
        val lowEvents = if (0 < lows.size)
            values_rdd
                .filter (filterFor (lows, value_max, ref))
                .map (row => (row.getString (mrid), row.getTimestamp (time), row.getInt (period), row.getDouble (value_max), row.getDouble (ref)))
                .groupBy (_._1).values.flatMap (check (access.simulation, lows))
        else
            spark.sparkContext.emptyRDD [Event]

        save (highEvents.union (lowEvents))

        {
            val _ = values_rdd.unpersist (false)
        }
        {
            val _ = keyvalues.unpersist (false)
        }
        {
            val _ = simulated_values.unpersist (false)
        }
    }
}

//@UDT(name="event_number")
@UserDefinedType (
    name = "event_number",
    columns = IndexedSeq [UDTFieldDef](
        UDTFieldDef ("orange", IntType),
        UDTFieldDef ("red", IntType)
    ))
case class EventNumber (
    orange: Int,
    red: Int)

case class Summarizer (spark: SparkSession, storage_level: StorageLevel = StorageLevel.fromString ("MEMORY_AND_DISK_SER"))
{
    def getEvents (implicit access: SimulationCassandraAccess): DataFrame =
    {
        val events = access.events
        val ret = events
            .withColumn ("date", events ("start_time").cast (DateType))
            .drop ("start_time", "end_time", "message", "ratio")
        val _ = events.unpersist (false)
        ret
    }

    def getRecorders (implicit access: SimulationCassandraAccess): DataFrame =
    {
        val recorders = access.recorders
        val ret = recorders
            .drop ("name", "aggregations", "interval", "property", "unit")
        val _ = recorders.unpersist (false)
        ret
    }

    type Transformer = String
    type Day = Date
    type Type = String
    type mRID = String
    type Severity = Int
    type TransformerDay = String
    type Color = String
    type Count = Int
    type Sev = (Color, Count)
    type Total = Map[Color, Count]
    type Sum = (mRID, Total)
    type Summary = Map[mRID, Total]

    def accumulate (severities: Total, severity: Sev): Total =
    {
        val color = severity._1
        val total = severity._2
        severities.get (color) match
        {
            case Some (current: Int) => severities.updated (color, current + total)
            case None => severities.updated (color, total)
        }
    }

    def aggregate (events: Iterable[(mRID, Severity)]): Summary =
    {
        def asColor (severity: Int): String =
        {
            severity match
            {
                case 1 => "red"
                case 2 => "orange"
                case _ => "unknown"
            }
        }

        def seqop (sum: Summary, event: (mRID, Severity)): Summary =
        {
            val (mrid, severity) = event
            val color = asColor (severity)
            sum.get (mrid) match
            {
                case Some (total: Total) => sum.updated (mrid, accumulate (total, (color, 1)))
                case None => sum.updated (mrid, Map [Color, Count](color -> 1))
            }
        }

        def gather (summary: Summary, sum: Sum) =
        {
            val (mrid, map) = sum
            summary.get (mrid) match
            {
                case Some (total: Total) => summary.updated (mrid, map.foldLeft (total)(accumulate))
                case None => summary.updated (mrid, map)
            }
        }

        def combop (a: Summary, b: Summary): Summary =
        {
            b.foldLeft (a)(gather)
        }

        events.aggregate (Map [mRID, Total]())(seqop, combop)
    }

    def severities (total: Summary): Total =
    {
        total.foldLeft (Map [Color, Count]())((map: Total, tot: Sum) => tot._2.foldLeft (map)(accumulate))
    }

    def summary (arg: (Transformer, Day, Iterable[(Type, mRID, Severity)])):
    (Transformer, Day, Summary, Total, Summary, Total, Summary, Total) =
    {
        val (transformer, day, records) = arg

        val voltage_events: Iterable[(mRID, Severity)] = records.filter (_._1 == "voltage").map (event => (event._2, event._3))
        val voltage_summary: Summary = aggregate (voltage_events)
        val voltage_totals: Total = severities (voltage_summary)

        val current_events: Iterable[(mRID, Severity)] = records.filter (_._1 == "current").map (event => (event._2, event._3))
        val current_summary: Summary = aggregate (current_events)
        val current_totals: Total = severities (current_summary)

        val power_events: Iterable[(mRID, Severity)] = records.filter (_._1 == "power").map (event => (event._2, event._3))
        val power_summary: Summary = aggregate (power_events)
        val power_totals: Total = severities (power_summary)

        (transformer, day, voltage_summary, voltage_totals, current_summary, current_totals, power_summary, power_totals)
    }

    @SuppressWarnings (Array ("org.wartremover.warts.TraversableOps"))
    def normalize (arg: Iterable[(Transformer, Day, Type, mRID, Severity)]): (Transformer, Day, Iterable[(Type, mRID, Severity)]) =
    {
        val head = arg.head
        (head._1, head._2, arg.map (event => (event._3, event._4, event._5)))
    }

    def toUDT (total: Total): EventNumber =
    {
        val orange = total.getOrElse ("orange", 0)
        val red = total.getOrElse ("red", 0)
        EventNumber (orange, red)
    }

    def asUDT (summary: Summary): Map[mRID, EventNumber] =
    {
        summary.mapValues (toUDT)
    }

    def summarize ()(implicit access: SimulationCassandraAccess): Unit =
    {
        // get the events - a mix of "voltage" "current" and "power" types
        val power_events = getEvents

        // get the recorders - a mix of "voltage" "current" and "power" types
        val recorders = getRecorders

        // join on mrid and type
        val values = power_events.join (recorders, Seq ("mrid", "type"))

        // aggregate over transformer & day
        if (!values.isEmpty)
        {
            val transformer = values.schema.fieldIndex ("transformer")
            val date = values.schema.fieldIndex ("date")
            val `type` = values.schema.fieldIndex ("type")
            val mrid = values.schema.fieldIndex ("mrid")
            val severity = values.schema.fieldIndex ("severity")

            val work1: RDD[(Transformer, Day, Type, mRID, Severity)] = values.rdd.map (
                row =>
                {
                    val t = row.getString (transformer)
                    val d = row.getDate (date)
                    val typ = row.getString (`type`)
                    (t, d, typ, row.getString (mrid), row.getInt (severity))
                }
            )

            val work2: RDD[Iterable[(Transformer, Day, Type, mRID, Severity)]] = work1.groupBy (row => s"${row._1}_${row._2.getTime.toString}").values
            val work3: RDD[(Transformer, Day, Iterable[(Type, mRID, Severity)])] = work2.map (normalize)
            val work4: RDD[(Transformer, Day, Summary, Total, Summary, Total, Summary, Total)] = work3.map (summary)

            val records = work4.map (
                x =>
                    (access.simulation, x._1, x._2, asUDT (x._3), toUDT (x._4), asUDT (x._5), toUDT (x._6), asUDT (x._7), toUDT (x._8))
            )

            val columns = SomeColumns ("simulation", "mrid", "day", "consumer", "consumer_total", "linesegments", "linesegments_total", "transformer", "transformer_total")
            val configuration = WriteConf.fromSparkConf (spark.sparkContext.getConf).copy (consistencyLevel = ConsistencyLevel.ANY)
            records.saveToCassandra (access.output_keyspace, "simulation_event_summary", columns, configuration)
        }
    }
}

/**
 * Find significant events in the simulation.
 *
 * Scan the results after running a simulation to locate various threshold events.
 *
 * @param triggers The list of trigger thresholds to check for.
 * @param spark    The Spark session
 * @param options  The simulation options. Note: Currently only the verbose and storage_level options are used.
 */
case class SimulationEvents (triggers: Iterable[Trigger])(spark: SparkSession, options: SimulationOptions)
    extends SimulationPostProcessor (spark, options)
{
    if (options.verbose) org.apache.log4j.LogManager.getLogger (getClass.getName).setLevel (org.apache.log4j.Level.INFO)
    val log: Logger = LoggerFactory.getLogger (getClass)

    def run (implicit access: SimulationCassandraAccess): Unit =
    {
        // organize the triggers by type, table, reference and default
        val sets = triggers.groupBy (trigger => (trigger.`type`, trigger.reference, trigger.default)).toArray
        log.info (s"checking for events in ${access.simulation} (input keyspace: ${access.input_keyspace}, output keyspace: ${access.output_keyspace})")

        // handle each trigger set
        for (((typ, ref, default), thresholds) <- sets)
        {
            log.info (s"$typ events with reference $ref and default $default")
            DoubleChecker (spark, options.cim_options.storage, options.three_phase).checkFor (thresholds)
            log.info (s"$typ deviation events saved to ${access.output_keyspace}.simulation_event")
        }

        // perform the summary
        log.info ("summarizing events")
        Summarizer (spark, options.cim_options.storage).summarize ()
        log.info (s"event summary saved to ${access.output_keyspace}.simulation_event_summary")
    }
}

/**
 * Parser for "event" post processor.
 */
object SimulationEvents extends SimulationPostProcessorParser
{
    val STANDARD_TRIGGERS: Iterable[Trigger] = List [Trigger](
        // voltage exceeds ±10% of nominal = red, voltage exceeds ±6%=orange
        HighTrigger ("voltage", 1, "ratedVoltage", 400.0, 1.06, 15 * 60 * 1000),
        LowTrigger ("voltage", 1, "ratedVoltage", 400.0, 0.94, 15 * 60 * 1000),
        HighTrigger ("voltage", 2, "ratedVoltage", 400.0, 1.10, 15 * 60 * 1000),
        LowTrigger ("voltage", 2, "ratedVoltage", 400.0, 0.90, 15 * 60 * 1000),

        // current >75% and >14h within 24h = orange
        // current >90% and >3h within 24h = red
        // current >110% for 15 minutes or more = red
        HighTrigger ("current", 1, "ratedCurrent", 100.0, 0.75, 14 * 60 * 60 * 1000),
        HighTrigger ("current", 2, "ratedCurrent", 100.0, 0.90, 3 * 60 * 60 * 1000),
        HighTrigger ("current", 2, "ratedCurrent", 100.0, 1.10, 15 * 60 * 1000),

        // power >75% and >14h within 24h = orange
        // power >90% and >3h within 24h = red
        // power >110% for 15 minutes or more = red
        HighTrigger ("power", 1, "ratedS", 630.0, 0.75, 14 * 60 * 60 * 1000),
        HighTrigger ("power", 2, "ratedS", 630.0, 0.90, 3 * 60 * 60 * 1000),
        HighTrigger ("power", 2, "ratedS", 630.0, 1.10, 15 * 60 * 1000)
    )


    def cls: String = "event"

    /**
     * Generates a JSON parser to populate a processor.
     *
     * @return A method that will return an instance of a post processor given the postprocessing element of a JSON.
     */
    def parser (): JsonObject => (SparkSession, SimulationOptions) => SimulationPostProcessor =
        post =>
        {
            val triggers = if (post.containsKey ("thresholds"))
            {
                val value = post.get ("thresholds")
                if (value.getValueType == JsonValue.ValueType.ARRAY)
                {
                    val post_processors: Seq[Option[Trigger]] = value
                        .asJsonArray
                        .asScala
                        .map
                        {
                            case threshold: JsonObject =>
                                val trigger = threshold.getString ("trigger", "high")
                                val `type` = threshold.getString ("type", "voltage")
                                val severity = threshold.getInt ("severity", 1)
                                val reference = threshold.getString ("reference", "ratedS")
                                val default = threshold.getJsonNumber ("default").doubleValue ()
                                val ratio = threshold.getJsonNumber ("ratio").doubleValue ()
                                val duration = threshold.getInt ("duration", 900000)
                                trigger match
                                {
                                    case "high" =>
                                        val hi: Trigger = HighTrigger (`type`, severity, reference, default, ratio, duration)
                                        Some (hi)
                                    case "low" =>
                                        val lo: Trigger = LowTrigger (`type`, severity, reference, default, ratio, duration)
                                        Some (lo)
                                    case _ => None
                                }
                            case _ =>
                                // ToDo: log.error
                                None
                        }
                    post_processors.flatten
                }
                else
                    STANDARD_TRIGGERS
            }
            else
                STANDARD_TRIGGERS

            SimulationEvents (triggers)(_: SparkSession, _: SimulationOptions)
        }
}
