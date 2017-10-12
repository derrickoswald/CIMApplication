package ch.ninecode.cim.cimweb

import java.util.logging.Logger
import javax.ejb.Stateless
import javax.json.Json
import javax.json.JsonObject
import javax.json.JsonStructure
import javax.ws.rs.GET
import javax.ws.rs.core.MediaType
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.resource.ResourceException
import javax.ws.rs.QueryParam

import scala.collection.JavaConversions._

import ch.ninecode.cim.connector.CIMFunction
import ch.ninecode.cim.connector.CIMInteractionSpec
import ch.ninecode.cim.connector.CIMInteractionSpecImpl
import ch.ninecode.cim.connector.CIMMappedRecord

@Stateless
@Path ("query/")
class Query extends RESTful
{
    @GET
    @Produces (Array (MediaType.APPLICATION_JSON))
    def Operation (
        @QueryParam ("sql") sql: String): String =
    {
        val ret = new RESTfulJSONResult ()
        val connection = getConnection (ret)
        if (null != connection)
            try
            {
                val spec: CIMInteractionSpec = new CIMInteractionSpecImpl
                spec.setFunctionName (CIMInteractionSpec.EXECUTE_CIM_FUNCTION)
                val input = getInputRecord ("input record containing the function to run")
                val query = QueryFunction (sql)
                input.asInstanceOf[map].put (CIMFunction.FUNCTION, query)
                val interaction = connection.createInteraction
                val output = interaction.execute (spec, input)
                if (null == output)
                    throw new ResourceException ("null is not a MappedRecord")
                else
                {
                    val record = output.asInstanceOf [CIMMappedRecord]
                    ret.setResult (record.get (CIMFunction.RESULT).asInstanceOf [JsonStructure])
                }
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
        ret.toString
    }
}

object Query
{
    val LOGGER_NAME: String = Query.getClass.getName
    val _Logger: Logger = Logger.getLogger (LOGGER_NAME)
}
