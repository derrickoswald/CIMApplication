package ch.ninecode.sim

import com.datastax.spark.connector._
import com.datastax.spark.connector.SomeColumns
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.SparkSession
import org.slf4j.Logger
import org.slf4j.LoggerFactory


/**
 * Queries for extra property data.
 *
 * @param title The name for the extra query.
 *              This is used as part of the Cassandra primary key (in addition to the simulation id and the key field).
 * @param query The query to get key value pairs.
 *              Must return key and value.
 *              The simulation will be added automatically
 */
case class SimulationExtraQuery
(
    title: String,
    query: String
)
{
    implicit val log: Logger = LoggerFactory.getLogger(getClass)

    def executeQuery (job: SimulationJob, session: SparkSession): Unit =
    {
        log.debug(s"""executing "${title}" as ${query}""")
        val df: DataFrame = session.sql(query).persist()
        if (df.count > 0)
        {
            val fields = df.schema.fieldNames
            if (!fields.contains("key") || !fields.contains("value"))
                log.error(s"""extra query "${title}" schema either does not contain a "key" or a "value" field: ${fields.mkString}""")
            else
            {
                val keyindex = df.schema.fieldIndex("key")
                val valueindex = df.schema.fieldIndex("value")
                val keytype = df.schema.fields(keyindex).dataType.simpleString
                val valuetype = df.schema.fields(valueindex).dataType.simpleString
                if ((keytype != "string") || (valuetype != "string"))
                    log.error(s"""extra query "${title}" schema fields key and value are not both strings (key=$keytype, value=$valuetype)""")
                else
                    df.rdd.map(row => (job.id, title, row.getString(keyindex), row.getString(valueindex))).saveToCassandra(job.output_keyspace, "key_value", SomeColumns("simulation", "query", "key", "value"))
            }
        }
        else
            log.warn(s"""extra query "${title}" returned no rows""")
        val _ = df.unpersist(false)
    }
}

object SimulationExtraQuery
{
    @SuppressWarnings(Array("org.wartremover.warts.Null"))
    def apply (title: String, queries: Seq[String]): SimulationExtraQuery =
        SimulationExtraQuery(title, queries.lastOption.orNull)
}