package ch.ninecode.cim.cimweb

import java.util.logging.Logger
import javax.ejb.Stateless
import javax.json.JsonStructure
import javax.ws.rs.GET
import javax.ws.rs.core.MediaType
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.resource.ResourceException
import javax.ws.rs.DefaultValue
import javax.ws.rs.QueryParam

import ch.ninecode.cim.connector.CIMFunction
import ch.ninecode.cim.connector.CIMInteractionSpec
import ch.ninecode.cim.connector.CIMInteractionSpecImpl
import ch.ninecode.cim.connector.CIMMappedRecord

@Stateless
@Path ("query/")
class Query extends RESTful
{
    import Query._

    /**
     * Execute a Spark SQL query.
     *
     * @param sql The query text
     * @param table_name The temporary table to register as the result of the query
     * @return The result set as a JSON array
     */
    @GET
    @Produces (Array (MediaType.APPLICATION_JSON))
    def query (
        @QueryParam ("sql") sql: String,
        @DefaultValue ("false") @QueryParam ("cassandra") cass: String,
        @DefaultValue ("") @QueryParam ("table_name") table_name: String,
        @DefaultValue ("") @QueryParam ("cassandra_table_name") cassandra_table_name: String): String =
    {
        val cassandra = try { cass.toBoolean } catch { case _: Throwable => false }
        _Logger.info ("query %ssql=%s%s%s".format (if (cassandra) "cassandra " else "", sql, if ("" != table_name) " table_name=" + table_name else "", if ("" != cassandra_table_name) " cassandra_table_name=" + cassandra_table_name else ""))
        val ret = new RESTfulJSONResult ()
        val connection = getConnection (ret)
        if (null != connection)
            try
            {
                val spec: CIMInteractionSpec = new CIMInteractionSpecImpl
                spec.setFunctionName (CIMInteractionSpec.EXECUTE_CIM_FUNCTION)
                val input = getInputRecord ("input record containing the function to run")
                val query = QueryFunction (sql, cassandra, table_name, cassandra_table_name)
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
