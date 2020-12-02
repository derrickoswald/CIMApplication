package ch.ninecode.cim.cimweb

import java.io.StringReader
import java.util.logging.Level
import java.util.logging.Logger

import javax.ejb.Stateless
import javax.json.Json
import javax.json.JsonArray
import javax.json.JsonException
import javax.json.JsonObject
import javax.json.JsonValue
import javax.ws.rs.BadRequestException
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import scala.collection.JavaConverters.mapAsScalaMapConverter

import ch.ninecode.cim.connector.CIMConnection
import ch.ninecode.cim.connector.CIMFunction
import ch.ninecode.cim.connector.CIMMappedRecord
import ch.ninecode.sc.FuseData
import ch.ninecode.sc.FuseTable
import ch.ninecode.sc.ShortCircuitOptions
import ch.ninecode.sc.ShortCircuitOptionsParser
import ch.ninecode.sc.ShortCircuitOutputType
import ch.ninecode.util.Complex

@Stateless
@Path("/")
class ShortCircuit extends RESTful
{
    lazy val LOGGER_NAME: String = getClass.getName
    lazy val _Logger: Logger = Logger.getLogger(LOGGER_NAME)

    def getLong (json: JsonObject, name: String, default: Long): Long =
        if (!json.containsKey(name) || json.isNull(name)) default else json.getJsonNumber(name).longValue

    def getDouble (json: JsonObject, name: String, default: Double): Double =
        if (!json.containsKey(name) || json.isNull(name)) default else json.getJsonNumber(name).doubleValue

    def getComplex (json: JsonObject, name: String, default: Complex): Complex =
        if (!json.containsKey(name) || json.isNull(name))
            default
        else
        {
            val c = json.getJsonObject(name)
            val z = Complex(getDouble(c, "re", 0.0), getDouble(c, "im", 0.0))
            if (Complex(0.0) == z) default else z
        }

    /**
     * Either convert the JSON object into a FuseData object, or a concatenated error string.
     *
     * @param json the object to convert
     * @return either the FuseData object or concatenated error strings
     */
    def parseFuseTables (json: JsonObject): Either[String, FuseData] =
    {
        val MEMBERNAME = "fuse_table"

        if (json.containsKey(MEMBERNAME))
        {
            val parser = new ShortCircuitOptionsParser (ShortCircuitOptions())
            json.get(MEMBERNAME) match
            {
                case obj: JsonObject =>
                    val list = obj.asScala.map
                    {
                        case (standard: String, table: JsonArray) =>
                            parser.parseFuseTable(table).flatMap(breakpoints => Right(FuseTable(standard, breakpoints)))
                        case (_, x: JsonValue) =>
                            Left(s"""expected JSON array type, got "${parser.typeString(x)}" for fuse mapping table""")
                    }.toList
                    parser.gatherFuseTables(list)
                case x: Any =>
                    Left(s"""JSON member "$MEMBERNAME" is not a JSON object (type "${parser.typeString(x)}") for fuse mapping table""")
            }
        }
        else
            Left(s"$MEMBERNAME missing for fuse mapping table")
    }

    def parseOptions (json: JsonObject): ShortCircuitOptions =
    {
        val table = parseFuseTables (json.getJsonObject("fuse_table"))
        val fuse_table = table.getOrElse(FuseData(Array(FuseTable.default)))
        ShortCircuitOptions(
            id = json.getString("id", ""),
            verbose = json.getBoolean("verbose", false),
            description = json.getString("description", ""),
            default_short_circuit_power_max = getDouble(json, "default_short_circuit_power_max", 200.0e6),
            default_short_circuit_impedance_max = getComplex(json, "default_short_circuit_impedance_max", Complex(0.437785783, -1.202806555)),
            default_short_circuit_power_min = getDouble(json, "default_short_circuit_power_min", 100.0e6),
            default_short_circuit_impedance_min = getComplex(json, "default_short_circuit_impedance_min", Complex(0.437785783, -1.202806555)),
            default_transformer_power_rating = getDouble(json, "default_transformer_power_rating", 630000),
            default_transformer_impedance = getComplex(json, "default_transformer_impedance", Complex(0.005899999998374999, 0.039562482211875)),
            base_temperature = getDouble(json, "base_temperature", 20.0),
            low_temperature = getDouble(json, "low_temperature", 60.0),
            high_temperature = getDouble(json, "high_temperature", 60.0),
            cmax = getDouble(json, "cmax", 1.0),
            cmin = getDouble(json, "cmin", 0.9),
            worstcasepf = json.getBoolean("worstcasepf", if (!json.containsKey("cosphi") || json.isNull("cosphi") || getDouble(json, "cosphi", 0.5).isNaN) true else false),
            cosphi = getDouble(json, "cosphi", 0.5),
            fuse_table = fuse_table,
            messagemax = json.getInt("messagemax", 5),
            batchsize = getLong(json, "batchsize", 10000),
            trafos = json.getString("trafos", ""),
            cable_impedance_limit = 5.0,
            workdir = json.getString("workdir", ""),
            calculate_public_lighting = json.getBoolean("calculate_public_lighting", false),
            output = ShortCircuitOutputType.Cassandra,
            outputfile = "results/shortcircuit.db", // not used because of the above output type
            keyspace = json.getString("keyspace", "cimapplication"),
            replication = json.getInt("replication", 1)
        )
    }

    def readJSON (json: String): Option[ShortCircuitOptions] =
    {
        try
        {
            Json.createReader(new StringReader(json)).readObject match
            {
                case obj: JsonObject => Some(parseOptions(obj))
                case _ =>
                    _Logger.log(Level.SEVERE, """not a JsonObject""")
                    None
            }
        }
        catch
        {
            case je: JsonException =>
                _Logger.log(Level.SEVERE, """unparseable as JSON""", je)
                None
            case e: Exception =>
                _Logger.log(Level.SEVERE, "cannot create JSON reader", e)
                None
        }
    }

    def processShortCircuit (options: ShortCircuitOptions) (connection: CIMConnection): Response =
    {
        val (spec, input) = getFunctionInput(ShortCircuitFunction(options))
        val interaction = connection.createInteraction
        val output = interaction.execute(spec, input)
        interaction.close()
        output match
        {
            case record: CIMMappedRecord =>
                record.get(CIMFunction.RESULT) match
                {
                    case json: JsonObject =>
                        val ret = new RESTfulJSONResult()
                        val result = Json.createObjectBuilder
                            .add("parameters", options.asJSON)
                            .add("result", json)
                        ret.setResult(result.build)
                        Response.ok(ret.toString, MediaType.APPLICATION_JSON).build
                    case _ =>
                        Response.serverError().entity ("ShortCircuitFunction result is not a JsonObject").build
                }
            case _ =>
                Response.serverError().entity("interaction result is not a MappedRecord").build
        }
    }

    @POST
    @Path("short_circuit")
    @Produces(Array(MediaType.APPLICATION_JSON))
    def getShortCircuitData (data: Array[Byte]): Response =
    {
        val json = new String(data, "UTF-8")
        _Logger.info(s"shortcircuit json = $json")
        readJSON(json) match
        {
            case Some(options) =>
                _Logger.info(s"shortcircuit options = $options")
                withConnection(processShortCircuit(options))
            case None =>
                new BadRequestException("invalid json").getResponse
        }
    }
}
