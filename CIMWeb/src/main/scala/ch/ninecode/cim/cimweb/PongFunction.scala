package ch.ninecode.cim.cimweb

import java.text.SimpleDateFormat
import java.util.Calendar
import javax.json.Json
import javax.json.JsonStructure

import org.apache.spark.sql.SparkSession

import scala.collection.JavaConversions.asScalaIterator
import scala.collection.JavaConversions.iterableAsScalaIterable

case class PongFunction () extends CIMWebFunction
{
    override def executeJSON (spark: SparkSession): JsonStructure =
    {
        // form the response
        val response = Json.createObjectBuilder

        // get the filesystem
        response.add ("filesystem", uri.toString)

        // get the HDFS properties
        val filesystem_configuration = Json.createObjectBuilder
        for (entry ← hdfs_configuration.iterator)
            filesystem_configuration.add (entry.getKey, entry.getValue)
        response.add ("filesystem_configuration", filesystem_configuration)

        // get the capacity and remaining
        val status = hdfs.getStatus (null)
        val filesystem_status = Json.createObjectBuilder
        filesystem_status.add ("capacity", status.getCapacity)
        filesystem_status.add ("used", status.getUsed)
        filesystem_status.add ("remaining", status.getRemaining)
        response.add ("filesystem_status", filesystem_status)

        // get the Spark version
        response.add ("spark_master", spark.sparkContext.master)
        response.add ("spark_version", spark.version)
        response.add ("spark_user", spark.sparkContext.sparkUser)
        val start = Calendar.getInstance
        start.setTimeInMillis (spark.sparkContext.startTime)
        response.add ("spark_start_time", new SimpleDateFormat ("yyyy-MM-dd'T'HH:mm:ssXXX").format (start.getTime))
        response.add ("spark_application_id", spark.sparkContext.applicationId)
        response.add ("spark_application_name", spark.sparkContext.appName)
        spark.sparkContext.uiWebUrl match { case Some(ui) ⇒ response.add ("spark_application_ui_url", ui) case None ⇒ }

        // get the Spark configuration
        val spark_configuration = Json.createObjectBuilder
        for (entry <- spark.sparkContext.getConf.getAll)
            spark_configuration.add (entry._1, entry._2)
        response.add ("spark_configuration", spark_configuration)

        // get the executors
        val executors = Json.createObjectBuilder
        for (executor ← spark.sparkContext.getExecutorMemoryStatus)
        {
            val memory = Json.createObjectBuilder
            memory.add ("max_memory_available", executor._2._1)
            memory.add ("remaining_memory_available", executor._2._2)
            executors.add (executor._1, memory)
        }
        response.add ("executors", executors)

        // get the Hadoop configuration
        val hadoop_configuration = Json.createObjectBuilder
        for (entry <- spark.sparkContext.hadoopConfiguration)
            hadoop_configuration.add (entry.getKey, entry.getValue)
        response.add ("hadoop_configuration", hadoop_configuration)

        response.build
    }

    override def toString: String =
    {
        val sb = new StringBuilder (super.toString)
        sb.append (" ()")
        sb.toString
    }
}