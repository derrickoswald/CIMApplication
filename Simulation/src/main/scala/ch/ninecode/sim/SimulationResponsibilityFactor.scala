package ch.ninecode.sim

import javax.json.JsonObject

import scala.reflect.runtime.universe.TypeTag

import com.datastax.spark.connector._

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.round
import org.apache.spark.sql.functions.udf
import org.apache.spark.sql.types.DateType
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
case class SimulationResponsibilityFactor (aggregations: Iterable[SimulationAggregate]) (spark: SparkSession, options: SimulationOptions)
    extends SimulationPostProcessor (spark, options)
{
    if (options.verbose) org.apache.log4j.LogManager.getLogger (getClass.getName).setLevel (org.apache.log4j.Level.INFO)
    val log: Logger = LoggerFactory.getLogger (getClass)

    /**
     * Responsibility factor
     */
    def run (implicit access: SimulationCassandraAccess): Unit =
    {
        log.info ("Responsibility Factor")

        def magnitude[Type_x: TypeTag, Type_y: TypeTag] = udf [Double, Double, Double]((x: Double, y: Double) => Math.sqrt (x * x + y * y))

        val typ = "power"
        val simulated_power_values = access.raw_values (typ)
        val power_values = simulated_power_values
            .drop ("period")

        val players = access.players ("energy")
        val trafo_loads = players
            .drop ("name", "property")
        val trafos = trafo_loads
            .drop ("mrid")
            .distinct
            .withColumnRenamed ("transformer", "mrid")

        val simulated_power_values_conditioned = power_values
            .withColumn ("magnitude", magnitude [Double, Double].apply (power_values ("real_a"), power_values ("imag_a")))
            .drop ("real_a", "imag_a")
            .withColumn ("date", power_values ("time").cast (DateType))

        // get the system peaks at each trafo for each day

        val simulated_value_trafos = simulated_power_values_conditioned
            .join (
                trafos,
                Seq ("mrid"))

        val trafo_max_per_day = simulated_value_trafos
            .groupBy ("mrid", "date")
            .agg ("magnitude" → "max")
            .drop ("magnitude")
            .withColumnRenamed ("max(magnitude)", "magnitude")

        val trafo_max_per_day_with_time = trafo_max_per_day.join (simulated_value_trafos, Seq ("mrid", "date", "magnitude"))
            .withColumnRenamed ("mrid", "transformer")
            .drop ("magnitude")

        // get the peak for each house on each day

        val simulated_value_houses = simulated_power_values_conditioned
            .join (
                trafo_loads,
                Seq ("mrid"))

        val house_max_per_day = simulated_value_houses
            .groupBy ("mrid", "date", "transformer")
            .agg ("magnitude" → "max")
            .drop ("magnitude")
            .withColumnRenamed ("max(magnitude)", "peak")

        // get the house power at the trafo peak time for each day

        val house_power_at_max = simulated_value_houses
            .join (trafo_max_per_day_with_time, Seq ("date", "time", "transformer"))

        val peak_times = house_max_per_day
            .join (house_power_at_max, Seq ("mrid", "date", "transformer"))

        val responsibilities = peak_times
            .withColumn ("responsibility", round (peak_times ("magnitude") / peak_times ("peak") * 100.0 * 100.0) / 100.0)
            .filter ("transformer != mrid")

        val mrid = responsibilities.schema.fieldIndex ("mrid")
        val date = responsibilities.schema.fieldIndex ("date")
        val time = responsibilities.schema.fieldIndex ("time")
        val transformer = responsibilities.schema.fieldIndex ("transformer")
        val power = responsibilities.schema.fieldIndex ("magnitude")
        val peak = responsibilities.schema.fieldIndex ("peak")
        val responsibility = responsibilities.schema.fieldIndex ("responsibility")

        val work = responsibilities.rdd.map (
            row ⇒
            {
                val resp = if (row.isNullAt (responsibility)) 0.0 else row.getDouble (responsibility)
                (row.getString (mrid), typ, row.getDate (date), row.getTimestamp (time), row.getString (transformer), row.getDouble (power), row.getDouble (peak), resp, "VA÷VA×100", access.simulation)
            }
        )
        // save to Cassandra
        work.saveToCassandra (access.output_keyspace, "responsibility_by_day",
            SomeColumns ("mrid", "type", "date", "time", "transformer", "power", "peak", "responsibility", "units", "simulation"))
        log.info ("""Responsibility Factor: responsibility records saved to %s.responsibility_by_day""".format (access.output_keyspace))
        simulated_power_values.unpersist (false)
        players.unpersist (false)
    }
}

object SimulationResponsibilityFactor extends SimulationPostProcessorParser
{
    // standard aggregation is daily
    val STANDARD_AGGREGATES: Iterable[SimulationAggregate] = List[SimulationAggregate] (
        SimulationAggregate (96, 0)
    )

    def cls: String = "responsibility_factor"

    /**
     * Generates a JSON parser to populate a processor.
     * @return A method that will return an instance of a post processor given the postprocessing element of a JSON.
     */
    def parser (): JsonObject ⇒ (SparkSession, SimulationOptions) ⇒ SimulationPostProcessor =
        post ⇒
        {
            val aggregates = if (post.containsKey ("aggregates"))
            {
                val list = post.getJsonArray ("aggregates")
                for (i ← 0 until list.size)
                    yield
                        {
                            val aggregate = list.getJsonObject (i)
                            val intervals = aggregate.getInt ("intervals", 96)
                            val ttl = if (aggregate.isNull ("ttl"))
                                0
                            else
                                aggregate.getJsonNumber ("ttl").intValue
                            SimulationAggregate (intervals, ttl)
                        }
            }
            else
                STANDARD_AGGREGATES

            SimulationResponsibilityFactor (aggregates) (_: SparkSession, _: SimulationOptions)
        }
}
