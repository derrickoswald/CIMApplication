package ch.ninecode.ts

import java.util.Date

import scala.collection.JavaConversions._
import com.datastax.driver.core.ResultSet
import com.datastax.driver.core.Row
import com.datastax.spark.connector.cql.CassandraConnector
import org.apache.commons.lang.StringUtils
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.Logger
import org.slf4j.LoggerFactory

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
    if (options.verbose) org.apache.log4j.LogManager.getLogger (getClass.getName).setLevel (org.apache.log4j.Level.INFO)
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

    def Range (mrid: String, `type`: String): (String, String, Date, Date, Long, Double) =
    {
        val sql = """select mrid, type, min(time) as min, max(time) as max, count(mrid) as count, cimapplication.standard_deviation (real_a) as standard_deviation from %s.measured_value where mrid='%s' and type = '%s' group by mrid, type""".format (options.keyspace, mrid, `type`)
        val range = CassandraConnector (session.sparkContext.getConf).withSessionDo
        {
            session ⇒
                val row = session.execute (sql).one()
                (row.getString(0), row.getString(1), row.getTimestamp (2), row.getTimestamp (3), row.getLong (4), row.getDouble (5))
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
        for (r ← range)
            log.info ("%s:%s %s⇒%s %8d %10.3f".format (
                StringUtils.leftPad (r._1, 10, " "),
                StringUtils.rightPad (r._2, 7, " "),
                StringUtils.leftPad (r._3.toString, 30, " "),
                StringUtils.rightPad (r._4.toString, 30, " "),
                r._5,
                r._6))
        val end = System.nanoTime ()
        log.info ("process: %s seconds".format ((end - begin) / 1e9))
    }
}
