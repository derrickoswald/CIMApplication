package ch.ninecode.cim.cimweb

import java.util.logging.Logger
import javax.ejb.Stateless
import javax.ws.rs.GET
import javax.ws.rs.core.MediaType
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.resource.ResourceException
import javax.ws.rs.PUT
import javax.ws.rs.core.Response

import ch.ninecode.cim.connector.CIMFunction
import ch.ninecode.cim.connector.CIMInteractionSpec
import ch.ninecode.cim.connector.CIMInteractionSpecImpl
import ch.ninecode.cim.connector.CIMMappedRecord

@Stateless
@Path ("export/")
class Export extends RESTful
{
    import Export._

    @GET
    @Path ("{island:[^;]*}")
    @Produces (Array (MediaType.APPLICATION_XML))
    def export (
        @PathParam ("island") island: String // some island name
        ): Response =
    {
        _Logger.info ("export %s".format (island))
        val ret = new RESTfulJSONResult
        val connection = getConnection (ret)
        val response: Response = if (null != connection)
            try
            {
                val spec: CIMInteractionSpec = new CIMInteractionSpecImpl
                spec.setFunctionName (CIMInteractionSpec.EXECUTE_CIM_FUNCTION)
                val input = getInputRecord ("input record containing the function to run")
                // set up the function with parameters
                val export = ExportFunction (island, null)
                input.asInstanceOf[map].put (CIMFunction.FUNCTION, export)
                val interaction = connection.createInteraction
                val output = interaction.execute (spec, input)
                try
                {
                    if (null == output)
                        throw new ResourceException ("null is not a MappedRecord")
                    else
                        if (!output.getClass.isAssignableFrom (classOf [CIMMappedRecord]))
                            throw new ResourceException ("object of class %s is not a MappedRecord".format (output.getClass.toGenericString))
                        else
                        {
                            val record = output.asInstanceOf [CIMMappedRecord]
                            val result = record.get (CIMFunction.RESULT).asInstanceOf [String]
                            Response.ok (result, MediaType.APPLICATION_XML)
                                .header ("content-disposition", "attachment; filename=%s.rdf".format (island))
                                .build
                        }
                }
                finally
                    interaction.close ()
            }
            catch
            {
                case resourceexception: ResourceException =>
                    ret.setResultException (resourceexception, "ResourceException on interaction")
                    Response.serverError ().entity (ret.message).build
            }
            finally
                try
                    connection.close ()
                catch
                {
                    case resourceexception: ResourceException =>
                        ret.setResultException (resourceexception, "ResourceException on close")
                        Response.serverError ().entity (ret.message).build
                }
        else
            Response.status (Response.Status.SERVICE_UNAVAILABLE).entity ("could not get connection").build

        response
    }

    @PUT
    @Path ("{path:[^;]*}")
    @Produces (Array (MediaType.APPLICATION_JSON))
    def putFile (
        @PathParam ("path") path: String, // some island RDF file name on HDFS
        island: String // the island name
        ): String =
    {
        val file = if (path.startsWith ("/")) path else "/" + path
        val ret = if ((null == island) || ("" == island))
            new RESTfulJSONResult (RESTfulJSONResult.FAIL, "no island specified")
        else
            new RESTfulJSONResult
        _Logger.info ("export %s to %s".format (island, file))
        if (ret.status == RESTfulJSONResult.OK)
        {
            val connection = getConnection (ret)
            if (null != connection)
                try
                {
                    val spec: CIMInteractionSpec = new CIMInteractionSpecImpl
                    spec.setFunctionName (CIMInteractionSpec.EXECUTE_CIM_FUNCTION)
                    val input = getInputRecord ("input record containing the function to run")
                    // set up the function with parameters
                    val export = ExportFunction (island, file)
                    input.asInstanceOf[map].put (CIMFunction.FUNCTION, export)
                    val interaction = connection.createInteraction
                    val output = interaction.execute (spec, input)
                    try
                    {
                        if (null == output)
                            throw new ResourceException ("null is not a MappedRecord")
                        else
                            if (!output.getClass.isAssignableFrom (classOf [CIMMappedRecord]))
                                throw new ResourceException ("object of class %s is not a MappedRecord".format (output.getClass.toGenericString))
                            else
                            {
                                val record = output.asInstanceOf [CIMMappedRecord]
                                ret.setResult (record.get (CIMFunction.RESULT).asInstanceOf [String])
                            }
                    }
                    finally
                        interaction.close ()
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
        }
        ret.toString
    }
}

object Export
{
    val LOGGER_NAME: String = Export.getClass.getName
    val _Logger: Logger = Logger.getLogger (LOGGER_NAME)
}
