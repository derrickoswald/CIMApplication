package ch.ninecode.cim.cimweb

import java.util.logging.Logger

import javax.ejb.Stateless
import javax.json.JsonObject
import javax.resource.ResourceException
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType

import ch.ninecode.cim.connector.CIMFunction
import ch.ninecode.cim.connector.CIMMappedRecord

@Stateless
@Path ("ingest")
class Ingest extends RESTful
{
    @POST
    @Produces (Array (MediaType.APPLICATION_JSON))
    def ingest (data: Array[Byte]): String =
    {
        val json = new String (data, "UTF-8")
        Logger.getLogger (getClass.getName).info (s"ingest json=$json")
        var ret = new RESTfulJSONResult
        getConnection (ret) match
        {
            case Some (connection) =>
                try
                {
                    // set up the function with parameters
                    val ingest = IngestFunction (json)
                    val (spec, input) = getFunctionInput (ingest)
                    val interaction = connection.createInteraction
                    val output = interaction.execute (spec, input)
                    output match
                    {
                        case record: CIMMappedRecord =>
                            record.get (CIMFunction.RESULT) match
                            {
                                case struct: JsonObject =>
                                    ret = RESTfulJSONResult (
                                        struct.getString ("status"),
                                        struct.getString ("message"),
                                        struct.getJsonObject ("result"))
                                case _ =>
                                    ret.setResultException (new ResourceException ("IngestFunction result is not a JsonObject"), "unhandled result type")
                            }
                        case _ =>
                            ret.setResultException (new ResourceException ("IngestFunction interaction result is not a MappedRecord"), "unhandled interaction result")
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
            case None =>
                ret.setResultException (new ResourceException ("no Spark connection"), "could not get Connection")
        }

        ret.toString
    }

}