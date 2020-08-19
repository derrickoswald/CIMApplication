package ch.ninecode.sim

import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.Logger
import org.slf4j.LoggerFactory

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
        val where = s"simulation = '$simulation' and query='$query'"
        spark.sql (s"""select `key` mrid, `value` from casscatalog.$output_keyspace.key_value where $where""").persist (storage_level)
    }

    def raw_values (`type`: String, to_drop: Seq[String], period: Int = PERIOD): DataFrame =
    {
        val where = s"simulation = '$simulation' and type = '${`type`}' and period = $period"
        val columns = Seq (
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
            "units").filter (!to_drop.contains (_)).map (x => s"`$x`").mkString (",")
        spark.sql (s"""select $columns from casscatalog.$output_keyspace.simulated_value where $where""").persist (storage_level)
    }

    def players (`type`: String): DataFrame =
    {
        val where = s"simulation = '$simulation' and type = '${`type`}'"
        spark.sql (s"""select `transformer`,`name`,`mrid`,`property` from casscatalog.$output_keyspace.simulation_player where $where""").persist (storage_level)
    }

    def recorders: DataFrame =
    {
        val where = s"simulation = '$simulation'"
        spark.sql (s"""select `transformer`,`name`,`aggregations`,`interval`,`mrid`,`property`,`type`,`unit` from casscatalog.$output_keyspace.simulation_recorder where $where""").persist (storage_level)
    }

    def events: DataFrame =
    {
        val where = s"simulation = '$simulation'"
        spark.sql (s"""select `mrid`,`type`,`start_time`,`end_time`,`message`,`ratio`,`severity` from casscatalog.$output_keyspace.simulation_event where $where""").persist (storage_level)
    }
}
