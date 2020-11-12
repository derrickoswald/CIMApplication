package ch.ninecode.cim.cimweb

import java.io.StringReader
import java.util.logging.Level
import java.util.logging.Logger

import javax.json.Json
import javax.json.JsonObject
import javax.json.JsonStructure
import javax.json.JsonException
import org.apache.spark.sql.SparkSession
import org.slf4j.LoggerFactory

import ch.ninecode.cim.cimweb.RESTfulJSONResult.OK
import ch.ninecode.cim.connector.CIMFunction.Return
import ch.ninecode.ingest.IngestOptions
import ch.ninecode.util.MainOptions
import ch.ninecode.util.SparkOptions

/**
 * Ingest smart meter data.
 *
 * @param job the details of the job for ingesting
 */
case class IngestFunction (job: String) extends CIMWebFunction
{
    jars = Array(
        jarForObject(this),
        jarForObject(IngestOptions()), // Ingest.jar
        jarForObject(MainOptions()), // Util.jar
        jarForObject(com.datastax.oss.driver.api.core.ConsistencyLevel.ANY), // spark-cassandra-connector.jar
        jarForObject(com.datastax.oss.driver.shaded.guava.common.collect.ImmutableListMultimap.of[String,String]()), // com/datastax/oss/driver/shaded/guava/common/collect/
        jarForObject(new com.datastax.oss.protocol.internal.util.Flags ()), // com.datastax.oss.protocol.internal.util.collection.NullAllowingImmutableMap
        jarForClass (classOf[org.reactivestreams.Publisher[_]]), // org/reactivestreams/Publisher
        jarForObject(com.typesafe.config.ConfigMemorySize.ofBytes(0)), // com/typesafe/config/ConfigMergeable
        jarForObject(Json.createObjectBuilder)) // javaee-api <JSON implementation>.jar
    override def getReturnType: Return = Return.JSON

    def readJSON (json: String): Option[JsonObject] =
    {
        try
            try
            Json.createReader(new StringReader(json)).readObject match
            {
                case obj: JsonObject => Some(obj)
                case _ =>
                    Logger.getLogger(getClass.getName).log(Level.SEVERE, """not a JsonObject""")
                    None
            }
            catch
            {
                case je: JsonException =>
                    Logger.getLogger(getClass.getName).log(Level.SEVERE, """unparseable as JSON""", je)
                    None
            }
        catch
        {
            case e: Exception =>
                Logger.getLogger(getClass.getName).log(Level.SEVERE, "cannot create JSON reader", e)
                None
        }
    }

    /**
     * Executes the ingest specified by options.
     *
     * @param spark the Spark session to use
     * @return the ingest status and options used
     */
    override def executeJSON (spark: SparkSession): JsonStructure =
    {
        val temp = IngestOptions()
        // since these are set when the Spark instance is created, they cannot affect the Ingest run, but include them anyway
        val host = spark.sparkContext.getConf.get("spark.cassandra.connection.host", "localhost")
        val port = spark.sparkContext.getConf.get("spark.cassandra.connection.port", "9042")
        val options = IngestOptions(
            spark_options = SparkOptions(
                master = spark.sparkContext.master,
                options = temp.spark_options.options + (
                    "spark.cassandra.connection.host" -> host,
                    "spark.cassandra.connection.port" -> port)),
            verbose = true,
            host = host,
            port = port.toInt,
            workdir = "/work/",
            ingestions = Seq(job)
        )
        val ingest = new ch.ninecode.ingest.Ingest(spark, options)
        ingest.run()
        LoggerFactory.getLogger(getClass).info("ingested")
        val result = Json.createObjectBuilder
            .add("verbose", options.verbose)
            .add("host", options.host)
            .add("port", options.port)
            .add("workdir", options.workdir)
            .add("ingestions", Json.createObjectBuilder(readJSON(job).getOrElse(Json.createObjectBuilder.build)))
        RESTfulJSONResult(OK, "ingest successful", result.build).getJSON
    }

    override def toString: String =
    {
        s"${super.toString} is IngestFunction"
    }
}