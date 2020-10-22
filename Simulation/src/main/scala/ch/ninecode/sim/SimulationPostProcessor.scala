package ch.ninecode.sim

import javax.json.JsonObject

import scala.reflect.runtime.universe.TypeTag

import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.udf
import org.apache.spark.sql.types.DateType

/**
 * Postprocessor base class.
 *
 * @param spark   the Spark session
 * @param options simulation options
 */
abstract class SimulationPostProcessor (spark: SparkSession, options: SimulationOptions)
{
    /**
     * Execute the post processor.
     *
     * @param access Access to the simulated data in Cassandra with the simulation id baked in to the queries.
     */
    def run (implicit access: SimulationCassandraAccess): Unit

    def unpersistDataFrame (dataframe: DataFrame): Unit =
    {
        val _ = dataframe.unpersist(false)
    }

    def simulatedPowerValues (mrids: Iterable[String])(implicit access: SimulationCassandraAccess): DataFrame =
    {
        def magnitude[Type_x: TypeTag, Type_y: TypeTag] = udf[Double, Double, Double]((x: Double, y: Double) => Math.sqrt(x * x + y * y))

        def magnitude3p[Type_ar: TypeTag, Type_ai: TypeTag, Type_br: TypeTag, Type_bi: TypeTag, Type_cr: TypeTag, Type_ci: TypeTag] =
            udf[Double, Double, Double, Double, Double, Double, Double]((ar: Double, ai: Double, br: Double, bi: Double, cr: Double, ci: Double) =>
                Math.sqrt(ar * ar + ai * ai) + Math.sqrt(br * br + bi * bi) + Math.sqrt(cr * cr + ci * ci))

        val typ = "power"
        val to_drop = if (options.three_phase)
            Seq("simulation", "type", "period", "units")
        else
            Seq("simulation", "type", "period", "real_b", "imag_b", "real_c", "imag_c", "units")
        val simulated_values = access.mrid_raw_values(typ, mrids, to_drop)
        val simulated_power_values =
            if (options.three_phase)
                simulated_values
                    .withColumn("power", magnitude3p[Double, Double, Double, Double, Double, Double].apply(
                        simulated_values("real_a"), simulated_values("imag_a"),
                        simulated_values("real_b"), simulated_values("imag_b"),
                        simulated_values("real_c"), simulated_values("imag_c")))
                    .drop("real_a", "imag_a", "real_b", "imag_b", "real_c", "imag_c")
            else
                simulated_values
                    .withColumn("power", magnitude[Double, Double].apply(simulated_values("real_a"), simulated_values("imag_a")))
                    .drop("real_a", "imag_a")
        simulated_power_values
            .withColumn("date", simulated_power_values("time").cast(DateType))
    }
}

/**
 * The 'trait' that all post processor companion objects must implement.
 *
 * @tparam T the postprocessor type
 */
abstract class SimulationPostProcessorParser[T <: SimulationPostProcessor]
{
    /**
     * The class name of the postprocessor, as found in the JSON file.
     *
     * @return A unique class of processor.
     */
    def cls: String

    /**
     * Generates a JSON parser to populate a processor.
     *
     * @return A method that will return a way to make an instance of a post processor given the postprocessing element of a JSON.
     */
    def parser (): JsonObject => (SparkSession, SimulationOptions) => SimulationPostProcessor
}
