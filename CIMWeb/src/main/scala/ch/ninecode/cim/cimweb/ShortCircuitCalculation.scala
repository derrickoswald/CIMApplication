package ch.ninecode.cim.cimweb

import java.util.logging.Logger
import javax.ejb.Stateless
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import java.sql.ResultSetMetaData
import java.sql.SQLException
import java.sql.Timestamp
import java.sql.Types
import javax.json.Json
import javax.json.JsonObjectBuilder
import javax.resource.ResourceException
import javax.ws.rs.DefaultValue
import javax.ws.rs.MatrixParam
import javax.ws.rs.core.MediaType

import ch.ninecode.cim.connector.CIMFunction
import ch.ninecode.cim.connector.CIMInteractionSpec
import ch.ninecode.cim.connector.CIMInteractionSpecImpl
import ch.ninecode.cim.connector.CIMResultSet
import ch.ninecode.sc.ShortCircuitOptions

@Stateless
@Path ("/short_circuit/")
class ShortCircuitCalculation extends RESTful
{
    import ShortCircuitCalculation._

    @GET
    @Produces (Array (MediaType.APPLICATION_JSON))
    def GetShortCircuitData (
        @DefaultValue ("200e6") @MatrixParam ("network_short_circuit_power") network_short_circuit_power: Double,
        @DefaultValue ("-70.0") @MatrixParam ("network_short_circuit_angle") network_short_circuit_angle: Double,
        @DefaultValue ("1.0") @MatrixParam ("cmax") cmax: Double,
        @DefaultValue ("0.9") @MatrixParam ("cmin") cmin: Double,
        @DefaultValue ("0.5") @MatrixParam ("cosphi") cosphi: Double,
        @DefaultValue ("1.0") @MatrixParam ("starting_ratio") starting_ratio: Double
    ): String =
        GetShortCircuitData ("all", network_short_circuit_power, network_short_circuit_angle, cmax, cmin, cosphi, starting_ratio)

    def packRow (resultset: CIMResultSet, meta: ResultSetMetaData): JsonObjectBuilder =
    {
        val ret = Json.createObjectBuilder
        for (column ← 1 to meta.getColumnCount)
        {
            val name = meta.getColumnName (column)
            meta.getColumnType (column) match
            {
                case Types.BOOLEAN ⇒
                    val value = resultset.getBoolean (column)
                    if (!resultset.wasNull ())
                        ret.add (name, value)
                case Types.TINYINT ⇒
                    val value = resultset.getInt (column)
                    if (!resultset.wasNull ())
                        ret.add (name, value)
                case Types.NVARCHAR ⇒
                    val value = resultset.getString (column)
                    if (!resultset.wasNull ())
                        ret.add (name, value)
                case Types.DECIMAL ⇒
                    val value = resultset.getDouble (column)
                    if (!resultset.wasNull ())
                        if (value.isNaN)
                            ret.add (name, "NaN")
                        else if (value.isInfinite)
                            ret.add (name, "∞")
                        else
                            ret.add (name, value)
                case Types.DOUBLE ⇒
                    val value = resultset.getDouble (column)
                    if (!resultset.wasNull ())
                        if (value.isNaN)
                            ret.add (name, "NaN")
                        else if (value.isInfinite)
                            ret.add (name, "∞")
                        else
                            ret.add (name, value)
                case Types.FLOAT ⇒
                    val value = resultset.getDouble (column)
                    if (!resultset.wasNull ())
                        if (value.isNaN)
                            ret.add (name, "NaN")
                        else if (value.isInfinite)
                            ret.add (name, "∞")
                        else
                            ret.add (name, value)
                case Types.INTEGER ⇒
                    val value = resultset.getInt (column)
                    if (!resultset.wasNull ())
                        ret.add (name, value)
                case Types.BIGINT ⇒
                    val value = resultset.getBigDecimal (column)
                    if (!resultset.wasNull ())
                        ret.add (name, value)
                case Types.SMALLINT ⇒
                    val value = resultset.getInt (column)
                    if (!resultset.wasNull ())
                        ret.add (name, value)
                case Types.NCHAR ⇒
                    val value = resultset.getString (column)
                    if (!resultset.wasNull ())
                        ret.add (name, value)
                case Types.STRUCT ⇒
                    val value = resultset.getObject (column)
val stuff = try
{
                    val s: java.sql.Struct = value.asInstanceOf[java.sql.Struct]
                    try
                    {
                        val attributes = s.getAttributes
                        val attr1 = attributes(0)
                        "attr class: " + attr1.getClass.getName
                    }
                    catch { case _: Throwable => "type: " + s.getSQLTypeName }
}
catch { case _: Throwable => "class: " + value.getClass.getName }
                    // ToDo:
                    if (!resultset.wasNull ())
                        ret.add (name, stuff)
                case Types.TIMESTAMP ⇒
                    val value = resultset.getTimestamp (column)
                    if (!resultset.wasNull ())
                        ret.add (name, value.getTime)
                case Types.OTHER ⇒
                    val value = resultset.getString (column)
                    if (!resultset.wasNull ())
                        ret.add (name, value)
                case _ ⇒
            }
        }

        ret
    }

    @GET
    @Path ("{item:[^;]*}")
    @Produces (Array (MediaType.APPLICATION_JSON))
    def GetShortCircuitData (
        @PathParam ("item") item: String,
        @DefaultValue ("200e6") @MatrixParam ("network_short_circuit_power") network_short_circuit_power: Double,
        @DefaultValue ("-70.0") @MatrixParam ("network_short_circuit_angle") network_short_circuit_angle: Double,
        @DefaultValue ("1.0") @MatrixParam ("cmax") cmax: Double,
        @DefaultValue ("0.9") @MatrixParam ("cmin") cmin: Double,
        @DefaultValue ("0.5") @MatrixParam ("cosphi") cosphi: Double,
        @DefaultValue ("1.0") @MatrixParam ("starting_ratio") starting_ratio: Double
    ): String =
    {
        val transformer = if (null != item && !(item == "")) if (item.startsWith ("/")) item.substring (1) else item else null
        _Logger.info ("shortcircuit transformer=%s network=%g<%g° cmax=%g cmin=%g cosφ=%g ratio=%g".format (transformer, network_short_circuit_power, network_short_circuit_angle, cmax, cmin, cosphi, starting_ratio))
        val ret = new RESTfulJSONResult ()
        val connection = getConnection (ret)
        if (null != connection)
            try
            {
                val spec: CIMInteractionSpec = new CIMInteractionSpecImpl
                spec.setFunctionName (CIMInteractionSpec.EXECUTE_CIM_FUNCTION)
                val input = getInputRecord ("input record containing the function to run")
                val options = ShortCircuitOptions (false, network_short_circuit_power, network_short_circuit_angle, cmax, cmin, cosphi, starting_ratio, transformer, null)
                val query = ShortCircuitFunction (options)
                input.asInstanceOf[map].put (CIMFunction.FUNCTION, query)
                val interaction = connection.createInteraction
                val output = interaction.execute (spec, input)
                try
                {
                    if (null == output)
                        throw new ResourceException ("null is not a ResultSet")
                    else
                        if (!output.getClass.isAssignableFrom (classOf [CIMResultSet]))
                            throw new ResourceException ("object of class %s is not a ResultSet".format (output.getClass.toGenericString))
                        else
                        {
                            val resultset = output.asInstanceOf [CIMResultSet]
                            try
                            {
                                // form the response
                                val houses = Json.createArrayBuilder
                                val meta = resultset.getMetaData
                                while (resultset.next)
                                {
                                    val house = packRow (resultset, meta)
                                    houses.add (house)
                                }
                                resultset.close ()
                                ret.setResult (houses.build)
                            }
                            catch
                            {
                                case sqlexception: SQLException =>
                                    ret.setResultException (sqlexception, "SQLException on ResultSet")
                            }
                        }
                }
                finally
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

object ShortCircuitCalculation
{
    val LOGGER_NAME: String = ShortCircuitCalculation.getClass.getName
    val _Logger: Logger = Logger.getLogger (LOGGER_NAME)
}
