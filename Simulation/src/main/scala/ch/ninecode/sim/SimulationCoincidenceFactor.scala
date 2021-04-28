package ch.ninecode.sim

import javax.json.JsonObject

import org.apache.spark.sql.SparkSession
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import com.datastax.spark.connector._

/**
 * Compute the coincidence factors
 *
 * Coincidence factor is the peak of a system divided by the sum of peak loads of its individual components.
 * It tells how likely the individual components are peaking at the same time.
 * The highest possible coincidence factor is 1, when all of the individual components are peaking at the same time.
 *
 * @param spark   The Spark session
 * @param options The simulation options.
 */
case class SimulationCoincidenceFactor (aggregations: Iterable[SimulationAggregate])(spark: SparkSession, options: SimulationOptions)
    extends SimulationPostProcessor(spark, options)
{
    if (options.verbose) org.apache.log4j.LogManager.getLogger(getClass.getName).setLevel(org.apache.log4j.Level.INFO)
    val log: Logger = LoggerFactory.getLogger(getClass)

    /**
     * Coincidence factor
     */
    @SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
    def run (implicit access: SimulationAccess): Unit =
    {
        log.info(s"Coincidence Factor")

        val typ = "power"
        val queries = access.mrids_for_recorders(typ)
        log.info(s"Computing ${queries.length} coincidence factors")

        for ((trafo, mrids) <- queries)
        {
            // get the mrid,power,date DataFrame for the trafo
            log.info(trafo)
            val simulated_power_values_by_day = simulatedPowerValues(mrids)
                .drop("time")
                .persist(options.cim_options.storage)

            // compute the peak power of the transformer
            val mrid = simulated_power_values_by_day.schema.fieldIndex("mrid")
            val peaks_trafos = simulated_power_values_by_day
                .filter(trafo == _.getString(mrid))
                .drop("mrid")
                .groupBy("date")
                .agg("power" -> "max")
                .drop("power")
                .withColumnRenamed("max(power)", "peak_power")

            // now do the peaks for the energy consumers
            val peak_consumers = simulated_power_values_by_day
                .filter(trafo != _.getString(mrid))
                .groupBy("mrid", "date")
                .agg("power" -> "max")
                .withColumnRenamed("max(power)", "power")

            // sum up the individual peaks for each date
            val sums_houses = peak_consumers
                .groupBy("date")
                .agg("power" -> "sum")
                .withColumnRenamed("sum(power)", "sum_power")

            val _coincidences = peaks_trafos
                .join(sums_houses, "date")
            val coincidences = _coincidences
                .withColumn("coincidence", _coincidences("peak_power") / _coincidences("sum_power"))

            val date = coincidences.schema.fieldIndex("date")
            val peak_power = coincidences.schema.fieldIndex("peak_power")
            val sum_power = coincidences.schema.fieldIndex("sum_power")
            val coincidence = coincidences.schema.fieldIndex("coincidence")

            val work = coincidences.rdd.map(
                row =>
                {
                    val factor = if (row.isNullAt(coincidence)) 0.0 else row.getDouble(coincidence)
                    (trafo, typ, row.getTimestamp(date), row.getDouble(peak_power), row.getDouble(sum_power), factor, "VA÷VA", access.simulation)
                }
            )

            // save to Cassandra
            work.saveToCassandra(access.output_keyspace, "coincidence_factor_by_day",
                SomeColumns("mrid", "type", "date", "peak_power", "sum_power", "coincidence_factor", "units", "simulation"))

            unpersistDataFrame(simulated_power_values_by_day)
        }

        log.info(s"""coincidence factor records saved to ${access.output_keyspace}.coincidence_factor_by_day""")
    }
}

object SimulationCoincidenceFactor extends SimulationPostProcessorParser
{
    // standard aggregation is daily
    val STANDARD_AGGREGATES: Iterable[SimulationAggregate] = List[SimulationAggregate](
        SimulationAggregate(96, 0)
    )

    def cls: String = "coincidence_factor"

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

            SimulationCoincidenceFactor(aggregates)(_: SparkSession, _: SimulationOptions)
        }
}
