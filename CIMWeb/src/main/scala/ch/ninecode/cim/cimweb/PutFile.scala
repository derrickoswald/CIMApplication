package ch.ninecode.cim.cimweb

import java.util.logging.Logger
import javax.ejb.Stateless
import javax.json.Json
import javax.json.JsonObject
import javax.resource.ResourceException
import javax.ws.rs.core.MediaType
import javax.ws.rs.PUT
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces

import scala.collection.JavaConversions._

import ch.ninecode.cim.connector.CIMInteractionSpec
import ch.ninecode.cim.connector.CIMInteractionSpecImpl
import ch.ninecode.cim.connector.CIMMappedRecord

/**
 * Save the (XML) file to HDFS.
 */
@Stateless
@Path ("put/")
class PutFile extends RESTful
{
    import PutFile._

    @PUT
    @Path ("{path:[^;]*}")
    @Produces (Array (MediaType.APPLICATION_JSON))
    def putFile (@PathParam ("path") path: String, data: Array[Byte]): String =
    {
        val file = if (path.startsWith ("/")) path else "/" + path
        _Logger.info ("put %s".format (file))
        val function = PutFileFunction (file, data)
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

object PutFile
{
    val LOGGER_NAME: String = PutFile.getClass.getName
    val _Logger: Logger = Logger.getLogger (LOGGER_NAME)
}
