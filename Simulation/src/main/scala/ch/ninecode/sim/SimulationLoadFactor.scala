package ch.ninecode.sim

import com.datastax.spark.connector._

import org.apache.spark.sql.SparkSession
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Compute the load factors
 *
 * Load factor is average load of a system divided by its peak load.
 * The higher the load factor is, the smoother the load profile is,
 * and the more the infrastructure is being utilized.
 * The highest possible load factor is 1, which indicates a flat load profile.
 * For this calculation we use the peak value per day.
 *
 * @param spark   The Spark session
 * @param options The simulation options.
 */
case class SimulationLoadFactor (aggregations: Iterable[SimulationAggregate])
    extends SimulationPostProcessor
{
    /**
     * Load factor
     */
    @SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
    def run (spark: SparkSession, access: SimulationCassandraAccess, options: SimulationOptions): Unit =
    {
        if (options.verbose) org.apache.log4j.LogManager.getLogger(getClass.getName).setLevel(org.apache.log4j.Level.INFO)
        val log: Logger = LoggerFactory.getLogger(getClass)

        log.info("Load Factor")

        val typ = "power"
        val queries = access.mrids_for_recorders(typ)
        log.info(s"Computing ${queries.length} load factors")

        for ((trafo, mrids) <- queries)
        {
            // get the mrid,power,date DataFrame for the trafo
            log.info(trafo)
            val simulated_power_values = simulatedPowerValues(mrids, access, options)
                .drop("time")
                .persist(options.cim_options.storage)

            // get the trafo aggregates
            val _mrid = simulated_power_values.schema.fieldIndex("mrid")
            val trafo_aggregates = simulated_power_values
                .filter(trafo == _.getString(_mrid))
                .groupBy("date") // sum and average over times for each day
                .agg("power" -> "avg", "power" -> "max")
                .withColumnRenamed("avg(power)", "avg_power")
                .withColumnRenamed("max(power)", "peak_power")

            val loadfactors = trafo_aggregates
                .withColumn("load_factor", trafo_aggregates("avg_power") / trafo_aggregates("peak_power"))

            val date = loadfactors.schema.fieldIndex("date")
            val avg_power = loadfactors.schema.fieldIndex("avg_power")
            val peak_power = loadfactors.schema.fieldIndex("peak_power")
            val load_factor = loadfactors.schema.fieldIndex("load_factor")

            val work = loadfactors.rdd.map(
                row => (trafo, typ, row.getDate(date), row.getDouble(avg_power), row.getDouble(peak_power), row.getDouble(load_factor), "VA÷VA", access.simulation))

            // save to Cassandra
            work.saveToCassandra(access.output_keyspace, "load_factor_by_day",
                SomeColumns("mrid", "type", "date", "avg_power", "peak_power", "load_factor", "units", "simulation"))

            unpersistDataFrame(simulated_power_values)
        }

        log.info(s"""Load Factor: load factor records saved to ${access.output_keyspace}.load_factor_by_day""")
    }
}

object SimulationLoadFactor // extends SimulationPostProcessorParser
{
    // standard aggregation is daily
    val STANDARD_AGGREGATES: Iterable[SimulationAggregate] = List[SimulationAggregate](
        SimulationAggregate(96, 0)
    )
}
