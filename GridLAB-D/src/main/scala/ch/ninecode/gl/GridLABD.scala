package ch.ninecode.gl

import java.io.UnsupportedEncodingException
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.HashMap
import java.util.TimeZone

import scala.collection.Map
import scala.Iterator
import scala.tools.nsc.io.Jar
import scala.util.Random

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path
import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import org.apache.spark.graphx.EdgeDirection
import org.apache.spark.graphx.EdgeTriplet
import org.apache.spark.graphx.Graph
import org.apache.spark.graphx.Graph.graphToGraphOps
import org.apache.spark.graphx.VertexId
import org.apache.spark.rdd.RDD
import org.apache.spark.rdd.RDD.rddToPairRDDFunctions
import org.apache.spark.sql.SQLContext
import org.apache.spark.storage.StorageLevel

import ch.ninecode.cim._
import ch.ninecode.model._

// create a holder for pre-computed transformer power availabaility
case class ShortCircuitData (mRID: String, Sk: Double, Ikw: Double, valid: Boolean)

// define the minimal node and edge classes
case class PreNode (id_seq: String, voltage: Double) extends Serializable
case class PreEdge (id_seq_1: String, id_cn_1: String, v1: Double, id_seq_2: String, id_cn_2: String, v2: Double, id_equ: String, equipment: ConductingEquipment, element: Element) extends Serializable

class GridLABD extends Serializable
{
    /**
     * Starting condition for Pregel trace.
     * Either start the Pregel trace from the single starting node provided as the equipment parameter,
     * or start from all nodes that are in the same EquipmentContainer (as provided by the CIM exporter).
     * The Line object contains connected equipment, which is what the trace figures out.
     * However, no significant speedup was observed, so the default value is false.
     * If true, use the single start node, if false use all nodes in the same Line (EquipmentContainer).
     */
    final val SINGLE_START_NODE = false

    var _StorageLevel = StorageLevel.MEMORY_ONLY
    var _TempFilePrefix = "hdfs://sandbox:9000/output/"
    var _ConfFileName = "lines"
    var _NodeFileName = "nodes"
    var _EdgeFileName = "edges"

    // name of file containing short circuit Ikw and Sk values for medium voltage transformers
    // e.g.
    //
    // "","Fehlerort","Un","Ikw...RST.","Sk..RST.","Beschreibung..SAP.Nr..","Abgang","NIS.ID","NIS.Name"
    // "1","Scheidbach Turbach",16,-37.34,89.733,20444,"SAA Lauenen","STA2040","Scheidbach"
    // "2","Bachegg",16,-36.22,83.805,20468,"SAA Lauenen","STA9390","Bachegg"
    //
    // this should only be needed until the medium voltage network is fully described and  calculations can
    // be done from the high voltage network "slack bus" connections
    var _CSV = "hdfs://sandbox:9000/data/KS_Leistungen.csv"

    def get (name: String, context: SparkContext): RDD[Element] =
    {
        val rdds = context.getPersistentRDDs
        for (key <- rdds.keys)
        {
            val rdd = rdds (key)
            if (rdd.name == name)
                return (rdd.asInstanceOf[RDD[Element]])
        }
        return (null)
    }

    // make a valid configuration name
    // ERROR    [INIT] : object name '4x4' invalid, names must start with a letter or an underscore
    def valid_config_name (string: String): String =
    {
        if ((null == string) || ("" == string))
            "unknown"
        else
            if (string.charAt (0).isLetter || ('_' == string.charAt (0)))
                string
            else
                "_" + string
    }

    def read_csv (context: SparkContext): RDD[ShortCircuitData] =
    {
        // read the csv as a text file (could also install com.databricks.spark.csv, see https://github.com/databricks/spark-csv)
        val spreadsheet = context.textFile (_CSV)

        // create a function to trim off the double quotes
        def trimplus (s: String): String =
        {
            var ret = s.trim
            if (ret.startsWith ("\"") && ret.endsWith ("\""))
                ret = ret.substring (1, ret.length - 1)
            return (ret)
        }
        // split / clean data
        val headerAndRows = spreadsheet.map (line => line.split(",").map (trimplus (_)))
        // get header
        val header = headerAndRows.first
        // filter out header (just check if the first val matches the first header name)
        val data = headerAndRows.filter (_(0) != header (0))
        // splits to map (header/value pairs)
        val short_circuit_power = data.map (splits => header.zip (splits).toMap)

        // keep only the relevant information
        short_circuit_power.map ((record: scala.collection.immutable.Map[String,String]) => { ShortCircuitData (record("NIS.ID"), record("Sk..RST.").toDouble, record("Ikw...RST.").toDouble, true) })
    }

    def short_circuit_data (context: SparkContext): RDD[(PowerTransformer, Substation, ShortCircuitData)] =
    {
        // get all transformers in substations
        val transformers = get ("PowerTransformer", context).asInstanceOf[RDD[PowerTransformer]]
        val substation_transformers = transformers.filter ((t: PowerTransformer) => { (t.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.name != "Messen_Steuern") })

        // get an RDD of substations by filtering out distribution boxes
        val stations = get ("Substation", context).asInstanceOf[RDD[ch.ninecode.model.Substation]].filter (_.ConnectivityNodeContainer.PowerSystemResource.PSRType == "PSRType_TransformerStation")

        // the Equipment container for a transformer could be a Bay, VoltageLevel or Station... the first two of which have a reference to their station
        def station_fn (x: Tuple2[String, Any]) =
        {
            x match
            {
                case (key: String, (t: ch.ninecode.model.PowerTransformer, station: ch.ninecode.model.Substation)) =>
                {
                    (station.id, t)
                }
                case (key: String, (t: ch.ninecode.model.PowerTransformer, bay: ch.ninecode.model.Bay)) =>
                {
                    (bay.Substation, t)
                }
                case (key: String, (t: ch.ninecode.model.PowerTransformer, level: ch.ninecode.model.VoltageLevel)) =>
                {
                    (level.Substation, t)
                }
                case _ =>
                {
                    throw new Exception ("this should never happen -- default case")
                }
            }
        }

        // create an RDD of transformer-container pairs, e.g. { (TRA13730,KAB8526), (TRA4425,STA4551), ... }
        val elements = get ("Elements", context).asInstanceOf[RDD[ch.ninecode.model.Element]]
        val tpairs = substation_transformers.keyBy(_.ConductingEquipment.Equipment.EquipmentContainer).join (elements.keyBy (_.id)).map (station_fn)

        val short_circuit = read_csv (context)

        // only keep the pairs where the transformer is in a substation we have
        val transformers_stations = tpairs.join (stations.keyBy (_.id)).values

        def transformer_fn (x: Tuple2[String, Any]) =
        {
            x match
            {
                case (key: String, ((a: ch.ninecode.model.PowerTransformer, b: ch.ninecode.model.Substation), Some (c: ShortCircuitData))) =>
                {
                    (a, b, c)
                }
                case (key: String, ((a: ch.ninecode.model.PowerTransformer, b: ch.ninecode.model.Substation), None)) =>
                {
                    (a, b, ShortCircuitData (b.id, 200, -70, false))
                }
                case _ =>
                {
                    throw new Exception ("this should never happen -- default case")
                }
            }
        }

        val transformers_short_circuit = transformers_stations.keyBy (_._2.id).leftOuterJoin (short_circuit.keyBy (_.mRID)).map (transformer_fn)

        return (transformers_short_circuit)
    }

    def edge_operator (voltages: Map[String, Double], topologicalnodes: Boolean)(arg: Tuple2[Tuple2[Element,Option[Iterable[PowerTransformerEnd]]], Iterable[Terminal]]): List[PreEdge] =
    {
        var ret = List[PreEdge] ()
        def node_name (t: Terminal): String =
        {
            return (if (topologicalnodes) t.TopologicalNode else t.ConnectivityNode)
        }

        val e = arg._1._1
        val pte_op = arg._1._2
        val t_it = arg._2
        // get the ConductingEquipment
        var c = e
        while ((null != c) && !c.getClass ().getName ().endsWith (".ConductingEquipment"))
            c = c.sup
        if (null != c)
        {
            // sort terminals by sequence number (and hence the primary is index 0)
            val terminals = t_it.toArray.sortWith (_.ACDCTerminal.sequenceNumber < _.ACDCTerminal.sequenceNumber)
            // get the equipment
            val equipment = c.asInstanceOf[ConductingEquipment]
            // make a list of voltages
            val volt = 1000.0 * voltages.getOrElse (equipment.BaseVoltage, 0.0)
            val volts =
                pte_op match
                {
                    case Some (x: Iterable[PowerTransformerEnd]) =>
                        // sort ends by end number
                        // ToDo: handle the case where terminal sequence and end sequence aren't the same
                        val tends = x.toArray.sortWith (_.TransformerEnd.endNumber < _.TransformerEnd.endNumber)
                        tends.map (e => 1000.0 * voltages.getOrElse (e.TransformerEnd.BaseVoltage, 0.0))
                    case None =>
                        Array[Double] (volt, volt)
                }
            // Note: we eliminate 230V edges because transformer information doesn't exist and
            // see also NE-51 NIS.CIM: Export / Missing 230V connectivity
            if (!volts.contains (230.0))
                // make a pre-edge for each pair of terminals
                ret = terminals.length match
                {
                    case 1 =>
                        ret :+
                            new PreEdge (
                                terminals(0).ACDCTerminal.IdentifiedObject.mRID,
                                node_name (terminals(0)),
                                volts(0),
                                "",
                                "",
                                volts(0),
                                terminals(0).ConductingEquipment,
                                equipment,
                                e)
                    case _ =>
                        {
                            for (i <- 1 until terminals.length) // for comprehension: iterate omitting the upper bound
                            {
                                ret = ret :+ new PreEdge (
                                        terminals(0).ACDCTerminal.IdentifiedObject.mRID,
                                        node_name (terminals(0)),
                                        volts(0),
                                        terminals(i).ACDCTerminal.IdentifiedObject.mRID,
                                        node_name (terminals(i)),
                                        volts(i),
                                        terminals(0).ConductingEquipment,
                                        equipment,
                                        e)
                            }
                            ret
                        }
                }
        }
        //else // shouldn't happen, terminals always reference ConductingEquipment, right?
            // throw new Exception ("element " + e.id + " is not derived from ConductingEquipment")
            // ProtectionEquipment and CurrentRelay are emitted with terminals even though they shouldn't be

        return (ret)
    }

    def topological_node_operator (arg: Tuple2[Tuple2[TopologicalNode,Terminal], PreEdge]): PreNode =
    {
        val node = arg._1._1
        val term = arg._1._2
        val edge = arg._2
        PreNode (node.id, if (term.ACDCTerminal.sequenceNumber == 1) edge.v1 else edge.v2)
    }

    def connectivity_node_operator (arg: Tuple2[Tuple2[ConnectivityNode,Terminal], PreEdge]): PreNode =
    {
        val node = arg._1._1
        val term = arg._1._2
        val edge = arg._2
        PreNode (node.id, if (term.ACDCTerminal.sequenceNumber == 1) edge.v1 else edge.v2)
    }

    // emit a GridLAB-D line_configuration
    def make_line_configuration (line: ACLineSegment): String =
    {
        var ret = ""
        val config = valid_config_name (line.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.name)

        // ToDo: convert the 0/1 sequence values from the CIM format into a Z matrix

        //        <cim:ACLineSegment rdf:ID="KLE9595">
        //                <cim:IdentifiedObject.name>GKN 3x25/25</cim:IdentifiedObject.name>
        //                <cim:IdentifiedObject.aliasName>202519879:nis_el_cable</cim:IdentifiedObject.aliasName>
        //                <cim:PowerSystemResource.Location rdf:resource="#_location_696818_1201171875_202519890"/>
        //                <cim:Conductor.length>55.60291275</cim:Conductor.length>
        //                <cim:PowerSystemResource.PSRType rdf:resource="#PSRType_Underground"/>
        //                <cim:ConductingEquipment.BaseVoltage rdf:resource="BaseVoltage_400"/>
        //                <cim:ConductingEquipment.SvStatus rdf:resource="#in_use"/>
        //                <cim:Equipment.EquipmentContainer rdf:resource="#_line_ABG52414|..."/>
        //                <cim:ACLineSegment.b0ch>106.8141502</cim:ACLineSegment.b0ch>
        //                <cim:ACLineSegment.bch>179.0707813</cim:ACLineSegment.bch>
        //                <cim:ACLineSegment.g0ch>0</cim:ACLineSegment.g0ch>
        //                <cim:ACLineSegment.gch>0</cim:ACLineSegment.gch>
        //                <cim:ACLineSegment.r0>3.368</cim:ACLineSegment.r0>
        //                <cim:ACLineSegment.r>0.841</cim:ACLineSegment.r>
        //                <cim:ACLineSegment.shortCircuitEndTemperature>250</cim:ACLineSegment.shortCircuitEndTemperature>
        //                <cim:ACLineSegment.x0>0.32</cim:ACLineSegment.x0>
        //                <cim:ACLineSegment.x>0.075</cim:ACLineSegment.x>
        //        </cim:ACLineSegment>
//
//            "        object line_configuration\n" +
//            "        {\n" +
//            "            name \"line_3x25Cu/25\";\n" +
//            "            z11 0.727+0.08j Ohm/km;\n" +
//            "            z12 0.0+0.0j Ohm/km;\n" +
//            "            z13 0.0+0.0j Ohm/km;\n" +
//            "            z21 0.0+0.0j Ohm/km;\n" +
//            "            z22 0.727+0.08j Ohm/km;\n" +
//            "            z23 0.0+0.0j Ohm/km;\n" +
//            "            z31 0.0+0.0j Ohm/km;\n" +
//            "            z32 0.0+0.0j Ohm/km;\n" +
//            "            z33 0.727+0.08j Ohm/km;\n" +
//            "        };\n" +
//            "\n" +
//            "        object line_configuration\n" +
//            "        {\n" +
//            "            name \"line_3x95Cu/95\";\n" +
//            "            z11 0.193+0.07j Ohm/km;\n" +
//            "            z12 0.0+0.0j Ohm/km;\n" +
//            "            z13 0.0+0.0j Ohm/km;\n" +
//            "            z21 0.0+0.0j Ohm/km;\n" +
//            "            z22 0.193+0.07j Ohm/km;\n" +
//            "            z23 0.0+0.0j Ohm/km;\n" +
//            "            z31 0.0+0.0j Ohm/km;\n" +
//            "            z32 0.0+0.0j Ohm/km;\n" +
//            "            z33 0.193+0.07j Ohm/km;\n" +
//            "        };\n" +

        // ToDo: get real values, "TT 1x150 EL_3" is actually "TT 1x150"
        val r = if (0 == line.r) 0.225 else line.r
        val x = if (0 == line.x) 0.068 else line.x
        val diag = r + "+" + x + "j Ohm/km"
        val zero = "0.0+0.0j Ohm/km"
        ret =
            "        object line_configuration\n" +
            "        {\n" +
            "            name \"" + config + "\";\n" +
            "            z11 " + diag + ";\n" +
            "            z12 " + zero + ";\n" +
            "            z13 " + zero + ";\n" +
            "            z21 " + zero + ";\n" +
            "            z22 " + diag + ";\n" +
            "            z23 " + zero + ";\n" +
            "            z31 " + zero + ";\n" +
            "            z32 " + zero + ";\n" +
            "            z33 " + diag + ";\n" +
            "        };\n"

        return (ret)
    }

    def transformer_power (transformer: PowerTransformer): String =
    {
        val name = transformer.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.name // e.g. 250kVA
        """([0-9]+)""".r.findFirstIn (name) match
        {
            case Some (x) =>
                x
            case _ =>
                "unknown"
        }
    }

    /**
     * Make one or more transformer configurations.
     * Most transformers have only two ends, so this should normally make one configurations
     * @param voltages a map of voltage mRID to floating point voltages
     */
    def make_transformer_configuration (voltages: Map[String, Double])(s: Tuple2[Tuple3[PowerTransformer,Substation,ShortCircuitData],Any]): String =
    {
        // see http://gridlab-d.sourceforge.net/wiki/index.php/Power_Flow_User_Guide#Transformer_Configuration_Parameters
        val transformer = s._1._1
        val power = transformer_power (transformer)
        val sc_data = s._1._3
        val ret =
            s._2 match
            {
                case None =>
                    ""
                case Some (x: Any) =>
                    // sort ends by sequence number
                    val iter = x.asInstanceOf[Iterable[PowerTransformerEnd]]
                    val ends = iter.toArray.sortWith (_.TransformerEnd.endNumber < _.TransformerEnd.endNumber)
                    var temp = ""
                    for (i <- 1 until ends.length)
                    {
                        val v0 = 1000.0 * voltages.getOrElse (ends(0).TransformerEnd.BaseVoltage, 0.0)
                        val v = 1000.0 * voltages.getOrElse (ends(i).TransformerEnd.BaseVoltage, 0.0)
                        // calculate per unit r and x
                        var base_va = ends(i).ratedS * 1e6 // ToDo: remove this multiplier when NE-66 NIS.CIM: Scale of PowerTransformerEnd ratedS is wrong
                        if (0.0 == base_va && "unknown" != power)
                            base_va = power.toDouble * 1000
                        val base_amps = base_va / v / Math.sqrt (3)
                        val base_ohms = v / base_amps / Math.sqrt (3)
                        val r = ends(i).r / base_ohms
                        val x = ends(i).x / base_ohms
                        // compute the fake line impedance from the high voltage to the transformer
                        // Z = c * V^2 / (Sk x 1e6)     e.g. 0.90 * 16000 * 16000 / -82e6  = -2.8097
                        // r = Z * sin(Ikw)
                        // x = Z * cos(Ikw)
                        val c = 0.9
                        val z = c * v0 * v0 / (Math.abs (sc_data.Sk) * 1e6)
                        val diag = "" + z + (if (0 <= sc_data.Ikw) "+" else "") + sc_data.Ikw + "d Ohm/km"
                        temp +=
                            "        object transformer_configuration\n" +
                            "        {\n" +
                            "            name \"" + transformer.id + "_configuration" + "\";\n" +
                            "            connect_type DELTA_GWYE;\n" + // ToDo: pick up Dyn5 values from CIM when they are exported correctly
                            "            install_type PADMOUNT;\n" +
                            "            power_rating " + (base_va / 1000.0) + ";\n" +
                            "            primary_voltage " + v0 + ";\n" +
                            "            secondary_voltage " + v + ";\n" +
                            "            resistance " + r + ";\n" +
                            "            reactance " + x + ";\n" +
                            "        };\n" +
                            // make a line configuration
                            "        object line_configuration\n" +
                            "        {\n" +
                            "            name \"" + transformer.id + "_fake_line_configuration\";\n" +
                            "            z11 " + diag + ";\n" +
                            "            z12 0.0+0.0d Ohm/km;\n" +
                            "            z13 0.0+0.0d Ohm/km;\n" +
                            "            z21 0.0+0.0d Ohm/km;\n" +
                            "            z22 " + diag + ";\n" +
                            "            z23 0.0+0.0d Ohm/km;\n" +
                            "            z31 0.0+0.0d Ohm/km;\n" +
                            "            z32 0.0+0.0d Ohm/km;\n" +
                            "            z33 " + diag + ";\n" +
                            "        };\n"
                    }
                    temp
            }

        return (ret)
    }

    // emit one GridLAB-D node
    def make_node (slack: String, multiplier: Double)(node: PreNode): String =
    {
        val ret =
        if (node.id_seq == slack)
        {
            val volts = node.voltage * multiplier
            "        object node\n" +
            "        {\n" +
            "            name \"" + node.id_seq + "\";\n" +
            "            phases ABCN;\n" +
            "            bustype SWING;\n" +
            "            nominal_voltage " + node.voltage + " V;\n" +
            "            voltage_A " + volts + "+0.0d V;\n" +
            "            voltage_B " + volts + "-120.0d V;\n" +
            "            voltage_C " + volts + "+120.0d V;\n" +
            "        };\n"
        }
        else
            "        object node\n" +
            "        {\n" +
            "            name \"" + node.id_seq + "\";\n" +
            "            phases ABCN;\n" +
            "            bustype PQ;\n" +
            "            nominal_voltage " + node.voltage + "V;\n" +
            "        };\n"
        return (ret)
    }

    // emit one GridLAB-D edge
    def make_link (edge: PreEdge): String =
    {
        val cls = edge.element.getClass.getName
        val clazz = cls.substring (cls.lastIndexOf (".") + 1)
        // for singlely connected objects, replace with a recorder
        val ret =
            if (edge.id_cn_2 == "")
                "        object recorder\n" +
                "        {\n" +
                "            name \"" + edge.id_equ + "\";\n" +
                "            parent \"" + edge.id_cn_1 + "\";\n" +
                "            property voltage[V];\n" +
                "            file \"" + edge.id_equ + ".csv\";\n" +
                "            interval -1;\n" +
                "        };\n"
            else
                clazz match
                {
                    case "ACLineSegment" =>
                        val line = edge.element.asInstanceOf[ACLineSegment]
                        val typ = if (line.Conductor.ConductingEquipment.Equipment.PowerSystemResource.PSRType == "PSRType_Underground")
                            "underground_line"
                        else
                            "overhead_line"
                        val config = valid_config_name (line.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.name)
                        "        object " + typ + "\n" +
                        "        {\n" +
                        "            name \"" + edge.id_equ + "\";\n" +
                        "            phases ABCN;\n" +
                        "            from \"" + edge.id_cn_1 + "\";\n" +
                        "            to \"" + edge.id_cn_2 + "\";\n" +
//                        (if (line.Conductor.len  <= 0)
//                        "            length 0.05m;\n" // ToDo: ERROR    [INIT] : init_overhead_line(obj=199;VER331619): Newton-Raphson method does not support zero length lines at this time
//                        else
                        "            length " + line.Conductor.len + "m;\n" +
                        "            configuration \"" + config + "\";\n" +
                        "        };\n"
                    case "PowerTransformer" =>
                        val transformer = edge.element.asInstanceOf[PowerTransformer]
                        val power = transformer_power (transformer)
                        // for power transformers without a configuration, just emit a link
                        if ("unknown" == power)
                            "        object link\n" +
                            "        {\n" +
                            "            name \"" + edge.id_equ + "\";\n" +
                            "            phases ABCN;\n" +
                            "            from \"" + edge.id_cn_1 + "\";\n" +
                            "            to \"" + edge.id_cn_2 + "\";\n" +
                            "        };\n"
                        else
                        {
                            "        object transformer\n" +
                            "        {\n" +
                            "            name \"" + edge.id_equ + "\";\n" +
                            "            phases ABCN;\n" +
                            "            from \"" + edge.id_cn_1 + "\";\n" +
                            "            to \"" + edge.id_cn_2 + "\";\n" +
                            "            configuration \"" + edge.id_equ + "_configuration" + "\";\n" +
                            "        };\n" +
                            // make a slack bus
                            "        object node\n" +
                            "        {\n" +
                            "            name \"" + edge.id_equ + "_swing_bus\";\n" +
                            "            phases ABCD;\n" + // ToDo: check if it's delta connected or not
                            "            bustype SWING;\n" +
                            "            nominal_voltage " + edge.v1 + " V;\n" +
                            "            voltage_A " + edge.v1 + "+30.0d V;\n" +
                            "            voltage_B " + edge.v1 + "-90.0d V;\n" +
                            "            voltage_C " + edge.v1 + "+150.0d V;\n" +
                            "        };\n" +
                            // make a fake cable joining the slack bus to the transformer
                            "        object underground_line\n" +
                            "        {\n" +
                            "            name \"" + edge.id_equ + "_feeder\";\n" +
                            "            phases ABCD;\n" + // ToDo: check if it's delta connected or not
                            "            from \"" + edge.id_equ + "_swing_bus\";\n" +
                            "            to \"" + edge.id_cn_1 + "\";\n" +
                            "            length 1000 m;\n" +
                            "            configuration \"" + edge.id_equ + "_fake_line_configuration\";\n" +
                            "        };\n"
                        }
                    case "Switch" =>
                        val switch = edge.element.asInstanceOf[Switch]
                        val status = if (switch.normalOpen) "OPEN" else "CLOSED"
                        "        object switch\n" +
                        "        {\n" +
                        "            name \"" + edge.id_equ + "\";\n" +
                        "            phases ABCN;\n" +
                        "            from \"" + edge.id_cn_1 + "\";\n" +
                        "            to \"" + edge.id_cn_2 + "\";\n" +
                        "            status \"" + status + "\";\n" +
                        "        };\n"
                    case "Cut" |
                         "Disconnector" |
                         "GroundDisconnector" |
                         "Jumper" |
                         "ProtectedSwitch" |
                         "Sectionaliser" =>
                        val switch = edge.element.sup.asInstanceOf[Switch]
                        val status = if (switch.normalOpen) "OPEN" else "CLOSED"
                        "        object switch\n" +
                        "        {\n" +
                        "            name \"" + edge.id_equ + "\";\n" +
                        "            phases ABCN;\n" +
                        "            from \"" + edge.id_cn_1 + "\";\n" +
                        "            to \"" + edge.id_cn_2 + "\";\n" +
                        "            status \"" + status + "\";\n" +
                        "        };\n"
                    case "Breaker" |
                         "LoadBreakSwitch" |
                         "Recloser" =>
                        val switch = edge.element.sup.sup.asInstanceOf[Switch]
                        val status = if (switch.normalOpen) "OPEN" else "CLOSED"
                        "        object switch\n" +
                        "        {\n" +
                        "            name \"" + edge.id_equ + "\";\n" +
                        "            phases ABCN;\n" +
                        "            from \"" + edge.id_cn_1 + "\";\n" +
                        "            to \"" + edge.id_cn_2 + "\";\n" +
                        "            status \"" + status + "\";\n" +
                        "        };\n"
                    case "Fuse" =>
                        val fuse = edge.element.asInstanceOf[Fuse]
                        val current = fuse.Switch.ratedCurrent
                        "        object fuse\n" +
                        "        {\n" +
                        "            name \"" + edge.id_equ + "\";\n" +
                        "            phases ABCN;\n" +
                        "            from \"" + edge.id_cn_1 + "\";\n" +
                        "            to \"" + edge.id_cn_2 + "\";\n" +
                        "            mean_replacement_time 3600.0;\n" + // sometimes: WARNING  [INIT] : Fuse:SIG8494 has a negative or 0 mean replacement time - defaulting to 1 hour
                        (if (current <= 0)
                            "            current_limit 9999.0A;\n" // ensure it doesn't trip
                        else
                            "            current_limit " + current + "A;\n") +
                        "        };\n"
                    case _ =>
                        "        object link\n" +
                        "        {\n" +
                        "            name \"" + edge.id_equ + "\";\n" +
                        "            phases ABCN;\n" +
                        "            from \"" + edge.id_cn_1 + "\";\n" +
                        "            to \"" + edge.id_cn_2 + "\";\n" +
                        "        };\n"
                }
        return (ret)
    }

    def vertex_id (string: String): VertexId =
    {
        string.hashCode().asInstanceOf[VertexId]
    }

    def make_graph_edges (e: PreEdge): org.apache.spark.graphx.Edge[PreEdge] =
    {
        org.apache.spark.graphx.Edge (vertex_id (e.id_cn_1), vertex_id (e.id_cn_2), e)
    }

    def vertexProgram (starting_nodes: Array[VertexId])(id: VertexId, v: Boolean, message: Boolean): Boolean =
    {
        if (message)
            if (v) v else true // here and also below, reuse the vertex data if possible to avoid memory churn
        else
        {
            // on the first pass through the Pregel algorithm all nodes get a false message
            // if this node is in in the list of starting nodes, update the vertex data
            val b = starting_nodes.contains (id)
            if (b)
                if (v) v else true
            else
                if (!v) v else false
        }
    }

    // function to see if the Pregel algorithm should continue or not
    def shouldContinue (element: Element): Boolean =
    {
        val clazz = element.getClass.getName
        val cls = clazz.substring (clazz.lastIndexOf (".") + 1)
        val ret = cls match
        {
            case "Switch" =>
                !element.asInstanceOf[Switch].normalOpen
            case "Cut" =>
                !element.asInstanceOf[Cut].Switch.normalOpen
            case "Disconnector" =>
                !element.asInstanceOf[Disconnector].Switch.normalOpen
            case "Fuse" =>
                !element.asInstanceOf[Fuse].Switch.normalOpen
            case "GroundDisconnector" =>
                !element.asInstanceOf[GroundDisconnector].Switch.normalOpen
            case "Jumper" =>
                !element.asInstanceOf[Jumper].Switch.normalOpen
            case "ProtectedSwitch" =>
                !element.asInstanceOf[ProtectedSwitch].Switch.normalOpen
            case "Sectionaliser" =>
                !element.asInstanceOf[Sectionaliser].Switch.normalOpen
            case "Breaker" =>
                !element.asInstanceOf[Breaker].ProtectedSwitch.Switch.normalOpen
            case "LoadBreakSwitch" =>
                !element.asInstanceOf[LoadBreakSwitch].ProtectedSwitch.Switch.normalOpen
            case "Recloser" =>
                !element.asInstanceOf[Recloser].ProtectedSwitch.Switch.normalOpen
            case "PowerTransformer" =>
                false
            case _ =>
            {
                true
            }
        }
        return (ret)
    }

    def sendMessage (triplet: EdgeTriplet[Boolean, PreEdge]): Iterator[(VertexId, Boolean)] =
    {
        var ret:Iterator[(VertexId, Boolean)] = Iterator.empty

        if (triplet.srcAttr && !triplet.dstAttr) // see if a message is needed
            if (shouldContinue (triplet.attr.element))
                ret = Iterator ((triplet.dstId, true))

        if (!triplet.srcAttr && triplet.dstAttr) // see if a message is needed in reverse
            if (shouldContinue (triplet.attr.element))
                ret = Iterator ((triplet.srcId, true))

        return (ret)
    }

    def mergeMessage (a: Boolean, b: Boolean): Boolean =
    {
        a || b // result message is true if either of them is true
    }

    def export (sc: SparkContext, sqlContext: SQLContext, args: String): String  =
    {
        val arguments = args.split (",").map (
            (s) =>
                {
                    val pair = s.split ("=")
                    if (2 == pair.length)
                        (pair(0), pair(1))
                    else
                        (pair(0), "")
                }
        ).toMap

        // get the name of the equipment of interest
        val equipment = arguments.getOrElse ("equipment", "")

        // see if we should use topology nodes
        val topologicalnodes = arguments.getOrElse ("topologicalnodes", "false").toBoolean

        // get a map of voltages
        val voltages = get ("BaseVoltage", sc).asInstanceOf[RDD[BaseVoltage]].map ((v) => (v.id, v.nominalVoltage)).collectAsMap ()

        // get the terminals
        val terminals = get ("Terminal", sc).asInstanceOf[RDD[Terminal]].filter (null != _.ConnectivityNode)

        // get the terminals keyed by equipment
        val terms = terminals.groupBy (_.ConductingEquipment)

        // get all elements
        val elements = get ("Elements", sc).asInstanceOf[RDD[Element]]

        // get the transformer ends
        val tends = get ("PowerTransformerEnd", sc).asInstanceOf[RDD[PowerTransformerEnd]]

        // get the transformer ends keyed by transformer
        val ends = tends.groupBy (_.PowerTransformer)

        // handle transformers specially, by attaching all PowerTransformerEnd objects to the elements
        val elementsplus = elements.keyBy (_.id).leftOuterJoin (ends)

        // map the terminal 'pairs' to edges
        val edges = elementsplus.join (terms).flatMapValues (edge_operator (voltages, topologicalnodes)).values

        // get terminal to voltage mapping by referencing the equipment voltage for each of two terminals
        val tv = edges.keyBy (_.id_seq_1).union (edges.keyBy (_.id_seq_2)).distinct

        val nodes = if (topologicalnodes)
        {
            // get the topological nodes RDD
            val tnodes = get ("TopologicalNode", sc).asInstanceOf[RDD[TopologicalNode]]

            // map the topological nodes to prenodes with voltages
            tnodes.keyBy (_.id).join (terminals.keyBy (_.TopologicalNode)).values.keyBy (_._2.id).join (tv).values.map (topological_node_operator).distinct
        }
        else
        {
            // get the connectivity nodes RDD
            val connectivitynodes = get ("ConnectivityNode", sc).asInstanceOf[RDD[ConnectivityNode]]

            // map the connectivity nodes to prenodes with voltages
            connectivitynodes.keyBy (_.id).join (terminals.keyBy (_.ConnectivityNode)).values.keyBy (_._2.id).join (tv).values.map (connectivity_node_operator).distinct
        }

        /*
         * Use GraphX to export only the nodes and edges in the trafo-kreise.
         * That is, emit all objects connected to the equipment,
         * isolated from the entire network by transformer(s)
         * i.e. include everything in a "trace all", but stop at transfomers
         */

        // eliminate edges with only one connectivity node, or the same connectivity node
        val real_edges = edges.filter (x => null != x.id_cn_1 && null != x.id_cn_2 && "" != x.id_cn_1 && "" != x.id_cn_2 && x.id_cn_1 != x.id_cn_2)

        // construct the initial graph from the real edges
        val initial = Graph.fromEdges[Boolean, PreEdge](real_edges.map (make_graph_edges), false, _StorageLevel, _StorageLevel)

        // find the starting node
        val starting = terminals.filter (_.ConductingEquipment == equipment).collect ()
        if (0 == starting.length)
            return ("" + starting.length + " equipment matched id " + equipment + "\n") // ToDo: proper logging
        val starting_terminal = starting (0)
        val starting_node_name = if (topologicalnodes) starting_terminal.TopologicalNode else starting_terminal.ConnectivityNode

        // while we could start the Pregel trace from a single node, vertex_id (starting_node_name)
        // it could be more efficient to get all objects that are in the same EquipmentContainer
        val starting_nodes =
            if (SINGLE_START_NODE)
                Array[VertexId] (vertex_id (starting_node_name))
            else
                if (topologicalnodes)
                {
                    // ToDo: add container to TopologicalNode
                    Array[VertexId] (vertex_id (starting_node_name))
                }
                else
                {
                    // find the equipment container containing the requested equipment
                    val connectivitynodes = get ("ConnectivityNode", sc).asInstanceOf[RDD[ConnectivityNode]]
                    val starting_nodes = connectivitynodes.filter (_.id == starting_node_name).collect ()
                    if (0 == starting_nodes.length)
                        return ("" + starting_nodes.length + " nodes matched id " + starting_node_name) // ToDo: proper logging
                    val starting_node = starting_nodes(0)
                    val container_nodes = connectivitynodes.filter (_.ConnectivityNodeContainer == starting_node.ConnectivityNodeContainer).collect ()
                    container_nodes.map (node => vertex_id (node.id))
                }

        // traverse the graph with the Pregel algorithm
        val graph = initial.pregel[Boolean] (false, 10000, EdgeDirection.Either) (vertexProgram (starting_nodes), sendMessage, mergeMessage)

        // get the list of traced vertices
        val touched = graph.vertices.filter (_._2).map (_._1)
        val traced_nodes = touched.keyBy (x => x).join (nodes.keyBy (x => vertex_id (x.id_seq))).reduceByKey ((a, b) ⇒ a).values.values

        // get the list of traced edges
        val traced_edges = traced_nodes.keyBy (_.id_seq).join (real_edges.keyBy (_.id_cn_1).union (real_edges.keyBy (_.id_cn_2))).values.values.keyBy (_.id_equ).reduceByKey ((a, b) ⇒ a).values

        // OK, this is subtle, edges that stop the trace have one node that isn't in the traced_nodes RDD
        val all_traced_nodes = traced_edges.keyBy (_.id_cn_1).union (traced_edges.keyBy (_.id_cn_2)).join (nodes.keyBy (_.id_seq)).reduceByKey ((a, b) ⇒ a).values.values

        // get one of each type of ACLineSegment and emit a configuration for each of them
        val l_strings = traced_edges.map (_.element).filter (_.getClass.getName.endsWith ("ACLineSegment")).asInstanceOf[RDD[ACLineSegment]]
            .keyBy (_.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.name)
            .reduceByKey ((a, b) => a) // all lines with the same name have the same configuration
            .values.map (make_line_configuration)

        // get each transformer and emit a configuration for each of them
        // val pt = traced_edges.map (_.element).filter (_.getClass.getName.endsWith ("PowerTransformer")).asInstanceOf[RDD[PowerTransformer]]
        val shorts = short_circuit_data (sc)
        val pt = traced_edges.map (_.element).keyBy (_.id).join (shorts.keyBy (_._1.id)).values.map (_._2)
        val t_strings = pt
            .keyBy (_._1.id).leftOuterJoin (ends).values
            .map (make_transformer_configuration (voltages))

        val c_strings = l_strings.union (t_strings)
        val n_strings = all_traced_nodes.map (make_node (starting_node_name, 1.03))
        val e_strings = traced_edges.map (make_link)

        c_strings.saveAsTextFile (_TempFilePrefix + _ConfFileName)
        n_strings.saveAsTextFile (_TempFilePrefix + _NodeFileName)
        e_strings.saveAsTextFile (_TempFilePrefix + _EdgeFileName)

        val conffiles = sc.wholeTextFiles (_TempFilePrefix + _ConfFileName)
        val nodefiles = sc.wholeTextFiles (_TempFilePrefix + _NodeFileName)
        val edgefiles = sc.wholeTextFiles (_TempFilePrefix + _EdgeFileName)

        /**
         * Create the output file.
         */

        val USE_UTC = false

        val format =
            if (USE_UTC)
            {
                // for dates in UTC
                val f = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss z")
                f.setTimeZone (TimeZone.getTimeZone ("UTC"))
                f
            }
            else
                // for dates in the local time zone
                new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss z")
        val start = Calendar.getInstance ()
        val finish = start.clone ().asInstanceOf[Calendar]
        finish.add (Calendar.MINUTE, 1)

        val prefix =
            "        module tape;\n" +
            "        module powerflow\n" +
            "        {\n" +
            "            solver_method NR;\n" +
            "            default_maximum_voltage_error 10e-6;\n" +
            "            NR_iteration_limit 5000;\n" +
            "            NR_superLU_procs 16;\n" +
            "            nominal_frequency 50;\n" +
            "        };\n" +
            "\n" +
            "        clock\n" +
            "        {\n" +
            "            timezone " + (if (USE_UTC) "GMT" else "CET-2CEST") + ";\n" + // ToDo: get local time zone string
            "            starttime '" + format.format (start.getTime ()) + "';\n" +
            "            stoptime '" + format.format (finish.getTime ()) + "';\n" +
            "        };\n" +
            "\n" +
            "        class player\n" +
            "        {\n" +
            "            complex value;\n" +
            "        };\n" +
            "\n" +
            "        object voltdump\n" +
            "        {\n" +
            "            filename \"voltdump.csv\";\n" +
            "            mode polar;\n" +
            "            runtime '" + format.format (finish.getTime ()) + "';\n" +
            "        };\n"

        val result = new StringBuilder ()
        result.append (prefix)

        result.append (conffiles.map ((item: Tuple2[String,String]) => item._2).fold ("")((x: String, y: String) => x + y))
        result.append (nodefiles.map ((item: Tuple2[String,String]) => item._2).fold ("")((x: String, y: String) => x + y))
        result.append (edgefiles.map ((item: Tuple2[String,String]) => item._2).fold ("")((x: String, y: String) => x + y))

        return (result.toString ())
    }
}

object GridLABD
{
    def jarForObject (obj: Object): String =
    {
        // see https://stackoverflow.com/questions/320542/how-to-get-the-path-of-a-running-jar-file
        var ret = obj.getClass.getProtectionDomain ().getCodeSource ().getLocation ().getPath ()
        try
        {
            ret = URLDecoder.decode (ret, "UTF-8")
        }
        catch
        {
            case e: UnsupportedEncodingException => e.printStackTrace ()
        }
        if (!ret.toLowerCase ().endsWith (".jar"))
        {
            // as an aid to debugging, make jar in tmp and pass that name
            val name = "/tmp/" + Random.nextInt (99999999) + ".jar"
            val writer = new Jar (new scala.reflect.io.File (new java.io.File (name))).jarWriter ()
            writer.addDirectory (new scala.reflect.io.Directory (new java.io.File (ret + "ch/")), "ch/")
            writer.close ()
            ret = name
        }

        return (ret)
    }

    def main (args: Array[String])
    {
        val gridlab = new GridLABD ()
        val filename = if (args.length > 0)
            args (0)
        else
            "hdfs://sandbox:9000/data/" + "NIS_CIM_Export_b4_Guemligen" + ".rdf"
        val house = if (args.length > 1)
            args (1)
        else
            "HAS10002"

        // create the configuration
        val configuration = new SparkConf (false)
        configuration.setAppName ("GridLAB-D")
        configuration.setMaster ("spark://sandbox:7077")
        configuration.setSparkHome ("/home/derrick/spark-1.6.0-bin-hadoop2.6/")
        configuration.set ("spark.driver.memory", "1g")
        configuration.set ("spark.executor.memory", "4g")
        // get the necessary jar files to send to the cluster
        val s1 = jarForObject (new DefaultSource ()) // "/home/derrick/code/CIMScala/target/CIMScala-2.10-1.6.0-1.6.0.jar"
        val s2 = jarForObject (gridlab) // "/home/derrick/code/CIMApplication/GridLAB-D/target/GridLAB-D-1.0-SNAPSHOT.jar"
        configuration.setJars (Array (s1, s2))

        // register low level classes
        configuration.registerKryoClasses (Array (classOf[Element], classOf[BasicElement], classOf[Unknown]))
        // register CIM case classes
        CHIM.apply_to_all_classes { x => configuration.registerKryoClasses (Array (x.runtime_class)) }
        // register edge related classes
        configuration.registerKryoClasses (Array (classOf[PreEdge], classOf[Extremum], classOf[ch.ninecode.cim.Edge]))

        // make a Spark context and SQL context
        val _Context = new SparkContext (configuration)
        _Context.setLogLevel ("INFO") // Valid log levels include: ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN
        val _SqlContext = new SQLContext (_Context)

        val start = System.nanoTime ()
        val files = filename.split (",")
        val options = new HashMap[String, String] ().asInstanceOf[java.util.Map[String,String]]
        options.put ("StorageLevel", "MEMORY_AND_DISK_SER")
        options.put ("ch.ninecode.cim.make_edges", "true") // backwards compatibility
        options.put ("ch.ninecode.cim.do_join", "false")
        val elements = _SqlContext.read.format ("ch.ninecode.cim").options (options).load (files:_*)
        val count = elements.count

        val read = System.nanoTime ()

        gridlab._StorageLevel = StorageLevel.MEMORY_AND_DISK_SER

        val prep = System.nanoTime ()

        val hdfs_configuration = new Configuration ()
        hdfs_configuration.set ("fs.hdfs.impl", "org.apache.hadoop.hdfs.DistributedFileSystem") // .class.getName ()
        hdfs_configuration.set ("fs.file.impl", "org.apache.hadoop.fs.LocalFileSystem")
        val hdfs = FileSystem.get (URI.create (gridlab._TempFilePrefix), hdfs_configuration)

        val nodePath = new Path (gridlab._NodeFileName)
        val edgePath = new Path (gridlab._EdgeFileName)

        hdfs.delete (nodePath, true)
        hdfs.delete (edgePath, true)

        val result = gridlab.export (_Context, _SqlContext, "equipment=" + house)

        val graph = System.nanoTime ()

        Files.write (Paths.get (house + ".glm"), result.getBytes (StandardCharsets.UTF_8))

        println ("" + count + " elements")
        println ("read : " + (read - start) / 1e9 + " seconds")
        println ("prep : " + (prep - read) / 1e9 + " seconds")
        println ("graph: " + (graph - prep) / 1e9 + " seconds")
        println ("write: " + (System.nanoTime () - graph) / 1e9 + " seconds")
        println ()
    }
}
