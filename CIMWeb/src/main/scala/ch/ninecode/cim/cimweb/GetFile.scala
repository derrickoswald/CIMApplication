package ch.ninecode.cim.cimweb

import java.util.logging.Logger
import javax.ejb.Stateless
import javax.resource.ResourceException
import javax.ws.rs.core.MediaType
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.core.Response

import ch.ninecode.cim.connector.CIMInteractionSpec
import ch.ninecode.cim.connector.CIMInteractionSpecImpl
import ch.ninecode.cim.connector.CIMMappedRecord

/**
 * Get the (XML) file from HDFS.
 */
@Stateless
@Path ("get/")
class GetFile extends RESTful
{
    import GetFile._

    @GET
    @Path ("{path:[^;]*}")
    @Produces (Array (MediaType.APPLICATION_XML))
    def getFile (@PathParam ("path") path: String): Response =
    {
        val file = if (path.startsWith ("/")) path else "/" + path
        _Logger.info ("get %s".format (file))
        val function = GetFileFunction (file)
        val ret = new RESTfulJSONResult
        val connection = getConnection (ret)
        val response: Response = if (null != connection)
            try
            {
                val spec: CIMInteractionSpec = new CIMInteractionSpecImpl
                spec.setFunctionName (CIMInteractionSpec.EXECUTE_CIM_FUNCTION)
                val input = getInputRecord ("input record containing the function to run")
                type map = java.util.Map[String,Object]
                input.asInstanceOf[map].put ("function", function)
                val interaction = connection.createInteraction
                val output = interaction.execute (spec, input)
                if (null == output)
                    Response.serverError ().entity ("null is not a MappedRecord").build
                else
                {
                    // if not found use Response.Status.NOT_FOUND
                    val record = output.asInstanceOf [CIMMappedRecord]
                    Response.ok (record.get ("result").asInstanceOf [String], MediaType.APPLICATION_XML).build
                }
            }
            catch
            {
                case resourceexception: ResourceException =>
                    Response.serverError ().entity ("ResourceException on interaction").build
            }
            finally
                try
                    connection.close ()
                catch
                {
                    case resourceexception: ResourceException =>
                        Response.serverError ().entity ("ResourceException on close").build
                }
        else
            Response.status (Response.Status.SERVICE_UNAVAILABLE).entity ("could not get connection").build

        response
    }
}

object GetFile
{
    val LOGGER_NAME: String = GetFile.getClass.getName
    val _Logger: Logger = Logger.getLogger (LOGGER_NAME)
}
