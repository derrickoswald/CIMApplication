package ch.ninecode.sim

import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.Logger
import org.slf4j.LoggerFactory

case class SimulationCassandraAccess (spark: SparkSession, storage_level: StorageLevel, simulation: String, input_keyspace: String, output_keyspace: String, verbose: Boolean = false, unittest: Boolean = false)
{
    if (verbose) org.apache.log4j.LogManager.getLogger (getClass.getName).setLevel (org.apache.log4j.Level.INFO)
    val log: Logger = LoggerFactory.getLogger (getClass)
    val executors: Int = Math.max (2, spark.sparkContext.getExecutorMemoryStatus.size - 1)
    val partitions: Int = 4 * executors

    // ToDo: how can we not hard-code this period?
    val PERIOD: Int = 900000
    def getPeriod: Int = PERIOD

    var geojsons: Map[String,DataFrame] = Map()

    var simulated_values: Map[String,DataFrame] = Map()

    var measured_values: DataFrame = _

    def geojson (table: String): DataFrame =
    {
        geojsons.get (table) match
        {
            case Some (dataframe) ⇒ dataframe
            case None ⇒
                val geojson = spark
                    .read
                    .format ("org.apache.spark.sql.cassandra")
                    .options (Map ("table" -> table, "keyspace" -> output_keyspace))
                    .load
                    .filter ("simulation = '%s'".format (simulation))
                    .drop ("simulation", "type", "geometry")
                    .coalesce (partitions)
                    .persist (storage_level)
                geojsons = geojsons + (table → geojson)
                geojson
        }
    }

    def raw_values (`type`: String, period: Int = PERIOD): DataFrame =
    {
        simulated_values.get (`type`) match
        {
            case Some (dataframe) ⇒ dataframe
            case None ⇒
                val values = spark
                    .read
                    .format ("org.apache.spark.sql.cassandra")
                    .options (Map ("table" -> "simulated_value", "keyspace" -> output_keyspace))
                    .load
                    // push down partition key = (simulation, mrid, type, period)
                    .filter ("simulation = '%s' and type = '%s' and period = %s".format (simulation, `type`, period))
                    .drop ("simulation", "type", "real_b", "real_c", "imag_b", "imag_c", "units")
                    .coalesce (partitions)
                    .persist (storage_level)
                simulated_values = simulated_values + (`type` → values)
                values
        }
    }

    def raw_meter_values: DataFrame =
    {
        if (null == measured_values)
        {
            measured_values = spark
                .read
                .format ("org.apache.spark.sql.cassandra")
                .options (Map ("table" -> "measured_value", "keyspace" -> input_keyspace))
                .load
                .filter ("type = 'energy'")
                .drop ("real_b", "real_c", "imag_b", "imag_c", "type", "units")
                .coalesce (partitions)
                .persist (storage_level)
            measured_values
        }
        measured_values
    }
}
