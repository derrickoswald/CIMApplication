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
import ch.ninecode.cim.connector.CIMInteractionSpec
import ch.ninecode.cim.connector.CIMInteractionSpecImpl
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
        val connection = getConnection (ret)
        if (null != connection)
            try
            {
                val spec: CIMInteractionSpec = new CIMInteractionSpecImpl
                spec.setFunctionName (CIMInteractionSpec.EXECUTE_CIM_FUNCTION)
                val input = getInputRecord ("input record containing the function to run")
                // set up the function with parameters
                val ingest = IngestFunction (json)
                input.asInstanceOf[map].put (CIMFunction.FUNCTION, ingest)
                val interaction = connection.createInteraction
                val output = interaction.execute (spec, input)
                if (null == output)
                    throw new ResourceException ("null is not a MappedRecord")
                else
                {
                    val record = output.asInstanceOf[CIMMappedRecord]
                    val struct = record.get (CIMFunction.RESULT).asInstanceOf[JsonObject]
                    ret = RESTfulJSONResult (
                        struct.getString ("status"),
                        struct.getString ("message"),
                        struct.getJsonObject ("result"))
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