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

abstract class Trigger (val `type`: String = "", val severity: Int = 0, val ratio: Double = 0.0, val duration: Int = 0) extends Serializable
{
    def comparator (nominal: Double): Double ⇒ Boolean
    def message (millis: Int): String
}

case class HighTrigger (
        override val `type`: String,
        override val severity: Int,
        override val ratio: Double,
        override val duration: Int)
    extends Trigger (`type`, severity, ratio, duration)
{
    def hi (threshold: Double)(value:Double): Boolean = value > threshold
    def comparator (nominal: Double): Double ⇒ Boolean = hi (ratio * nominal)
    def message (millis: Int): String = "%s exceeds %s%% threshold for %s milliseconds".format (`type`, ratio * 100.0, millis)
}

case class LowTrigger (
        override val `type`: String,
        override val severity: Int,
        override val ratio: Double,
        override val duration: Int)
    extends Trigger (`type`, severity, ratio, duration)
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

    def logInfo (msg: => String): Unit = if (log.isInfoEnabled && options.unittest) log.info (msg)

    def show (dataframe: DataFrame, records: Int = 5): Unit = if (options.unittest) dataframe.show (records)

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
    val VOLTAGETHRESHOLD1 = HighTrigger ("voltage", 1, 1.06, 15 * 60 * 1000)
    val VOLTAGETHRESHOLD2 =  LowTrigger ("voltage", 1, 0.94, 15 * 60 * 1000)
    val VOLTAGETHRESHOLD3 = HighTrigger ("voltage", 2, 1.10, 15 * 60 * 1000)
    val VOLTAGETHRESHOLD4 =  LowTrigger ("voltage", 2, 0.90, 15 * 60 * 1000)

    // current >75% and >14h within 24h = orange
    // current >90% and >3h within 24h = red
    // current >110% for 15 minutes or more = red
    val CURRENTTHRESHOLD1 = HighTrigger ("current", 1, 0.75, 14 * 60 * 60 * 1000)
    val CURRENTTHRESHOLD2 = HighTrigger ("current", 2, 0.90,  3 * 60 * 60 * 1000)
    val CURRENTTHRESHOLD3 = HighTrigger ("current", 2, 1.10,      15 * 60 * 1000)

    // power >75% and >14h within 24h = orange
    // power >90% and >3h within 24h = red
    // power >110% for 15 minutes or more = red
    val POWERTHRESHOLD1 = HighTrigger ("power", 1, 0.75, 14 * 60 * 60 * 1000)
    val POWERTHRESHOLD2 = HighTrigger ("power", 2, 0.90,  3 * 60 * 60 * 1000)
    val POWERTHRESHOLD3 = HighTrigger ("power", 2, 1.10,      15 * 60 * 1000)

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
            val voltage = values(i)._3
            checkers.foreach (x ⇒ x.next (ltime, period, voltage))
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

    def save (access: SimulationCassandraAccess, events: RDD[Event]): Unit =
    {
        events.saveToCassandra (access.output_keyspace, "simulation_event",
            SomeColumns ("simulation", "mrid", "type", "severity", "start_time", "end_time"))
    }

    def voltageCheck (access: SimulationCassandraAccess, thresholds: Array[Trigger]): Unit =
    {
        def magnitude[Type_x: TypeTag, Type_y: TypeTag] = udf [Double, Double, Double]((x: Double, y: Double) => Math.sqrt (x * x + y * y))

        log.info ("Voltage events")
        val typ = "voltage"
        val simulated_voltages = access.raw_values (typ).persist (options.storage_level)
        logInfo ("""Voltage quality: %d simulation voltages to process""".format (simulated_voltages.count))
        show (simulated_voltages)

        val points = access.geojson_points

        def getNominalVoltage[Type_x: TypeTag] = udf [Double, Map[String, String]]((map: Map[String, String]) => map.getOrElse ("nominalVoltage", "400.0").toDouble)

        val voltages = simulated_voltages
            .withColumn ("voltage", magnitude [Double, Double].apply (simulated_voltages ("real_a"), simulated_voltages ("imag_a")))
            .drop ("real_a", "imag_a")
            .join (
                points,
                Seq ("mrid"))
        val nominalVoltages = voltages
            .withColumn ("nominalVoltage", getNominalVoltage [Map[String, String]].apply (voltages ("properties")))
            .drop ("transformer", "properties")
            .persist (options.storage_level)

        val mrid = nominalVoltages.schema.fieldIndex ("mrid")
        val period = nominalVoltages.schema.fieldIndex ("period")
        val time = nominalVoltages.schema.fieldIndex ("time")
        val voltage = nominalVoltages.schema.fieldIndex ("voltage")
        val nominalVoltage = nominalVoltages.schema.fieldIndex ("nominalVoltage")

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
            nominalVoltages.filter (filterFor (highs, typ, "nominalVoltage"))
                .rdd.map (row ⇒ (row.getString (mrid), row.getTimestamp (time), row.getInt (period), row.getDouble (voltage), row.getDouble (nominalVoltage))).persist (options.storage_level)
                .groupBy (_._1).values.flatMap (check (access.simulation, highs)).persist (options.storage_level)
        else
            spark.sparkContext.emptyRDD[Event]

        val lowEvents = if (0 < lows.length)
            nominalVoltages.filter (filterFor (lows, typ, "nominalVoltage"))
                .rdd.map (row ⇒ (row.getString (mrid), row.getTimestamp (time), row.getInt (period), row.getDouble (voltage), row.getDouble (nominalVoltage))).persist (options.storage_level)
                .groupBy (_._1).values.flatMap (check (access.simulation, lows)).persist (options.storage_level)
        else
            spark.sparkContext.emptyRDD[Event]

        save (access, highEvents.union (lowEvents))
        log.info ("""voltageCheck: voltage deviation records saved to %s.simulation_event""".format (access.output_keyspace))
    }

    def currentCheck (access: SimulationCassandraAccess, thresholds: Array[Trigger]): Unit =
    {
        log.info ("Current events")
        val typ = "current"
        val simulated_current_values = access.raw_values (typ).persist (options.storage_level)
        logInfo ("""Current limit: %d simulated current values to process""".format (simulated_current_values.count))
        show (simulated_current_values)

        val lines = access.geojson_lines

        def magnitude[Type_x: TypeTag, Type_y: TypeTag] = udf [Double, Double, Double]((x: Double, y: Double) => Math.sqrt (x * x + y * y))

        def maxCurrent[Type_x: TypeTag] = udf [Double, Map[String, String]]((map: Map[String, String]) => map.getOrElse ("ratedCurrent", "1.0").toDouble)

        val cables = simulated_current_values
            .withColumn ("current", magnitude [Double, Double].apply (simulated_current_values ("real_a"), simulated_current_values ("imag_a")))
            .drop ("real_a", "imag_a")
            .join (
                lines,
                Seq ("mrid"))
        val ratedCables = cables
            .withColumn ("ratedCurrent", maxCurrent [Map[String, String]].apply (cables ("properties")))
            .drop ("transformer", "properties")
            .persist (options.storage_level)

        val mrid = ratedCables.schema.fieldIndex ("mrid")
        val period = ratedCables.schema.fieldIndex ("period")
        val time = ratedCables.schema.fieldIndex ("time")
        val current = ratedCables.schema.fieldIndex ("current")
        val ratedCurrent = ratedCables.schema.fieldIndex ("ratedCurrent")

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
            ratedCables.filter (filterFor (highs, typ, "ratedCurrent"))
                .rdd.map (row ⇒ (row.getString (mrid), row.getTimestamp (time), row.getInt (period), row.getDouble (current), row.getDouble (ratedCurrent))).persist (options.storage_level)
                .groupBy (_._1).values.flatMap (check (access.simulation, highs)).persist (options.storage_level)
        else
            spark.sparkContext.emptyRDD[Event]

        val lowEvents = if (0 < lows.length)
            ratedCables.filter (filterFor (lows, typ, "ratedCurrent"))
                .rdd.map (row ⇒ (row.getString (mrid), row.getTimestamp (time), row.getInt (period), row.getDouble (current), row.getDouble (ratedCurrent))).persist (options.storage_level)
                .groupBy (_._1).values.flatMap (check (access.simulation, lows)).persist (options.storage_level)
        else
            spark.sparkContext.emptyRDD[Event]

        save (access, highEvents.union (lowEvents))
        log.info ("""currentCheck: overcurrent records saved to %s.simulation_event""".format (access.output_keyspace))
    }

    def powerCheck (access: SimulationCassandraAccess, thresholds: Array[Trigger]): Unit =
    {
        log.info ("Power events")
        val typ = "power"
        val simulated_power_values = access.raw_values (typ).persist (options.storage_level)
        logInfo ("""Power limit: %d simulated power values to process""".format (simulated_power_values.count))
        show (simulated_power_values)

        val polygons = access.geojson_polygons

        def magnitude[Type_x: TypeTag, Type_y: TypeTag] = udf [Double, Double, Double]((x: Double, y: Double) => Math.sqrt (x * x + y * y))

        def maxPower[Type_x: TypeTag] = udf [Double, Map[String, String]]((map: Map[String, String]) => map.getOrElse ("ratedS", "1.0").toDouble)

        val trafos = simulated_power_values
            .withColumn ("power", magnitude [Double, Double].apply (simulated_power_values ("real_a"), simulated_power_values ("imag_a")))
            .drop ("real_a", "imag_a")
            .join (
                polygons,
                Seq ("mrid"))
        val ratedTrafos = trafos
            .withColumn ("ratedS", maxPower [Map[String, String]].apply (trafos ("properties")))
            .drop ("properties")
            .persist (options.storage_level)

        val mrid = ratedTrafos.schema.fieldIndex ("mrid")
        val period = ratedTrafos.schema.fieldIndex ("period")
        val time = ratedTrafos.schema.fieldIndex ("time")
        val power = ratedTrafos.schema.fieldIndex ("power")
        val ratedS = ratedTrafos.schema.fieldIndex ("ratedS")

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
            ratedTrafos.filter (filterFor (highs, typ, "ratedS"))
                .rdd.map (row ⇒ (row.getString (mrid), row.getTimestamp (time), row.getInt (period), row.getDouble (power), row.getDouble (ratedS))).persist (options.storage_level)
                .groupBy (_._1).values.flatMap (check (access.simulation, highs)).persist (options.storage_level)
        else
            spark.sparkContext.emptyRDD[Event]

        val lowEvents = if (0 < lows.length)
            ratedTrafos.filter (filterFor (lows, typ, "ratedS"))
                .rdd.map (row ⇒ (row.getString (mrid), row.getTimestamp (time), row.getInt (period), row.getDouble (power), row.getDouble (ratedS))).persist (options.storage_level)
                .groupBy (_._1).values.flatMap (check (access.simulation, lows)).persist (options.storage_level)
        else
            spark.sparkContext.emptyRDD[Event]

        save (access, highEvents.union (lowEvents))
        log.info ("""powerCheck: overpower records saved to %s.simulation_event""".format (access.output_keyspace))
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
                    val access = SimulationCassandraAccess (spark, id, input, output, options.verbose, options.unittest)
                    log.info ("""checking for events in %s (input keyspace: %s, output keyspace: %s)""".format (access.simulation, access.input_keyspace, access.output_keyspace))
                    voltageCheck (access, Array (VOLTAGETHRESHOLD1, VOLTAGETHRESHOLD2, VOLTAGETHRESHOLD3, VOLTAGETHRESHOLD4))
                    currentCheck (access, Array (CURRENTTHRESHOLD1, CURRENTTHRESHOLD2, CURRENTTHRESHOLD3))
                    powerCheck (access, Array (POWERTHRESHOLD1, POWERTHRESHOLD2, POWERTHRESHOLD3))
                case None ⇒
                    log.error ("""simulation %s not found in keyspaces (%s)""".format (simulation, lookup.map (_._2).mkString (",")))
            }
        }

    }
}
