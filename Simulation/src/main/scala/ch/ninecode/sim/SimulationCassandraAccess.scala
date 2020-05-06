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

    // ToDo: how can we not hard-code this period?
    val PERIOD: Int = 900000
    def getPeriod: Int = PERIOD

    /**
     * Get a DataFrame of the key-value pairs.
     *
     * @param query The name of the extra query to fetch values for.
     * @return A DataFrame with columns mrid and value (both are String) for the given query.
     */
    def key_value (query: String): DataFrame =
    {
        val keyvalue = spark
            .read
            .format ("org.apache.spark.sql.cassandra")
            .options (Map ("table" -> "key_value", "keyspace" -> output_keyspace))
            .load
            .filter ("simulation = '%s' and query='%s'".format (simulation, query))
            .drop ("simulation", "query")
            .withColumnRenamed ("key", "mrid")
            .persist (storage_level)
        keyvalue
    }

    def geojson (table: String, coordinate_system: String = "wgs84"): DataFrame =
    {
        val geojson = spark
            .read
            .format ("org.apache.spark.sql.cassandra")
            .options (Map ("table" -> table, "keyspace" -> output_keyspace))
            .load
            .filter ("simulation = '%s' and coordinate_system='%s'".format (simulation, coordinate_system))
            .drop ("simulation", "coordinate_system", "type", "geometry")
            .persist (storage_level)
        geojson
    }

    def raw_values (`type`: String, to_drop: Seq[String], period: Int = PERIOD): DataFrame =
    {
        val values = spark
            .read
            .format ("org.apache.spark.sql.cassandra")
            .options (Map ("table" -> "simulated_value", "keyspace" -> output_keyspace))
            .load
            // push down partition key = (simulation, mrid, type, period)
            .filter ("simulation = '%s' and type = '%s' and period = %s".format (simulation, `type`, period))
            .drop (to_drop:_*)
            .persist (storage_level)
        values
    }

    def raw_meter_values: DataFrame =
    {
        val values = spark
            .read
            .format ("org.apache.spark.sql.cassandra")
            .options (Map ("table" -> "measured_value", "keyspace" -> input_keyspace))
            .load
            .filter ("type = 'energy'")
            .drop ("real_b", "real_c", "imag_b", "imag_c", "type", "units")
            .persist (storage_level)
        values
    }

    def players (`type`: String): DataFrame =
    {
        val values = spark
            .read
            .format ("org.apache.spark.sql.cassandra")
            .options (Map ("table" -> "simulation_player", "keyspace" -> output_keyspace))
            .load
            .filter ("simulation = '%s' and type = '%s'".format (simulation, `type`))
            .drop ("simulation", "type")
            .persist (storage_level)
        values
    }

    def recorders: DataFrame =
    {
        val values = spark
            .read
            .format ("org.apache.spark.sql.cassandra")
            .options (Map ("table" -> "simulation_recorder", "keyspace" -> output_keyspace))
            .load
            .filter ("simulation = '%s'".format (simulation))
            .drop ("simulation")
            .persist (storage_level)
        values
    }

    def events: DataFrame =
    {
        val values = spark
            .read
            .format ("org.apache.spark.sql.cassandra")
            .options (Map ("table" -> "simulation_event", "keyspace" -> output_keyspace))
            .load
            .filter ("simulation = '%s'".format (simulation))
            .drop ("simulation")
            .persist (storage_level)
        values
    }
}
