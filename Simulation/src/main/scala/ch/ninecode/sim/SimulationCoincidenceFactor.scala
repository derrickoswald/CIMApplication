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

        def magnitude[Type_x: TypeTag, Type_y: TypeTag] = udf [Double, Double, Double]((x: Double, y: Double) => Math.sqrt (x * x + y * y))

        val typ = "power"
        val simulated_power_values = access.raw_values (typ)
            .drop ("period")
            .cache

        val trafos = access.geojson ("geojson_polygons").drop ("properties").cache

        val simulated_value_trafos = simulated_power_values
            .withColumn ("power", magnitude [Double, Double].apply (simulated_power_values ("real_a"), simulated_power_values ("imag_a")))
            .drop ("real_a", "imag_a")
            .withColumn ("date", simulated_power_values ("time").cast (DateType))
            .join (
                trafos,
                Seq ("mrid"))

        val peaks_trafos = simulated_value_trafos
            .groupBy ("mrid", "date")
            .agg ("power" → "max")
            .withColumnRenamed ("mrid", "transformer")
            .withColumnRenamed ("max(power)", "peak_power")

        // now do the peaks for the energy consumers

        val _measured_value = access.raw_meter_values
        val houses = access.geojson ("geojson_points").drop ("properties").cache

        def power[Type_x: TypeTag, Type_y: TypeTag, Type_z: TypeTag] = udf [Double, Double, Double, Int]((x: Double, y: Double, z: Int) => (60 * 60 * 1000) / z * Math.sqrt (x * x + y * y))

        val measured_value = _measured_value
            .withColumn ("power", power [Double, Double, Int].apply (_measured_value ("real_a"), _measured_value ("imag_a"), _measured_value ("period")))
            .drop ("real_a", "imag_a")
            .withColumn ("date", _measured_value ("time").cast (DateType))
            .cache

        val peaks_houses = measured_value
            .groupBy ("mrid", "date")
            .agg ("power" → "max")
            .withColumnRenamed ("max(power)", "power")

        val measured_value_houses = peaks_houses
            .join (
                houses,
                Seq ("mrid"))

        // sum up the individual peaks for each transformer and date combination
        val sums_houses = measured_value_houses
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
