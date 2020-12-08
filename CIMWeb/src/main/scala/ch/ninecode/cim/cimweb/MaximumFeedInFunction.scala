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
import ch.ninecode.gl.GLMGenerator
import ch.ninecode.mfi.Einspeiseleistung
import ch.ninecode.mfi.EinspeiseleistungOptions
import ch.ninecode.mfi.MaximumFeedInOutputType
import ch.ninecode.net.LoadFlowNode
import ch.ninecode.util.Complex

/**
 * Calculate maximum PV power feed-in at each node.
 *
 * @param job the details of the job for calculation
 */
case class MaximumFeedInFunction (job: String) extends CIMWebFunction
{
    jars = Array(
        jarForObject(this),
        jarForClass(classOf[Einspeiseleistung]),
        jarForObject(new GLMGenerator()), // GridLAB-D.jar
        jarForObject(new LoadFlowNode("", 0.0)), // Net.jar
        jarForObject(Complex(0.0, 0.0)), // Util.jar
        jarForClass(classOf[javax.json.JsonStructure]), // javaee-api <JSON implementation>.jar
        jarForObject(com.datastax.oss.driver.api.core.ConsistencyLevel.ANY), // spark-cassandra-connector.jar
        jarForObject(com.datastax.oss.driver.shaded.guava.common.collect.ImmutableListMultimap.of[String,String]()), // com/datastax/oss/driver/shaded/guava/common/collect/
        jarForObject(new com.datastax.oss.protocol.internal.util.Flags ()), // com.datastax.oss.protocol.internal.util.collection.NullAllowingImmutableMap
        jarForClass (classOf[org.reactivestreams.Publisher[_]]), // org/reactivestreams/Publisher
        jarForObject(com.typesafe.config.ConfigMemorySize.ofBytes(0))) // com/typesafe/config/ConfigMergeable

    override def getReturnType: Return = Return.JSON

    def getLong (json: JsonObject, name: String, default: Long): Long =
        if (!json.containsKey(name) || json.isNull(name)) default else json.getJsonNumber(name).longValue

    def getDouble (json: JsonObject, name: String, default: Double): Double =
        if (!json.containsKey(name) || json.isNull(name)) default else json.getJsonNumber(name).doubleValue

    def getComplex (json: JsonObject, name: String, default: Complex): Complex =
        if (!json.containsKey(name) || json.isNull(name))
            default
        else
        {
            val c = json.getJsonObject(name)
            val z = Complex(getDouble(c, "re", 0.0), getDouble(c, "im", 0.0))
            if (Complex(0.0) == z) default else z
        }

    def parseOptions (json: JsonObject): EinspeiseleistungOptions =
    {
        EinspeiseleistungOptions (
            workdir = json.getString("workdir", ""),
            verbose = json.getBoolean("verbose", false),
            three = json.getBoolean("three", false),
            precalculation = json.getBoolean("precalculation", false),
            trafos = json.getString("trafos", ""),
            export_only = json.getBoolean("export_only", false),
            all = json.getBoolean("all", false),
            erase = json.getBoolean("erase", false),
            simulation = json.getInt("simulation", -1),
            reference = json.getInt("reference", -1),
            delta = getDouble (json, "delta", 1e-6),
            precalc_factor = getDouble (json, "precalc_factor", 2.5),
            cosphi = getDouble (json, "cosphi", 1.0),
            voltage_threshold = getDouble (json, "voltage_threshold", 3.0),
            voltage_threshold2 = getDouble (json, "voltage_threshold2", 3.0),
            ignore_other = json.getBoolean("ignore_other", false),
            cable_impedance_limit = getDouble (json, "cable_impedance_limit", 5.0),
            base_temperature = getDouble (json, "base_temperature", 20.0),
            sim_temperature = getDouble (json, "sim_temperature", 20.0),
            output = MaximumFeedInOutputType.Cassandra,
            outputfile = "simulation/results.db",  // not used because of the above output type
            keyspace = json.getString("keyspace", "cimapplication"),
            replication = json.getInt("replication", 1)
        )
    }

    def readJSON (json: String): Option[JsonObject] =
    {
        try
        {
            try
            {
                Json.createReader(new StringReader(json)).readObject match
                {
                    case obj: JsonObject => Some(obj)
                    case _ =>
                        Logger.getLogger(getClass.getName).log(Level.SEVERE, """not a JsonObject""")
                        None
                }
            }
            catch
            {
                case je: JsonException =>
                    Logger.getLogger(getClass.getName).log(Level.SEVERE, """unparseable as JSON""", je)
                    None
            }
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
                case _ => "MaximumFeedIn"
            }
        }
        else
            "MaximumFeedIn"
        val options = parseOptions(json).copy(id = java.util.UUID.randomUUID.toString) // generate a new id
        val mfi = new ch.ninecode.mfi.Einspeiseleistung(spark, options)
        spark.sparkContext.setJobGroup(id, "compute maximum PV feed-in")
        val count = mfi.run()
        LoggerFactory.getLogger(getClass).info("computed")
        spark.sparkContext.setJobGroup(null, null)
        val result = Json.createObjectBuilder
            .add("parameters", options.asJSON)
            .add("transformers", count)
            .add ("id", options.id)
            .add ("run", 1) // always 1 for a new id
        RESTfulJSONResult(OK, "maximum feed in successful", result.build).getJSON
    }

    override def toString: String =
    {
        s"${super.toString} is MaximumFeedInFunction"
    }
}