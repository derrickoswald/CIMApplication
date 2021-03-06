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
import ch.ninecode.cim.connector.CIMMappedRecord

@Stateless
@Path("query/")
class Query extends RESTful
{
    lazy val LOGGER_NAME: String = getClass.getName
    lazy val _Logger: Logger = Logger.getLogger(LOGGER_NAME)

    /**
     * Execute a Spark SQL query.
     *
     * @param sql        The query text
     * @param table_name The temporary table to register as the result of the query
     * @return The result set as a JSON array
     */
    @GET
    @Produces(Array(MediaType.APPLICATION_JSON))
    def query (
        @QueryParam("sql") sql: String,
        @DefaultValue("false") @QueryParam("cassandra") cass: String,
        @DefaultValue("") @QueryParam("table_name") table_name: String,
        @DefaultValue("") @QueryParam("cassandra_table_name") cassandra_table_name: String): String =
    {
        val cassandra = asBoolean(cass)
        _Logger.info(s"query ${if (cassandra) "cassandra " else ""}sql=$sql${if ("" != table_name) " table_name=" + table_name else ""}${if ("" != cassandra_table_name) s" cassandra_table_name=$cassandra_table_name" else ""}")
        val ret = new RESTfulJSONResult()
        getConnection(ret) match
        {
            case Some(connection) =>
                try
                {
                    val query = QueryFunction(sql, cassandra, table_name, cassandra_table_name)
                    val (spec, input) = getFunctionInput(query)
                    val interaction = connection.createInteraction
                    val output = interaction.execute(spec, input)
                    output match
                    {
                        case record: CIMMappedRecord =>
                            record.get(CIMFunction.RESULT) match
                            {
                                case json: JsonStructure =>
                                    ret.setResult(json)
                                case _ =>
                                    ret.setResultException(new ResourceException("QueryFunction result is not a JsonObject"), "unhandled result type")
                            }
                        case _ =>
                            ret.setResultException(new ResourceException("QueryFunction interaction result is not a MappedRecord"), "unhandled interaction result")
                    }
                    interaction.close()
                }
                catch
                {
                    case resourceexception: ResourceException =>
                        ret.setResultException(resourceexception, "ResourceException on interaction")
                }
                finally
                    try
                    connection.close()
                    catch
                    {
                        case resourceexception: ResourceException =>
                            ret.setResultException(resourceexception, "ResourceException on close")
                    }
            case None =>
                ret.setResultException(new ResourceException("no Spark connection"), "could not get Connection")
        }
        ret.toString
    }
}
