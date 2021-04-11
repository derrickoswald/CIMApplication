package ch.ninecode.sc

import java.io.StringReader

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.mapAsScalaMapConverter
import scala.io.Source

import javax.json.Json
import javax.json.JsonArray
import javax.json.JsonException
import javax.json.JsonObject
import javax.json.JsonValue
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.util.CIMReaderOptionsParser
import ch.ninecode.util.CassandraOptionsParser
import ch.ninecode.util.Complex
import ch.ninecode.util.MainOptionsParser
import ch.ninecode.util.SparkOptionsParser

/**
 * Parser for command line operation.
 */
@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements", "org.wartremover.warts.Throw"))
class ShortCircuitOptionsParser (default: ShortCircuitOptions)
    extends MainOptionsParser[ShortCircuitOptions](default)
    with SparkOptionsParser[ShortCircuitOptions]
    with CIMReaderOptionsParser[ShortCircuitOptions]
    with CassandraOptionsParser[ShortCircuitOptions]
{
    val log: Logger = LoggerFactory.getLogger(getClass)

    implicit val complexRead: scopt.Read[Complex] = scopt.Read.reads(
        s => Complex.fromString(s)
    )

    implicit val FormatsRead: scopt.Read[ShortCircuitOutputType.Value] = scopt.Read.reads(ShortCircuitOutputType.withName)

    implicit val FuseTableRead: scopt.Read[FuseData] = scopt.Read.reads(
        s =>
        {
            readFile(s).flatMap(readJSON).flatMap(parseFuseTables) match
            {
                case Right(fusedata) => fusedata
                case Left(error) =>
                    options.find (_.name == "fuse_table") match
                    {
                        case Some (option) => option.validate(_ => Left (error))
                        case None => sys.exit(1)
                    }
                    throw new Exception(error) // sadly, scopt only understands exceptions
            }
        }
    )

    opt[String]("id")
        .valueName("<text>")
        .action((x, c) => c.copy(id = x))
        .text(s"unique id for this analysis (not used for SQLite output) [${default.id}]")

    opt[Unit]("verbose")
        .action((_, c) => c.copy(verbose = true))
        .text(s"log informational messages [${default.verbose}]")

    opt[String]("description")
        .valueName("<text>")
        .action((x, c) => c.copy(description = x))
        .text(s"text describing this program execution [${default.description}]")

    opt[Double]("netp_max")
        .valueName("<Sk_max>")
        .action((x, c) => c.copy(default_short_circuit_power_max = x)).
        text(s"maximum network power if not in CIM, VA [${default.default_short_circuit_power_max}]")

    opt[Complex]("netz_max")
        .valueName("<r + xj>")
        .action((x, c) => c.copy(default_short_circuit_impedance_max = x))
        .text(s"network impedance at maximum power if not in CIM, Ω [${default.default_short_circuit_impedance_max}]")

    opt[Double]("neta_max")
        .valueName("<angle>")
        .action((x, c) => c.copy(default_short_circuit_angle_max = x))
        .text(s"network power factor angle at maximum power if not in CIM, overrides impedance, ° [${default.default_short_circuit_angle_max}]")

    opt[Double]("netp_min")
        .valueName("<Sk_min>")
        .action((x, c) => c.copy(default_short_circuit_power_min = x))
        .text(s"minimum network power if not in CIM, VA [${default.default_short_circuit_power_min}]")

    opt[Complex]("netz_min")
        .valueName("<r + xj>")
        .action((x, c) => c.copy(default_short_circuit_impedance_min = x))
        .text(s"network impedance at minumum power if not in CIM, Ω [${default.default_short_circuit_impedance_min}]")

    opt[Double]("neta_min")
        .valueName("<angle>")
        .action((x, c) => c.copy(default_short_circuit_angle_min = x))
        .text(s"network power factor angle at minimum power if not in CIM, overrides impedance, ° [${default.default_short_circuit_angle_min}]")

    opt[Double]("tbase")
        .valueName("<value>")
        .action((x, c) => c.copy(base_temperature = x))
        .text(s"temperature assumed in CIM file (°C) [${default.base_temperature}]")

    opt[Double]("tlow")
        .valueName("<value>")
        .action((x, c) => c.copy(low_temperature = x))
        .text(s"low temperature for maximum fault (°C) [${default.low_temperature}]")

    opt[Double]("thigh")
        .valueName("<value>")
        .action((x, c) => c.copy(high_temperature = x))
        .text(s"high temperature for minimum fault (°C) [${default.high_temperature}]")

    opt[String]("trafos")
        .valueName("<TRA file>")
        .action((x, c) =>
            {
                // do all transformers listed in the file
                using (Source.fromFile(x, "UTF-8"))(
                    source =>
                    {
                        val lines = source.getLines().filter(_ != "").toArray
                        if (0 == lines.length)
                            throw new Exception("no transformers to process") // sadly, scopt only understands exceptions
                        c.copy(trafos = lines)
                    }
                )
            }
        )
        .text("file of transformer names (one per line) to process")

    opt[Double]("trafop")
        .valueName("<ratedS>")
        .action((x, c) => c.copy(default_transformer_power_rating = x))
        .text(s"transformer power if not in CIM, VA [${default.default_transformer_power_rating}]")

    opt[Complex]("trafoz")
        .valueName("<r + xj>")
        .action((x, c) => c.copy(default_transformer_impedance = x))
        .text(s"transformer impedance if not in CIM, Ω [${default.default_transformer_impedance}]")

    opt[Double]("cmax")
        .action((x, c) => c.copy(cmax = x))
        .text(s"voltage factor for maximum fault level, used for rating equipment[${default.cmax}]")

    opt[Double]("cmin")
        .action((x, c) => c.copy(cmin = x))
        .text(s"voltage factor for minimum fault level, used for protections settings [${default.cmin}]")

    opt[Double]("cosphi")
        .action((x, c) => c.copy(cosphi = x, worstcasepf = false))
        .text("load power factor, used for maximum inrush current[worst case]")

    opt[FuseData]("fuse_table")
        .action((x, c) => c.copy(fuse_table = x))
        .text(s"recommended fuse sizing table JSON file, [${default.fuse_table}]")

    opt[Int]("messagemax")
        .action((x, c) => c.copy(messagemax = x))
        .text(s"maximum number of warning and error messages per node [${default.messagemax}]")

    opt[Int]("batchsize")
        .action((x, c) => c.copy(batchsize = x))
        .text(s"size of result collections for driver database writes [${default.batchsize}]")

    opt[Double]("cable_impedance_limit")
        .valueName("<value>")
        .action((x, c) => c.copy(cable_impedance_limit = x))
        .text(s"cables with higher impedances for R1 will not be processed with gridlabd [${default.cable_impedance_limit}]")

    opt[Boolean]("calculate_public_lighting")
        .action((x, c) => c.copy(calculate_public_lighting = x))
        .text(s"calculate public lighting [${default.calculate_public_lighting}]")

    opt[String]("workdir")
        .valueName("<dir>")
        .action((x, c) => c.copy(workdir = x))
        .text("shared directory (HDFS or NFS share) with scheme (hdfs:// or file:/) for work files")

    opt[ShortCircuitOutputType.Value]("output")
        .action((x, c) => c.copy(output = x))
        .text(s"type of output, one of ${ShortCircuitOutputType.values.iterator.mkString(",")} [${default.output}]")

    opt[String]("outputfile")
        .valueName("<file>")
        .action((x, c) => c.copy(outputfile = x))
        .text(s"name of the SQLite database results file [${default.outputfile}]")

    opt[String]("keyspace")
        .action((x, c) => c.copy(keyspace = x))
        .text(s"target Cassandra keyspace [${default.keyspace}]")

    opt[Int]("replication")
        .action((x, c) => c.copy(replication = x))
        .text(s"keyspace replication if the Cassandra keyspace needs creation [${default.replication}]")

    /**
     * Either convert the JSON object into a FuseData object, or a concatenated error string.
     *
     * @param json the object to convert
     * @return either the FuseData object or concatenated error strings
     */
    def parseFuseTables (json: JsonObject): Either[String, FuseData] =
    {
        val MEMBERNAME = "fuse_mapping"

        if (json.containsKey(MEMBERNAME))
        {
            json.get(MEMBERNAME) match
            {
                case obj: JsonObject =>
                    val list = obj.asScala.map
                    {
                        case (standard: String, table: JsonArray) =>
                            parseFuseTable(table).flatMap(breakpoints => Right(FuseTable(standard, breakpoints)))
                        case (_, x: JsonValue) =>
                            Left(s"""expected JSON array type, got "${typeString(x)}" for fuse mapping table""")
                    }.toList
                    gatherFuseTables(list)
                case x: Any =>
                    Left(s"""JSON member "$MEMBERNAME" is not a JSON object (type "${typeString(x)}") for fuse mapping table""")
            }
        }
        else
            Left(s"$MEMBERNAME missing for fuse mapping table")
    }

    /**
     * Parse an array of fuse table breakpoints.
     *
     * @param array the JSON array of (theoretically) Amp objects
     * @return either the array of Amp objects or an error string
     */
    def parseFuseTable (array: JsonArray): Either[String, Array[Amp]] =
    {
        val list = array.asScala.map
        {
            case obj: JsonObject =>
                parseAmp(obj)
            case x: Any =>
                Left(s"""expected JSON object type, got "${typeString(x)}" for fuse mapping table""")
        }.toList
        gatherAmps(list)
    }

    /**
     * Turn a JSON object into an Amp object.
     *
     * Validates the JSON object and if it passes the checks, converts it to an Amp object.
     * Note: This could be an apply() constructor on the Amp class eventually - if the Amp class is JSON cognizant.
     *
     * @param obj the object to convert
     * @return either the Amp object or an error string
     */
    def parseAmp (obj: JsonObject): Either[String, Amp] =
    {
        val checks = Seq(
            Check("ik", JsonValue.ValueType.NUMBER, true),
            Check("rating", JsonValue.ValueType.NUMBER, true)
        )
        pass(obj, checks).flatMap(obj => Right(asAmp(obj)))
    }

    /**
     * Validate the JSON object passes all checks.
     *
     * @param obj    the JSON object to check
     * @param checks the list of validity checks to apply
     * @return either the JSON object or a string or error messages
     */
    def pass (obj: JsonObject, checks: Seq[Check]): Either[String, JsonObject] =
    {
        val errors = checks.flatMap(error(obj))
        if (0 == errors.length)
            Right(obj)
        else
            Left(errors.mkString(","))
    }

    /**
     * Apply validity check to candidate object.
     *
     * @param candidate the candidate object
     * @param check     the validity check to apply
     * @return an error if any, or None
     */
    def error (candidate: JsonObject)(check: Check): Option[String] =
    {
        if (check.required && !candidate.containsKey(check.property))
            Some(s"""missing ${check.property} element in "$candidate"""")
        else
            if (candidate.containsKey(check.property))
            {
                val value = candidate.get(check.property)
                if (value.getValueType == check.typ)
                    None
                else
                    Some(s"""unexpected JSON type for $candidate element ("${typeString(value)}")""")
            }
            else
                None
    }

    /**
     * Turn a JSON object into an Amp object.
     *
     * Note: It is assumed that the object is a valid Amp representation.
     *
     * @param obj the object to convert
     * @return the Amp object
     */
    def asAmp (obj: JsonObject): Amp = Amp(
        obj.getJsonNumber("ik").doubleValue(),
        obj.getJsonNumber("rating").doubleValue())

    /**
     * Either gather the Amp values from the list into an array, or a concatenated error string.
     *
     * @param list the list of Amp or error strings
     * @return either the array of Amp or concatenated error strings
     */
    def gatherAmps (list: List[Either[String, Amp]]): Either[String, Array[Amp]] =
    {
        list.partition(_.isLeft) match
        {
            case (Nil, amps) =>
                val ampList = for (Right(amp) <- amps) yield amp
                if (0 != ampList.length)
                    Right(ampList.toArray)
                else
                    Left(s"""no fuse elements found for fuse mapping table""")
            case (strings, _) =>
                Left(gatherLeft(strings))
        }
    }

    /**
     * Utility to return the JSON type as a string.
     *
     * @param value the JSON value
     * @return the string representation of the type
     */
    def typeString (value: JsonValue): String = value.getValueType.toString

    /**
     * Either gather the FuseTable values from the list into a FuseData, or a concatenated error string.
     *
     * @param list the list of FuseTable or error strings
     * @return either the FuseData or concatenated error strings
     */
    def gatherFuseTables (list: List[Either[String, FuseTable]]): Either[String, FuseData] =
    {
        list.partition(_.isLeft) match
        {
            case (Nil, tables) =>
                val fuseTables = for (Right(table) <- tables) yield table
                if (0 != fuseTables.length)
                    Right(FuseData(fuseTables.toArray))
                else
                    Left(s"""no mappings found for fuse mapping table""")
            case (strings, _) =>
                Left(gatherLeft(strings))
        }
    }

    /**
     * Concatenate the error messages held in the Left() elements.
     *
     * @param strings   the list of (all Left()) items to gather
     * @param separator the separator characters, default is ","
     * @return the concatenation of the strings
     */
    def gatherLeft (strings: List[Either[String, _]], separator: String = ","): String =
    {
        val list = for (Left(string) <- strings) yield string
        list.mkString(separator)
    }

    /**
     * Either convert a string into a JSON object or an error strings.
     *
     * @param string the String to convert
     * @return either a JSON object or an error string
     */
    def readJSON (string: String): Either[String, JsonObject] =
    {
        try
        {
            Right(Json.createReader(new StringReader(string)).readObject)
        }
        catch
        {
            case je: JsonException =>
                Left(s"""non valid input for fuse mapping table (${je.getMessage})""")
        }
    }

    /**
     * Either read a file name into its string contents or an error string.
     *
     * @param filename the file name to read
     * @return either the file contents or an error string
     */
    def readFile (filename: String): Either[String, String] =
    {
        try
        {
            val sep = System.getProperty("file.separator")
            lazy val pwd = new java.io.File(".").getCanonicalPath
            val file = if (filename.startsWith(sep)) filename else s"$pwd$sep$filename"
            val source = scala.io.Source.fromFile(file, "UTF-8")
            val text = source.mkString
            source.close
            Right(text)
        }
        catch
        {
            case e: Exception =>
                Left(s"bad input file for fuse mapping table ${e.getMessage}")
        }
    }

    case class Check (property: String, typ: JsonValue.ValueType, required: Boolean)

}