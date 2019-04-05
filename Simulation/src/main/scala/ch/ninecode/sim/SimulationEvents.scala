package ch.ninecode.sim

import com.datastax.driver.core.ResultSet
import com.datastax.spark.connector.cql.CassandraConnector
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.SparkSession
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

    // voltage exceeds ±10%=red, voltage exceeds ±6%=orange
    def voltageCheck (access: SimulationCassandraAccess): Unit =
    {
        log.info ("Voltage events")
        val simulated_value = access.raw_values ("voltage")
            .drop ("type", "period")
            .cache
        logInfo ("""Voltage quality: %d simulation voltages to process""".format (simulated_value.count))
        show (simulated_value)

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
