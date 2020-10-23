package ch.ninecode.sc

import java.io.FileNotFoundException
import java.io.StringReader

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.mapAsScalaMapConverter
import scala.collection.mutable

import javax.json.Json
import javax.json.JsonArray
import javax.json.JsonException
import javax.json.JsonObject
import javax.json.JsonValue
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.util.CIMReaderOptionsParser
import ch.ninecode.util.Complex

/**
 * Parser for command line operation.
 */
@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements", "org.wartremover.warts.Throw"))
class ShortCircuitOptionsParser (options: ShortCircuitOptions) extends CIMReaderOptionsParser[ShortCircuitOptions](options)
{
    val log: Logger = LoggerFactory.getLogger(getClass)

    implicit val complexRead: scopt.Read[Complex] = scopt.Read.reads(
        s => Complex.fromString(s)
    )

    implicit val FormatsRead: scopt.Read[ShortCircuitOutputType.Value] = scopt.Read.reads(ShortCircuitOutputType.withName)

    def readFuseTable (file: String): Option[FuseData] =
    {
        val fileText = readFile(file)
        val json = readJSON(fileText)
        parseFuseTables(json)
    }

    opt[Unit]("verbose")
        .action((_, c) => c.copy(verbose = true))
        .text(s"log informational messages [${options.verbose}]")

    opt[String]("id")
        .valueName("<text>")
        .action((x, c) => c.copy(id = x))
        .text(s"unique id for this analysis (not used for SQLite output) [${options.id}]")

    opt[String]("description")
        .valueName("<text>")
        .action((x, c) => c.copy(description = x))
        .text(s"text describing this program execution [${options.description}]")

    opt[Double]("netp_max")
        .valueName("<Sk_max>")
        .action((x, c) => c.copy(default_short_circuit_power_max = x)).
        text(s"maximum network power if not in CIM, VA [${options.default_short_circuit_power_max}]")

    opt[Complex]("netz_max")
        .valueName("<r + xj>")
        .action((x, c) => c.copy(default_short_circuit_impedance_max = x))
        .text(s"network impedance at maximum power if not in CIM, Ω [${options.default_short_circuit_impedance_max}]")

    opt[Double]("neta_max")
        .valueName("<angle>")
        .action((x, c) => c.copy(default_short_circuit_angle_max = x))
        .text(s"network power factor angle at maximum power if not in CIM, overrides impedance, ° [${options.default_short_circuit_angle_max}]")

    opt[Double]("netp_min")
        .valueName("<Sk_min>")
        .action((x, c) => c.copy(default_short_circuit_power_min = x))
        .text(s"minimum network power if not in CIM, VA [${options.default_short_circuit_power_min}]")

    opt[Complex]("netz_min")
        .valueName("<r + xj>")
        .action((x, c) => c.copy(default_short_circuit_impedance_min = x))
        .text(s"network impedance at minumum power if not in CIM, Ω [${options.default_short_circuit_impedance_min}]")

    opt[Double]("neta_min")
        .valueName("<angle>")
        .action((x, c) => c.copy(default_short_circuit_angle_min = x))
        .text(s"network power factor angle at minimum power if not in CIM, overrides impedance, ° [${options.default_short_circuit_angle_min}]")

    opt[Double]("tbase")
        .valueName("<value>")
        .action((x, c) => c.copy(base_temperature = x))
        .text(s"temperature assumed in CIM file (°C) [${options.base_temperature}]")

    opt[Double]("tlow")
        .valueName("<value>")
        .action((x, c) => c.copy(low_temperature = x))
        .text(s"low temperature for maximum fault (°C) [${options.low_temperature}]")

    opt[Double]("thigh")
        .valueName("<value>")
        .action((x, c) => c.copy(high_temperature = x))
        .text(s"high temperature for minimum fault (°C) [${options.high_temperature}]")

    opt[String]("trafos")
        .valueName("<TRA file>")
        .action((x, c) => c.copy(trafos = x))
        .text("file of transformer names (one per line) to process")

    opt[Double]("trafop")
        .valueName("<ratedS>")
        .action((x, c) => c.copy(default_transformer_power_rating = x))
        .text(s"transformer power if not in CIM, VA [${options.default_transformer_power_rating}]")

    opt[Complex]("trafoz")
        .valueName("<r + xj>")
        .action((x, c) => c.copy(default_transformer_impedance = x))
        .text(s"transformer impedance if not in CIM, Ω [${options.default_transformer_impedance}]")

    opt[Double]("cmax")
        .action((x, c) => c.copy(cmax = x))
        .text(s"voltage factor for maximum fault level, used for rating equipment[${options.cmax}]")

    opt[Double]("cmin")
        .action((x, c) => c.copy(cmin = x))
        .text(s"voltage factor for minimum fault level, used for protections settings [${options.cmin}]")

    opt[Double]("cosphi")
        .action((x, c) => c.copy(cosphi = x, worstcasepf = false))
        .text("load power factor, used for maximum inrush current[worst case]")

    opt[String]("fuse_table")
        .action(
            (x, c) =>
            {
                readFuseTable(x) match
                {
                    case Some(fuse_table) => c.copy(fuse_table = fuse_table)
                    case None => c
                }
            }
        )
        .text(s"recommended fuse sizing table JSON file, [${options.fuse_table}]")

    opt[Int]("messagemax")
        .action((x, c) => c.copy(messagemax = x))
        .text(s"maximum number of warning and error messages per node [${options.messagemax}]")

    opt[Long]("batchsize")
        .action((x, c) => c.copy(batchsize = x))
        .text(s"size of result collections for driver database writes [${options.batchsize}]")

    opt[Double]("cable_impedance_limit")
        .valueName("<value>")
        .action((x, c) => c.copy(cable_impedance_limit = x))
        .text(s"cables with higher impedances for R1 will not be processed with gridlabd [${options.cable_impedance_limit}]")

    opt[Boolean]("calculate_public_lighting")
        .action((x, c) => c.copy(calculate_public_lighting = x))
        .text(s"calculate public lighting [${options.calculate_public_lighting}]")

    opt[String]("workdir")
        .valueName("<dir>")
        .action((x, c) => c.copy(workdir = x))
        .text("shared directory (HDFS or NFS share) with scheme (hdfs:// or file:/) for work files")

    opt[ShortCircuitOutputType.Value]("output")
        .action((x, c) => c.copy(output = x))
        .text(s"type of output, one of ${ShortCircuitOutputType.values.iterator.mkString(",")} [${options.output}]")

    opt[String]("outputfile")
        .valueName("<file>")
        .action((x, c) => c.copy(outputfile = x))
        .text(s"name of the SQLite database results file [${options.outputfile}]")

    opt[String]("host")
        .valueName("Cassandra")
        .action((x, c) =>
            c.copy(
                host = x,
                spark_options = c.spark_options.copy(options = c.spark_options.options + ("spark.cassandra.connection.host" -> x))
            )
        )
        .text(s"Cassandra connection host (listen_address or seed in cassandra.yaml) [${options.host}]")

    opt[Int]("port")
        .valueName("<port_number>")
        .action((x, c) =>
            c.copy(
                port = x,
                spark_options = c.spark_options.copy(options = c.spark_options.options + ("spark.cassandra.connection.port" -> x.toString))
            )
        )
        .text(s"Cassandra connection port [${options.port}]")

    opt[String]("keyspace")
        .action((x, c) => c.copy(keyspace = x))
        .text(s"target Cassandra keyspace [${options.keyspace}]")

    opt[Int]("replication")
        .action((x, c) => c.copy(replication = x))
        .text(s"keyspace replication if the Cassandra keyspace needs creation [${options.replication}]")

    def parseFuseTables (json: JsonObject): Option[FuseData] =
    {
        val MEMBERNAME = "fuse_mapping"

        if (json.containsKey(MEMBERNAME))
        {
            val value: JsonValue = json.get(MEMBERNAME)
            value match
            {
                case obj: JsonObject =>
                    val mapping: mutable.Map[String, JsonValue] = obj.asScala
                    val array = mapping.flatMap(
                        {
                            case (standard: String, table: JsonArray) =>
                                parseFuseTable(table) match
                                {
                                    case Some(breakpoints) =>
                                        Some(FuseTable(standard, breakpoints))
                                    case _ => None
                                }
                            case (_, x: JsonValue) =>
                                log.error(s"""expected JSON array type, got "${typeString(x)}"""")
                                throw new JsonException("non valid input for fuse table mapping")
                        }
                    ).toArray
                    if (0 != array.length)
                        Some(FuseData(array))
                    else
                    {
                        log.error(s"""no mappings found""")
                        throw new JsonException("non valid input for fuse table mapping")
                    }
                case _ =>
                    log.warn(s"""JSON member "$MEMBERNAME" is not a JSON object (type "${typeString(value)}")""")
                    throw new JsonException("non valid input for fuse table mapping")
            }
        }
        else
        {
            log.error(s"$MEMBERNAME missing in fuse mapping table")
            throw new JsonException("non valid input for fuse table mapping")
        }
    }

    def parseFuseTable (json: JsonArray): Option[Array[Amp]] =
    {
        val array = json
            .asScala
            .flatMap(
                {
                    case obj: JsonObject =>
                        parseAmp(obj)
                    case x: Any =>
                        log.error(s"""expected JSON object type, got "${typeString(x)}"""")
                        throw new FileNotFoundException("non valid input for fuse table mapping")
                }
            )
            .toArray
        if (0 != array.length)
            Some(array)
        else
        {
            log.error(s"""no fuse elements found""")
            throw new FileNotFoundException("non valid input for fuse table mapping")
        }
    }

    def parseAmp (obj: JsonObject): Option[Amp] =
    {
        val checks = Seq(
            ("ik", JsonValue.ValueType.NUMBER, true),
            ("rating", JsonValue.ValueType.NUMBER, true)
        )
        if (pass(obj, checks))
            Some(Amp(obj.getJsonNumber("ik").doubleValue(), obj.getJsonNumber("rating").doubleValue()))
        else
            None
    }

    def pass (obj: JsonObject, checks: Seq[(String, JsonValue.ValueType, Boolean)]): Boolean =
    {
        checks.forall(x =>
        {
            val (name, typ, required) = x
            if (required && !obj.containsKey(name))
            {
                log.error(s"""missing $name element in "$obj"""")
                false
            }
            else
                if (obj.containsKey(name))
                {
                    if (obj.get(name).getValueType == typ)
                        true
                    else
                    {
                        log.error(s"""unexpected JSON type for $obj element ("${typeString(obj.get(name))}")""")
                        false
                    }
                }
                else
                    true
        }
        )
    }

    def typeString (value: JsonValue): String = value.getValueType.toString

    def readJSON (json: String): JsonObject =
    {
        try
        {
            Json.createReader(new StringReader(json)).readObject
        } catch
        {
            case je: JsonException =>
                log.error(s"""non valid input for fuse table mapping (${je.getMessage})""")
                throw new JsonException("non valid input for fuse table mapping")
        }
    }

    def readFile (filename: String): String =
    {
        try
        {
            val sep = System.getProperty("file.separator")
            val file = if (filename.startsWith(sep)) filename else s"${new java.io.File(".").getCanonicalPath}$sep$filename"
            val source = scala.io.Source.fromFile(file, "UTF-8")
            val text = source.mkString
            source.close
            text
        }
        catch
        {
            case e: Exception =>
                log.error("bad input file for fuse table mapping", e)
                throw new FileNotFoundException("bad input file for fuse table mapping")
        }
    }
}