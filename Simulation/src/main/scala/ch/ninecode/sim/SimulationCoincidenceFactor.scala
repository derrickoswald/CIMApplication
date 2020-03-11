package ch.ninecode.sim

import javax.json.JsonObject

import scala.reflect.runtime.universe.TypeTag

import com.datastax.spark.connector._

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.udf
import org.apache.spark.sql.types.DateType
import org.slf4j.Logger
import org.slf4j.LoggerFactory

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
case class SimulationCoincidenceFactor (aggregations: Iterable[SimulationAggregate]) (spark: SparkSession, options: SimulationOptions)
    extends SimulationPostProcessor (spark, options)
{
    if (options.verbose) org.apache.log4j.LogManager.getLogger (getClass.getName).setLevel (org.apache.log4j.Level.INFO)
    val log: Logger = LoggerFactory.getLogger (getClass)

    /**
     * Coincidence factor
     */
    def run (implicit access: SimulationCassandraAccess): Unit =
    {
        log.info ("Coincidence Factor")

        def magnitude[Type_x: TypeTag, Type_y: TypeTag] = udf[Double, Double, Double]((x: Double, y: Double) => Math.sqrt (x * x + y * y))
        def summation[Type_a: TypeTag, Type_b: TypeTag, Type_c: TypeTag] = udf[Double, Double, Double, Double]((a: Double, b: Double, c: Double) => a + b + c)

        val typ = "power"
        val to_drop = if (options.three_phase)
            Seq("simulation", "type", "period", "units")
        else
            Seq("simulation", "type", "period", "real_b", "imag_b", "real_c", "imag_c", "units")
        val simulated_values = access.raw_values (typ, to_drop)
        val simulated_power_values =
            if (options.three_phase)
            {
                val intermediate = simulated_values
                    .withColumn ("power_a", magnitude[Double, Double].apply (simulated_values ("real_a"), simulated_values ("imag_a")))
                    .withColumn ("power_b", magnitude[Double, Double].apply (simulated_values ("real_b"), simulated_values ("imag_b")))
                    .withColumn ("power_c", magnitude[Double, Double].apply (simulated_values ("real_c"), simulated_values ("imag_c")))
                    .drop ("real_a", "imag_a", "real_b", "imag_b", "real_c", "imag_c")
                intermediate
                    .withColumn ("power", summation[Double, Double, Double].apply (intermediate ("power_a"), intermediate ("power_b"), intermediate ("power_c")))
                    .drop ("power_a", "power_b", "power_c")
            }
            else
                simulated_values
                    .withColumn ("power", magnitude[Double, Double].apply (simulated_values ("real_a"), simulated_values ("imag_a")))
                    .drop ("real_a", "imag_a")
        val simulated_power_values_by_day = simulated_power_values
            .withColumn ("date", simulated_values ("time").cast (DateType))
            .drop ("time")

        val players = access.players ("energy")
        val trafo_loads = players
            .drop ("name", "property")
        val trafos = trafo_loads
            .drop ("mrid")
            .distinct
            .withColumnRenamed ("transformer", "mrid")

        val simulated_value_trafos = simulated_power_values_by_day
            .join (
                trafos,
                Seq ("mrid"))

        val peaks_trafos = simulated_value_trafos
            .groupBy ("mrid", "date")
            .agg ("power" → "max")
            .withColumnRenamed ("mrid", "transformer")
            .withColumnRenamed ("max(power)", "peak_power")

        // now do the peaks for the energy consumers

        val peak_consumers = simulated_power_values_by_day
            .groupBy ("mrid", "date")
            .agg ("power" → "max")
            .withColumnRenamed ("max(power)", "power")

        val peak_houses = peak_consumers
            .join (
                trafo_loads,
                Seq ("mrid"))

        // sum up the individual peaks for each transformer and date combination
        val sums_houses = peak_houses
            .groupBy ("transformer", "date")
            .agg ("power" → "sum")
            .withColumnRenamed ("sum(power)", "sum_power")

        val _coincidences = peaks_trafos
            .join (sums_houses, Seq ("transformer", "date"))
        val coincidences = _coincidences
            .withColumn ("coincidence", _coincidences ("peak_power") / _coincidences ("sum_power"))

        val transformer = coincidences.schema.fieldIndex ("transformer")
        val date = coincidences.schema.fieldIndex ("date")
        val peak_power = coincidences.schema.fieldIndex ("peak_power")
        val sum_power = coincidences.schema.fieldIndex ("sum_power")
        val coincidence = coincidences.schema.fieldIndex ("coincidence")

        val work = coincidences.rdd.map (
            row ⇒
            {
                val factor = if (row.isNullAt (coincidence)) 0.0 else row.getDouble (coincidence)
                (row.getString (transformer), typ, row.getDate (date), row.getDouble (peak_power), row.getDouble (sum_power), factor, "VA÷VA", access.simulation)
            }
        )

        // save to Cassandra
        work.saveToCassandra (access.output_keyspace, "coincidence_factor_by_day",
            SomeColumns ("mrid", "type", "date", "peak_power", "sum_power", "coincidence_factor", "units", "simulation"))
        log.info ("""Coincidence Factor: coincidence factor records saved to %s.coincidence_factor_by_day""".format (access.output_keyspace))
        players.unpersist (false)
        simulated_values.unpersist (false)
    }
}

object SimulationCoincidenceFactor extends SimulationPostProcessorParser
{
    // standard aggregation is daily
    val STANDARD_AGGREGATES: Iterable[SimulationAggregate] = List[SimulationAggregate] (
        SimulationAggregate (96, 0)
    )

    def cls: String = "coincidence_factor"

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

            SimulationCoincidenceFactor (aggregates) (_: SparkSession, _: SimulationOptions)
        }
}
