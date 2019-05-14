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
        val typ = "power"
        val simulated_power_values = access.raw_values (typ)
            .drop ("period")
            .cache

        val trafos = access.geojson ("geojson_polygons").drop ("properties").cache

        def magnitude[Type_x: TypeTag, Type_y: TypeTag] = udf [Double, Double, Double]((x: Double, y: Double) => Math.sqrt (x * x + y * y))

        val simulated_value_trafos = simulated_power_values
            .withColumn ("magnitude", magnitude [Double, Double].apply (simulated_power_values ("real_a"), simulated_power_values ("imag_a")))
            .drop ("real_a", "imag_a")
            .withColumn ("date", simulated_power_values ("time").cast (DateType))
            .join (
                trafos,
                Seq ("mrid"))

        val peaks = simulated_value_trafos
            .groupBy ("mrid", "date")
            .agg ("magnitude" → "max")
            .withColumnRenamed ("max(magnitude)", "magnitude")

        val info = peaks.join (simulated_value_trafos, Seq ("mrid", "date", "magnitude"))
            .withColumnRenamed ("mrid", "transformer")

        val _measured_value = access.raw_meter_values

        def power[Type_x: TypeTag, Type_y: TypeTag, Type_z: TypeTag] = udf [Double, Double, Double, Int]((x: Double, y: Double, z: Int) => (60 * 60 * 1000) / z * Math.sqrt (x * x + y * y))

        val measured_value = _measured_value
            .withColumn ("power", power [Double, Double, Int].apply (_measured_value ("real_a"), _measured_value ("imag_a"), _measured_value ("period")))
            .drop ("real_a", "imag_a", "period")
            .withColumn ("date", _measured_value ("time").cast (DateType))
            .cache

        val houses = access.geojson ("geojson_points").drop ("properties").cache

        val measured_value_and_trafo = measured_value
            .join (
                houses,
                Seq ("mrid"))

        val maximums = measured_value_and_trafo
            .groupBy ("mrid", "date")
            .agg ("power" → "max")
            .withColumnRenamed ("max(power)", "peak")

        val peak_times = measured_value_and_trafo
            .join (info, Seq ("date", "time", "transformer"))
            .drop ("magnitude")

        val responsibilities = peak_times.join (maximums, Seq ("mrid", "date"))
            .withColumn ("responsibility", round (measured_value_and_trafo ("power") / maximums ("peak") * 100.0 * 100.0) / 100.0)

        val mrid = responsibilities.schema.fieldIndex ("mrid")
        val date = responsibilities.schema.fieldIndex ("date")
        val time = responsibilities.schema.fieldIndex ("time")
        val transformer = responsibilities.schema.fieldIndex ("transformer")
        val p = responsibilities.schema.fieldIndex ("power")
        val peak = responsibilities.schema.fieldIndex ("peak")
        val responsibility = responsibilities.schema.fieldIndex ("responsibility")

        val work = responsibilities.rdd.map (
            row ⇒
            {
                val resp = if (row.isNullAt (responsibility)) 0.0 else row.getDouble (responsibility)
                (row.getString (mrid), typ, row.getDate (date), row.getTimestamp (time), row.getString (transformer), row.getDouble (p), row.getDouble (peak), resp, "VA÷VA×100", access.simulation)
            }
        )
        // save to Cassandra
        work.saveToCassandra (access.output_keyspace, "responsibility_by_day",
            SomeColumns ("mrid", "type", "date", "time", "transformer", "power", "peak", "responsibility", "units", "simulation"))
        log.info ("""Responsibility Factor: responsibility records saved to %s.responsibility_by_day""".format (access.output_keyspace))
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
