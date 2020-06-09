package ch.ninecode.cim.cimweb

import java.io.StringReader
import java.sql.ResultSetMetaData
import java.sql.SQLException
import java.sql.Types
import java.util.logging.Level
import java.util.logging.Logger

import javax.ejb.Stateless
import javax.ws.rs.Path
import javax.ws.rs.Produces

import javax.json.Json
import javax.json.JsonObjectBuilder
import javax.resource.ResourceException
import javax.ws.rs.core.MediaType
import ch.ninecode.cim.connector.CIMFunction
import ch.ninecode.cim.connector.CIMInteractionSpec
import ch.ninecode.cim.connector.CIMInteractionSpecImpl
import ch.ninecode.cim.connector.CIMResultSet
import ch.ninecode.gl.Complex
import ch.ninecode.sc.ShortCircuitOptions
import javax.json.JsonException
import javax.json.JsonObject
import javax.ws.rs.POST

@Stateless
@Path ("short_circuit")
class ShortCircuitCalculation extends RESTful
{
    import ShortCircuitCalculation._

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

    def getLong (json: JsonObject, name: String, default: Long): Long =
        if (!json.containsKey (name) || json.isNull (name)) default else json.getJsonNumber (name).longValue

    def getDouble (json: JsonObject, name: String, default: Double): Double =
        if (!json.containsKey (name) || json.isNull (name)) default else json.getJsonNumber (name).doubleValue

    def getComplex (json: JsonObject, name: String, default: Complex): Complex =
        if (!json.containsKey (name) || json.isNull (name))
            default
        else
        {
            val c = json.getJsonObject (name)
            val z = Complex (getDouble (c, "re", 0.0), getDouble (c, "im", 0.0))
            if (Complex (0.0) == z) default else z
        }

    def parseOptions (json: JsonObject): ShortCircuitOptions =
    {
        ShortCircuitOptions (
            verbose = json.getBoolean ("verbose", false),
            description = json.getString ("description", ""),
            default_short_circuit_power_max = getDouble (json, "default_short_circuit_power_max", 200.0e6),
            default_short_circuit_impedance_max = getComplex (json, "default_short_circuit_impedance_max", Complex (0.437785783, -1.202806555)),
            default_short_circuit_power_min = getDouble (json, "default_short_circuit_power_min", 100.0e6),
            default_short_circuit_impedance_min = getComplex (json, "default_short_circuit_impedance_min", Complex (0.437785783, -1.202806555)),
            default_transformer_power_rating = getDouble (json, "default_transformer_power_rating", 630000),
            default_transformer_impedance = getComplex (json, "default_transformer_impedance", Complex (0.005899999998374999, 0.039562482211875)),
            base_temperature = getDouble (json, "base_temperature", 20.0),
            low_temperature = getDouble (json, "low_temperature", 60.0),
            high_temperature = getDouble (json, "high_temperature", 60.0),
            cmax = getDouble (json, "cmax", 1.0),
            cmin = getDouble (json, "cmin", 0.9),
            worstcasepf = json.getBoolean ("worstcasepf", if (!json.containsKey ("cosphi") || json.isNull ("cosphi") || getDouble (json, "cosphi", 0.5).isNaN) true else false),
            cosphi = getDouble (json, "cosphi", 0.5),
            fuse_table = json.getInt ("fuse_table", 1),
            messagemax = json.getInt ("messagemax", 5),
            batchsize = getLong (json, "batchsize", 10000),
            trafos = json.getString ("trafos", ""),
            workdir = json.getString ("workdir", ""),
            calculate_public_lighting = json.getBoolean("calculate_public_lighting", false)
        )
    }

    def readJSON (json: String): Option[ShortCircuitOptions] =
    {
        try
        {
            try
                Json.createReader (new StringReader (json)).readObject match
                {
                    case obj: JsonObject ⇒ Some (parseOptions (obj))
                    case _ ⇒
                        _Logger.log (Level.SEVERE, """not a JsonObject""")
                        None
                }
            catch
            {
                case je: JsonException ⇒
                    _Logger.log (Level.SEVERE, """unparseable as JSON""", je)
                    None
            }
        }
        catch
        {
            case e: Exception =>
                _Logger.log (Level.SEVERE, "cannot create JSON reader", e)
                None
        }
    }

    def complexAsJSON (value: Complex): JsonObject =
    {
        val c = Json.createObjectBuilder
        c.add ("re", value.re)
        c.add ("im", value.im)
        c.build
    }

    @POST
    @Produces (Array (MediaType.APPLICATION_JSON))
    def GetShortCircuitData (
        data: Array[Byte]
    ): String =
    {
        val json = new String (data, "UTF-8")
        _Logger.info ("""shortcircuit json = %s""".format (json))
        val ret = new RESTfulJSONResult ()
        val parsed = readJSON (json)
        parsed match
        {
            case Some (options) ⇒
                _Logger.info ("""shortcircuit options = %s""".format (options))
                val connection = getConnection (ret)
                if (null != connection)
                    try
                    {
                        val spec: CIMInteractionSpec = new CIMInteractionSpecImpl
                        spec.setFunctionName (CIMInteractionSpec.EXECUTE_CIM_FUNCTION)
                        val input = getInputRecord ("input record containing the function to run")
                        input.asInstanceOf[map].put (CIMFunction.FUNCTION, ShortCircuitFunction (options))
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
                                        parameters.add ("default_short_circuit_power_max", complexAsJSON (options.default_short_circuit_power_max))
                                        parameters.add ("default_short_circuit_impedance_max", complexAsJSON (options.default_short_circuit_impedance_max))
                                        parameters.add ("default_short_circuit_power_min", complexAsJSON (options.default_short_circuit_power_min))
                                        parameters.add ("default_short_circuit_impedance_min", complexAsJSON (options.default_short_circuit_impedance_min))
                                        parameters.add ("default_transformer_power_rating", options.default_transformer_power_rating)
                                        parameters.add ("default_transformer_impedance", complexAsJSON (options.default_transformer_impedance))
                                        parameters.add ("base_temperature", options.base_temperature)
                                        parameters.add ("low_temperature", options.low_temperature)
                                        parameters.add ("high_temperature", options.high_temperature)
                                        parameters.add ("cmax", options.cmax)
                                        parameters.add ("cmin", options.cmin)
                                        parameters.add ("worstcasepf", options.worstcasepf)
                                        if (!options.worstcasepf)
                                            parameters.add ("cosphi", options.cosphi)
                                        parameters.add ("fuse_table", options.fuse_table)
                                        parameters.add ("messagemax", options.messagemax)
                                        parameters.add ("batchsize", options.batchsize)
                                        if (null != options.trafos)
                                            parameters.add ("trafos", options.trafos)
                                        if (null == options.workdir)
                                            parameters.add ("workdir", options.workdir)
                                        result.add ("parameters", parameters)
                                        result.add ("records", records)
                                        if (null != options.calculate_public_lighting)
                                            parameters.add ("calculate_public_lighting", options.calculate_public_lighting)
                                        ret.setResult (result.build)
                                    }
                                    catch
                                    {
                                        case sqlexception: SQLException ⇒
                                            ret.setResultException (sqlexception, "SQLException on ResultSet")
                                    }
                                }
                        }
                        finally
                            interaction.close ()
                    }
                    catch
                    {
                        case resourceexception: ResourceException ⇒
                            ret.setResultException (resourceexception, "ResourceException on interaction")
                    }
                    finally
                        try
                            connection.close ()
                        catch
                        {
                            case resourceexception: ResourceException ⇒
                                ret.setResultException (resourceexception, "ResourceException on close")
                        }
            case None ⇒
                ret.message = "invalid POST json"
        }
        ret.toString
    }
}

object ShortCircuitCalculation
{
    val LOGGER_NAME: String = ShortCircuitCalculation.getClass.getName
    val _Logger: Logger = Logger.getLogger (LOGGER_NAME)
}
