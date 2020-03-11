package ch.ninecode.copy

import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import com.datastax.driver.core.ConsistencyLevel
import com.datastax.spark.connector.CassandraRow
import com.datastax.spark.connector.SomeColumns
import com.datastax.spark.connector.cql.CassandraConnector
import com.datastax.spark.connector.rdd.CassandraTableScanRDD
import com.datastax.spark.connector.rdd.ReadConf
import com.datastax.spark.connector.writer.WriteConf
import com.datastax.spark.connector._

import ch.ninecode.util.Schema

/**
 * Copy between Cassandra instances and/or keyspaces.
 *
 * @param session The Spark session to use.
 * @param options Options regarding Cassandra source and target.
 */
case class Copy (session: SparkSession, options: CopyOptions)
{
    org.apache.log4j.LogManager.getLogger (getClass.getName).setLevel (org.apache.log4j.Level.INFO)
    implicit val log: Logger = LoggerFactory.getLogger (getClass)
    val HOST = "spark.cassandra.connection.host"
    val PORT = "spark.cassandra.connection.port"

    def cassandra (host: String, port: Int): SparkConf =
    {
        val s = session.sparkContext.getConf
        s.set (HOST, host).set (PORT, port.toString)
    }

    def run (): Unit =
    {
        val source: CassandraConnector = CassandraConnector (cassandra (options.source_host, options.source_port))
        val target: CassandraConnector = CassandraConnector (cassandra (options.target_host, options.target_port))
        val schema = Schema (session, "/simulation_schema.sql", options.target_keyspace, options.target_replication, true)
        if (schema.make (target))
        {
            val tables: Array[String] =
            {
                implicit val c: CassandraConnector = target
                val t = session.sparkContext.cassandraTable ("system_schema", "tables")
                val f = t.where (s"keyspace_name='${options.target_keyspace}'")
                f.collect.map (_.getString ("table_name"))
            }
            log.info (s"tables ${tables.mkString (",")}")

            for (table <- tables)
            {
                val (data, columns) =
                {
                    // sets source as default connection for everything in this code block
                    implicit val c: CassandraConnector = source
                    implicit val configuration: ReadConf = ReadConf.fromSparkConf (session.sparkContext.getConf).copy (consistencyLevel = ConsistencyLevel.ONE)
                    val rdd: CassandraTableScanRDD[CassandraRow] = session.sparkContext.cassandraTable (options.source_keyspace, table)
                    val cols = rdd.columnNames.selectFrom (rdd.tableDef)
                    log.info (s"table $table columns ${cols.map (_.columnName).mkString (",")}")
                    (rdd, SomeColumns (cols: _*))
                }

                {
                    // sets target as the default connection for everything in this code block
                    implicit val c: CassandraConnector = target
                    val configuration = WriteConf.fromSparkConf (session.sparkContext.getConf).copy (consistencyLevel = ConsistencyLevel.ANY)
                    data.saveToCassandra (options.target_keyspace, table, columns, configuration)
                    log.info (s"table $table copied")
                }
            }
        }
    }
}




