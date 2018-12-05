package ch.ninecode.sim

import java.io.InputStream

import scala.io.Source

import com.datastax.spark.connector.cql.CassandraConnector
import org.apache.spark.sql.SparkSession
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/*
 * Create the schema in Cassandra.
 *
 * Requires adjustment of cassandra.yaml to enable user defined functions
 * and scripted user defined functions:
 *
 * $ sed --in-place 's/enable_user_defined_functions: false/enable_user_defined_functions: true/g' /etc/cassandra/cassandra.yaml \
 * $ sed --in-place 's/enable_scripted_user_defined_functions: false/enable_scripted_user_defined_functions: true/g' /etc/cassandra/cassandra.yaml
 *
 */
case class Schema (session: SparkSession, options: SimulationOptions)
{
    if (options.verbose)
        org.apache.log4j.LogManager.getLogger (getClass.getName).setLevel (org.apache.log4j.Level.INFO)
    implicit val log: Logger = LoggerFactory.getLogger (getClass)

    val resource = """/schema.sql"""
    val default_keyspace = """cimapplication"""

    def toKeySpace (lines: String): String =
    {
        if (options.keyspace != default_keyspace)
            lines.replace (default_keyspace, options.keyspace)
        else
            lines
    }

    /**
     * Create the schema according to the schema.sql file.
     *
     * The file is in a special form:
     *   - DDL statements are separated by a blank line
     *   - only DDL is permitted in the schema script
     *   - the keyspace must be cimapplication - which is changed according to options.keyspace via simple global substitution
     *
     * @return <code>true</code> if all DDL executed successsuflly, <code>false</code> if the schema file doesn't exist or there were errors
     */
    def make: Boolean =
    {
        val schema = this.getClass.getResourceAsStream (resource)
        if (null != schema)
        {
            log.info ("""ensuring Cassandra keyspace %s exists""".format (options.keyspace))

            // separate at blank lines and change keyspace
            var sqls = Source.fromInputStream (schema, "UTF-8").getLines.mkString ("\n").split ("\n\n").map (toKeySpace)

            // need to apply each DDL separateley
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
