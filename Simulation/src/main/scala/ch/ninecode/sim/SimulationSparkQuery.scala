package ch.ninecode.sim

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.slf4j.Logger
import org.slf4j.LoggerFactory

case class SimulationSparkQuery (session: SparkSession, verbose: Boolean = false)
{
    if (verbose)
        org.apache.log4j.LogManager.getLogger (getClass.getName).setLevel (org.apache.log4j.Level.INFO)
    val log: Logger = LoggerFactory.getLogger (getClass)

    val keyfield: String = "island"
    val namefield: String = "name"
    val mridfield: String = "mrid"
    val parentfield: String = "parent"
    val typefield: String = "type"
    val propertyfield: String = "property"
    val unitfield: String = "unit"

    def executePlayerQuery (query: SimulationPlayerQuery): RDD[(String, SimulationPlayerResult)] =
    {
        log.info ("""executing "%s" as %s""".format (query.title, query.query))
        val resultset = session.sql (query.query).cache
        val island = resultset.schema.fieldIndex (keyfield)
        val name = resultset.schema.fieldIndex (namefield)
        val parent = resultset.schema.fieldIndex (parentfield)
        val `type` = resultset.schema.fieldIndex (typefield)
        val property = resultset.schema.fieldIndex (propertyfield)
        resultset.rdd.keyBy (row ⇒ row.getString (island)).mapValues (
            row ⇒
            {
                val substitutions = query.bind.map (y ⇒ row.getString (row.schema.fieldIndex (y)))
                SimulationPlayerResult (
                    query.title,
                    query.cassandraquery,
                    substitutions,
                    row.getString (name),
                    row.getString (parent),
                    row.getString (`type`),
                    row.getString (property))
            }
        )
    }

    def executeRecorderQuery (query: SimulationRecorderQuery): RDD[(String, SimulationRecorderResult)] =
    {
        log.info ("""executing "%s" as %s""".format (query.title, query.query))
        val resultset = session.sql (query.query).cache
        val island = resultset.schema.fieldIndex (keyfield)
        val name = resultset.schema.fieldIndex (namefield)
        val mrid = resultset.schema.fieldIndex (mridfield)
        val parent = resultset.schema.fieldIndex (parentfield)
        val `type` = resultset.schema.fieldIndex (typefield)
        val property = resultset.schema.fieldIndex (propertyfield)
        val unit = resultset.schema.fieldIndex (unitfield)
        resultset.rdd.keyBy (row ⇒ row.getString (island)).mapValues (
            row ⇒
            {
                SimulationRecorderResult (
                    query.title,
                    query.interval,
                    query.aggregations,
                    row.getString (name),
                    row.getString (mrid),
                    row.getString (parent),
                    row.getString (`type`),
                    row.getString (property),
                    row.getString (unit))
            }
        )
    }
}
