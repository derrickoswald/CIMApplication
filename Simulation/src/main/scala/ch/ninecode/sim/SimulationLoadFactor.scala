package ch.ninecode.sim

import javax.json.JsonObject

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
case class SimulationLoadFactor (aggregations: Iterable[SimulationAggregate])(spark: SparkSession, options: SimulationOptions)
    extends SimulationPostProcessor(spark, options)
{
    if (options.verbose) org.apache.log4j.LogManager.getLogger(getClass.getName).setLevel(org.apache.log4j.Level.INFO)
    val log: Logger = LoggerFactory.getLogger(getClass)

    /**
     * Load factor
     */
    @SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
    def run (implicit access: SimulationAccess): Unit =
    {
        log.info("Load Factor")

        val typ = "power"
        val queries = access.mrids_for_recorders(typ)
        log.info(s"Computing ${queries.length} load factors")

        for ((trafo, mrids) <- queries)
        {
            // get the mrid,power,date DataFrame for the trafo
            log.info(trafo)
            val simulated_power_values = simulatedPowerValues(mrids)
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
                row => (trafo, typ, row.getTimestamp(date), row.getDouble(avg_power), row.getDouble(peak_power), row.getDouble(load_factor), "VA÷VA", access.simulation))

            // save to Cassandra
            work.saveToCassandra(access.output_keyspace, "load_factor_by_day",
                SomeColumns("mrid", "type", "date", "avg_power", "peak_power", "load_factor", "units", "simulation"))

            unpersistDataFrame(simulated_power_values)
        }

        log.info(s"""Load Factor: load factor records saved to ${access.output_keyspace}.load_factor_by_day""")
    }
}

object SimulationLoadFactor extends SimulationPostProcessorParser
{
    // standard aggregation is daily
    val STANDARD_AGGREGATES: Iterable[SimulationAggregate] = List[SimulationAggregate](
        SimulationAggregate(96, 0)
    )

    def cls: String = "load_factor"

    /**
     * Generates a JSON parser to populate a processor.
     *
     * @return A method that will return an instance of a post processor given the postprocessing element of a JSON.
     */
    def parser (): JsonObject => (SparkSession, SimulationOptions) => SimulationPostProcessor =
        post =>
        {
            val aggregates = if (post.containsKey("aggregates"))
            {
                val list = post.getJsonArray("aggregates")
                for (i <- 0 until list.size)
                    yield
                        {
                            val aggregate = list.getJsonObject(i)
                            val intervals = aggregate.getInt("intervals", 96)
                            val ttl = if (aggregate.isNull("ttl"))
                                0
                            else
                                aggregate.getJsonNumber("ttl").intValue
                            SimulationAggregate(intervals, ttl)
                        }
            }
            else
                STANDARD_AGGREGATES

            SimulationLoadFactor(aggregates)(_: SparkSession, _: SimulationOptions)
        }
}
