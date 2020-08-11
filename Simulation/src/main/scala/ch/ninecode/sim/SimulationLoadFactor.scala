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
    extends SimulationPostProcessor (spark, options)
{
    if (options.verbose) org.apache.log4j.LogManager.getLogger (getClass.getName).setLevel (org.apache.log4j.Level.INFO)
    val log: Logger = LoggerFactory.getLogger (getClass)

    /**
     * Load factor
     */
    def run (implicit access: SimulationCassandraAccess): Unit =
    {
        log.info ("Load Factor")

        def magnitude[Type_x: TypeTag, Type_y: TypeTag] = udf [Double, Double, Double]((x: Double, y: Double) => Math.sqrt (x * x + y * y))

        def summation[Type_a: TypeTag, Type_b: TypeTag, Type_c: TypeTag] = udf [Double, Double, Double, Double]((a: Double, b: Double, c: Double) => a + b + c)

        val typ = "power"
        val to_drop = if (options.three_phase)
            Seq ("simulation", "type", "period", "units")
        else
            Seq ("simulation", "type", "period", "real_b", "imag_b", "real_c", "imag_c", "units")
        val raw = access.raw_values (typ, to_drop)

        val trafo_loads = access.players ("energy")
        val trafos = trafo_loads
            .drop ("name", "property", "mrid")
            .distinct
            .withColumnRenamed ("transformer", "mrid")

        val simulated_values = raw
            .withColumn ("date", raw ("time").cast (DateType))
            .drop ("time")
            .join (
                trafos,
                Seq ("mrid"))

        val simulated_power_values =
            if (options.three_phase)
            {
                val intermediate = simulated_values
                    .withColumn ("power_a", magnitude [Double, Double].apply (simulated_values ("real_a"), simulated_values ("imag_a")))
                    .withColumn ("power_b", magnitude [Double, Double].apply (simulated_values ("real_b"), simulated_values ("imag_b")))
                    .withColumn ("power_c", magnitude [Double, Double].apply (simulated_values ("real_c"), simulated_values ("imag_c")))
                    .drop ("real_a", "imag_a", "real_b", "imag_b", "real_c", "imag_c")
                intermediate
                    .withColumn ("power", summation [Double, Double, Double].apply (intermediate ("power_a"), intermediate ("power_b"), intermediate ("power_c")))
                    .drop ("power_a", "power_b", "power_c")
            }
            else
                simulated_values
                    .withColumn ("power", magnitude [Double, Double].apply (simulated_values ("real_a"), simulated_values ("imag_a")))
                    .drop ("real_a", "imag_a")

        val aggregates = simulated_power_values
            .groupBy ("mrid", "date") // sum over time for each day
            .agg ("power" → "avg", "power" → "max")
            .withColumnRenamed ("avg(power)", "avg_power")
            .withColumnRenamed ("max(power)", "peak_power")
        val loadfactors = aggregates
            .withColumn ("load_factor", aggregates ("avg_power") / aggregates ("peak_power"))

        val mrid = loadfactors.schema.fieldIndex ("mrid")
        val date = loadfactors.schema.fieldIndex ("date")
        val avg_power = loadfactors.schema.fieldIndex ("avg_power")
        val peak_power = loadfactors.schema.fieldIndex ("peak_power")
        val load_factor = loadfactors.schema.fieldIndex ("load_factor")

        val work = loadfactors.rdd.map (
            row ⇒ (row.getString (mrid), typ, row.getDate (date), row.getDouble (avg_power), row.getDouble (peak_power), row.getDouble (load_factor), "VA÷VA", access.simulation))

        // save to Cassandra
        work.saveToCassandra (access.output_keyspace, "load_factor_by_day",
            SomeColumns ("mrid", "type", "date", "avg_power", "peak_power", "load_factor", "units", "simulation"))
        log.info ("""Load Factor: load factor records saved to %s.load_factor_by_day""".format (access.output_keyspace))
        raw.unpersist (false)
        trafo_loads.unpersist (false)
    }
}

object SimulationLoadFactor extends SimulationPostProcessorParser
{
    // standard aggregation is daily
    val STANDARD_AGGREGATES: Iterable[SimulationAggregate] = List [SimulationAggregate](
        SimulationAggregate (96, 0)
    )

    def cls: String = "load_factor"

    /**
     * Generates a JSON parser to populate a processor.
     *
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

            SimulationLoadFactor (aggregates)(_: SparkSession, _: SimulationOptions)
        }
}

