package ch.ninecode.mfi

import java.util.Calendar

import com.datastax.spark.connector.SomeColumns
import com.datastax.spark.connector._
import com.datastax.spark.connector.cql.CassandraConnector
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.util.Schema

case class MaximumFeedInCassandra (
    session: SparkSession,
    options: EinspeiseleistungOptions
)
{
    val log: Logger = LoggerFactory.getLogger(getClass)

    /**
     * Get the highest run number + 1
     *
     * @return the next run number
     */
    def nextRun: Int =
    {
        CassandraConnector(session.sparkContext.getConf).withSessionDo(
            session =>
            {
                val resultset = session.execute(s"""select max(run) as hi from "${options.keyspace}".maximumfeedin_run where id='${options.id}'""")
                val row = resultset.one
                if (row.isNull(0))
                    1
                else
                    row.getInt(0) + 1
            }
        )
    }

    def storeRun (description: String): Int =
    {
        val run = nextRun
        val current_time = Calendar.getInstance
        val data = Map[String, Any](
            "id" -> options.id,
            "run" -> run,
            "description" -> description,
            "cim" -> options.cim_options.files.mkString(","),
            "cimreaderoptions" -> options.cim_options.toMap,
            "run_time" -> current_time,
            "three" -> options.three,
            "precalculation" -> options.precalculation,
            "trafos" -> options.trafos,
            "export_only" -> options.export_only,
            "all" -> options.all,
            "erase" -> options.erase,
            "simulation" -> options.simulation,
            "reference" -> options.reference,
            "delta" -> options.delta,
            "precalc_factor" -> options.precalc_factor,
            "cosphi" -> options.cosphi,
            "voltage_threshold" -> options.voltage_threshold,
            "voltage_threshold2" -> options.voltage_threshold2,
            "ignore_other" -> options.ignore_other,
            "cable_impedance_limit" -> options.cable_impedance_limit,
            "base_temperature" -> options.base_temperature,
            "sim_temperature" -> options.sim_temperature
        )
        val row = CassandraRow.fromMap(data)
        val rdd = session.sparkContext.parallelize(Seq(row))
        rdd.saveToCassandra(options.keyspace, "maximumfeedin_run",
            SomeColumns(
                "id",
                "run",
                "description",
                "cim",
                "cimreaderoptions",
                "run_time",
                "three",
                "precalculation",
                "trafos",
                "export_only",
                "all",
                "erase",
                "simulation",
                "reference",
                "delta",
                "precalc_factor",
                "cosphi",
                "voltage_threshold",
                "voltage_threshold2",
                "ignore_other",
                "cable_impedance_limit",
                "base_temperature",
                "sim_temperature"
            ))

        run
    }

    @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf", "org.wartremover.warts.Null"))
    def toPrecalculationRow (result: MaxPowerFeedingNodeEEA): (String, String, String, String, Double, Int, String, String) =
    {
        def eea_count (result: MaxPowerFeedingNodeEEA): Int = if (result.eea != null) result.eea.size else 0
        result.reason match
        {
            case "voltage limit" | "current limit" | "transformer limit" =>
                (options.id, result.mrid, result.source_obj, result.feeder, result.max_power_feeding, eea_count(result), result.reason, result.details)
            case _ =>
                (options.id, result.mrid, result.source_obj, result.feeder, null.asInstanceOf[Double],  eea_count(result), "no results", result.reason)
        }
    }

    def storePrecalculation (results: RDD[MaxPowerFeedingNodeEEA]): Unit =
    {
        val rdd = results.map (toPrecalculationRow)
        rdd.saveToCassandra(
            options.keyspace,
            "maximumfeedin",
            SomeColumns(
                "id",
                "house",
                "trafo",
                "feeder",
                "maximum",
                "eea",
                "reason",
                "details"
            )
        )
    }

    def store_precalculation (description: String) (results: RDD[MaxPowerFeedingNodeEEA]): (String, Int) =
    {
        log.info(s"storing precalculation results ${options.id}")

        val schema = Schema(session, "/maximumfeedin_schema.sql", options.verbose)
        if (schema.make(keyspace = options.keyspace, replication = options.replication))
        {
            val run = storeRun(description)
            storePrecalculation(results)
            log.info(s"stored precalculation results ${options.id} run $run")
            (options.id, run)
        }
        else
            ("", -1)
    }

    @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf", "org.wartremover.warts.Null"))
    def toMaximumFeedInRow (result: MaxEinspeiseleistung): (String, String, String, String, Double, Int, String, String) =
    {
        (options.id, result.house, result.trafo, result.feeder, result.max.getOrElse(0.0), null.asInstanceOf[Int], result.reason, result.details)
    }

    def storeMaximumFeedIn (results: RDD[MaxEinspeiseleistung]): Unit =
    {
        val rdd = results.map (toMaximumFeedInRow)
        rdd.saveToCassandra(
            options.keyspace,
            "maximumfeedin",
            SomeColumns(
                "id",
                "house",
                "trafo",
                "feeder",
                "maximum",
                "eea",
                "reason",
                "details"
            )
        )
    }

    def store (description: String) (results: RDD[MaxEinspeiseleistung]): (String, Int) =
    {
        log.info(s"storing maximum feed-in results ${options.id}")

        val schema = Schema(session, "/maximumfeedin_schema.sql", options.verbose)
        if (schema.make(keyspace = options.keyspace, replication = options.replication))
        {
            val run = storeRun(description)
            storeMaximumFeedIn(results)
            log.info(s"stored feed-in results ${options.id} run $run")
            (options.id, run)
        }
        else
            ("", -1)
    }
}