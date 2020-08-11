package ch.ninecode.sim

import javax.json.JsonObject

import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.SparkSession

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
        val _ = dataframe.unpersist (false)
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
