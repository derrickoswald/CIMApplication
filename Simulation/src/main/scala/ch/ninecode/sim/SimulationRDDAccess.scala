package ch.ninecode.sim

import scala.collection.convert.ImplicitConversions.`collection asJava`

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.Row
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel

/**
 * Standard queries to Cassandra.
 *
 * @param spark           the spark session to use (has spark.cassandra.connection.host and spark.cassandra.connection.port set).
 * @param storage_level   persistence applied to each created DataFrame
 * @param simulation      the simulation id (part of the primary key for all tables)
 * @param input_keyspace  the keyspace used for measured and synthesized values
 * @param output_keyspace the keyspace used for simulated and other values
 * @param verbose         if <code>true</code> turns on logging level INFO for this class
 */
class SimulationRDDAccess (
    override val spark: SparkSession,
    override val storage_level: StorageLevel,
    override val simulation: String,
    override val input_keyspace: String,
    override val output_keyspace: String,
    override val verbose: Boolean = false,
    val simulationresults: RDD[SimulationResult],
    val tasks: RDD[SimulationTask],
    val key_values: DataFrame)
    extends SimulationAccess(spark, storage_level, simulation, input_keyspace, output_keyspace, verbose)
{

    private def simulationFilter: (SimulationResult => Boolean) = (simulationResult: SimulationResult) => {
        simulationResult.simulation.equals(simulation)
    }

    private def typeFilter: (Type) => (SimulationResult => Boolean) = (`type`) =>
    {
        (simulationResult) =>  {
            simulationResult.`type`.equals(`type`)
        }
    }

    private def periodFilter: SimulationResult => Boolean = (simulationResult) =>
    {
        simulationResult.period == PERIOD
    }

    private def mridFilter: (Iterable[Mrid] => (SimulationResult => Boolean)) = (mrids) => {
        (simulationResult) => {
            mrids.contains(simulationResult.mrid)
        }
    }

    private def queryFilter: (String => (Row => Boolean)) = (reference) => {
        (keyValueRow) => {
            keyValueRow.getAs[String]("query").equals(reference)
        }
    }

    @SuppressWarnings(Array("org.wartremover.warts.TraversableOps"))
    private def values (filterList: Seq[SimulationResult => Boolean], to_drop: Seq[String]): DataFrame = {
        val filteredResults = simulationresults.filter((result) => {
            filterList.forall( (filterToTest) =>  {
                filterToTest(result)
            })
        })

        val all_columns = Seq(
            "simulation",
            "mrid",
            "type",
            "time",
            "period",
            "imag_a",
            "imag_b",
            "imag_c",
            "real_a",
            "real_b",
            "real_c",
            "units",
            "ttl")
        val columns = all_columns.filter(!(to_drop +: "ttl").contains(_))

        import spark.implicits._
        val filterSimulationResultsDF = filteredResults.toDF(all_columns:_*)

        filterSimulationResultsDF.select(columns.head, columns.tail: _*)
    }

    override def key_value (reference: String): DataFrame = {
        key_values.filter(queryFilter(reference))
    }

    override def raw_values (`type`: String, to_drop: Seq[String], period: Int = PERIOD): DataFrame =
    {
        values(Seq(simulationFilter, typeFilter(`type`), periodFilter), to_drop)
    }

    override def mrid_raw_values (`type`: Type, mrids: Iterable[Mrid], to_drop: Seq[String], period: Int = PERIOD): DataFrame =
    {
        values(Seq(simulationFilter, typeFilter(`type`), periodFilter, mridFilter(mrids)), to_drop)
    }

    override def recorders: DataFrame =
    {
        import spark.implicits._
        val recorders =
            tasks.flatMap(
                task => task.recorders.map(recorder => (task.transformer, recorder.name, recorder.aggregationsMap, recorder.interval, recorder.mrid, recorder.property, recorder.`type`, recorder.unit)))
        recorders.toDF("transformer", "name","aggregations","interval", "mrid","property","type", "unit")
    }
}
