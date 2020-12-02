package ch.ninecode.cim.cimweb

import java.util.logging.Logger

import javax.ejb.Stateless
import javax.json.JsonObject
import javax.resource.ResourceException
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

import ch.ninecode.cim.connector.CIMConnection
import ch.ninecode.cim.connector.CIMFunction
import ch.ninecode.cim.connector.CIMMappedRecord

@Stateless
@Path("/")
class MaximumFeedIn extends RESTful
{
    def processMaximumFeedIn (json: String) (connection: CIMConnection): Response =
    {
        // set up the function with parameters
        val mfi = MaximumFeedInFunction(json)
        val (spec, input) = getFunctionInput(mfi)
        val interaction = connection.createInteraction
        val output = interaction.execute(spec, input)
        output match
        {
            case record: CIMMappedRecord =>
                record.get(CIMFunction.RESULT) match
                {
                    case struct: JsonObject =>
                        val ret = RESTfulJSONResult(
                            struct.getString("status"),
                            struct.getString("message"),
                            struct.getJsonObject("result"))
                        Response.ok(ret.toString, MediaType.APPLICATION_JSON).build
                    case _ =>
                        Response.serverError().entity ("MaximumFeedInFunction result is not a JsonObject").build
                }
            case _ =>
                Response.serverError().entity("MaximumFeedInFunction interaction result is not a MappedRecord").build
        }
    }

    @POST
    @Path("maximumfeedin")
    @Produces(Array(MediaType.APPLICATION_JSON))
    def maximumfeedin (data: Array[Byte]): Response =
    {
        val json = new String(data, "UTF-8")
        Logger.getLogger(getClass.getName).info(s"maximumfeedin json=$json")
        withConnection(processMaximumFeedIn(json))
    }

}