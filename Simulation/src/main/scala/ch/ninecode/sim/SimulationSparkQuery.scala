package ch.ninecode.sim

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.Logger
import org.slf4j.LoggerFactory

case class SimulationSparkQuery (session: SparkSession, storage_level: StorageLevel, verbose: Boolean = false)
{
    if (verbose)
        org.apache.log4j.LogManager.getLogger(getClass.getName).setLevel(org.apache.log4j.Level.INFO)
    val log: Logger = LoggerFactory.getLogger(getClass)
    val executors: Int = Math.max(2, session.sparkContext.getExecutorMemoryStatus.size - 1)
    val partitions: Int = 4 * executors

    val keyfield: String = "island"
    val namefield: String = "name"
    val mridfield: String = "mrid"
    val parentfield: String = "parent"
    val typefield: String = "type"
    val propertyfield: String = "property"
    val unitfield: String = "unit"
    val synthesisfield: String = "synthesis"

    def pack (string: String): String =
    {
        string.replace("\n", " ").replaceAll("[ ]+", " ").trim
    }

    def executePlayerQuery (query: SimulationPlayerQuery): RDD[(String, SimulationPlayerResult)] =
    {
        log.info("""executing "%s" as %s""".format(query.title, pack(query.query)))
        val resultset = session.sql(query.query)
        val island = resultset.schema.fieldIndex(keyfield)
        val name = resultset.schema.fieldIndex(namefield)
        val parent = resultset.schema.fieldIndex(parentfield)
        val mrid = resultset.schema.fieldIndex(mridfield)
        val `type` = resultset.schema.fieldIndex(typefield)
        val property = resultset.schema.fieldIndex(propertyfield)
        val synthesis = if (resultset.schema.fieldNames.contains(synthesisfield)) resultset.schema.fieldIndex(synthesisfield) else -1
        resultset.rdd.keyBy(row => row.getString(island)).mapValues(
            row =>
            {
                SimulationPlayerResult(
                    query.title,
                    row.getString(name),
                    row.getString(parent),
                    row.getString(mrid),
                    row.getString(`type`),
                    row.getString(property),
                    query.transform,
                    if (-1 == synthesis) "" else row.getString(synthesis))
            }
        ).coalesce(partitions).persist(storage_level).setName(query.title)
    }

    def executeRecorderQuery (query: SimulationRecorderQuery): RDD[(String, SimulationRecorderResult)] =
    {
        log.info("""executing "%s" as %s""".format(query.title, pack(query.query)))
        val resultset = session.sql(query.query)
        val island = resultset.schema.fieldIndex(keyfield)
        val name = resultset.schema.fieldIndex(namefield)
        val mrid = resultset.schema.fieldIndex(mridfield)
        val parent = resultset.schema.fieldIndex(parentfield)
        val `type` = resultset.schema.fieldIndex(typefield)
        val property = resultset.schema.fieldIndex(propertyfield)
        val unit = resultset.schema.fieldIndex(unitfield)
        resultset.rdd.keyBy(row => row.getString(island)).mapValues(
            row =>
            {
                SimulationRecorderResult(
                    query.title,
                    query.interval,
                    query.aggregations,
                    row.getString(name),
                    row.getString(mrid),
                    row.getString(parent),
                    row.getString(`type`),
                    row.getString(property),
                    row.getString(unit))
            }
        ).coalesce(partitions).persist(storage_level).setName(query.title)
    }
}
