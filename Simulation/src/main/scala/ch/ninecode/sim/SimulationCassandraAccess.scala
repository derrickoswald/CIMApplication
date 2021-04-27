package ch.ninecode.sim

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
class SimulationCassandraAccess (
    override val spark: SparkSession,
    override val storage_level: StorageLevel,
    override val simulation: String,
    override val input_keyspace: String,
    override val output_keyspace: String,
    override val verbose: Boolean = false) extends SimulationAccess(spark, storage_level, simulation, input_keyspace, output_keyspace, verbose)
{


    /**
     * Get a DataFrame of the key-value pairs.
     *
     * @param query The name of the extra query to fetch values for.
     * @return A DataFrame with columns mrid and value (both are String) for the given query.
     */
    def key_value (query: String): DataFrame =
    {
        val where = s"simulation = '$simulation' and query='$query'"
        spark.sql(s"""select `key` mrid, `value` from casscatalog.$output_keyspace.key_value where $where""").persist(storage_level)
    }

    /**
     * Get a DataFrame of the simulated values of type <code>`type`</code>.
     *
     * @param type    the type of simulated value (e.g. power, voltage, current)
     * @param to_drop column names to drop
     * @param period  the period of the simulated values
     * @return the DataFrame of simulated values
     */
    def raw_values (`type`: String, to_drop: Seq[String], period: Int = PERIOD): DataFrame =
    {
        val where = s"simulation = '$simulation' and type = '${`type`}' and period = $period"
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
            "units").filter(!to_drop.contains(_)).map(x => s"`$x`").mkString(",")
        spark.sql(s"""select $columns from casscatalog.$output_keyspace.simulated_value where $where""").persist(storage_level)
    }

    /**
     * Get a DataFrame of the simulated values of type <code>`type`</code> for the mRID values provided.
     *
     * @param type    the type of simulated value (e.g. power, voltage, current)
     * @param mrids   the list of mRID values to fetch
     * @param to_drop column names to drop
     * @param period  the period of the simulated values
     * @return the DataFrame of simulated values
     */
    def mrid_raw_values (`type`: Type, mrids: Iterable[Mrid], to_drop: Seq[String], period: Int = PERIOD): DataFrame =
    {
        val in = mrids.map(x => s"'$x'").mkString("mrid in (", ",", ")")
        val where = s"simulation = '$simulation' and type = '${`type`}' and period = $period and $in"
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
            "units").filter(!to_drop.contains(_)).map(x => s"`$x`").mkString(",")
        spark.sql(s"""select $columns from casscatalog.$output_keyspace.simulated_value where $where""")
    }

    def players (`type`: String): DataFrame =
    {
        val where = s"simulation = '$simulation' and type = '${`type`}'"
        spark.sql(s"""select `transformer`,`name`,`mrid`,`property` from casscatalog.$output_keyspace.simulation_player where $where""").persist(storage_level)
    }

    def recorders: DataFrame =
    {
        val where = s"simulation = '$simulation'"
        spark.sql(s"""select `transformer`,`name`,`aggregations`,`interval`,`mrid`,`property`,`type`,`unit` from casscatalog.$output_keyspace.simulation_recorder where $where""").persist(storage_level)
    }

    def mrids_for_recorders (`type`: Type): Array[(Trafo, Iterable[Mrid])] =
    {
        import spark.implicits._

        val rec = recorders
        val r = rec
            .where(rec("property") === "power" || rec("property") === "power_in") // select only consumers and transformer
            .drop("name", "aggregations", "interval", "property", "type", "unit")
            .distinct
        val transformer = r.schema.fieldIndex("transformer")
        val mrid = r.schema.fieldIndex("mrid")
        r
            .map(x => (x.getString(transformer), x.getString(mrid)))
            .rdd
            .groupByKey
            .collect
    }

    /*
    def events: DataFrame =
    {
        val where = s"simulation = '$simulation'"
        spark.sql(s"""select `mrid`,`type`,`start_time`,`end_time`,`message`,`ratio`,`severity` from casscatalog.$output_keyspace.simulation_event where $where""").persist(storage_level)
    }
     */
}
