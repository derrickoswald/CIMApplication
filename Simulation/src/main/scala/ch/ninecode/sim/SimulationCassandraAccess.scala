package ch.ninecode.sim

import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.SparkSession
import org.slf4j.Logger
import org.slf4j.LoggerFactory

case class SimulationCassandraAccess (spark: SparkSession, simulation: String, input_keyspace: String, output_keyspace: String, verbose: Boolean = false, unittest: Boolean = false)
{
    if (verbose) org.apache.log4j.LogManager.getLogger (getClass.getName).setLevel (org.apache.log4j.Level.INFO)
    val log: Logger = LoggerFactory.getLogger (getClass)
    def logInfo (msg: => String): Unit = if (log.isInfoEnabled && unittest) log.info (msg)
    def show (dataframe: DataFrame, records: Int = 5): Unit = if (unittest) dataframe.show (records)

    // ToDo: how can we not hard-code this period?
    val PERIOD: Int = 900000
    def getPeriod: Int = PERIOD

    var points: DataFrame = _

    var lines: DataFrame = _

    var polygons: DataFrame = _

    var simulated_values: Map[String,DataFrame] = Map()

    var measured_values: DataFrame = _

    def geojson_points: DataFrame =
    {
        if (null == points)
        {
            points = spark
                .read
                .format ("org.apache.spark.sql.cassandra")
                .options (Map ("table" -> "geojson_points", "keyspace" -> output_keyspace))
                .load
                .filter ("simulation = '%s'".format (simulation))
                .drop ("simulation", "type", "geometry")
                .cache
            logInfo ("""%d GeoJSON points to process""".format (points.count))
            show (points)
        }
        points
    }

    def geojson_lines: DataFrame =
    {
        if (null == lines)
        {
            lines = spark
                .read
                .format ("org.apache.spark.sql.cassandra")
                .options (Map ("table" -> "geojson_lines", "keyspace" -> output_keyspace))
                .load
                .filter ("simulation = '%s'".format (simulation))
                .drop ("simulation", "type", "geometry")
                .cache
            logInfo ("""%d GeoJSON lines to process""".format (lines.count))
            show (lines)
        }
        lines
    }

    def geojson_polygons: DataFrame =
    {
        if (null == polygons)
        {
            polygons = spark
                .read
                .format ("org.apache.spark.sql.cassandra")
                .options (Map ("table" -> "geojson_polygons", "keyspace" -> output_keyspace))
                .load
                .filter ("simulation = '%s'".format (simulation))
                .drop ("simulation", "type", "geometry")
                .cache
            logInfo ("""%d GeoJSON polygons to process""".format (polygons.count))
            show (polygons)
        }
        polygons
    }

    def raw_values (`type`: String, period: Int = PERIOD): DataFrame =
    {
        simulated_values.get(`type`) match
        {
            case Some(dataframe) ⇒ dataframe
            case None ⇒
                val values = spark
                    .read
                    .format ("org.apache.spark.sql.cassandra")
                    .options (Map ("table" -> "simulated_value", "keyspace" -> output_keyspace))
                    .load
                    // push down partition key = (simulation, mrid, type, period)
                    .filter ("simulation = '%s' and type = '%s' and period = %s".format (simulation, `type`, period))
                    .drop ("simulation", "type", "real_b", "real_c", "imag_b", "imag_c", "units")
                    .cache
                logInfo ("""%d simulated values to process""".format (values.count))
                show (values)
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
                .cache
            logInfo ("""%d measured values to process""".format (measured_values.count))
            show (measured_values)
            measured_values
        }
        measured_values
    }
}
