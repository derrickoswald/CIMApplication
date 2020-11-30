package ch.ninecode.cim.cimweb

import java.io.StringReader
import java.util.logging.Level
import java.util.logging.Logger

import javax.json.Json
import javax.json.JsonObject
import javax.json.JsonStructure
import javax.json.JsonException
import javax.json.JsonString
import org.apache.spark.sql.SparkSession
import org.slf4j.LoggerFactory

import ch.ninecode.cim.cimweb.RESTfulJSONResult.OK
import ch.ninecode.cim.connector.CIMFunction.Return
import ch.ninecode.ingest.IngestOptions
import ch.ninecode.util.MainOptions

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
        jarForClass(classOf[javax.json.JsonStructure])) // javaee-api <JSON implementation>.jar
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
    @SuppressWarnings(Array("org.wartremover.warts.Null"))
    override def executeJSON (spark: SparkSession): JsonStructure =
    {
        val json = readJSON(job).getOrElse(Json.createObjectBuilder.build)
        val id = if (json.containsKey("id"))
        {
            val value = json.get("id")
            value match
            {
                case string: JsonString => string.getString
                case _ => "Ingest"
            }
        }
        else
            "Ingest"
        val options = IngestOptions(
            verbose = true,
            workdir = "/work/",
            ingestions = Seq(job)
        )
        val ingest = new ch.ninecode.ingest.Ingest(spark, options)
        spark.sparkContext.setJobGroup(id, "ingest smart meter data")
        ingest.run()
        LoggerFactory.getLogger(getClass).info("ingested")
        spark.sparkContext.setJobGroup(null, null)
        val result = Json.createObjectBuilder
            .add("verbose", options.verbose)
            .add("workdir", options.workdir)
            .add("ingestions", Json.createObjectBuilder(json))
        RESTfulJSONResult(OK, "ingest successful", result.build).getJSON
    }

    override def toString: String =
    {
        s"${super.toString} is IngestFunction"
    }
}