package ch.ninecode.cim.cimweb

import javax.json.Json
import javax.json.JsonStructure

import com.datastax.driver.core.Cluster
import org.apache.spark.sql.SparkSession
import org.slf4j.LoggerFactory

import ch.ninecode.cim.cimweb.RESTfulJSONResult.OK
import ch.ninecode.cim.connector.CIMFunction.Return
import ch.ninecode.ingest.IngestJob
import ch.ninecode.ingest.IngestOptions

/**
 * Ingest smart meter data.
 *
 * @param options the options for ingesting
 */
case class IngestFunction (options: IngestOptions, job: IngestJob) extends CIMWebFunction
{
    jars = Array (
        jarForObject (this),
        jarForObject (options),                           // Ingest.jar
        jarForObject (Cluster.builder),                   // spark-cassandra-connector.jar
        jarForObject (Json.createObjectBuilder))          // javaee-api <JSON implementation>.jar

    override def getReturnType: Return = Return.JSON

    /**
     * Executes the ingest specified by options.
     *
     * @param spark the Spark session to use
     * @return the ingest status and options used
     */
    override def executeJSON (spark: SparkSession): JsonStructure =
    {
        val cassandra = spark.sparkContext.getConf.get ("spark.cassandra.connection.host", options.host)
        val port = spark.sparkContext.getConf.get ("spark.cassandra.connection.port", options.port.toString).toInt
        val _options = options.copy (host = cassandra, port = port)
        val ingest = new ch.ninecode.ingest.Ingest (spark, _options)
        ingest.runJob (job)
        LoggerFactory.getLogger (getClass).info ("ingested")
        val result = Json.createObjectBuilder
        result.add ("verbose", _options.verbose)
        result.add ("host", _options.host)
        result.add ("port", _options.port)
        result.add ("storage", _options.storage)
        result.add ("log_level", _options.log_level.toString)
//        result.add ("nocopy", _options.nocopy)
//        result.add ("mapping", _options.mapping)
//        result.add ("metercol", _options.metercol)
//        result.add ("mridcol", _options.mridcol)
//        result.add ("timezone", _options.timezone)
//        result.add ("mintime", _options.mintime)
//        result.add ("maxtime",_options.maxtime)
//        result.add ("format", _options.format.toString)
//        val files = Json.createArrayBuilder
//        for (f <- _options.datafiles)
//            files.add (f)
//        result.add ("datafiles", files.build)
//        result.add ("keyspace", _options.keyspace)
//        result.add ("replication", _options.replication)
        RESTfulJSONResult (OK, "ingest successful", result.build).getJSON
    }

    override def toString: String =
    {
        s"${super.toString} is IngestFunction"
    }
}