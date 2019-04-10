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
    //          simulation mrid   type    start_time end_time
    type Event = (String, String, String, Timestamp, Timestamp)

    // 400V >±6%, >±10%
    val NOMINAL_VOLTAGE = 400.0
    val THRESHOLD1 = 0.06
    val THRESHOLD2 = 0.10

    case class Trigger (ratio: Double, duration: Int)

    // current >75% and >14h within 24h
    // current >90% and >3h within 24h
    // current >110% for 15 minutes or more

    val BOUNDARY1 = Trigger (0.75, 14 * 60 * 60 * 1000)
    val BOUNDARY2 = Trigger (0.90,  3 * 60 * 60 * 1000)
    val BOUNDARY3 = Trigger (1.10,      15 * 60 * 1000)

    // also the same for transformer apparent power
    // power >75% and >14h within 24h
    // power >90% and >3h within 24h
    // power >110% for 15 minutes or more

    class Checker (simulation: String, mrid: String, boundary: Trigger, limit: Double, typ: String)
    {
        var ret: List[Event] = Nil

        val threshold = boundary.ratio * limit // test value
        var start = 0L                         // starting time
        var end = 0L                           // ending time
        var timeout = 0                        // count-down
        lazy val message = "%s exceeds %s%% threshold for %s milliseconds".format (typ, boundary.ratio * 100.0, boundary.duration)

        def next (time: Long, period: Int, current: Double): Unit =
        {
            if (current > threshold)
            {
                if (0L == start)
                {
                    // start counting
                    start = time
                    end = time + period
                    timeout = boundary.duration - period
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
                        if (timeout < 0L)
                            ret = (simulation, mrid, message, new Timestamp (start), new Timestamp (end)) :: ret
                        // start counting again
                        start = time
                        end = time + period
                        timeout = boundary.duration - period
                    }
                }
            }
            else
            {
                // emit an event if the duration has elapsed
                if (timeout < 0L)
                    ret = (simulation, mrid, message, new Timestamp (start), new Timestamp (end)) :: ret
            }
        }

        def gimme: List[Event] =
        {
            // emit an event if the duration has elapsed
            if (0L != start && timeout < 0L)
            {
                ret = (simulation, mrid, message, new Timestamp (start), new Timestamp (end)) :: ret
                start = 0L // don't do it again
                timeout = 0
            }
            ret
        }
    }

    def save (access: SimulationCassandraAccess, events: RDD[Event]): Unit =
    {
        events.saveToCassandra (access.output_keyspace, "simulation_event",
            SomeColumns ("simulation", "mrid", "type", "start_time", "end_time"))
    }

    // voltage exceeds ±10%=red, voltage exceeds ±6%=orange
    def voltageCheck (access: SimulationCassandraAccess, voltage: Double = NOMINAL_VOLTAGE, threshold1: Double = THRESHOLD1, threshold2: Double = THRESHOLD2): Unit =
    {
        def magnitude[Type_x: TypeTag, Type_y: TypeTag] = udf [Double, Double, Double]((x: Double, y: Double) => Math.sqrt (x * x + y * y))

        log.info ("Voltage events")
        val typ = "voltage"
        val simulated_voltages = access.raw_values (typ).persist (options.storage_level)
        logInfo ("""Voltage quality: %d simulation voltages to process""".format (simulated_voltages.count))
        show (simulated_voltages)
        val voltages = simulated_voltages
            .withColumn ("voltage", magnitude [Double, Double].apply (simulated_voltages ("real_a"), simulated_voltages ("imag_a")))
            .drop ("real_a", "imag_a")
            .persist (options.storage_level)

        {
            val voltage_over_threshold = voltages
                .filter ("voltage > %s".format (voltage * (1.0 + threshold1)))

            val mrid = voltage_over_threshold.schema.fieldIndex ("mrid")
            val period = voltage_over_threshold.schema.fieldIndex ("period")
            val time = voltage_over_threshold.schema.fieldIndex ("time")
            val volts = voltage_over_threshold.schema.fieldIndex ("voltage")
            val work = voltage_over_threshold.rdd.map (row ⇒ (access.simulation, row.getString (mrid), row.getTimestamp (time), new Timestamp (row.getTimestamp (time).getTime + row.getInt (period)), row.getDouble (volts))).cache

            // save to Cassandra - split based on second threshold
            val over1 = typ + " exceeds %s%% threshold".format (threshold1 * 100.0)
            val overvoltage1: RDD[Event] = work.filter (_._5 <= voltage * (1.0 + threshold2)).map (x ⇒ (x._1, x._2, over1, x._3, x._4))
            save (access, overvoltage1)
            val over2 = typ + " exceeds %s%% threshold".format (threshold2 * 100.0)
            val overvoltage2: RDD[Event] = work.filter (_._5 > voltage * (1.0 + threshold2)).map (x ⇒ (x._1, x._2, over2, x._3, x._4))
            save (access, overvoltage2)
            log.info ("""voltageCheck: overvoltage records saved to %s.simulation_event""".format (access.output_keyspace))
        }

        {
            val voltage_under_threshold = voltages
                .filter ("voltage < %s".format (voltage * (1.0 - threshold1)))

            val mrid = voltage_under_threshold.schema.fieldIndex ("mrid")
            val period = voltage_under_threshold.schema.fieldIndex ("period")
            val time = voltage_under_threshold.schema.fieldIndex ("time")
            val volts = voltage_under_threshold.schema.fieldIndex ("voltage")
            val work = voltage_under_threshold.rdd.map (row ⇒ (access.simulation, row.getString (mrid), row.getTimestamp (time), new Timestamp (row.getTimestamp (time).getTime + row.getInt (period)), row.getDouble (volts))).cache

            // save to Cassandra - split based on second threshold
            val under1 = typ + " subceeds %s%% threshold".format (threshold1 * 100.0)
            val undervoltage1 = work.filter (_._5 >= voltage * (1.0 - threshold2)).map (x ⇒ (x._1, x._2, under1, x._3, x._4))
            save (access, undervoltage1)
            val under2 = typ + " subceeds %s%% threshold".format (threshold2 * 100.0)
            val undervoltage2 = work.filter (_._5 < voltage * (1.0 - threshold2)).map (x ⇒ (x._1, x._2, under2, x._3, x._4))
            save (access, undervoltage2)
            log.info ("""voltageCheck: undervoltage records saved to %s.simulation_event""".format (access.output_keyspace))
        }
    }

    def currentCheck (access: SimulationCassandraAccess, boundary1: Trigger = BOUNDARY1, boundary2: Trigger = BOUNDARY2, boundary3: Trigger = BOUNDARY3): Unit =
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

        val minratio = if (boundary1.ratio < boundary2.ratio) if (boundary1.ratio < boundary3.ratio) boundary1.ratio else boundary3.ratio else if (boundary2.ratio < boundary3.ratio) boundary2.ratio else boundary3.ratio
        val interesting = ratedCables.filter ("current > (%s * ratedCurrent)".format (minratio))

        val mrid = interesting.schema.fieldIndex ("mrid")
        val period = interesting.schema.fieldIndex ("period")
        val time = interesting.schema.fieldIndex ("time")
        val current = interesting.schema.fieldIndex ("current")
        val ratedCurrent = interesting.schema.fieldIndex ("ratedCurrent")
        val work = interesting.rdd.map (row ⇒ (row.getString (mrid), row.getTimestamp (time), row.getInt (period), row.getDouble (current), row.getDouble (ratedCurrent))).persist (options.storage_level)

        def checkCable (stuff: Iterable[(String, Timestamp, Int, Double, Double)]): Iterable[Event] =
        {
            // all the mRID and currentLimit are the same
            val mrid = stuff.head._1
            val ratedCurrent = stuff.head._5

            // pare down the data and sort by time
            val values = stuff.map (x ⇒ (x._2, x._3, x._4)).toArray.sortWith (_._1.getTime < _._1.getTime)

            val b1 = new Checker (access.simulation, mrid, boundary1, ratedCurrent, typ)
            val b2 = new Checker (access.simulation, mrid, boundary2, ratedCurrent, typ)
            val b3 = new Checker (access.simulation, mrid, boundary3, ratedCurrent, typ)
            for (i ← values.indices)
            {
                val time = values(i)._1
                val ltime = time.getTime
                val period = values(i)._2
                val current = values(i)._3
                b1.next (ltime, period, current)
                b2.next (ltime, period, current)
                b3.next (ltime, period, current)
            }

            b1.gimme ::: b2.gimme ::: b3.gimme
        }

        // group by mRID and check each cable three ways
        val events: RDD[Event] = work.groupBy (_._1).values.flatMap (checkCable).persist (options.storage_level)
        save (access, events)
        log.info ("""currentCheck: overcurrent records saved to %s.simulation_event""".format (access.output_keyspace))
    }

    def powerCheck (access: SimulationCassandraAccess, boundary1: Trigger = BOUNDARY1, boundary2: Trigger = BOUNDARY2, boundary3: Trigger = BOUNDARY3): Unit =
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

        val minratio = if (boundary1.ratio < boundary2.ratio) if (boundary1.ratio < boundary3.ratio) boundary1.ratio else boundary3.ratio else if (boundary2.ratio < boundary3.ratio) boundary2.ratio else boundary3.ratio
        val interesting = ratedTrafos.filter ("power > (%s * ratedS)".format (minratio))

        val mrid = interesting.schema.fieldIndex ("mrid")
        val period = interesting.schema.fieldIndex ("period")
        val time = interesting.schema.fieldIndex ("time")
        val power = interesting.schema.fieldIndex ("power")
        val ratedS = interesting.schema.fieldIndex ("ratedS")
        val work = interesting.rdd.map (row ⇒ (row.getString (mrid), row.getTimestamp (time), row.getInt (period), row.getDouble (power), row.getDouble (ratedS))).persist (options.storage_level)

        def checkTrafo (stuff: Iterable[(String, Timestamp, Int, Double, Double)]): Iterable[Event] =
        {
            // all the mRID and ratedS are the same
            val mrid = stuff.head._1
            val ratedS = stuff.head._5

            // pare down the data and sort by time
            val values = stuff.map (x ⇒ (x._2, x._3, x._4)).toArray.sortWith (_._1.getTime < _._1.getTime)

            val b1 = new Checker (access.simulation, mrid, boundary1, ratedS, typ)
            val b2 = new Checker (access.simulation, mrid, boundary2, ratedS, typ)
            val b3 = new Checker (access.simulation, mrid, boundary3, ratedS, typ)
            for (i ← values.indices)
            {
                val time = values(i)._1
                val ltime = time.getTime
                val period = values(i)._2
                val power = values(i)._3
                b1.next (ltime, period, power)
                b2.next (ltime, period, power)
                b3.next (ltime, period, power)
            }

            b1.gimme ::: b2.gimme ::: b3.gimme
        }

        // group by mRID and check each transformer three ways
        val events: RDD[Event] = work.groupBy (_._1).values.flatMap (checkTrafo).persist (options.storage_level)
        save (access, events)
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
                    voltageCheck (access)
                    currentCheck (access)
                    powerCheck (access)
                case None ⇒
                    log.error ("""simulation %s not found in keyspaces (%s)""".format (simulation, lookup.map (_._2).mkString (",")))
            }
        }

    }
}
