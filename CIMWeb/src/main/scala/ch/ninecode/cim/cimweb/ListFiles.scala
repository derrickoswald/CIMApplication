package ch.ninecode.cim.cimweb

import java.util.logging.Logger
import javax.ejb.Stateless
import javax.resource.ResourceException
import javax.ws.rs.core.MediaType
import javax.ws.rs.DefaultValue
import javax.ws.rs.GET
import javax.ws.rs.MatrixParam
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces

import ch.ninecode.cim.connector.CIMInteractionSpec
import ch.ninecode.cim.connector.CIMInteractionSpecImpl
import ch.ninecode.cim.connector.CIMMappedRecord

/**
 * List the files in HDFS.
 */
@Stateless
@Path ("list/")
class ListFiles extends RESTful
{
    import ListFiles._

    @GET
    @Produces (Array (MediaType.APPLICATION_JSON))
    def listFiles (@DefaultValue ("false") @MatrixParam ("debug") debug: String): String =
        listFiles ("", debug)

    @GET
    @Path ("{path}")
    @Produces (Array (MediaType.APPLICATION_JSON))
    def listFiles (
        @PathParam ("path") path: String,
        @DefaultValue ("false") @MatrixParam ("debug") debug: String): String =
    {
        val ret = new RESTfulResult
        val directory = if (path.startsWith ("/")) path else "/" + path
        _Logger.info ("list %s".format (directory))
        val function = ListFilesFunction (directory, debug.toBoolean)
        val connection = getConnection (ret)
        if (null != connection)
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
                    throw new ResourceException ("null is not a MappedRecord")
                else
                {
                    val record = output.asInstanceOf [CIMMappedRecord]
                    ret.setResult (record.get ("result").asInstanceOf [String])
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

object ListFiles
{
    val LOGGER_NAME: String = Pong.getClass.getName
    val _Logger: Logger = Logger.getLogger (LOGGER_NAME)
}
