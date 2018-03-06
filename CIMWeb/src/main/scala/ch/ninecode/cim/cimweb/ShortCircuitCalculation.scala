package ch.ninecode.cim.cimweb

import java.util.logging.Logger
import javax.ejb.Stateless
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import java.sql.ResultSetMetaData
import java.sql.SQLException
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
import ch.ninecode.sc.Complex
import ch.ninecode.sc.ShortCircuitOptions
import ch.ninecode.sc.ScError

@Stateless
@Path ("/short_circuit/")
class ShortCircuitCalculation extends RESTful
{
    import ShortCircuitCalculation._

    @GET
    @Produces (Array (MediaType.APPLICATION_JSON))
    def GetShortCircuitData (
        @DefaultValue ("200e6") @MatrixParam ("network_short_circuit_power") network_short_circuit_power: Double,
        @DefaultValue ("0.437785783") @MatrixParam ("network_short_circuit_resistance") network_short_circuit_resistance: Double,
        @DefaultValue ("-1.202806555") @MatrixParam ("network_short_circuit_reactance") network_short_circuit_reactance: Double,
        @DefaultValue ("630000") @MatrixParam ("transformer_power_rating") transformer_power_rating: Double,
        @DefaultValue ("0.005899999998374999") @MatrixParam ("transformer_resistance") transformer_resistance: Double,
        @DefaultValue ("0.039562482211875") @MatrixParam ("transformer_reactance") transformer_reactance: Double,
        @DefaultValue ("20.0") @MatrixParam ("tbase") tbase: Double,
        @DefaultValue ("60.0") @MatrixParam ("tlow") tlow: Double,
        @DefaultValue ("90.0") @MatrixParam ("thigh") thigh: Double,
        @DefaultValue ("1.0") @MatrixParam ("cmax") cmax: Double,
        @DefaultValue ("0.9") @MatrixParam ("cmin") cmin: Double,
        @DefaultValue ("NaN") @MatrixParam ("cosphi") cosphi: Double
    ): String =
        GetShortCircuitData ("all", network_short_circuit_power, network_short_circuit_resistance, network_short_circuit_reactance, transformer_power_rating, transformer_resistance, transformer_reactance, tbase, tlow, thigh, cmax, cmin, cosphi)

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
                    if (!resultset.wasNull ())
                        ret.add (name, value.getClass.getName)
                case Types.TIMESTAMP ⇒
                    val value = resultset.getTimestamp (column)
                    if (!resultset.wasNull ())
                        ret.add (name, value.getTime)
                case Types.OTHER ⇒
                    val value = resultset.getObject (column)
                    if (!resultset.wasNull ())
                        try
                        {
                            val array = value.asInstanceOf[scala.collection.mutable.WrappedArray[Double]]
                            val doubles: Array[Double] = array.toArray[Double]
                            val json = Json.createArrayBuilder
                            doubles.map (json.add)
                            ret.add (name, json)
                        }
                        catch
                        {
                            case _: Throwable =>
                                try
                                {
                                    val array = value.asInstanceOf[scala.collection.mutable.WrappedArray[String]]
                                    val strings: Array[String] = array.toArray[String]
                                    val json = Json.createArrayBuilder
                                    strings.map (json.add)
                                    ret.add (name, json)
                                }
                                catch
                                {
                                    case x: Throwable =>
                                        ret.add (name, "class: " + value.getClass.getName + " (" + x.getMessage + ")")
                                }
                        }
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
        @DefaultValue ("0.437785783") @MatrixParam ("network_short_circuit_resistance") network_short_circuit_resistance: Double,
        @DefaultValue ("-1.202806555") @MatrixParam ("network_short_circuit_reactance") network_short_circuit_reactance: Double,
        @DefaultValue ("630000") @MatrixParam ("transformer_power_rating") transformer_power_rating: Double,
        @DefaultValue ("0.005899999998374999") @MatrixParam ("transformer_resistance") transformer_resistance: Double,
        @DefaultValue ("0.039562482211875") @MatrixParam ("transformer_reactance") transformer_reactance: Double,
        @DefaultValue ("20.0") @MatrixParam ("tbase") tbase: Double,
        @DefaultValue ("60.0") @MatrixParam ("tlow") tlow: Double,
        @DefaultValue ("90.0") @MatrixParam ("thigh") thigh: Double,
        @DefaultValue ("1.0") @MatrixParam ("cmax") cmax: Double,
        @DefaultValue ("0.9") @MatrixParam ("cmin") cmin: Double,
        @DefaultValue ("NaN") @MatrixParam ("cosphi") cosphi: Double
    ): String =
    {
        val transformer = if (null != item && !(item == "")) if (item.startsWith ("/")) item.substring (1) else item else null
        val netz = Complex (network_short_circuit_resistance, network_short_circuit_reactance)
        val txz = Complex (transformer_resistance, transformer_reactance)
        _Logger.info ("shortcircuit transformer=%s network=%gVA,%sΩ tx=%gVA,%sΩ tbase=%g tlow=%g thigh=%g cmax=%g cmin=%g cosφ=%g".format (transformer, network_short_circuit_power, netz, transformer_power_rating, txz, tbase, tlow, thigh, cmax, cmin, cosphi))
        val ret = new RESTfulJSONResult ()
        val connection = getConnection (ret)
        if (null != connection)
            try
            {
                val spec: CIMInteractionSpec = new CIMInteractionSpecImpl
                spec.setFunctionName (CIMInteractionSpec.EXECUTE_CIM_FUNCTION)
                val input = getInputRecord ("input record containing the function to run")
                val worstcasepf = cosphi.isNaN
                val options = ShortCircuitOptions (false, "CIMApplication", network_short_circuit_power, netz, transformer_power_rating, txz, tbase, tlow, thigh, cmax, cmin, worstcasepf, cosphi, 3, transformer, null)
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
                                val records = Json.createArrayBuilder
                                val meta = resultset.getMetaData
                                while (resultset.next)
                                    records.add (packRow (resultset, meta))
                                resultset.close ()
                                val result = Json.createObjectBuilder
                                val parameters = Json.createObjectBuilder
                                parameters.add ("verbose", options.verbose)
                                parameters.add ("description", options.description)
                                parameters.add ("default_short_circuit_power", options.default_short_circuit_power)
                                parameters.add ("default_short_circuit_impedance", options.default_short_circuit_impedance.toString)
                                parameters.add ("default_transformer_power_rating", options.default_transformer_power_rating)
                                parameters.add ("default_transformer_impedance", options.default_transformer_impedance.toString)
                                parameters.add ("base_temperature", options.base_temperature)
                                parameters.add ("low_temperature", options.low_temperature)
                                parameters.add ("high_temperature", options.high_temperature)
                                parameters.add ("cmax", options.cmax)
                                parameters.add ("cmin", options.cmin)
                                parameters.add ("worstcasepf", options.worstcasepf)
                                parameters.add ("cosphi", if (options.cosphi.isNaN) "NaN" else options.cosphi.toString)
                                parameters.add ("trafos", if (null == options.trafos) "null" else options.trafos)
                                parameters.add ("workdir", if (null == options.workdir) "null" else options.workdir)
                                result.add ("parameters", parameters)
                                result.add ("records", records)
                                ret.setResult (result.build)
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
