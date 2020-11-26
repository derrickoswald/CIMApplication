package ch.ninecode.cim.cimweb

import java.io.StringReader
import java.util.logging.Level
import java.util.logging.Logger

import javax.ejb.Stateless
import javax.json.Json
import javax.json.JsonException
import javax.json.JsonObject
import javax.ws.rs.BadRequestException
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

import ch.ninecode.cim.connector.CIMConnection
import ch.ninecode.cim.connector.CIMFunction
import ch.ninecode.cim.connector.CIMMappedRecord
import ch.ninecode.sc.Amp
import ch.ninecode.sc.FuseData
import ch.ninecode.sc.FuseTable
import ch.ninecode.sc.ShortCircuitOptions
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

    def parseOptions (json: JsonObject): ShortCircuitOptions =
    {
        val table = json.getInt("fuse_table", 1)
        val fuse_table: FuseData = table match
        {
            case 2 =>
                FuseData(
                    Array(
                        FuseTable(
                            "",
                            Array(
                                Amp(0, 0), // failsafe fallback for currents less than 28A
                                Amp(28, 10),
                                Amp(40, 16),
                                Amp(55, 20),
                                Amp(70, 25),
                                Amp(93, 32),
                                Amp(120, 40),
                                Amp(160, 50),
                                Amp(190, 63),
                                Amp(230, 80),
                                Amp(305, 100),
                                Amp(380, 125),
                                Amp(490, 160),
                                Amp(690, 200),
                                Amp(820, 250),
                                Amp(1150, 315),
                                Amp(1350, 400),
                                Amp(1900, 500),
                                Amp(2500, 630)
                            )
                        )
                    )
                )
            case 3 =>
                FuseData(
                    Array(
                        FuseTable(
                            "DIN",
                            Array(
                                Amp(0, 0), // failsafe fallback for currents less than 65A
                                Amp(65, 25),
                                Amp(105, 40),
                                Amp(140, 50),
                                Amp(180, 63),
                                Amp(240, 80),
                                Amp(320, 100),
                                Amp(380, 125),
                                Amp(500, 160),
                                Amp(650, 200),
                                Amp(800, 250),
                                Amp(1050, 315),
                                Amp(1300, 400),
                                Amp(1750, 500),
                                Amp(2400, 630)
                            )
                        ),
                        FuseTable(
                            "SEV",
                            Array(
                                Amp(0, 0), // failsafe fallback for currents less than 200A
                                Amp(200, 60),
                                Amp(250, 75),
                                Amp(300, 100),
                                Amp(340, 125),
                                Amp(500, 150),
                                Amp(600, 200),
                                Amp(720, 250),
                                Amp(850, 300),
                                Amp(1150, 400)
                            )
                        )
                    )
                )
            case 4 =>
                FuseData(
                    Array(
                        FuseTable(
                            "",
                            Array(
                                Amp(0, 6), // failsafe fallback for currents less than 65A
                                Amp(65, 25),
                                Amp(105, 35),
                                Amp(140, 50),
                                Amp(180, 50),
                                Amp(240, 63),
                                Amp(320, 100),
                                Amp(380, 100),
                                Amp(500, 160),
                                Amp(650, 160),
                                Amp(800, 200),
                                Amp(1050, 250),
                                Amp(1300, 400),
                                Amp(1750, 400),
                                Amp(2400, 500)
                            )
                        )
                    )
                )
            case _ =>
                FuseData(
                    Array(
                        FuseTable(
                            "DIN",
                            Array(
                                Amp(0, 0), // failsafe fallback for currents less than 65A
                                Amp(65, 25),
                                Amp(105, 40),
                                Amp(140, 50),
                                Amp(180, 63),
                                Amp(240, 80),
                                Amp(320, 100),
                                Amp(380, 125),
                                Amp(500, 160),
                                Amp(650, 200),
                                Amp(800, 250),
                                Amp(1050, 315),
                                Amp(1300, 400),
                                Amp(1750, 500),
                                Amp(2400, 630)
                            )
                        )
                    )
                )
        }
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
            fuse_table = fuse_table, // ToDo: editable fuse table
            messagemax = json.getInt("messagemax", 5),
            batchsize = getLong(json, "batchsize", 10000),
            trafos = json.getString("trafos", ""),
            cable_impedance_limit = 5.0,
            workdir = json.getString("workdir", ""),
            calculate_public_lighting = json.getBoolean("calculate_public_lighting", false),
            output = ShortCircuitOutputType.Cassandra,
            outputfile = "results/shortcircuit.db", // not used because of the above output type
            host = "localhost",
            port = 9042,
            keyspace = "cimapplication",
            replication = 1
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
