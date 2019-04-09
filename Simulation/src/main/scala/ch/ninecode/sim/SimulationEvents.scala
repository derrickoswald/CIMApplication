package ch.ninecode.sim

import java.sql.Timestamp

import scala.reflect.runtime.universe.TypeTag

import com.datastax.driver.core.ResultSet
import com.datastax.spark.connector._
import com.datastax.spark.connector.SomeColumns
import com.datastax.spark.connector.cql.CassandraConnector
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

    val NOMINAL_VOLTAGE = 400.0
    val THRESHOLD1 = 0.03
    val THRESHOLD2 = 0.04

    // voltage exceeds ±10%=red, voltage exceeds ±6%=orange
    def voltageCheck (access: SimulationCassandraAccess, voltage: Double = NOMINAL_VOLTAGE, threshold1: Double = THRESHOLD1, threshold2: Double = THRESHOLD2): Unit =
    {
        def magnitude[Type_x: TypeTag, Type_y: TypeTag] = udf [Double, Double, Double]((x: Double, y: Double) => Math.sqrt (x * x + y * y))

        log.info ("Voltage events")
        val typ = "voltage"
        val simulated_voltages = access.raw_values (typ).cache
        logInfo ("""Voltage quality: %d simulation voltages to process""".format (simulated_voltages.count))
        show (simulated_voltages)
        val voltages = simulated_voltages
            .withColumn ("voltage", magnitude [Double, Double].apply (simulated_voltages ("real_a"), simulated_voltages ("imag_a")))
            .drop ("real_a", "imag_a")
            .persist (options.storage_level)

        val voltage_over_threshold = voltages
            .filter ("voltage > %s".format (voltage * (1.0 + threshold1)))

        {
            val mrid = voltage_over_threshold.schema.fieldIndex ("mrid")
            val period = voltage_over_threshold.schema.fieldIndex ("period")
            val time = voltage_over_threshold.schema.fieldIndex ("time")
            val voltage = voltage_over_threshold.schema.fieldIndex ("voltage")
            val work = voltage_over_threshold.rdd.map (row ⇒ (access.simulation, row.getString (mrid), row.getTimestamp (time), new Timestamp (row.getTimestamp (time).getTime + row.getInt (period)), row.getDouble (voltage))).cache

            // save to Cassandra - split based on second threshold
            val over1 = typ + " exceeds %s%% threshold".format (threshold1 * 100.0)
            work.filter (_._5 <= voltage * (1.0 + threshold2)).map (x ⇒ (x._1, x._2, x._3, x._4, over1)).saveToCassandra (access.output_keyspace, "simulation_event",
                SomeColumns ("simulation", "mrid", "start_time", "end_time", "type"))
            val over2 = typ + " exceeds %s%% threshold".format (threshold2 * 100.0)
            work.filter (_._5 > voltage * (1.0 + threshold2)).map (x ⇒ (x._1, x._2, x._3, x._4, over2)).saveToCassandra (access.output_keyspace, "simulation_event",
                SomeColumns ("simulation", "mrid", "start_time", "end_time", "type"))
            log.info ("""voltageCheck: overvoltage records saved to %s.simulation_event""".format (access.output_keyspace))
        }

        val voltage_under_threshold = voltages
            .filter ("voltage < %s".format (voltage * (1.0 - threshold1)))

        {
            val mrid = voltage_under_threshold.schema.fieldIndex ("mrid")
            val period = voltage_under_threshold.schema.fieldIndex ("period")
            val time = voltage_under_threshold.schema.fieldIndex ("time")
            val voltage = voltage_under_threshold.schema.fieldIndex ("voltage")
            val work = voltage_under_threshold.rdd.map (row ⇒ (access.simulation, row.getString (mrid), row.getTimestamp (time), new Timestamp (row.getTimestamp (time).getTime + row.getInt (period)), row.getDouble (voltage))).cache

            // save to Cassandra - split based on second threshold
            val under1 = typ + " subceeds %s%% threshold".format (threshold1 * 100.0)
            work.filter (_._5 >= voltage * (1.0 - threshold2)).map (x ⇒ (x._1, x._2, x._3, x._4, under1)).saveToCassandra (access.output_keyspace, "simulation_event",
                SomeColumns ("simulation", "mrid", "start_time", "end_time", "type"))
            val under2 = typ + " subceeds %s%% threshold".format (threshold2 * 100.0)
            work.filter (_._5 < voltage * (1.0 - threshold2)).map (x ⇒ (x._1, x._2, x._3, x._4, under2)).saveToCassandra (access.output_keyspace, "simulation_event",
                SomeColumns ("simulation", "mrid", "start_time", "end_time", "type"))
            log.info ("""voltageCheck: undervoltage records saved to %s.simulation_event""".format (access.output_keyspace))
        }
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
                    log.info ("""summarizing %s (input keyspace: %s, output keyspace: %s)""".format (access.simulation, access.input_keyspace, access.output_keyspace))
                    voltageCheck (access)
                case None ⇒
                    log.error ("""simulation %s not found in keyspaces (%s)""".format (simulation, lookup.map (_._2).mkString (",")))
            }
        }

    }
}
