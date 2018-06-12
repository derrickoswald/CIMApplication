package ch.ninecode.sim

import java.io.InputStream

import scala.io.Source

import com.datastax.spark.connector.cql.CassandraConnector
import org.apache.spark.sql.SparkSession
import org.slf4j.Logger
import org.slf4j.LoggerFactory

case class Schema (session: SparkSession, options: SimulationOptions)
{
    if (options.verbose)
        org.apache.log4j.LogManager.getLogger (getClass.getName).setLevel (org.apache.log4j.Level.INFO)
    implicit val log: Logger = LoggerFactory.getLogger (getClass)

    val resource = """/schema.sql"""
    val default_keyspace = """cimapplication"""

    def make: Boolean =
    {
        val schema: InputStream = this.getClass.getResourceAsStream (resource)
        if (null != schema)
        {
            log.info ("""ensuring Cassandra keyspace %s exists""".format (options.keyspace))

            // separate at blank lines
            var accumulator: List[String] = List()
            var sqls: Iterator[String] = Source.fromInputStream (schema, "UTF-8").getLines.flatMap (
                line ⇒
                    if ("" == line)
                    {
                        val lines = accumulator.mkString ("\n")
                        val sql =
                            if (options.keyspace != default_keyspace)
                                lines.replace (default_keyspace, options.keyspace)
                            else
                                lines
                        accumulator = List()
                        List (sql)
                    }
                    else
                    {
                        accumulator  = accumulator :+ line
                        List ()
                    }

            )
            sqls.forall (
                sql ⇒
                {
                    try
                    {
                        CassandraConnector (session.sparkContext.getConf).withSessionDo (session => session.execute (sql))
                        true
                    }
                    catch
                    {
                        case exception: Exception ⇒ log.error ("""failed to create schema in Cassandra keyspace %s""".format (options.keyspace), exception)
                        false
                    }
                }
            )
        }
        else
            false
    }
}
