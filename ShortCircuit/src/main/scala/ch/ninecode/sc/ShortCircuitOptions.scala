package ch.ninecode.sc

import java.net.URI

import org.json4s.JArray
import org.json4s.JField
import org.json4s.JValue
import org.json4s.JsonAST.JDouble
import org.json4s.JsonAST.JInt
import org.json4s.JsonAST.JLong
import org.json4s.JsonAST.JObject
import org.json4s.JsonAST.JString

import ch.ninecode.cim.CIMTopologyOptions
import ch.ninecode.cim.ForceTrue
import ch.ninecode.util.Cassandraable
import ch.ninecode.util.CassandraOptions
import ch.ninecode.util.CIMAble
import ch.ninecode.util.CIMReaderOptions
import ch.ninecode.util.Complex
import ch.ninecode.util.JSON
import ch.ninecode.util.JSONAble
import ch.ninecode.util.JSONCustomSerializer
import ch.ninecode.util.MainOptions
import ch.ninecode.util.Mainable
import ch.ninecode.util.SparkOptions
import ch.ninecode.util.Sparkable

/**
 * Short circuit calculation options.
 *
 * @param main_options                        main() program options
 * @param spark_options                       Spark session options
 * @param cim_options                         CIMReader options
 * @param cassandra_options                   Cassandra options
 * @param id                                  run id on input, primary key for database on output
 * @param verbose                             flag to output progress and interesting values
 * @param description                         text describing this program execution
 * @param default_short_circuit_power_max     maximum available short circuit power (at transformer primary) to be used if no equivalent injection is found (VA)
 * @param default_short_circuit_impedance_max short circuit impedance to be used under maximum conditions if no equivalent injection is found (Ω)
 * @param default_short_circuit_angle_max     short circuit angle to be used under maximum conditions if no equivalent injection is found, overrides impedance if specified (°)
 * @param default_short_circuit_power_min     minimum available short circuit power (at transformer primary) to be used if no equivalent injection is found (VA)
 * @param default_short_circuit_impedance_min short circuit impedance to be used under minimum conditions if no equivalent injection is found (Ω)
 * @param default_short_circuit_angle_min     short circuit angle to be used under minimum conditions if no equivalent injection is found, overrides impedance if specified (°)
 * @param default_transformer_power_rating    default transformer maximum power rating to be applied if a transformer has no ratedS specified (VA)
 * @param default_transformer_impedance       characteristic impedance to be applied if a transformer has no r or x specified (Ω)
 * @param base_temperature                    temperature of elements in the input CIM file (°C)
 * @param low_temperature                     low temperature for lowest resistance (maximum fault level) calculations (used for rating equipment) (°C)
 * @param high_temperature                    high temperature for highest resistance (minimum fault level) calculations (used for protections settings) (°C)
 * @param cmax                                voltage factor for maximum fault level (used for rating equipment), IEC60909 specifies 1.05 for voltages < 1kV, 1.1 for voltages > 1kV (dimensionless)
 * @param cmin                                voltage factor for minimum fault level (used for protections settings), IEC60909 specifies 0.95 for voltages < 1kV, 1.0 for voltages > 1kV (dimensionless)
 * @param worstcasepf                         assume worst case motor power factor (cos term = 1.0, ignore cosphi)
 * @param cosphi                              power factor of (motor) load e.g. cos (60), the cosine of the motor starting current-voltage phase angle, typical values range from 0.2 (φ = 78°) to 0.6 (φ = 53°) during startup (dimensionless)
 * @param fuse_table                          recommended fuse sizing table as pairs of short circuit current⇒recommended fuse size, categorized by fuse type ("SEV" or "DIN")
 * @param messagemax                          maximum number of warning and error messages to keep for each node
 * @param batchsize                           size of result collections for driver database writes
 * @param trafos                              transformer names to process
 * @param cable_impedance_limit               cables with a R1 value higher than this are not calculated with gridlab, the reason is bad performance in gridlab with too high impedance values
 * @param workdir                             shared directory (HDFS or NFS share) for intermediate results
 * @param calculate_public_lighting           whether to consider public lighting in the shortcircuit calculations
 * @param output                              type of output, SQLite or Cassandra
 * @param outputfile                          SQLite output file name
 * @param keyspace                            Cassandra keyspace
 * @param replication                         keyspace replication factor if the Cassandra keyspace needs creation
 */
case class ShortCircuitOptions
(
    var main_options: MainOptions = MainOptions(),
    var spark_options: SparkOptions = SparkOptions(),
    var cim_options: CIMReaderOptions = CIMReaderOptions(
        topology = true,
        topology_options = CIMTopologyOptions(
            identify_islands = true,
            force_retain_fuses = ForceTrue)),
    var cassandra_options: CassandraOptions = CassandraOptions(),
    id: String = "",
    verbose: Boolean = true,
    description: String = "",
    default_short_circuit_power_max: Double = 200.0e6,
    default_short_circuit_impedance_max: Complex = Complex(0.437785783, -1.202806555),
    default_short_circuit_angle_max: Double = Double.NaN,
    default_short_circuit_power_min: Double = 100.0e6,
    default_short_circuit_impedance_min: Complex = Complex(0.875571570, -2.405613110),
    default_short_circuit_angle_min: Double = Double.NaN,
    default_transformer_power_rating: Double = 630000,
    default_transformer_impedance: Complex = Complex(0.005899999998374999, 0.039562482211875),
    base_temperature: Double = 20.0,
    low_temperature: Double = 60.0,
    high_temperature: Double = 90.0,
    cmax: Double = 1.0,
    cmin: Double = 0.90,
    worstcasepf: Boolean = true,
    cosphi: Double = 0.5,
    fuse_table: FuseData = FuseData(Array(FuseTable.default)),
    messagemax: Int = 5,
    batchsize: Int = 10000,
    trafos: Array[String] = Array(),
    cable_impedance_limit: Double = 5.0,
    workdir: String = "",
    calculate_public_lighting: Boolean = false,
    output: ShortCircuitOutputType.Value = ShortCircuitOutputType.SQLite,
    outputfile: String = "results/shortcircuit.db",
    keyspace: String = "cimapplication",
    replication: Int = 1
) extends Mainable with Sparkable with CIMAble with Cassandraable with JSONAble[ShortCircuitOptions]
{
    def derive_work_dir (files: Seq[String]): String =
    {
        files.toList match
        {
            case paths :: _ =>
                val file = paths.split(",")(0).replace(" ", "%20")
                val uri = new URI(file)
                val scheme = uri.getScheme
                val auth = if (null == uri.getAuthority) "" else uri.getAuthority
                if (null == scheme)
                    "/simulation/"
                else
                    s"$scheme://$auth/simulation/"
            case _ =>
                "/simulation/"
        }
    }

    /**
     * Get user specified directory or generate a working directory matching the files.
     */
    def getWorkDir: String = if ("" != workdir) workdir else derive_work_dir(cim_options.files)

    /**
     * Output equivalent JSON options.
     */
    override def toJSON: String = ShortCircuitOptions.toJSON(this)

    /**
     * Create one of these option objects from JSON.
     *
     * @param text the JSON text
     * @return either an error message in Left or the options instance in Right
     */
    override def fromJSON (text: String): Either[String, ShortCircuitOptions] = ShortCircuitOptions.fromJSON(text)
}
object ShortCircuitOptions extends JSON[ShortCircuitOptions]
{
    /**
     * The name of the resource containing the JSON schema for the options.
     *
     * @return a resource name string for use by ClassLoader.getResourceAsStream
     */
    def schemaResourceName: String = "ShortCircuitOptionsSchema.json"
    def schemaUriMap: Map[String,String] = Map[String,String](
        "https://raw.githubusercontent.com/derrickoswald/CIMApplication/master/json-schema/ShortCircuitOptionsSchema.json" -> "resource:ShortCircuitOptionsSchema.json"
    ) ++ MainOptions.schemaUriMap ++ CIMReaderOptions.schemaUriMap ++ SparkOptions.schemaUriMap ++ CassandraOptions.schemaUriMap ++ Complex.schemaUriMap

    class ShortCircuitOutputTypeSerializer extends JSONCustomSerializer[ShortCircuitOutputType.Value](
        (format: org.json4s.Formats) =>
            (
                {
                    case JString(s) => ShortCircuitOutputType.withName(s)
                },
                {
                    case x: ShortCircuitOutputType.Value => JString(x.toString)
                }
            )
    )

    def ampAsJSON (z: Amp): JObject =
        JObject (List (JField ("ik", JDouble (z.Ik)), JField ("rating", JDouble (z.Rating))))

    def tableAsJSON (y: FuseTable): JField =
        JField (y.Standard, JArray (y.Table.map (ampAsJSON).toList))

    def valueAsDouble (v: JValue): Either[String,Double] =
    {
        v match
        {
            case JDouble(d) => Right (d)
            case JLong(l) => Right (l.toDouble)
            case JInt(i) => Right (i.longValue().toDouble)
            case v => Left(s"unsupported type in Amp deserialization ${v.getClass}")
        }
    }

    def valueAsAmp (l: List[(String, JValue)]): Either[String,Amp] =
    {
        val initial: Either[String,Amp] = Right (Amp(0.0, 0.0))
        l.foldLeft(initial)(
            (a: Either[String,Amp], v: (String, JValue)) => v match
            {
                case ("ik", n) =>
                    valueAsDouble(n).fold (Left (_), ik => a.fold (Left(_), (y: Amp) => Right(y.copy(Ik = ik))))
                case ("rating", n) =>
                    valueAsDouble(n).fold (Left (_), rating => a.fold (Left(_), (y: Amp) => Right(y.copy(Rating = rating))))
                case other =>
                    Left(s"unknown property ${other._1}")
            }
        )
    }

    def valueAsAmps (arr: List[JValue]): Either[String,Array[Amp]] =
    {
        val amps: Seq[Either[String, Amp]] = arr.map
        {
            case JObject(o) => valueAsAmp(o)
            case other => Left(s"unsuppored type for ampere breakpoint (${other.getClass})")
        }
        amps.partition(_.isLeft) match
        {
            case (Nil, amps) => Right((for(Right(amp) <- amps) yield amp).toArray)
            case (strings, _) => Left((for(Left(s) <- strings) yield s).mkString ("\n"))
        }
    }

    def valueAsFuses (v: JValue): Either[String, Array[Amp]] =
    {
        v match
        {
            case JArray(arr) => valueAsAmps(arr)
            case other => Left(s"unsupported type for fuse array (${other.getClass})")
        }
    }

    def valueAsFuseTable (v: (String, JValue)): Either[String, FuseTable] =
    {
        val (name, fuses) = v
        valueAsFuses (fuses).fold(Left(_), t => Right (FuseTable(name, t)))
    }

    def valueAsFuseTables (l: List[(String, JValue)]): Either[String, Array[FuseTable]] =
    {
        val tables: Seq[Either[String, FuseTable]] = l.map (valueAsFuseTable)
        tables.partition(_.isLeft) match
        {
            case (Nil, tables) => Right((for(Right(table) <- tables) yield table).toArray)
            case (strings, _) => Left((for(Left(s) <- strings) yield s).mkString ("\n"))
        }
    }

    @SuppressWarnings(Array("org.wartremover.warts.EitherProjectionPartial"))
    class FuseDataSerializer extends JSONCustomSerializer[FuseData](
        (format: org.json4s.Formats) =>
            (
                {
                    case JObject(o) if FuseDataSerializer.test(o) =>
                        FuseData(valueAsFuseTables(o).right.get)
                },
                {
                    case x: FuseData => JObject(x.Tables.map (tableAsJSON).toList)
                }
            )
    )
    {
        override def errors: String = FuseDataSerializer.errors
    }
    object FuseDataSerializer
    {
        var errors: String = ""
        def test(l: List[(String, JValue)]): Boolean =
            valueAsFuseTables(l) match
            {
                case Right(_) => true
                case Left(message) => errors = message; false
            }
    }

    /**
     * The list of custom serializers for the options.
     */
    override def customSerializers: Seq[JSONCustomSerializer[_]] = List.concat(
        MainOptions.customSerializers,
        CIMReaderOptions.customSerializers,
        SparkOptions.customSerializers,
        CassandraOptions.customSerializers,
        Complex.customSerializers,
        List(new ShortCircuitOutputTypeSerializer, new FuseDataSerializer)
    )
}