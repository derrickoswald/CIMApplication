package ch.ninecode.sim

import java.sql.Timestamp

import scala.reflect.runtime.universe.TypeTag
import com.datastax.driver.core.ResultSet
import com.datastax.spark.connector._
import com.datastax.spark.connector.SomeColumns
import com.datastax.spark.connector.cql.CassandraConnector
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.udf
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Event trigger.
 *
 * Base class that contains details about the condition that triggers an event.
 *
 * @param `type` The type of value, which corresponds to the <code>type</code> column of the simulated_value Cassandra table.
 * @param severity The severity of the event.
 * @param table The name of the geojson_XXX Cassandra table.
 * @param reference The reference value, which corresponds to the property in the <code>properties</code> column of the geojson_XXX Cassandra table.
 * @param default The reference default value if it is not found in the <code>properties</code> column. (same units as simulated_value records for the type)
 * @param ratio The ratio between the value and reference required to trigger the event.
 * @param duration The continuous duration that the ratio must hold for the event to be registered. (mSec)
 */
abstract class Trigger (
        val `type`: String,
        val severity: Int,
        val table: String,
        val reference: String,
        val default: Double,
        val ratio: Double,
        val duration: Int)
    extends Serializable
{
    /**
     * Required zero arg constructor for serializability.
     */
    def this () = this ("", 0, "", "", 0.0, 0.0, 0)

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
 * @param table The name of the geojson_XXX Cassandra table.
 * @param reference The reference value, which corresponds to the property in the <code>properties</code> column of the geojson_XXX Cassandra table.
 * @param default The reference default value if it is not found in the <code>properties</code> column. (same units as simulated_value records for the type)
 * @param ratio The ratio between the value and reference above which the event will be triggered.
 * @param duration The continuous duration that the ratio must be exceeded for the event to be registered (mSec).
 */
case class HighTrigger (
        override val `type`: String,
        override val severity: Int,
        override val table: String,
        override val reference: String,
        override val default: Double,
        override val ratio: Double,
        override val duration: Int)
    extends Trigger (`type`, severity, table, reference, default, ratio, duration)
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
 * @param table The name of the geojson_XXX Cassandra table.
 * @param reference The reference value, which corresponds to the property in the <code>properties</code> column of the geojson_XXX Cassandra table.
 * @param default The reference default value if it is not found in the <code>properties</code> column. (same units as simulated_value records for the type)
 * @param ratio The ratio between the value and reference below which the event will be triggered.
 * @param duration The continuous duration that the ratio must be subceeded for the event to be registered (mSec).
 */
case class LowTrigger (
        override val `type`: String,
        override val severity: Int,
        override val table: String,
        override val reference: String,
        override val default: Double,
        override val ratio: Double,
        override val duration: Int)
    extends Trigger (`type`, severity, table, reference, default, ratio, duration)
{
    def lo (threshold: Double)(value:Double): Boolean = value < threshold
    def comparator (nominal: Double): Double ⇒ Boolean = lo (ratio * nominal)
    def message (millis: Int): String = "%s subceeds %s%% threshold for %s milliseconds".format (`type`, ratio * 100.0, millis)
}

/**
 * Find significant events in the simulation.
 *
 * Scan the results after running a simulation to locate various threshold events.
 *
 * @param spark   The Spark session
 * @param options The simulation options. Note: Currently only the verbose option is used.
 */
case class SimulationEvents (spark: SparkSession, options: SimulationOptions)
{
    if (options.verbose) org.apache.log4j.LogManager.getLogger (getClass.getName).setLevel (org.apache.log4j.Level.INFO)
    val log: Logger = LoggerFactory.getLogger (getClass)

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

    // database schema
    //          simulation mrid   type  severity, start_time end_time
    type Event = (String, String, String, Int, Timestamp, Timestamp)

    // voltage exceeds ±10% of nominal = red, voltage exceeds ±6%=orange
    val VOLTAGETHRESHOLD1 = HighTrigger ("voltage", 1, "geojson_points", "ratedVoltage", 400.0, 1.06, 15 * 60 * 1000)
    val VOLTAGETHRESHOLD2 =  LowTrigger ("voltage", 1, "geojson_points", "ratedVoltage", 400.0, 0.94, 15 * 60 * 1000)
    val VOLTAGETHRESHOLD3 = HighTrigger ("voltage", 2, "geojson_points", "ratedVoltage", 400.0, 1.10, 15 * 60 * 1000)
    val VOLTAGETHRESHOLD4 =  LowTrigger ("voltage", 2, "geojson_points", "ratedVoltage", 400.0, 0.90, 15 * 60 * 1000)

    // current >75% and >14h within 24h = orange
    // current >90% and >3h within 24h = red
    // current >110% for 15 minutes or more = red
    val CURRENTTHRESHOLD1 = HighTrigger ("current", 1, "geojson_lines", "ratedCurrent", 100.0, 0.75, 14 * 60 * 60 * 1000)
    val CURRENTTHRESHOLD2 = HighTrigger ("current", 2, "geojson_lines", "ratedCurrent", 100.0, 0.90,  3 * 60 * 60 * 1000)
    val CURRENTTHRESHOLD3 = HighTrigger ("current", 2, "geojson_lines", "ratedCurrent", 100.0, 1.10,      15 * 60 * 1000)

    // power >75% and >14h within 24h = orange
    // power >90% and >3h within 24h = red
    // power >110% for 15 minutes or more = red
    val POWERTHRESHOLD1 = HighTrigger ("power", 1, "geojson_polygons", "ratedS", 630.0, 0.75, 14 * 60 * 60 * 1000)
    val POWERTHRESHOLD2 = HighTrigger ("power", 2, "geojson_polygons", "ratedS", 630.0, 0.90,  3 * 60 * 60 * 1000)
    val POWERTHRESHOLD3 = HighTrigger ("power", 2, "geojson_polygons", "ratedS", 630.0, 1.10,      15 * 60 * 1000)

    class Checker (simulation: String, mrid: String, trigger: Trigger, limit: Double)
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
                            ret = (simulation, mrid, trigger.message (trigger.duration - timeout), trigger.severity, new Timestamp (start), new Timestamp (end)) :: ret
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
                    ret = (simulation, mrid, trigger.message (trigger.duration - timeout), trigger.severity, new Timestamp (start), new Timestamp (end)) :: ret
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
                ret = (simulation, mrid, trigger.message (trigger.duration - timeout), trigger.severity, new Timestamp (start), new Timestamp (end)) :: ret
                start = 0L // don't do it again
                end = 0L
                timeout = Int.MaxValue
            }
            ret
        }
    }

    def check (simulation: String, thresholds: Array[_ <: Trigger]) (stuff: Iterable[(String, Timestamp, Int, Double, Double)]): Iterable[Event] =
    {
        // all the mRID and threshold values are the same
        val mrid = stuff.head._1
        val threshold = stuff.head._5

        // pare down the data and sort by time
        val values = stuff.map (x ⇒ (x._2, x._3, x._4)).toArray.sortWith (_._1.getTime < _._1.getTime)

        val checkers = thresholds.map (x ⇒ new Checker (simulation, mrid, x, threshold))
        for (i ← values.indices)
        {
            val time = values(i)._1
            val ltime = time.getTime
            val period = values(i)._2
            val value = values(i)._3
            checkers.foreach (x ⇒ x.next (ltime, period, value))
        }
        checkers.flatMap (_.gimme)
    }

    def filterFor (thresholds: Array[_ <: Trigger], column: String, nominal: String): String =
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

    def save (events: RDD[Event]) (implicit access: SimulationCassandraAccess): Unit =
    {
        events.saveToCassandra (access.output_keyspace, "simulation_event",
            SomeColumns ("simulation", "mrid", "type", "severity", "start_time", "end_time"))
    }

    def checkFor (thresholds: Array[Trigger]) (implicit access: SimulationCassandraAccess) : Unit =
    {
        // all the threshold types, geometry tables and reference are the same
        val `type` = thresholds(0).`type`
        val table = thresholds(0).table
        val reference = thresholds(0).reference
        val default = thresholds(0).default.toString

        def magnitude[Type_x: TypeTag, Type_y: TypeTag] = udf [Double, Double, Double]((x: Double, y: Double) => Math.sqrt (x * x + y * y))
        def getReference[Type_x: TypeTag] = udf [Double, Map[String, String]]((map: Map[String, String]) => map.getOrElse (reference, default).toDouble)

        log.info ("""%s events""".format (`type`))
        val simulated_values = access.raw_values (`type`).persist (options.storage_level)
        val geometries = access.geojson (table)

        val values = simulated_values
            .withColumn ("value", magnitude [Double, Double].apply (simulated_values ("real_a"), simulated_values ("imag_a")))
            .drop ("real_a", "imag_a")
            .join (
                geometries,
                Seq ("mrid"))
        val augmentedvalues = values
            .withColumn ("reference", getReference [Map[String, String]].apply (values ("properties")))
            .drop ("transformer", "properties")

        val mrid = augmentedvalues.schema.fieldIndex ("mrid")
        val period = augmentedvalues.schema.fieldIndex ("period")
        val time = augmentedvalues.schema.fieldIndex ("time")
        val value = augmentedvalues.schema.fieldIndex ("value")
        val ref = augmentedvalues.schema.fieldIndex ("reference")

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

        val highEvents = if (0 < highs.length)
            augmentedvalues.filter (filterFor (highs, "value", "reference"))
                .rdd.map (row ⇒ (row.getString (mrid), row.getTimestamp (time), row.getInt (period), row.getDouble (value), row.getDouble (ref)))
                .groupBy (_._1).values.flatMap (check (access.simulation, highs)).persist (options.storage_level)
        else
            spark.sparkContext.emptyRDD[Event]

        val lowEvents = if (0 < lows.length)
            augmentedvalues.filter (filterFor (lows, "value", "reference"))
                .rdd.map (row ⇒ (row.getString (mrid), row.getTimestamp (time), row.getInt (period), row.getDouble (value), row.getDouble (ref)))
                .groupBy (_._1).values.flatMap (check (access.simulation, lows)).persist (options.storage_level)
        else
            spark.sparkContext.emptyRDD[Event]

        save (highEvents.union (lowEvents))
        log.info ("""%s deviation events saved to %s.simulation_event""".format (`type`, access.output_keyspace))
    }

    def run (simulations: Seq[String]): Unit =
    {
        val sql = "select keyspace_name from system_schema.tables where table_name = 'simulation' allow filtering"
        val keyspaces = CassandraConnector (spark.sparkContext.getConf).withSessionDo (
            session =>
            {
                val results: ResultSet = session.execute (sql)
                val iter = results.iterator ()
                var list = List[String]()
                while (iter.hasNext)
                {
                    val row = iter.next
                    list = row.getString (0) :: list
                }
                list
            }
        )
        log.info ("""found keyspaces %s""".format (keyspaces.mkString (",")))

        val lookup = keyspaces.flatMap (
            keyspace ⇒
            {
                spark
                    .read
                    .format ("org.apache.spark.sql.cassandra")
                    .options (Map ("table" -> "simulation", "keyspace" -> keyspace))
                    .load
                    .select ("id", "input_keyspace", "output_keyspace")
                    .rdd
                    .map (row ⇒ (row.getString (0), row.getString (1), row.getString (2)))
                    .collect
            }
        )

        for (simulation ← simulations)
        {
            val found = lookup.find (_._1 == simulation)
            found match
            {
                case Some ((id, input, output)) ⇒
                    implicit val access: SimulationCassandraAccess =
                        SimulationCassandraAccess (spark, options.storage_level, id, input, output, options.verbose, options.unittest)
                    log.info ("""checking for events in %s (input keyspace: %s, output keyspace: %s)""".format (access.simulation, access.input_keyspace, access.output_keyspace))
                    checkFor (Array (VOLTAGETHRESHOLD1, VOLTAGETHRESHOLD2, VOLTAGETHRESHOLD3, VOLTAGETHRESHOLD4))
                    checkFor (Array (CURRENTTHRESHOLD1, CURRENTTHRESHOLD2, CURRENTTHRESHOLD3))
                    checkFor (Array (POWERTHRESHOLD1, POWERTHRESHOLD2, POWERTHRESHOLD3))
                case None ⇒
                    log.error ("""simulation %s not found in keyspaces (%s)""".format (simulation, lookup.map (_._2).mkString (",")))
            }
        }

    }
}
