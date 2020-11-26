package ch.ninecode.sc

import java.util.Calendar

import com.datastax.spark.connector.SomeColumns
import com.datastax.spark.connector._
import com.datastax.spark.connector.cql.CassandraConnector
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.util.Schema

case class ScCassandra (
    session: SparkSession,
    options: ShortCircuitOptions
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
                val resultset = session.execute(s"""select max(run) as hi from "${options.keyspace}".shortcircuit_run where id='${options.id}'""")
                val row = resultset.one
                if (row.isNull(0))
                    1
                else
                    row.getInt(0) + 1
            }
        )
    }

    def storeRun: Int =
    {
        val run = nextRun
        val current_time = Calendar.getInstance
        val rdd = session.sparkContext.parallelize(Seq(
            (
                options.id,
                run,
                options.description,
                options.cim_options.files.mkString(","),
                options.cim_options.toMap,
                current_time,
                options.default_short_circuit_power_max,
                options.default_short_circuit_impedance_max.re,
                options.default_short_circuit_impedance_max.im,
                options.default_short_circuit_power_min,
                options.default_short_circuit_impedance_min.re,
                options.default_short_circuit_impedance_min.im,
                options.base_temperature,
                options.low_temperature,
                options.high_temperature,
                options.cmax,
                options.cmin,
                options.worstcasepf,
                options.cosphi
            )
        ))

        rdd.saveToCassandra(options.keyspace, "shortcircuit_run",
            SomeColumns(
                "id",
                "run",
                "description",
                "cim",
                "cimreaderoptions",
                "run_time",
                "max_default_short_circuit_power",
                "max_default_short_circuit_resistance",
                "max_default_short_circuit_reactance",
                "min_default_short_circuit_power",
                "min_default_short_circuit_resistance",
                "min_default_short_circuit_reactance",
                "base_temperature",
                "low_temperature",
                "high_temperature",
                "cmax",
                "cmin",
                "worstcasepf",
                "cosphi"
            ))

        run
    }

    @SuppressWarnings(Array("org.wartremover.warts.Null"))
    def toShortCircuitRow (result: ScResult): CassandraRow =
    {
        val container = if ((null == result.container) || ("" == result.container)) null else result.container
        val errors = if (null == result.errors) null else result.errors.mkString(",")
        val (fuseString, lastFusesString, lastFusesId, iksplitString, fusemax, fuseok) =
            if (null == result.branches)
                (null, null, null, null, null, null)
            else
            {
                val fuseok = if (result.lastFuseHasMissingValues)
                    null
                else
                    result.fuseOK(options)
                (
                    result.fuseString,
                    result.lastFusesString,
                    result.lastFusesId,
                    result.iksplitString,
                    result.fuseMax(options),
                    fuseok
                )
            }
        val data = Map[String, Any](
            "id" -> options.id,
            "node" -> result.node,
            "equipment" -> result.equipment,
            "terminal" -> result.terminal,
            "container" -> container,
            "errors" -> errors,
            "trafo" -> result.tx,
            "prev" -> result.prev,
            "low_r" -> result.low_r,
            "low_x" -> result.low_x,
            "low_r0" -> result.low_r0,
            "low_x0" -> result.low_x0,
            "low_ik" -> result.low_ik,
            "low_ik3pol" -> result.low_ik3pol,
            "low_ip" -> result.low_ip,
            "low_sk" -> result.low_sk,
            "high_r" -> result.high_r,
            "high_x" -> result.high_x,
            "high_r0" -> result.high_r0,
            "high_x0" -> result.high_x0,
            "high_ik" -> result.high_ik,
            "high_ik3pol" -> result.high_ik3pol,
            "high_ip" -> result.high_ip,
            "high_sk" -> result.high_sk,
            "costerm" -> result.costerm,
            "imax_3ph_low" -> result.imax_3ph_low,
            "imax_1ph_low" -> result.imax_1ph_low,
            "imax_2ph_low" -> result.imax_2ph_low,
            "imax_3ph_med" -> result.imax_3ph_med,
            "imax_1ph_med" -> result.imax_1ph_med,
            "imax_2ph_med" -> result.imax_2ph_med,
            "fuses" -> fuseString,
            "last_fuses" -> lastFusesString,
            "last_fuses_id" -> lastFusesId,
            "iksplit" -> iksplitString,
            "fusemax" -> fusemax,
            "fuseok" -> fuseok
        )
        CassandraRow.fromMap(data)
    }

    def storeShortcircuitTable (results: RDD[ScResult]): Unit =
    {
        val rdd = results.map(toShortCircuitRow)
        rdd.saveToCassandra(
            options.keyspace,
            "shortcircuit",
            SomeColumns(
                "id",
                "node",
                "equipment",
                "terminal",
                "container",
                "errors",
                "trafo",
                "prev",
                "low_r",
                "low_x",
                "low_r0",
                "low_x0",
                "low_ik",
                "low_ik3pol",
                "low_ip",
                "low_sk",
                "high_r",
                "high_x",
                "high_r0",
                "high_x0",
                "high_ik",
                "high_ik3pol",
                "high_ip",
                "high_sk",
                "costerm",
                "imax_3ph_low",
                "imax_1ph_low",
                "imax_2ph_low",
                "imax_3ph_med",
                "imax_1ph_med",
                "imax_2ph_med",
                "fuses",
                "last_fuses",
                "last_fuses_id",
                "iksplit",
                "fusemax",
                "fuseok"
            )
        )
    }

    def isOK (result: ScResult): Option[(String, Option[Boolean])] =
    {
        if ((null == result.container) || ("" == result.container) || (null == result.branches))
            None
        else
            if (result.lastFuseHasMissingValues)
                Some((result.container, None))
            else
                Some((result.container, Some(result.fuseOK(options))))
    }

    def summarize (list: Iterable[Option[Boolean]]): (Boolean, Int, Int, Int) =
    {
        val total = list.size
        val hasvalue = list.flatten
        val isnull = total - hasvalue.size
        val (ok, bad) = hasvalue.partition(x => x)
        val okcount = ok.size
        val badcount = bad.size
        (0 == badcount + isnull, okcount, badcount, isnull)

    }

    def storeFuseSummaryTable (results: RDD[ScResult]): Unit =
    {
        val evaluation = results.flatMap(isOK)
        val summary: RDD[(String, (Boolean, Int, Int, Int))] = evaluation.groupByKey.mapValues(summarize)
        val rdd = summary.map(x => (options.id, x._1, x._2._1, x._2._2, x._2._3, x._2._4))
        rdd.saveToCassandra(
            options.keyspace,
            "fusesummary",
            SomeColumns(
                "id",
                "container",
                "allok",
                "ok",
                "bad",
                "unknown"
            )
        )
    }

    def store (results: RDD[ScResult]): (String, Int) =
    {
        log.info(s"storing short circuit results ${options.id}")

        val schema = Schema(session, "/shortcircuit_schema.sql", options.verbose)
        if (schema.make(keyspace = options.keyspace, replication = options.replication))
        {
            val run = storeRun
            storeShortcircuitTable(results)
            storeFuseSummaryTable(results)
            log.info(s"stored short circuit results ${options.id} run $run")
            (options.id, run)
        }
        else
            ("", -1)
    }
}