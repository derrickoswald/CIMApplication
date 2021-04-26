package ch.ninecode.sim

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.DataFrame
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
    val RDD: RDD[SimulationResult]) extends SimulationAccess(spark, storage_level, simulation, input_keyspace, output_keyspace, verbose)
{
    override def mrid_raw_values (`type`: Type, mrids: Iterable[Mrid], to_drop: Seq[String], period: Int = PERIOD): DataFrame =
    {
        spark.emptyDataFrame // TODO
    }

    override def events: DataFrame = ???

    override def raw_values (`type`: String, to_drop: Seq[String], period: Int): DataFrame = ???

    override def key_value (reference: String): DataFrame = ???

    override def mrids_for_recorders (typ: String): Array[(Trafo, Iterable[Mrid])] = ???

    override def recorders: DataFrame = ???
}
