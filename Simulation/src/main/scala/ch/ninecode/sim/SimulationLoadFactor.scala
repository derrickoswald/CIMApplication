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
case class SimulationLoadFactor (aggregations: Iterable[SimulationAggregate]) (spark: SparkSession, options: SimulationOptions)
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
        val typ = "power"
        val simulated__power_values = access.raw_values (typ) // ToDo: how to pick the transformer power values if another recorder asks for power
            .cache

        val trafos = access.geojson ("geojson_polygons").drop ("properties").cache

        def magnitude[Type_x: TypeTag, Type_y: TypeTag] = udf [Double, Double, Double]((x: Double, y: Double) => Math.sqrt (x * x + y * y))

        val simulated_value_trafos = simulated__power_values
            .drop ("period")
            .withColumn ("date", simulated__power_values ("time").cast (DateType))
            .drop ("time")
            .withColumn ("power", magnitude [Double, Double].apply (simulated__power_values ("real_a"), simulated__power_values ("imag_a")))
            .drop ("real_a", "imag_a")
            .join (
                trafos,
                Seq ("mrid"))

        val aggregates = simulated_value_trafos
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
    }
}

object SimulationLoadFactor extends SimulationPostProcessorParser
{
    // standard aggregation is daily
    val STANDARD_AGGREGATES: Iterable[SimulationAggregate] = List[SimulationAggregate] (
        SimulationAggregate (96, 0)
    )

    def cls: String = "load_factor"

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

            SimulationLoadFactor (aggregates) (_: SparkSession, _: SimulationOptions)
        }
}

