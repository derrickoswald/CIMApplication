package ch.ninecode.ts

import java.util.Date

import scala.collection.JavaConverters._

import org.apache.commons.lang.StringUtils
import org.apache.spark.sql.SparkSession
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import com.datastax.oss.driver.api.core.ConsistencyLevel
import com.datastax.oss.driver.api.core.cql.ResultSet
import com.datastax.oss.driver.api.core.cql.Row
import com.datastax.spark.connector.cql.CassandraConnector
import com.datastax.spark.connector._
import com.datastax.spark.connector.writer.WriteConf

/**
 * Validate, correct and model time series in Cassandra.
 *
 * [Eventually] Checks for data outliers, missing values, start and end mismatches in the measured_value table,
 * and generates load-profile model(s) that can be used to generate random load-profiles or fill in missing values.
 *
 * @param session The Spark session to use.
 * @param options Options Processing options.
 */
case class TimeSeriesStats (session: SparkSession, options: TimeSeriesOptions)
{
    org.apache.log4j.LogManager.getLogger (getClass).setLevel (options.spark_options.log)
    val log: Logger = LoggerFactory.getLogger (getClass)

    def Scope: Seq[(String, String)] =
    {
        val sql = """select distinct mrid, type from %s.measured_value""".format (options.keyspace)
        val iterator = CassandraConnector (session.sparkContext.getConf).withSessionDo
        {
            session =>
                val resultset: ResultSet = session.execute (sql)
                for (row: Row <- resultset.iterator.asScala)
                    yield (row.getString (0), row.getString (1))
        }
        iterator.toSeq
    }

    def Range (mrid: String, `type`: String): Option[(String, String, Date, Date, Long, Int, Double, Double, Double, Double)] =
    {
        // assumes the period is always the same
        val sql = s"""select mrid, type, min(period) as period, min(time) as min, max(time) as max, count(mrid) as count, min(real_a) as min, avg(real_a) as avg, max(real_a) as max, ${options.keyspace}.standard_deviation (real_a) as standard_deviation from ${options.keyspace}.measured_value where mrid='$mrid' and real_a > 0.0 and type = '${`type`}' group by mrid, type allow filtering"""
        val range = CassandraConnector (session.sparkContext.getConf).withSessionDo
        {
            session =>
                val row = session.execute (sql).one ()
                if (null != row)
                {
                    val period = row.getInt (2)
                    //                    val start = row.getTimestamp (3).getTime
                    //                    val end = row.getTimestamp (4).getTime
                    val start = row.getInstant (3).toEpochMilli
                    val end = row.getInstant (4).toEpochMilli
                    val expected = (end - start + period) / period
                    val count = row.getLong (5)
                    val missing = (expected - count).toInt
                    Some ((row.getString (0), row.getString (1), new Date (start), new Date (end), count, missing, row.getDouble (6), row.getDouble (7), row.getDouble (8), row.getDouble (9)))
                }
                else
                    None
        }
        range
    }

    def run (): Unit =
    {
        val begin = System.nanoTime ()
        val scope = Scope
        val count = scope.size
        log.info ("%s distinct mrid and type".format (count))
        val range = for ((mrid, typ) <- scope)
            yield Range (mrid, typ)
        val stats = for (r <- range.flatten)
            yield
                {
                    log.info ("%s:%s %s⇒%s %8d %6d %10.3f %10.3f %10.3f %10.3f".format (
                        StringUtils.leftPad (r._1, 7, " "),
                        StringUtils.rightPad (r._2, 6, " "),
                        StringUtils.leftPad (r._3.toString, 30, " "),
                        StringUtils.rightPad (r._4.toString, 30, " "),
                        r._5,
                        r._6,
                        r._7,
                        r._8,
                        r._9,
                        r._10))
                    r
                }
        val columns = SomeColumns ("mrid", "type", "start", "end", "count", "missing", "minimum", "average", "maximum", "stddev")
        val writeConf = WriteConf (consistencyLevel = ConsistencyLevel.ANY)
        session.sparkContext.parallelize (stats).saveToCassandra (options.keyspace, "measured_value_stats", columns, writeConf)
        val end = System.nanoTime ()
        log.info ("process: %s seconds".format ((end - begin) / 1e9))
    }
}
