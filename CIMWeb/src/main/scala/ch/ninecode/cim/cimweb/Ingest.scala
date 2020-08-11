package ch.ninecode.cim.cimweb

import java.io.StringReader
import java.util.logging.Level
import java.util.logging.Logger

import javax.ejb.Stateless
import javax.json.Json
import javax.json.JsonException
import javax.json.JsonObject
import javax.json.JsonValue
import javax.resource.ResourceException
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType

import scala.collection.JavaConversions._

import ch.ninecode.cim.connector.CIMFunction
import ch.ninecode.cim.connector.CIMInteractionSpec
import ch.ninecode.cim.connector.CIMInteractionSpecImpl
import ch.ninecode.cim.connector.CIMMappedRecord
import ch.ninecode.ingest.IngestOptions
import ch.ninecode.ingest.Formats
import ch.ninecode.ingest.LogLevels

@Stateless
@Path ("ingest")
class Ingest extends RESTful
{
    def getLogLevel (json: JsonObject, name: String, default: LogLevels.Value): LogLevels.Value =
        if (!json.containsKey (name) || json.isNull (name)) default else LogLevels.withName (json.getString (name))

    def getFormat (json: JsonObject, name: String, default: Formats.Value): Formats.Value =
        if (!json.containsKey (name) || json.isNull (name)) default else Formats.withName (json.getString (name))

    def getLong (json: JsonObject, name: String, default: Long): Long =
        if (!json.containsKey (name) || json.isNull (name)) default else json.getJsonNumber (name).longValue

    def getArray (json: JsonObject, name: String, default: Seq[String]): Seq[String] =
        if (!json.containsKey (name) || json.isNull (name)) default else
        {
            val a = json.getJsonArray (name)
            val s = for (s <- a.iterator ()
                         if s.getValueType == JsonValue.ValueType.STRING)
                yield (s.asInstanceOf [String])
            s.toList
        }

    def parseOptions (json: JsonObject): IngestOptions =
    {
        new IngestOptions (
            verbose = json.getBoolean ("verbose", false),
            host = json.getString ("host", "localhost"),
            port = json.getInt ("port", 9042),
            storage = json.getString ("storage", "MEMORY_AND_DISK_SER"),
            log_level = getLogLevel (json, "log_level", LogLevels.OFF),
            nocopy = json.getBoolean ("nocopy", false),
            mapping = json.getString ("mapping", ""),
            metercol = json.getString ("metercol", "Messpunktbezeichnung"),
            mridcol = json.getString ("mridcol", "NISNr"),
            timezone = json.getString ("timezone", "Europe/Berlin"),
            mintime = getLong (json, "mintime", 0L), // "1970-01-01 00:00:00.000+0000"
            maxtime = getLong (json, "maxtime", Long.MaxValue), // "292278994-08-17 07:12:55.807+0000"
            format = getFormat (json, "format", Formats.Belvis),
            datafiles = getArray (json, "datafiles", Seq ()),
            keyspace = json.getString ("keyspace", "cimapplication"),
            replication = json.getInt ("replication", 1)
        )
    }

    def readJSON (json: String): Option[IngestOptions] =
    {
        try
        try
        Json.createReader (new StringReader (json)).readObject match
        {
            case obj: JsonObject ⇒ Some (parseOptions (obj))
            case _ ⇒
                Logger.getLogger (getClass.getName).log (Level.SEVERE, """not a JsonObject""")
                None
        }
        catch
        {
            case je: JsonException ⇒
                Logger.getLogger (getClass.getName).log (Level.SEVERE, """unparseable as JSON""", je)
                None
        }
        catch
        {
            case e: Exception =>
                Logger.getLogger (getClass.getName).log (Level.SEVERE, "cannot create JSON reader", e)
                None
        }
    }

    @POST
    @Produces (Array (MediaType.APPLICATION_JSON))
    def ingest (data: Array[Byte]): String =
    {
        val json = new String (data, "UTF-8")
        Logger.getLogger (getClass.getName).info ("""ingest json=%s""".format (json))
        var ret = new RESTfulJSONResult
        val connection = getConnection (ret)
        if (null != connection)
            try
            {
                val spec: CIMInteractionSpec = new CIMInteractionSpecImpl
                spec.setFunctionName (CIMInteractionSpec.EXECUTE_CIM_FUNCTION)
                val input = getInputRecord ("input record containing the function to run")
                // set up the function with parameters
                // Note:
                // to determine the Cassandra host and port we need to look in
                // the SparkContext configuration for spark.cassandra.connection.host, i.e.	"sandbox",
                // so we do that in the EstimationFunction when we get a SparkSession,
                // otherwise it defaults to localhost:9042
                val parsed = readJSON (json)
                parsed match
                {
                    case Some (options) ⇒
                        Logger.getLogger (getClass.getName).info ("""ingest options = %s""".format (options))
                        val ingest = IngestFunction (options)
                        input.asInstanceOf[map].put (CIMFunction.FUNCTION, ingest)
                        val interaction = connection.createInteraction
                        val output = interaction.execute (spec, input)
                        if (null == output)
                            throw new ResourceException ("null is not a MappedRecord")
                        else
                        {
                            val record = output.asInstanceOf [CIMMappedRecord]
                            val struct = record.get (CIMFunction.RESULT).asInstanceOf [JsonObject]
                            ret = RESTfulJSONResult (struct.getString ("status"), struct.getString ("message"), struct.getJsonObject ("result"))
                        }
                    case None =>
                        ret.message = "invalid POST json"
                }
            }
            catch
            {
                case resourceexception: ResourceException =>
                    ret.setResultException (resourceexception, "ResourceException on interaction")
            }
            finally
                try
                connection.close ()
                catch
                {
                    case resourceexception: ResourceException =>
                        ret.setResultException (resourceexception, "ResourceException on close")
                }

        ret.toString
    }

}