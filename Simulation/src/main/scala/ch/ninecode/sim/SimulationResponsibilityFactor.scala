package ch.ninecode.sim

import javax.json.JsonObject

import com.datastax.spark.connector._
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.round
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Compute the responsibility factors
 *
 * Responsibility factor is the load of an individual component at the time of system peak divided by
 * the peak load of this individual component.
 * Responsibility factor tells how much of the component is contributing to the system peak.
 * When a component peaks at the same time as the system, its responsibility factor is 100%.
 * For this calculation we use the transformer peak value per day.
 *
 * @param spark   The Spark session
 * @param options The simulation options.
 */
case class SimulationResponsibilityFactor (aggregations: Iterable[SimulationAggregate])(spark: SparkSession, options: SimulationOptions)
    extends SimulationPostProcessor(spark, options)
{
    if (options.verbose) org.apache.log4j.LogManager.getLogger(getClass.getName).setLevel(org.apache.log4j.Level.INFO)
    val log: Logger = LoggerFactory.getLogger(getClass)

    /**
     * Responsibility factor
     */
    @SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
    def run (implicit access: SimulationAccess): Unit =
    {
        log.info("Responsibility Factor")

        val typ = "power"
        val queries = access.mrids_for_recorders(typ)
        log.info(s"Computing ${queries.length} responsibility factors")

        for ((trafo, mrids) <- queries)
        {
            // get the mrid,power,time,date DataFrame for the trafo
            log.info(trafo)
            val simulated_power_values = simulatedPowerValues(mrids)
                .persist(options.cim_options.storage)

            // get the time at system peak for each day
            val _mrid = simulated_power_values.schema.fieldIndex("mrid")
            val trafo_max_per_day = simulated_power_values
                .filter(trafo == _.getString(_mrid))
                .groupBy("date")
                .agg("power" -> "max")
                .withColumnRenamed("max(power)", "power")

            val trafo_max_per_day_with_time = trafo_max_per_day
                .join(simulated_power_values, Seq("date", "power"))
                .drop("mrid", "power")

            // get the peak for each house on each day
            val house_max_per_day = simulated_power_values
                .filter(trafo != _.getString(_mrid))
                .groupBy("mrid", "date")
                .agg("power" -> "max")
                .drop("power")
                .withColumnRenamed("max(power)", "peak")

            // get the house power at the trafo peak time for each day
            val house_power_at_max = simulated_power_values
                .filter(trafo != _.getString(_mrid))
                .join(trafo_max_per_day_with_time, Seq("date", "time"))

            val peak_times = house_max_per_day
                .join(house_power_at_max, Seq("mrid", "date"))

            val responsibilities = peak_times
                .withColumn("responsibility", round(peak_times("power") / peak_times("peak") * 100.0 * 100.0) / 100.0)

            val mrid = responsibilities.schema.fieldIndex("mrid")
            val date = responsibilities.schema.fieldIndex("date")
            val time = responsibilities.schema.fieldIndex("time")
            val power = responsibilities.schema.fieldIndex("power")
            val peak = responsibilities.schema.fieldIndex("peak")
            val responsibility = responsibilities.schema.fieldIndex("responsibility")

            val work = responsibilities.rdd.map(
                row =>
                {
                    val resp = if (row.isNullAt(responsibility)) 0.0 else row.getDouble(responsibility)
                    (row.getString(mrid), typ, row.getDate(date), row.getTimestamp(time), trafo, row.getDouble(power), row.getDouble(peak), resp, "VA÷VA×100", access.simulation)
                }
            )
            // save to Cassandra
            work.saveToCassandra(access.output_keyspace, "responsibility_by_day",
                SomeColumns("mrid", "type", "date", "time", "transformer", "power", "peak", "responsibility", "units", "simulation"))

            unpersistDataFrame(simulated_power_values)
        }

        log.info(s"""responsibility records saved to ${access.output_keyspace}.responsibility_by_day""")
    }
}

object SimulationResponsibilityFactor extends SimulationPostProcessorParser
{
    // standard aggregation is daily
    val STANDARD_AGGREGATES: Iterable[SimulationAggregate] = List[SimulationAggregate](
        SimulationAggregate(96, 0)
    )

    def cls: String = "responsibility_factor"

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

            SimulationResponsibilityFactor(aggregates)(_: SparkSession, _: SimulationOptions)
        }
}
