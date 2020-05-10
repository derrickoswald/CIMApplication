package ch.ninecode.cim.cimweb

import java.text.SimpleDateFormat
import java.util.Calendar

import javax.json.Json
import javax.json.JsonObjectBuilder
import javax.json.JsonStructure

import scala.collection.JavaConversions.asScalaIterator
import scala.collection.JavaConversions.iterableAsScalaIterable

import org.apache.spark.sql.SparkSession

case class PongFunction () extends CIMWebFunction
{
    // get the HDFS properties
    def filesystemConfiguration: JsonObjectBuilder =
    {
        val filesystem_configuration = Json.createObjectBuilder
        for (entry <- hdfs_configuration.iterator)
            filesystem_configuration.add (entry.getKey, entry.getValue)
        filesystem_configuration
    }

    // get the capacity and remaining
    def filesystemStatus: JsonObjectBuilder =
    {
        val status = hdfs.getStatus ()
        Json.createObjectBuilder
            .add ("capacity", status.getCapacity)
            .add ("used", status.getUsed)
            .add ("remaining", status.getRemaining)
    }

    def startTime (spark: SparkSession): String =
    {
        val start = Calendar.getInstance
        start.setTimeInMillis (spark.sparkContext.startTime)
        new SimpleDateFormat ("yyyy-MM-dd'T'HH:mm:ssXXX").format (start.getTime)
    }

    // get the Spark configuration
    def sparkConfiguration (spark: SparkSession): JsonObjectBuilder =
    {
        val spark_configuration = Json.createObjectBuilder
        for (entry <- spark.sparkContext.getConf.getAll)
            spark_configuration.add (entry._1, entry._2)
        spark_configuration
    }

    def executorStats (spark: SparkSession): JsonObjectBuilder =
    {
        // get the executors
        val executors = Json.createObjectBuilder
        for (executor <- spark.sparkContext.getExecutorMemoryStatus)
        {
            val memory = Json.createObjectBuilder
                .add ("max_memory_available", executor._2._1)
                .add ("remaining_memory_available", executor._2._2)
            executors.add (executor._1, memory)
        }
        executors
    }

    // get the Hadoop configuration
    def hadoopConfiguration (spark: SparkSession): JsonObjectBuilder =
    {
        val hadoop_configuration = Json.createObjectBuilder
        for (entry <- spark.sparkContext.hadoopConfiguration)
            hadoop_configuration.add (entry.getKey, entry.getValue)
        hadoop_configuration
    }

    override def executeJSON (spark: SparkSession): JsonStructure =
    {
        // form the response
        val response = Json.createObjectBuilder
            .add ("filesystem", uri.toString)
            .add ("filesystem_configuration", filesystemConfiguration)
            .add ("filesystem_status", filesystemStatus)
            .add ("spark_master", spark.sparkContext.master)
            .add ("spark_version", spark.version) // get the Spark version
            .add ("spark_user", spark.sparkContext.sparkUser)
            .add ("spark_start_time", startTime (spark))
            .add ("spark_application_id", spark.sparkContext.applicationId)
            .add ("spark_application_name", spark.sparkContext.appName)
            .add ("spark_configuration", sparkConfiguration (spark))
            .add ("executors", executorStats (spark))
            .add ("hadoop_configuration", hadoopConfiguration (spark))
        spark.sparkContext.uiWebUrl.foreach (ui => response.add ("spark_application_ui_url", ui))

        response.build
    }

    override def toString: String = s"${super.toString} ()"
}