package ch.ninecode.copy

import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import com.datastax.oss.driver.api.core.ConsistencyLevel
import com.datastax.spark.connector.CassandraRow
import com.datastax.spark.connector.SomeColumns
import com.datastax.spark.connector.cql.CassandraConnector
import com.datastax.spark.connector.rdd.CassandraTableScanRDD
import com.datastax.spark.connector.rdd.ReadConf
import com.datastax.spark.connector.writer.WriteConf
import com.datastax.spark.connector._

import ch.ninecode.util.Main
import ch.ninecode.util.MainOptions
import ch.ninecode.util.Schema
import ch.ninecode.util.SparkInitializer
import ch.ninecode.util.SparkOptions

/**
 * Copy between Cassandra instances and/or keyspaces.
 *
 * @param session The Spark session to use.
 * @param options Options regarding Cassandra source and target.
 */
case class Copy (session: SparkSession, options: CopyOptions)
{
    org.apache.log4j.LogManager.getLogger (getClass).setLevel (org.apache.log4j.Level.INFO)
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
        val schema = Schema (session, "/simulation_schema.sql", true)
        if (schema.make (target, options.target_keyspace, options.target_replication))
        {
            val target_tables: Array[String] =
            {
                implicit val c: CassandraConnector = target
                val t = session.sparkContext.cassandraTable ("system_schema", "tables")
                val f = t.where (s"keyspace_name='${options.target_keyspace}'")
                f.collect.map (_.getString ("table_name"))
            }
            log.info (s"tables ${target_tables.mkString (",")}")

            val source_tables: Array[String] =
            {
                implicit val c: CassandraConnector = source
                val t = session.sparkContext.cassandraTable ("system_schema", "tables")
                val f = t.where (s"keyspace_name='${options.source_keyspace}'")
                f.collect.map (_.getString ("table_name"))
            }

            for (table <- target_tables if source_tables.contains (table))
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

object Copy extends SparkInitializer[CopyOptions] with Main
{
    def same (options: CopyOptions): Boolean =
    {
        options.source_host == options.target_host &&
            options.source_keyspace == options.target_keyspace
    }

    def run (options: CopyOptions): Unit =
    {
        if (options.main_options.valid)
        {
            org.apache.log4j.LogManager.getLogger (getClass).setLevel (org.apache.log4j.Level.INFO)
            if (!same (options))
            {
                val session: SparkSession = createSession (options)
                time ("execution: %s seconds")
                {
                    Copy (session, options).run ()
                }
            }
            else
                log.error (s"""copy to the same host "${options.source_host}" and keyspace "${options.source_keyspace}" ignored""")
        }
    }

    def main (args: Array[String])
    {
        val have = scala.util.Properties.versionNumberString
        val need = scala_library_version
        if (have != need)
        {
            log.error (s"Scala version ($have) does not match the version ($need) used to build $application_name")
            sys.exit (1)
        }
        else
        {
            // get the necessary jar files to send to the cluster
            val jars = Set (
                jarForObject (com.datastax.spark.connector.mapper.ColumnMapper),
                jarForObject (CopyOptions ())
            ).toArray

            // initialize the default options
            val default = CopyOptions (
                main_options = MainOptions (application_name, application_version),
                spark_options = SparkOptions (jars = jars),
            )

            // parse the command line arguments
            new CopyOptionsParser (default).parse (args, default) match
            {
                case Some (options) =>
                    // execute the main program if everything checks out
                    run (options)
                    if (!options.main_options.unittest)
                        sys.exit (0)
                case None =>
                    sys.exit (1)
            }
        }
    }
}
