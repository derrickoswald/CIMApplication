package ch.ninecode.cim.cimweb

import java.io.StringReader
import java.sql.ResultSetMetaData
import java.sql.SQLException
import java.sql.Types
import java.util.logging.Level
import java.util.logging.Logger

import ch.ninecode.cim.connector.CIMResultSet
import ch.ninecode.sc.ShortCircuitOptions
import ch.ninecode.util.Complex
import javax.ejb.Stateless
import javax.json.Json
import javax.json.JsonArrayBuilder
import javax.json.JsonException
import javax.json.JsonObject
import javax.json.JsonObjectBuilder
import javax.resource.ResourceException
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType

@Stateless
@Path ("short_circuit")
class ShortCircuitCalculation extends RESTful
{
    lazy val LOGGER_NAME: String = getClass.getName
    lazy val _Logger: Logger = Logger.getLogger (LOGGER_NAME)

    def packRow (resultset: CIMResultSet, meta: ResultSetMetaData): JsonObjectBuilder =
    {
        val ret = Json.createObjectBuilder
        for (column <- 1 to meta.getColumnCount)
        {
            val name = meta.getColumnName (column)
            meta.getColumnType (column) match
            {
                case Types.BOOLEAN =>
                    val value = resultset.getBoolean (column)
                    if (!resultset.wasNull ())
                        ret.add (name, value)
                case Types.TINYINT =>
                    val value = resultset.getInt (column)
                    if (!resultset.wasNull ())
                        ret.add (name, value)
                case Types.NVARCHAR =>
                    val value = resultset.getString (column)
                    if (!resultset.wasNull ())
                        ret.add (name, value)
                case Types.DECIMAL =>
                    val value = resultset.getDouble (column)
                    if (!resultset.wasNull ())
                        if (value.isNaN)
                            ret.add (name, "NaN")
                        else if (value.isInfinite)
                            ret.add (name, "∞")
                        else
                            ret.add (name, value)
                case Types.DOUBLE =>
                    val value = resultset.getDouble (column)
                    if (!resultset.wasNull ())
                        if (value.isNaN)
                            ret.add (name, "NaN")
                        else if (value.isInfinite)
                            ret.add (name, "∞")
                        else
                            ret.add (name, value)
                case Types.FLOAT =>
                    val value = resultset.getDouble (column)
                    if (!resultset.wasNull ())
                        if (value.isNaN)
                            ret.add (name, "NaN")
                        else if (value.isInfinite)
                            ret.add (name, "∞")
                        else
                            ret.add (name, value)
                case Types.INTEGER =>
                    val value = resultset.getInt (column)
                    if (!resultset.wasNull ())
                        ret.add (name, value)
                case Types.BIGINT =>
                    val value = resultset.getBigDecimal (column)
                    if (!resultset.wasNull ())
                        ret.add (name, value)
                case Types.SMALLINT =>
                    val value = resultset.getInt (column)
                    if (!resultset.wasNull ())
                        ret.add (name, value)
                case Types.NCHAR =>
                    val value = resultset.getString (column)
                    if (!resultset.wasNull ())
                        ret.add (name, value)
                case Types.STRUCT =>
                    val value = resultset.getObject (column)
                    if (!resultset.wasNull ())
                        ret.add (name, value.getClass.getName)
                case Types.TIMESTAMP =>
                    val value = resultset.getTimestamp (column)
                    if (!resultset.wasNull ())
                        ret.add (name, value.getTime)
                case Types.OTHER =>
                    val value: AnyRef = resultset.getObject (column)
                    if (!resultset.wasNull ())
                        value match
                        {
//                            doesn't work because of type erasure
//                            case doubles: scala.collection.mutable.WrappedArray[Double] =>
//                                ret.add (name, doubles.foldLeft (Json.createArrayBuilder)((b, d) => b.add (d)))
//                            case strings: scala.collection.mutable.WrappedArray[String] =>
//                                ret.add (name, strings.foldLeft (Json.createArrayBuilder)((b, d) => b.add (d)))
                            case array: scala.collection.mutable.WrappedArray[_] =>
                                array.headOption match
                                {
                                    case Some (_: Double) =>
                                        val aa = array.collect ({ case d: Double => d })
                                        ret.add (name, aa.foldLeft (Json.createArrayBuilder)((b, d) => b.add (d)))
                                    case Some (_: String) =>
                                        val aa = array.collect ({ case s: String => s })
                                        ret.add (name, aa.foldLeft (Json.createArrayBuilder)((b, s) => b.add (s)))
                                    case Some (_) | None =>
                                }
                            case _ =>
                                ret.add (name, s"unhandled class: ${value.getClass.getName}")
                        }
                case _ =>
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
                    case obj: JsonObject => Some (parseOptions (obj))
                    case _ =>
                        _Logger.log (Level.SEVERE, """not a JsonObject""")
                        None
                }
            catch
            {
                case je: JsonException =>
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
        Json.createObjectBuilder
            .add ("re", value.re)
            .add ("im", value.im)
            .build
    }

    def getParameters (options: ShortCircuitOptions): JsonObjectBuilder =
    {
        Json.createObjectBuilder
            .add ("verbose", options.verbose)
            .add ("description", options.description)
            .add ("default_short_circuit_power_max", complexAsJSON (options.default_short_circuit_power_max))
            .add ("default_short_circuit_impedance_max", complexAsJSON (options.default_short_circuit_impedance_max))
            .add ("default_short_circuit_power_min", complexAsJSON (options.default_short_circuit_power_min))
            .add ("default_short_circuit_impedance_min", complexAsJSON (options.default_short_circuit_impedance_min))
            .add ("default_transformer_power_rating", options.default_transformer_power_rating)
            .add ("default_transformer_impedance", complexAsJSON (options.default_transformer_impedance))
            .add ("base_temperature", options.base_temperature)
            .add ("low_temperature", options.low_temperature)
            .add ("high_temperature", options.high_temperature)
            .add ("cmax", options.cmax)
            .add ("cmin", options.cmin)
            .add ("worstcasepf", options.worstcasepf)
            .add ("cosphi", options.cosphi)
            .add ("fuse_table", options.fuse_table)
            .add ("messagemax", options.messagemax)
            .add ("batchsize", options.batchsize)
            .add ("trafos", options.trafos)
            .add ("workdir", options.workdir)
            .add ("calculate_public_lighting", options.calculate_public_lighting)

    }

    def getRecords (resultset: CIMResultSet): JsonArrayBuilder =
    {
        val records = Json.createArrayBuilder
        val meta = resultset.getMetaData
        while (resultset.next)
        {
            val _ = records.add (packRow (resultset, meta))
        }
        resultset.close ()
        records
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
            case Some (options) =>
                _Logger.info ("""shortcircuit options = %s""".format (options))
                getConnection (ret) match
                {
                    case Some (connection) =>
                        try
                        {
                            val (spec, input) = getFunctionInput (ShortCircuitFunction (options))
                            val interaction = connection.createInteraction
                            val output = interaction.execute (spec, input)
                            output match
                            {
                                case resultset: CIMResultSet =>
                                    try
                                    {
                                        // form the response
                                        val result = Json.createObjectBuilder
                                            .add ("parameters", getParameters (options))
                                            .add ("records", getRecords (resultset))
                                        ret.setResult (result.build)
                                    }
                                    catch
                                    {
                                        case sqlexception: SQLException =>
                                            ret.setResultException (sqlexception, "SQLException on ResultSet")
                                    }
                                case _ =>
                                    ret.setResultException (new ResourceException ("ShortCircuitFunction interaction result is not a ResultSet"), "unhandled interaction result")
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
                    case None =>
                        ret.setResultException (new ResourceException ("no Spark connection"), "could not get Connection")
                }
            case None =>
                ret.message = "invalid POST json"
        }
        ret.toString
    }
}
