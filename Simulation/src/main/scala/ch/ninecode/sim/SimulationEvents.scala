package ch.ninecode.sim

import java.sql.Date
import java.sql.Timestamp

import javax.json.JsonObject

import scala.reflect.runtime.universe.TypeTag

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.udf
import org.apache.spark.sql.types.DateType
import org.apache.spark.storage.StorageLevel
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import com.datastax.driver.core.ConsistencyLevel
import com.datastax.driver.mapping.annotations.UDT
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

@UDT(name="event_number")
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
        events.unpersist (false)
        ret
    }

    def getRecorders (implicit access: SimulationCassandraAccess): DataFrame =
    {
        val recorders = access.recorders
        val ret = recorders
            .drop ("name", "aggregations", "interval", "property", "unit")
        recorders.unpersist (false)
        ret
    }

    def isEmpty (dataframe: DataFrame): Boolean =
    {
        0 == dataframe.take (1).length
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

    def accumulate (severities: Total, severity: Sev):  Total =
    {
        val color = severity._1
        val total = severity._2
        severities.get (color) match
        {
            case Some (current: Int) ⇒ severities.updated (color, current + total)
            case None ⇒ severities.updated (color, total)
        }
    }

    def aggregate (events: Iterable[(mRID, Severity)]): Summary =
    {
        def seqop (sum: Summary, event: (mRID, Severity)): Summary =
        {
            val mrid = event._1
            val color = event._2 match { case 1 ⇒ "red" case 2 ⇒ "orange" case _ ⇒ "unknown" }
            sum.get (mrid) match
            {
                case Some (total: Total) ⇒ sum.updated (mrid, accumulate (total, (color, 1)))
                case None ⇒                sum.updated (mrid, Map[Color, Count] (color → 1))
            }
        }
        def combop (a: Summary, b: Summary): Summary =
        {
            b.foldLeft (a) (
                (summary: Summary, sum: Sum) ⇒
                {
                    val mrid: mRID = sum._1
                    val map: Total = sum._2
                    summary.get (mrid) match
                    {
                        case Some (total: Total) ⇒ summary.updated (mrid, map.foldLeft (total)(accumulate))
                        case None ⇒                summary.updated (mrid, map)
                    }
                }

            )
        }
        events.aggregate (Map[mRID, Total] ()) (seqop, combop)
    }

    def severities (total: Summary): Total =
    {
        total.foldLeft (Map[Color, Count] ()) ((map: Total, tot: Sum) ⇒ tot._2.foldLeft (map) (accumulate))
    }

    def summary (transformer: Transformer, day: Day, records: Iterable[(Type, mRID, Severity)]):
        (Transformer, Day, Summary, Total, Summary, Total, Summary, Total) =
    {
        val voltage_events: Iterable[(mRID, Severity)] = records.filter (_._1 == "voltage").map (event ⇒ (event._2, event._3))
        val voltage_summary: Summary = aggregate (voltage_events)
        val voltage_totals: Total = severities (voltage_summary)

        val current_events: Iterable[(mRID, Severity)] = records.filter (_._1 == "current").map (event ⇒ (event._2, event._3))
        val current_summary: Summary = aggregate (current_events)
        val current_totals: Total = severities (current_summary)

        val power_events: Iterable[(mRID, Severity)] = records.filter (_._1 == "power").map (event ⇒ (event._2, event._3))
        val power_summary: Summary = aggregate (power_events)
        val power_totals: Total = severities (power_summary)

        (transformer, day, voltage_summary, voltage_totals, current_summary, current_totals, power_summary, power_totals)
    }

    def summarize () (implicit access: SimulationCassandraAccess): Unit =
    {
        // get the events - a mix of "voltage" "current" and "power" types
        val power_events = getEvents

        // get the recorders - a mix of "voltage" "current" and "power" types
        val recorders = getRecorders

        // join on mrid and type
        val values = power_events.join (recorders, Seq ("mrid", "type"))

        // aggregate over transformer & day
        if (!isEmpty (values)) // ToDo: change to Dataset.isEmpty when Spark version > 2.4.0
        {
            val transformer = values.schema.fieldIndex ("transformer")
            val date = values.schema.fieldIndex ("date")
            val `type` = values.schema.fieldIndex ("type")
            val mrid = values.schema.fieldIndex ("mrid")
            val severity = values.schema.fieldIndex ("severity")

            val work1: RDD[(Transformer, Day, Type, mRID, Severity)] = values.rdd.map (
                row ⇒
                {
                    val t = row.getString (transformer)
                    val d = row.getDate (date)
                    val typ = row.getString (`type`)
                    (t, d, typ, row.getString (mrid), row.getInt (severity))
                }
            )

            val work2: RDD[Iterable[(Transformer, Day, Type, mRID, Severity)]] = work1.groupBy (row ⇒ row._1 + "_" + row._2.getTime).values
            val work3: RDD[(Transformer, Day, Iterable[(Type, mRID, Severity)])] = work2.map (record ⇒ (record.head._1, record.head._2, record.map (event ⇒ (event._3, event._4, event._5))))
            val work4: RDD[(Transformer, Day, Summary, Total, Summary, Total, Summary, Total)] = work3.map (record ⇒ summary (record._1, record._2, record._3))

            def toUDT (total: Total): EventNumber =
            {
                val orange = total.getOrElse("orange", 0)
                val red = total.getOrElse("red", 0)
                EventNumber (orange, red)
            }

            val records = work4.map (x ⇒
                (
                    access.simulation,
                    x._1,
                    x._2,
                    x._3.mapValues (toUDT),
                    toUDT (x._4),
                    x._5.mapValues (toUDT),
                    toUDT (x._6),
                    x._7.mapValues (toUDT),
                    toUDT (x._8)
                )
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

        // handle each trigger set
        for (i ← sets.indices)
        {
            val thresholds = sets(i)
            val `type` = thresholds.head.`type`
            log.info ("""%s events""".format (`type`))
            DoubleChecker (spark, options.storage_level).checkFor (thresholds)
            log.info ("""%s deviation events saved to %s.simulation_event""".format (`type`, access.output_keyspace))
        }

        // perform the summary
        log.info ("""summarizing events""")
        Summarizer (spark, options.storage_level).summarize ()
        log.info ("""event summary saved to %s.simulation_event_summary""".format (access.output_keyspace))
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
