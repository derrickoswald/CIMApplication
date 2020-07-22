package ch.ninecode.sim

import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Standard queries to Cassandra.
 *
 * @param spark the spark session to use (has spark.cassandra.connection.host and spark.cassandra.connection.port set).
 * @param storage_level persistence applied to each created DataFrame
 * @param simulation the simulation id (part of the primary key for all tables)
 * @param input_keyspace the keyspace used for measured and synthesized values
 * @param output_keyspace the keyspace used for simulated and other values
 * @param verbose if <code>true</code> turns on logging level INFO for this class
 */
case class SimulationCassandraAccess (
    spark: SparkSession,
    storage_level: StorageLevel,
    simulation: String,
    input_keyspace: String,
    output_keyspace: String,
    verbose: Boolean = false)
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
            .filter (s"simulation = '$simulation' and query='$query'")
            .drop ("simulation", "query")
            .withColumnRenamed ("key", "mrid")
            .persist (storage_level)
        keyvalue
    }

    // not used
    def geojson (table: String, coordinate_system: String = "wgs84"): DataFrame =
    {
        val geojson = spark
            .read
            .format ("org.apache.spark.sql.cassandra")
            .options (Map ("table" -> table, "keyspace" -> output_keyspace))
            .load
            .filter (s"simulation = '$simulation' and coordinate_system='$coordinate_system'")
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
            // can't push down partition key = (simulation, mrid, type, period)
            // so it relies on indices on simulation, type and period
            .filter (s"simulation = '$simulation' and type = '${`type`}' and period = $period")
            .drop (to_drop:_*)
            .persist (storage_level)
        values
    }

    // not used
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
            .filter (s"simulation = '$simulation' and type = '${`type`}'")
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
            .filter (s"simulation = '$simulation'")
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
            .filter (s"simulation = '$simulation'")
            .drop ("simulation")
            .persist (storage_level)
        values
    }
}
