package ch.ninecode.cim.cimweb

import java.util.logging.Logger
import javax.ejb.Stateless
import javax.json.Json
import javax.json.JsonObject
import javax.json.JsonStructure
import javax.resource.ResourceException
import javax.ws.rs.DELETE
import javax.ws.rs.DefaultValue
import javax.ws.rs.core.MediaType
import javax.ws.rs.GET
import javax.ws.rs.MatrixParam
import javax.ws.rs.PUT
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.core.Response

import scala.collection.JavaConversions._
import ch.ninecode.cim.connector.CIMFunction
import ch.ninecode.cim.connector.CIMInteractionSpec
import ch.ninecode.cim.connector.CIMInteractionSpecImpl
import ch.ninecode.cim.connector.CIMMappedRecord

/**
 * Get,Put,Delete the (XML) file from HDFS.
 */
@Stateless
@Path ("file/")
class FileOperations extends RESTful
{
    import FileOperations._

    @GET
    @Produces (Array (MediaType.APPLICATION_JSON))
    def getFile (@DefaultValue ("false") @MatrixParam ("debug") debug: String): Response =
        getFile ("", debug)

    @GET
    @Path ("{path:[^;]*}")
    @Produces (Array (MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON))
    def getFile (
        @PathParam ("path") path: String,
        @DefaultValue ("false") @MatrixParam ("debug") debug: String): Response =
    {
        val file = if (path.startsWith ("/")) path else "/" + path
        _Logger.info ("file get %s".format (file))
        val fetch = !file.endsWith ("/")
        val function =
            if (fetch)
                GetFileFunction (file) // get the (XML) file from HDFS.
            else
                ListFilesFunction (file, try { debug.toBoolean } catch { case _: Throwable => false }) // list the files in HDFS.
        val ret = new RESTfulJSONResult
        val connection = getConnection (ret)
        val response: Response = if (null != connection)
            try
            {
                val spec: CIMInteractionSpec = new CIMInteractionSpecImpl
                spec.setFunctionName (CIMInteractionSpec.EXECUTE_CIM_FUNCTION)
                val input = getInputRecord ("input record containing the function to run")
                input.asInstanceOf[map].put (CIMFunction.FUNCTION, function)
                val interaction = connection.createInteraction
                val output = interaction.execute (spec, input)
                if (null == output)
                    Response.serverError ().entity ("null is not a MappedRecord").build
                else
                {
                    // if not found use Response.Status.NOT_FOUND
                    val record = output.asInstanceOf [CIMMappedRecord]
                    if (fetch)
                        Response.ok (record.get (CIMFunction.RESULT).asInstanceOf [String], MediaType.APPLICATION_XML).build
                    else
                    {
                        ret.setResult (record.get (CIMFunction.RESULT).asInstanceOf [JsonStructure])
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
                        Response.ok (ret.toString, MediaType.APPLICATION_JSON).build
                    }
                }
            }
            catch
            {
                case resourceexception: ResourceException =>
                    Response.serverError ().entity ("ResourceException on interaction\n" + resourceexception.getMessage).build
            }
            finally
                try
                    connection.close ()
                catch
                {
                    case resourceexception: ResourceException =>
                        Response.serverError ().entity ("ResourceException on close\n" + resourceexception.getMessage).build
                }
        else
            Response.status (Response.Status.SERVICE_UNAVAILABLE).entity ("could not get connection").build

        response
    }

    @PUT
    @Path ("{path:[^;]*}")
    @Produces (Array (MediaType.APPLICATION_JSON))
    def putFile (@PathParam ("path") path: String, data: Array[Byte]): String =
    {
        val file = if (path.startsWith ("/")) path else "/" + path
        _Logger.info ("file put %s".format (file))
        val function = PutFileFunction (file, data)
        val ret = new RESTfulJSONResult
        val connection = getConnection (ret)
        if (null != connection)
            try
            {
                val spec: CIMInteractionSpec = new CIMInteractionSpecImpl
                spec.setFunctionName (CIMInteractionSpec.EXECUTE_CIM_FUNCTION)
                val input = getInputRecord ("input record containing the function to run")
                input.asInstanceOf[map].put (CIMFunction.FUNCTION, function)
                val interaction = connection.createInteraction
                val output = interaction.execute (spec, input)
                if (null == output)
                    throw new ResourceException ("null is not a MappedRecord")
                else
                {
                    val record = output.asInstanceOf [CIMMappedRecord]
                    ret.setResult (record.get (CIMFunction.RESULT).asInstanceOf [JsonStructure])
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

    @DELETE
    @Path ("{path:[^;]*}")
    @Produces (Array (MediaType.APPLICATION_JSON))
    def deleteFile (@PathParam ("path") path: String): String =
    {
        val file = if (path.startsWith ("/")) path else "/" + path
        _Logger.info ("file delete %s".format (file))
        val function = DeleteFileFunction (file)
        val ret = new RESTfulJSONResult
        val connection = getConnection (ret)
        if (null != connection)
            try
            {
                val spec: CIMInteractionSpec = new CIMInteractionSpecImpl
                spec.setFunctionName (CIMInteractionSpec.EXECUTE_CIM_FUNCTION)
                val input = getInputRecord ("input record containing the function to run")
                input.asInstanceOf[map].put (CIMFunction.FUNCTION, function)
                val interaction = connection.createInteraction
                val output = interaction.execute (spec, input)
                if (null == output)
                    throw new ResourceException ("null is not a MappedRecord")
                else
                {
                    val record = output.asInstanceOf [CIMMappedRecord]
                    ret.setResult (record.get (CIMFunction.RESULT).asInstanceOf [JsonStructure])
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

object FileOperations
{
    val LOGGER_NAME: String = FileOperations.getClass.getName
    val _Logger: Logger = Logger.getLogger (LOGGER_NAME)
}