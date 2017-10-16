package ch.ninecode.cim.cimweb

import java.util.logging.Logger
import javax.ejb.Stateless
import javax.ws.rs.GET
import javax.ws.rs.core.MediaType
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.resource.ResourceException
import javax.ws.rs.core.Response

import ch.ninecode.cim.connector.CIMFunction
import ch.ninecode.cim.connector.CIMInteractionSpec
import ch.ninecode.cim.connector.CIMInteractionSpecImpl
import ch.ninecode.cim.connector.CIMMappedRecord

@Stateless
@Path ("gridlab/")
class GridLABExport extends RESTful
{
    import GridLABExport._

    // get a righteous string for a glm filename
    def glm_name (simulation: String): String =
    {
        val index = simulation.lastIndexOf ("/")
        val suffix = if (simulation.endsWith (".json")) simulation.length - 4 else simulation.length
        (if (-1 == index) simulation.substring (0, suffix) else simulation.substring (index + 1, suffix)) + ".glm"
    }

    @GET
    @Path ("{simulation:[^;]*}")
    @Produces (Array (MediaType.APPLICATION_JSON))
    def export (
        @PathParam ("simulation") simulation: String // the name of the JSON simulation file on HDFS
        ): Response =
    {
        _Logger.info ("gridlab %s".format (simulation))
        val ret = new RESTfulJSONResult
        val connection = getConnection (ret)
        val response: Response = if (null != connection)
            try
            {
                val spec: CIMInteractionSpec = new CIMInteractionSpecImpl
                spec.setFunctionName (CIMInteractionSpec.EXECUTE_CIM_FUNCTION)
                val input = getInputRecord ("input record containing the function to run")
                // set up the function with parameters
                val gridlab = GridLABExportFunction (if (simulation.startsWith ("/")) simulation else "/" + simulation)
                input.asInstanceOf[map].put (CIMFunction.FUNCTION, gridlab)
                val interaction = connection.createInteraction
                val output = interaction.execute (spec, input)
                if (null == output)
                    throw new ResourceException ("null is not a MappedRecord")
                else
                    if (!output.getClass.isAssignableFrom (classOf [CIMMappedRecord]))
                        throw new ResourceException ("object of class %s is not a MappedRecord".format (output.getClass.toGenericString))
                    else
                    {
                        val record = output.asInstanceOf [CIMMappedRecord]
                        Response.ok (record.get (CIMFunction.RESULT).asInstanceOf [String], MediaType.APPLICATION_OCTET_STREAM)
                            .header ("content-disposition", "attachment; filename=%s".format (glm_name (simulation)))
                            .build
                    }
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
}

object GridLABExport
{
    val LOGGER_NAME: String = GridLABExport.getClass.getName
    val _Logger: Logger = Logger.getLogger (LOGGER_NAME)
}
