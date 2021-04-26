package ch.ninecode.sim

import scala.collection.convert.ImplicitConversions.`collection asJava`

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.Encoder
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
    val tasks: RDD[SimulationTask]) extends SimulationAccess(spark, storage_level, simulation, input_keyspace, output_keyspace, verbose)
{

    @SuppressWarnings(Array("org.wartremover.warts.TraversableOps"))
    private def values (filterList: Seq[SimulationResult => Boolean], to_drop: Seq[String]): DataFrame = {
        val filteredResults = simulationresults.filter((result) => {
            filterList.forall( (filterToTest) =>  {
                filterToTest(result)
            })
        })
        import spark.implicits._
        val filterSimulationResultsDF = spark.createDataFrame(filteredResults.map(attributes => Row(attributes)), implicitly[Encoder[SimulationResult]].schema)

        val columns = Seq(
            "simulation",
            "mrid",
            "type",
            "period",
            "time",
            "imag_a",
            "imag_b",
            "imag_c",
            "real_a",
            "real_b",
            "real_c",
            "units").filter(!to_drop.contains(_))

        filterSimulationResultsDF.select(columns.head, columns.tail: _*)
    }

    override def key_value (reference: String): DataFrame = ???

    override def raw_values (`type`: String, to_drop: Seq[String], period: Int = PERIOD): DataFrame =
    {
        def simulationFilter: SimulationResult => Boolean = (simulationResult) => {
            simulationResult.simulation.equals(simulation)
        }
        def typeFilter: SimulationResult => Boolean = (simulationResult) =>  {
            simulationResult.`type`.equals(`type`)
        }
        def periodFilter: SimulationResult => Boolean = (simulationResult) => {
            simulationResult.period == PERIOD
        }

        values(Seq(simulationFilter, typeFilter, periodFilter), to_drop)
    }

    override def mrid_raw_values (`type`: Type, mrids: Iterable[Mrid], to_drop: Seq[String], period: Int = PERIOD): DataFrame =
    {
        def simulationFilter: SimulationResult => Boolean = (simulationResult) => {
            simulationResult.simulation.equals(simulation)
        }
        def typeFilter: SimulationResult => Boolean = (simulationResult) =>  {
            simulationResult.`type`.equals(`type`)
        }
        def periodFilter: SimulationResult => Boolean = (simulationResult) => {
            simulationResult.period == PERIOD
        }
        def mridFilter:  SimulationResult => Boolean = (simulationResult) => {
            mrids.contains(simulationResult.mrid)
        }
        values(Seq(simulationFilter, typeFilter, periodFilter, mridFilter), to_drop)
    }

    override def recorders: DataFrame = ???

    override def mrids_for_recorders (typ: String): Array[(Trafo, Iterable[Mrid])] = ???

    override def events: DataFrame = ???
}
