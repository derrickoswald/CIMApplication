package ch.ninecode.cim.cimweb

import java.sql.SQLException
import java.sql.Time
import java.sql.Types
import java.util.logging.Logger
import javax.ejb.Stateless
import javax.json.Json
import javax.ws.rs.GET
import javax.ws.rs.core.MediaType
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.resource.ResourceException
import javax.ws.rs.QueryParam

import ch.ninecode.cim.connector.CIMFunction
import ch.ninecode.cim.connector.CIMInteractionSpec
import ch.ninecode.cim.connector.CIMInteractionSpecImpl
import ch.ninecode.cim.connector.CIMResultSet

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
                    throw new ResourceException ("null is not a ResultSet")
                else
                    if (!output.getClass.isAssignableFrom (classOf [CIMResultSet]))
                        throw new ResourceException ("object of class %s is not a ResultSet".format (output.getClass.toGenericString))
                    else
                    {
                        val resultset = output.asInstanceOf [CIMResultSet]
                        val meta = resultset.getMetaData
                        try
                        {
                            // form the response
                            val array = Json.createArrayBuilder
                            while (resultset.next)
                            {
                                val obj = Json.createObjectBuilder
                                for (i <- 1 to meta.getColumnCount)
                                {
                                    val name = meta.getColumnName (i)
                                    meta.getColumnType (i) match
                                    {
                                        case Types.BOOLEAN ⇒ val v = resultset.getBoolean (i); if (!resultset.wasNull ()) obj.add (name, v)
                                        case Types.TINYINT ⇒ val v = resultset.getShort (i); if (!resultset.wasNull ()) obj.add (name, v)
                                        case Types.TIME ⇒ val v = resultset.getTime (i).getTime; if (!resultset.wasNull ()) obj.add (name, v)
                                        case Types.DECIMAL ⇒ val v = resultset.getDouble (i); if (!resultset.wasNull ()) obj.add (name, v)
                                        case Types.DOUBLE ⇒ val v = resultset.getDouble (i); if (!resultset.wasNull ()) obj.add (name, v)
                                        case Types.FLOAT ⇒ val v = resultset.getDouble (i); if (!resultset.wasNull ()) obj.add (name, v)
                                        case Types.INTEGER ⇒ val v = resultset.getInt (i); if (!resultset.wasNull ()) obj.add (name, v)
                                        case Types.BIGINT ⇒ val v = resultset.getBigDecimal (i); if (!resultset.wasNull ()) obj.add (name, v)
                                        case Types.SMALLINT ⇒ val v = resultset.getInt (i); if (!resultset.wasNull ()) obj.add (name, v)
                                        case Types.NCHAR ⇒ val v = resultset.getString (i); if (!resultset.wasNull ()) obj.add (name, v)
                                        case Types.TIMESTAMP ⇒ val v = resultset.getTimestamp (i); if (!resultset.wasNull ()) obj.add (name, v.getTime)
                                        case Types.STRUCT ⇒ val v = resultset.getObject (i); if (!resultset.wasNull ()) obj.add (name, v.toString) // ToDo: make what?
                                        case Types.OTHER ⇒ val v = resultset.getObject (i); if (!resultset.wasNull ()) obj.add (name, v.toString) // ToDo: make what?
                                        case _ ⇒
                                            throw new ResourceException ("unhandled SQL type %d".format (meta.getColumnType (i)))
                                    }
                                }
                                array.add (obj)
                            }
                            resultset.close ()
                            ret.setResult (array.build)
                        }
                        catch
                        {
                            case sqlexception: SQLException =>
                                ret.setResultException (sqlexception, "SQLException on ResultSet")
                        }
                    }
                interaction.close ()
                connection.close ()
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
