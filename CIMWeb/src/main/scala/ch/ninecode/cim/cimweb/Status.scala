package ch.ninecode.cim.cimweb

import java.util.logging.Logger

import javax.ejb.Stateless
import javax.json.JsonArray
import javax.resource.ResourceException
import javax.ws.rs.core.MediaType
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.Response

import ch.ninecode.cim.connector.CIMConnection
import ch.ninecode.cim.connector.CIMFunction
import ch.ninecode.cim.connector.CIMMappedRecord

/**
 * Get status information from Spark about running jobs.
 */
@Stateless
@Path("status")
class Status extends RESTful
{
    lazy val _Logger: Logger = Logger.getLogger(getClass.getName)

    def processStatus (connection: CIMConnection): Response =
    {
        try
        {
            val function = StatusFunction()
            val (spec, input) = getFunctionInput(function)
            val interaction = connection.createInteraction
            val output = interaction.execute(spec, input)
            output match
            {
                case record: CIMMappedRecord =>
                    record.get(CIMFunction.RESULT) match
                    {
                        case json: JsonArray =>
                            val ret = new RESTfulJSONResult()
                            ret.setResult(json)
                            Response.ok(ret.toString, MediaType.APPLICATION_JSON).build
                        case _ =>
                            Response.serverError().entity("Status interaction result is not a JsonObject").build
                    }
                case _ =>
                    Response.serverError().entity("interaction result is not a MappedRecord").build
            }
        }
        catch
        {
            case resourceexception: ResourceException =>
                Response.serverError().entity(s"ResourceException on interaction: ${resourceexception.getMessage}").build
        }
    }

    /**
     * Get the running jobs.
     *
     * @return A JSON array of jobs.
     */
    @GET
    @Produces(Array(MediaType.APPLICATION_JSON))
    def getStatus: Response =
    {
        _Logger.info(s"status get")
        withConnection (processStatus)
    }

}