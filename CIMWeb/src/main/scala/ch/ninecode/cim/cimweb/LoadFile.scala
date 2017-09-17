package ch.ninecode.cim.cimweb

import java.util.logging.Logger
import javax.ejb.Stateless
import javax.json.Json
import javax.json.JsonObject
import javax.resource.ResourceException
import javax.ws.rs.core.MediaType
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces

import scala.collection.JavaConversions._

import ch.ninecode.cim.connector.CIMInteractionSpec
import ch.ninecode.cim.connector.CIMInteractionSpecImpl
import ch.ninecode.cim.connector.CIMMappedRecord

/**
 * Load the (RDF) file from HDFS into Spark.
 */
@Stateless
@Path ("load/")
class LoadFile extends RESTful
{
    import LoadFile._

    @GET
    @Path ("{path:[^;]*}")
    @Produces (Array (MediaType.APPLICATION_JSON))
    def getFile (@PathParam ("path") path: String): String =
    {
        val file = if (path.startsWith ("/")) path else "/" + path
        _Logger.info ("load %s".format (file))
        val function = LoadFileFunction (file) // , new scala.collection.mutable.HashMap[String, String] ()
        val ret = new RESTfulJSONResult
        val connection = getConnection (ret)
        if (null != connection)
            try
            {
                val spec: CIMInteractionSpec = new CIMInteractionSpecImpl
                spec.setFunctionName (CIMInteractionSpec.EXECUTE_CIM_FUNCTION)
                val input = getInputRecord ("input record containing the function to run")
                input.asInstanceOf[map].put ("function", function)
                val interaction = connection.createInteraction
                val output = interaction.execute (spec, input)
                if (null == output)
                    throw new ResourceException ("null is not a MappedRecord")
                else
                {
                    // if not found use Response.Status.NOT_FOUND
                    val record = output.asInstanceOf [CIMMappedRecord]
                    ret.setResult (record.get ("result").asInstanceOf [String])
                    val response = ret.result.asInstanceOf[JsonObject]
                    if (response.containsKey ("error"))
                    {
                        ret.status = RESTfulJSONResult.FAIL
                        ret.message = response.getString ("error")
                        val result = Json.createObjectBuilder
                        for (key <- response.keySet)
                            if (key != "error")
                                result.add (key, response.get (key))
                        ret.setResult (result.build)
                    }
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

object LoadFile
{
    val LOGGER_NAME: String = LoadFile.getClass.getName
    val _Logger: Logger = Logger.getLogger (LOGGER_NAME)
}
