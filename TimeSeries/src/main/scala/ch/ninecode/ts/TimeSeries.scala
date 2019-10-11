package ch.ninecode.ts

import java.util.Date

import scala.collection.JavaConversions._

import org.apache.commons.lang.StringUtils
import org.apache.log4j.Level
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import com.datastax.driver.core.ResultSet
import com.datastax.driver.core.Row
import com.datastax.spark.connector.cql.CassandraConnector

/**
 * Validate, correct and model time series in Cassandra.
 *
 * [Eventually] Checks for data outliers, missing values, start and end mismatches in the measured_value table,
 * and generates load-profile model(s) that can be used to generate random load-profiles or fill in missing values.
 *
 * @param session The Spark session to use.
 * @param options Options Processing options.
 */
case class TimeSeries (session: SparkSession, options: TimeSeriesOptions)
{
    org.apache.log4j.LogManager.getLogger (getClass.getName).setLevel (Level.toLevel (options.log_level.toString))
    val log: Logger = LoggerFactory.getLogger (getClass)
    val storage_level: StorageLevel = StorageLevel.fromString (options.storage)

    def Scope: Seq[(String, String)] =
    {
        val sql = """select distinct mrid, type from %s.measured_value""".format (options.keyspace)
        val iterator = CassandraConnector (session.sparkContext.getConf).withSessionDo
        {
            session =>
                val resultset: ResultSet = session.execute (sql)
                for (row: Row ← resultset.iterator)
                    yield (row.getString(0), row.getString(1))
        }
        iterator.toSeq
    }

    def Range (mrid: String, `type`: String): (String, String, Date, Date, Long, Double, Double, Double, Double) =
    {
        val sql = s"""select mrid, type, min(time) as min, max(time) as max, count(mrid) as count, min(real_a) as min, avg(real_a) as avg, max(real_a) as max, ${options.keyspace}.standard_deviation (real_a) as standard_deviation from ${options.keyspace}.measured_value where mrid='$mrid' and real_a > 0.0 and type = '${`type`}' group by mrid, type allow filtering"""
        val range = CassandraConnector (session.sparkContext.getConf).withSessionDo
        {
            session ⇒
                val row = session.execute (sql).one()
                if (null != row)
                    (row.getString(0), row.getString(1), row.getTimestamp (2), row.getTimestamp (3), row.getLong (4), row.getDouble (5), row.getDouble (6), row.getDouble (7), row.getDouble (8))
                else
                    null
        }
        range
    }

    def run (): Unit =
    {
        val begin = System.nanoTime ()
        val scope = Scope
        val count = scope.size
        log.info ("%s distinct mrid and type".format (count))
        val range = for ((mrid, typ) ← scope)
            yield Range (mrid, typ)
        for (
            r ← range
            if null != r
            )
        {
            val expected = (r._4.getTime - r._3.getTime + 900000) / 900000
            log.info ("%s:%s %s⇒%s %8d %8d %10.3f %10.3f %10.3f %10.3f".format (
                StringUtils.leftPad (r._1, 10, " "),
                StringUtils.rightPad (r._2, 7, " "),
                StringUtils.leftPad (r._3.toString, 30, " "),
                StringUtils.rightPad (r._4.toString, 30, " "),
                r._5,
                (expected - r._5),
                r._6,
                r._7,
                r._8,
                r._9))
        }
        val end = System.nanoTime ()
        log.info ("process: %s seconds".format ((end - begin) / 1e9))
    }
}
