package ch.ninecode.gl

import java.io.File
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
import javax.xml.bind.DatatypeConverter

import scala.collection.Map
import scala.Iterator
import scala.tools.nsc.io.Jar
import scala.util.Random

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.LocatedFileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.RemoteIterator;

import org.apache.commons.io.FileUtils
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
import org.apache.spark.sql.Dataset
import org.apache.spark.sql.Row
import org.apache.spark.sql.SQLContext
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.types.{StructType, StructField, StringType, IntegerType, DoubleType}
import org.apache.spark.storage.StorageLevel

import ch.ninecode.cim._
import ch.ninecode.model._

// just to get a single copy of the vertex_id function
trait Graphable
{
    def vertex_id (string: String): VertexId =
    {
        string.hashCode.asInstanceOf[VertexId]
    }
}

// define the minimal node and edge classes
case class PreNode (id_seq: String, voltage: Double) extends Graphable with Serializable
case class PreEdge (id_seq_1: String, id_cn_1: String, v1: Double, id_seq_2: String, id_cn_2: String, v2: Double, id_equ: String, equipment: ConductingEquipment, element: Element) extends Graphable with Serializable
{
    // provide a key on the two connections, independent of to-from from-to ordering
    def key (): String =
    {
        if (id_cn_1 < id_cn_2) id_cn_1 + id_cn_2 else id_cn_2 + id_cn_1
    }
}
case class PV (node: String, solar: SolarGeneratingUnit)
case class Transformer (node: String, transformer: PowerTransformer)

// output
case class Solution (node: String, voltA_mag: Double, voltA_angle: Double, voltB_mag: Double, voltB_angle: Double, voltC_mag: Double, voltC_angle: Double)
case class ThreePhaseComplexVoltageDataElement (millis: Long, value_a: Complex, value_b: Complex, value_c: Complex)
case class ThreePhaseComplexCurrentDataElement (millis: Long, value_a: Complex, value_b: Complex, value_c: Complex)

class GridLABD (session: SparkSession) extends Serializable
{
    val USE_UTC = false

    val _DateFormat = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss z")
    if (USE_UTC)
        _DateFormat.setTimeZone (TimeZone.getTimeZone ("UTC"))

    var _StorageLevel = StorageLevel.MEMORY_ONLY
    var _TempFilePrefix = "hdfs://sandbox:8020/output/"
    var _ConfFileName = "lines"
    var _NodeFileName = "nodes"
    var _EdgeFileName = "edges"
    var _NextSlot = 0

    // name of file containing short circuit Ikw and Sk values for medium voltage transformers
    // e.g.
    //
    // "","Fehlerort","Un","Ikw...RST.","Sk..RST.","Beschreibung..SAP.Nr..","Abgang","NIS.ID","NIS.Name"
    // "1","Scheidbach Turbach",16,-37.34,89.733,20444,"SAA Lauenen","STA2040","Scheidbach"
    // "2","Bachegg",16,-36.22,83.805,20468,"SAA Lauenen","STA9390","Bachegg"
    //
    // this should only be needed until the medium voltage network is fully described and  calculations can
    // be done from the high voltage network "slack bus" connections
    var _CSV = "hdfs://sandbox:8020/data/KS_Leistungen.csv"

    def get (name: String): RDD[Element] =
    {
        val rdds = session.sparkContext.getPersistentRDDs
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

    def base_name (s: String): String =
    {
        if (s.endsWith ("_topo_fuse"))
            s.substring (0, s.length - "_topo_fuse".length)
        else if (s.endsWith ("_topo"))
            s.substring (0, s.length - "_topo".length)
        else
            s
    }

    def emit_pv (solargeneratingunits: List[SolarGeneratingUnit], parent: String, voltage: Double): String =
    {
        var load = ""
        var index = 1
        for (solargeneratingunit <- solargeneratingunits)
        {
            val power = solargeneratingunit.GeneratingUnit.ratedNetMaxP * 1000
            val power3 = power / 3 // per phase
            if (power > 0)
            {
                load +=
                    "\n" +
                    "        object load\n" +
                    "        {\n" +
                    "             name \"" + parent + "_pv_" + index + "\";\n" +
                    "             parent \"" + parent + "\";\n" +
                    "             phases ABCN;\n" +
                    "             constant_power_A -" + power3 + ";\n" +
                    "             constant_power_B -" + power3 + ";\n" +
                    "             constant_power_C -" + power3 + ";\n" +
                    "             nominal_voltage " + voltage + "V;\n" +
                    "             load_class R;\n" +
                    "        }\n"
                index += 1
            }
        }
        load
    }

    def emit_primary_node (transformers: List[PowerTransformer], name: String): String =
    {
        var trafo = ""
//        for (transformer <- transformers)
//        {
//            val id = transformer.id
//            val voltage = "16000" // ToDo: don't cheat here
//            trafo +=
//                "        object node\n" +
//                "        {\n" +
//                "            name \"" + name + "\";\n" + // ToDo: handle multiple transformers
//                "            phases ABCD;\n" + // ToDo: check if it's delta connected or not
//                "            bustype SWING;\n" +
//                "            nominal_voltage " + voltage + " V;\n" +
//                "            voltage_A " + voltage + "+30.0d V;\n" +
//                "            voltage_B " + voltage + "-90.0d V;\n" +
//                "            voltage_C " + voltage + "+150.0d V;\n" +
//                "        };\n"
//        }
        trafo
    }

    def has (string: String): String =
    {
        string.substring (0, string.indexOf ("_"))
    }

    def emit_slack (name: String, voltage: Double): String =
    {
        "        object meter\n" +
        "        {\n" +
        "            name \"" + name + "\";\n" +
        "            phases ABCN;\n" +
        "            bustype SWING;\n" +
        "            nominal_voltage " + voltage + "V;\n" +
// the DELTA-GWYE connection somehow introduces a 30° rotation in the phases, so we compensate here:
        "            voltage_A " + voltage + "+30.0d;\n" +
        "            voltage_B " + voltage + "-90.0d;\n" +
        "            voltage_C " + voltage + "+150.0d;\n" +
// or we could use player files from meter measurements
//        "            object player\n" +
//        "            {\n" +
//        "                property \"voltage_A\";\n" +
//        "                file \"meter_data/TRA16716_R.csv\";\n" +
//        "            };\n" +
//        "            object player\n" +
//        "            {\n" +
//        "                property \"voltage_B\";\n" +
//        "                file \"meter_data/TRA16716_S.csv\";\n" +
//        "            };\n" +
//        "            object player\n" +
//        "            {\n" +
//        "                property \"voltage_C\";\n" +
//        "                file \"meter_data/TRA16716_T.csv\";\n" +
//        "            };\n" +
        "        };\n" +
        "\n" +
        "        object recorder {\n" +
        "            name \"" + has (name) + "_voltage_recorder\";\n" +
        "            parent \"" + name + "\";\n" +
        "            property voltage_A.real,voltage_A.imag,voltage_B.real,voltage_B.imag,voltage_C.real,voltage_C.imag;\n" +
        "            interval 5;\n" +
        "            file \"" + _TempFilePrefix + name + "_voltage.csv\";\n" +
        "        };\n"
    }

    def exists (filename: String): Boolean =
    {
        val f = new File (filename)
        f.exists
    }

    def load_from_player_file (name: String, voltage: Double): String =
    {
        // assumes meter data files exist
        // from Yamshid,
        // then: for file in meter_data/*; do sed -i.bak '/Timestamp/d' $file; done
        // and then: for file in meter_data/*; do sed -i.bak 's/\"//g' $file; done
        val house = has (name)
        // by supplying player files for only EnergyConsumer objects
        // this existence test picks only HASXXXX nodes (i.e. not ABGXXXX or PINXXXX)
        val ret =
            if (exists ("meter_data/" + house + "_R.csv"))
                "\n" +
                "        object load\n" +
                "        {\n" +
                "            name \"" + name + "_load\";\n" +
                "            parent \"" + name + "\";\n" +
                "            phases ABCN;\n" +
                "            nominal_voltage " + voltage + "V;\n" +
                "            object player\n" +
                "            {\n" +
                "                property \"constant_current_A\";\n" +
                "                file \"meter_data/" + house + "_R.csv\";\n" +
                "            };\n" +
                "            object player\n" +
                "            {\n" +
                "                property \"constant_current_B\";\n" +
                "                file \"meter_data/" + house + "_S.csv\";\n" +
                "            };\n" +
                "            object player\n" +
                "            {\n" +
                "                property \"constant_current_C\";\n" +
                "                file \"meter_data/" + house + "_T.csv\";\n" +
                "            };\n" +
                "        };\n"
            else
                ""
        return (ret)
    }

    def ramp_by (from: Double, to: Double, step: Double, angle: Double, interval: Int, slot: Int, window: Int, t0: Calendar): String =
    {
        val ret = new StringBuilder ()
        def addrow (time: Calendar, power: Double, angle: Double) =
        {
            ret.append (DatatypeConverter.printDateTime (time).replace ("T", " ").replace ("+01:00", ""))
            ret.append (",")
            ret.append (- power / 3) // negative load injects power, 1/3 per phase
            ret.append ("<")
            ret.append (angle)
            ret.append ("d\n")
        }
        val time = t0.clone ().asInstanceOf[Calendar]
        // all zero before this slot
        time.add (Calendar.SECOND, window * slot)
        addrow (time, 0.0, angle)
        time.add (Calendar.SECOND, interval)
        var power = from
        while (power < to)
        {
            addrow (time, power, angle)
            time.add (Calendar.SECOND, interval)
            power = power + step
        }
        addrow (time, 0.0, angle) // gridlab extends the first and last rows till infinity -> make them zero

        return (ret.toString ())
    }

    def generate_player_file (name: String, voltage: Double, t0: Calendar): String =
    {
        val house = has (name)
        val ret =
            if (house.startsWith ("HAS"))
            {
//                val accumulator = session.sparkContext.longAccumulator ("Experiment_Slot")
//                accumulator.add (1L)
//                val next_slot = accumulator.value // window in player file time series where this experiment is run
                val next_slot = _NextSlot
                _NextSlot += 1

                val r_phase = 0.0
                val s_phase = 240.0
                val t_phase = 120.0
                var contents = ramp_by (0, 100000, 1000, r_phase, 5, next_slot, 15 * 60, t0)
                Files.write (Paths.get ("generated_data/" + house + "_R.csv"), contents.getBytes (StandardCharsets.UTF_8))
                    contents = ramp_by (0, 100000, 1000, s_phase, 5, next_slot, 15 * 60, t0)
                Files.write (Paths.get ("generated_data/" + house + "_S.csv"), contents.getBytes (StandardCharsets.UTF_8))
                    contents = ramp_by (0, 100000, 1000, t_phase, 5, next_slot, 15 * 60, t0)
                Files.write (Paths.get ("generated_data/" + house + "_T.csv"), contents.getBytes (StandardCharsets.UTF_8))

                "\n" +
                "        object load\n" +
                "        {\n" +
                "            name \"" + name + "_load\";\n" +
                "            parent \"" + name + "\";\n" +
                "            phases ABCN;\n" +
                "            nominal_voltage " + voltage + "V;\n" +
                "            object player\n" +
                "            {\n" +
                "                property \"constant_power_A\";\n" +
                "                file \"generated_data/" + house + "_R.csv\";\n" +
                "            };\n" +
                "            object player\n" +
                "            {\n" +
                "                property \"constant_power_B\";\n" +
                "                file \"generated_data/" + house + "_S.csv\";\n" +
                "            };\n" +
                "            object player\n" +
                "            {\n" +
                "                property \"constant_power_C\";\n" +
                "                file \"generated_data/" + house + "_T.csv\";\n" +
                "            };\n" +
                "        };\n"
            }
            else
                ""
        return (ret)
    }

    def emit_node (name: String, voltage: Double, t0: Calendar): String =
    {
        "        object meter\n" +
        "        {\n" +
        "            name \"" + name + "\";\n" +
        "            phases ABCN;\n" +
        "            bustype PQ;\n" +
        "            nominal_voltage " + voltage + "V;\n" +
        "        };\n" +
//                 load_from_player_file (name, voltage) +
                 generate_player_file (name, voltage, t0) +
        "\n" +
        "        object recorder\n" +
        "        {\n" +
        "            name \"" + has (name) + "_voltage_recorder\";\n" +
        "            parent \"" + name + "\";\n" +
        "            property voltage_A.real,voltage_A.imag,voltage_B.real,voltage_B.imag,voltage_C.real,voltage_C.imag;\n" +
        "            interval 5;\n" +
        "            file \"" + _TempFilePrefix + name + "_voltage.csv\";\n" +
        "        };\n"
    }

    // emit one GridLAB-D node
    def make_node (slack: String, t0: Calendar)(arg: Tuple3[PreNode,Option[Iterable[PV]],Option[Iterable[Transformer]]]): String =
    {
        val node = arg._1
        val pv = arg._2
        val transformer = arg._3
        val loads = pv match
        {
            case Some (solars) =>
                val solargeneratingunits = solars.map ((x) => { x.solar }).toList
                emit_pv (solargeneratingunits, node.id_seq, node.voltage)
            case None =>
                ""
        }
        val trans = transformer match
        {
            case Some (trafos) =>
                val transformers = trafos.map ((x) => { x.transformer }).toList
                emit_primary_node (transformers, node.id_seq)
            case None =>
                ""
        }
        val nod =
            if (node.id_seq == slack)
                emit_slack (node.id_seq, node.voltage)
            else
                emit_node (node.id_seq, node.voltage, t0)

        val ret = nod + trans + loads

        return (ret)
    }

    // emit one GridLAB-D edge
    def make_link (line: Line, trans: Trans) (edges: Iterable[PreEdge]): String =
    {
        val edge = edges.head
        val cls = edge.element.getClass.getName
        val clazz = cls.substring (cls.lastIndexOf (".") + 1)
        val ret =
                clazz match
                {
                    case "ACLineSegment" =>
                        line.emit (edges) +
                        "\n" +
                        "        object recorder {\n" +
                        "            name \"" + edge.id_equ + "_current_recorder\";\n" +
                        "            parent \"" + edge.id_equ + "\";\n" +
                        "            property current_in_A.real,current_in_A.imag,current_in_B.real,current_in_B.imag,current_in_C.real,current_in_C.imag;\n" +
                        "            interval 5;\n" +
                        "            file \"" + _TempFilePrefix + edge.id_equ + "_current.csv\";\n" +
                        "        };\n"
                    case "PowerTransformer" =>
                        trans.emit (edges)
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

    def make_graph_vertices (v: PreNode): Tuple2[VertexId, PreNode] =
    {
        (v.vertex_id (v.id_seq), v)
    }

    def make_graph_edges (e: PreEdge): org.apache.spark.graphx.Edge[PreEdge] =
    {
        org.apache.spark.graphx.Edge (e.vertex_id (e.id_cn_1), e.vertex_id (e.id_cn_2), e)
    }

    // get the existing photo-voltaic installations keyed by terminal
    def getSolarInstallations (topologicalnodes: Boolean): RDD[PV] =
    {

        // start with pv stations
        val solar = get ("SolarGeneratingUnit").asInstanceOf[RDD[SolarGeneratingUnit]]

        // link to service location ids via UserAttribute
        val attributes = get ("UserAttribute").asInstanceOf[RDD[UserAttribute]]
        val sl = solar.keyBy (_.id).join (attributes.keyBy (_.name)).values

        // link to energy consumer (house connection)
        val hs = sl.keyBy (_._2.value).join (attributes.keyBy (_.name))

        // just get the house and pv
        val ss = hs.map (x => (x._2._2.value, x._2._1._1))

        // get the terminals
        val terminals = get ("Terminal").asInstanceOf[RDD[Terminal]].filter (null != _.ConnectivityNode)

        // link to the connectivity node through the terminal
        val t = terminals.keyBy (_.ConductingEquipment).join (ss).values.map (
            (x) => PV (if (topologicalnodes) x._1.TopologicalNode else x._1.ConnectivityNode, x._2))

        return (t)
    }

    def prepare (topologicalnodes: Boolean): Graph[PreNode, PreEdge]  =
    {
        // get a map of voltages
        val voltages = get ("BaseVoltage").asInstanceOf[RDD[BaseVoltage]].map ((v) => (v.id, v.nominalVoltage)).collectAsMap ()

        // get the terminals
        val terminals = get ("Terminal").asInstanceOf[RDD[Terminal]].filter (null != _.ConnectivityNode)

        // get the terminals keyed by equipment
        val terms = terminals.groupBy (_.ConductingEquipment)

        // get all elements
        val elements = get ("Elements").asInstanceOf[RDD[Element]]

        // get the transformer ends keyed by transformer
        val ends = get ("PowerTransformerEnd").asInstanceOf[RDD[PowerTransformerEnd]].groupBy (_.PowerTransformer)

        // handle transformers specially, by attaching all PowerTransformerEnd objects to the elements
        val elementsplus = elements.keyBy (_.id).leftOuterJoin (ends)

        // map the terminal 'pairs' to edges
        val edges = elementsplus.join (terms).flatMapValues (edge_operator (voltages, topologicalnodes)).values

        // eliminate edges with only one connectivity node, or the same connectivity node
        val real_edges = edges.filter (x => null != x.id_cn_1 && null != x.id_cn_2 && "" != x.id_cn_1 && "" != x.id_cn_2 && x.id_cn_1 != x.id_cn_2)

        // get terminal to voltage mapping by referencing the equipment voltage for each of two terminals
        val tv = edges.keyBy (_.id_seq_1).union (edges.keyBy (_.id_seq_2)).distinct

        // get the nodes RDD
        val nodes = if (topologicalnodes)
        {
            // get the topological nodes RDD
            val tnodes = get ("TopologicalNode").asInstanceOf[RDD[TopologicalNode]]

            // map the topological nodes to prenodes with voltages
            tnodes.keyBy (_.id).join (terminals.keyBy (_.TopologicalNode)).values.keyBy (_._2.id).join (tv).values.map (topological_node_operator).distinct
        }
        else
        {
            // get the connectivity nodes RDD
            val connectivitynodes = get ("ConnectivityNode").asInstanceOf[RDD[ConnectivityNode]]

            // map the connectivity nodes to prenodes with voltages
            connectivitynodes.keyBy (_.id).join (terminals.keyBy (_.ConnectivityNode)).values.keyBy (_._2.id).join (tv).values.map (connectivity_node_operator).distinct
        }

        // persist edges and nodes to avoid recompute
        real_edges.persist (_StorageLevel)
        nodes.persist (_StorageLevel)

        // construct the initial graph from the real edges and nodes
        return (Graph.apply[PreNode, PreEdge] (nodes.map (make_graph_vertices), real_edges.map (make_graph_edges), PreNode ("", 0.0), _StorageLevel, _StorageLevel))
    }

    def make_glm (
        topologicalnodes: Boolean,
        initial: Graph[PreNode, PreEdge],
        starting_node: String,
        equipment: String,
        start: Calendar,
        finish: Calendar,
        swing_node: String,
        with_feeder: Boolean): String =
    {
        /*
         * Use GraphX to export only the nodes and edges in the trafo-kreise.
         * That is, emit all objects connected to the equipment,
         * isolated from the entire network by transformer(s)
         * i.e. include everything in a "trace all", but stop at transfomers
         */
        val trace = new Trace (initial)
        val pn = PreNode ("", 0.0) // just to access the vertex_id function
        val start_at = Array[VertexId] (pn.vertex_id (starting_node))
        val (traced_nodes, traced_edges) = trace.run (start_at)

        // GridLAB-D doesn't understand parallel admittance paths, so we have to do it
        val combined_edges = traced_edges.groupBy (_.key).values

        // get one of each type of ACLineSegment and emit a configuration for each of them
        val line = new Line ()
        val l_strings = line.getACLineSegmentConfigurations (combined_edges)

        // get the transformer ends keyed by transformer
        // ToDo: avoid this duplication
        val ends = get ("PowerTransformerEnd").asInstanceOf[RDD[PowerTransformerEnd]].groupBy (_.PowerTransformer)

        // get a map of voltages
        val voltages = get ("BaseVoltage").asInstanceOf[RDD[BaseVoltage]].map ((v) => (v.id, v.nominalVoltage)).collectAsMap ()

        // get the transformer configuration using short circuit data to model the upstream connection
        val _transformers = new Transformers (if (with_feeder) _CSV else null)
        val _td = _transformers.getTransformerData (session)
        val trans = new Trans (_td, ends, voltages, with_feeder)
        val t_strings = trans.getTransformerConfigurations (combined_edges)

        // get the combined configuration strings
        val c_strings = l_strings.union (t_strings)

        // get the existing photo-voltaic installations keyed by terminal
        val solars = getSolarInstallations (topologicalnodes)

        // get the transformers keyed by primary terminal
        val transformers = trans.getTransformers (combined_edges)

        // get the node strings
        val dd = traced_nodes.keyBy (_.id_seq).leftOuterJoin (solars.groupBy (_.node))
        val qq = dd.leftOuterJoin (transformers.groupBy (_.node)).values.map ((x) => (x._1._1, x._1._2, x._2))
        val n_strings = qq.map (make_node (swing_node, start))

        // get the edge strings
        val e_strings = combined_edges.map (make_link (line, trans))

        c_strings.saveAsTextFile (_TempFilePrefix + _ConfFileName)
        n_strings.saveAsTextFile (_TempFilePrefix + _NodeFileName)
        e_strings.saveAsTextFile (_TempFilePrefix + _EdgeFileName)

        val conffiles = session.sparkContext.wholeTextFiles (_TempFilePrefix + _ConfFileName)
        val nodefiles = session.sparkContext.wholeTextFiles (_TempFilePrefix + _NodeFileName)
        val edgefiles = session.sparkContext.wholeTextFiles (_TempFilePrefix + _EdgeFileName)

        /**
         * Create the output file.
         */

        val prefix =
            "// $Id: " + equipment + ".glm\n" +
            "// Einspeiseleistung\n" +
            "//*********************************************\n" +
            "\n" +
            "        module tape;\n" +
            "\n" +
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
            "            starttime '" + _DateFormat.format (start.getTime ()) + "';\n" +
            "            stoptime '" + _DateFormat.format (finish.getTime ()) + "';\n" +
            "        };\n" +
            "\n" +
            "        class player\n" +
            "        {\n" +
            "            complex value;\n" +
            "        };\n" +
            "\n" +
            "        object voltdump\n" +
            "        {\n" +
            "            filename \"" + _TempFilePrefix + equipment + "_voltdump.csv\";\n" +
            "            mode polar;\n" +
            "            runtime '" + _DateFormat.format (finish.getTime ()) + "';\n" +
            "        };\n" +
            "\n"

        val result = new StringBuilder ()
        result.append (prefix)

        result.append (conffiles.map ((item: Tuple2[String,String]) => item._2).fold ("")((x: String, y: String) => x + y))
        result.append (nodefiles.map ((item: Tuple2[String,String]) => item._2).fold ("")((x: String, y: String) => x + y))
        result.append (edgefiles.map ((item: Tuple2[String,String]) => item._2).fold ("")((x: String, y: String) => x + y))

        return (result.toString ())
    }

    def export (session: SparkSession, args: String): String =
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

        // clean up from any prior failed run
        FileUtils.deleteDirectory (new File (_TempFilePrefix))

        // see if we should use topology nodes
        val topologicalnodes = arguments.getOrElse ("topologicalnodes", "false").toBoolean

        // get the name of the equipment of interest
        val equipment = arguments.getOrElse ("equipment", "")

        // get the starting and finishing time
        val t0 = arguments.getOrElse ("start", DatatypeConverter.printDateTime (Calendar.getInstance ()))
        val t0plus = DatatypeConverter.parseDateTime (t0)
        t0plus.add (Calendar.MINUTE, 1)
        val t1 = arguments.getOrElse ("finish", DatatypeConverter.printDateTime (t0plus))

        // get the name of the swing bus equipment
        val swing = arguments.getOrElse ("swing", "")

        // get the flag for adding a medium voltage feeder
        val with_feeder = arguments.getOrElse ("feeder", "false").toBoolean

        // find the starting node
        val starting = get ("Terminal").asInstanceOf[RDD[Terminal]].filter (t => ((null != t.ConnectivityNode) && (equipment == t.ConductingEquipment))).sortBy (t => t.ACDCTerminal.sequenceNumber, false).collect ()
        if (0 == starting.length)
        {
            println ("No equipment matched id " + equipment + "\n") // ToDo: proper logging
            return ("")
        }
        else if (1 < starting.length)
            println ("More than one terminal for " + equipment + " - choosing highest sequence number\n") // ToDo: proper logging
        val starting_terminal = starting(0)
        val starting_node_name = if (topologicalnodes) starting_terminal.TopologicalNode else starting_terminal.ConnectivityNode

        // find the swing bus node
        val swinging = get ("Terminal").asInstanceOf[RDD[Terminal]].filter (t => ((null != t.ConnectivityNode) && (swing == t.ConductingEquipment))).sortBy (t => t.ACDCTerminal.sequenceNumber, false).collect ()
        if (0 == swinging.length)
        {
            println ("No equipment matched id " + swing + "\n") // ToDo: proper logging
            return ("")
        }
        else if (1 < swinging.length)
            println ("More than one terminal for " + swing + " - choosing highest sequence number\n") // ToDo: proper logging
        val swing_terminal = swinging(0)
        val swing_terminal_name = if (topologicalnodes) swing_terminal.TopologicalNode else swing_terminal.ConnectivityNode

        // prepare the initial graph
        val initial = prepare (topologicalnodes)

        val start = DatatypeConverter.parseDateTime (t0)
        val finish = DatatypeConverter.parseDateTime (t1)

        return (make_glm (topologicalnodes, initial, starting_node_name, equipment, start, finish, swing_terminal_name, with_feeder))
    }

    def read_voltage_records (session: SparkSession, filename: String): RDD[ThreePhaseComplexVoltageDataElement] =
    {
        def toTimeStamp (string: String): Long =
        {
            // ToDo: unkludge this assumption of CET
            javax.xml.bind.DatatypeConverter.parseDateTime (string.replace (" CET", "").replace (" ", "T")).getTimeInMillis ()
        }

        val customSchema = StructType (
            Array
            (
                StructField ("timestamp", StringType, true),
                StructField ("voltage_A.real", DoubleType, true),
                StructField ("voltage_A.imag", DoubleType, true),
                StructField ("voltage_B.real", DoubleType, true),
                StructField ("voltage_B.imag", DoubleType, true),
                StructField ("voltage_C.real", DoubleType, true),
                StructField ("voltage_C.imag", DoubleType, true)
            )
        )

        val df = session.read
            .format ("csv")
            .option ("header", "true")
            .option ("comment", "#")
            .schema (customSchema)
            .csv (filename)

        import session.implicits._
        df.map ( r => ThreePhaseComplexVoltageDataElement (toTimeStamp (r.getString (0)), Complex (r.getDouble (1), r.getDouble (2)), Complex (r.getDouble (3), r.getDouble (4)), Complex (r.getDouble (5), r.getDouble (6))) ).rdd
    }

    def read_current_records (session: SparkSession, filename: String): RDD[ThreePhaseComplexCurrentDataElement] =
    {
        def toTimeStamp (string: String): Long =
        {
            // ToDo: unkludge this assumption of CET
            javax.xml.bind.DatatypeConverter.parseDateTime (string.replace (" CET", "").replace (" ", "T")).getTimeInMillis ()
        }

        val customSchema = StructType (
            Array
            (
                StructField ("timestamp", StringType, true),
                StructField ("current_in_A.real", DoubleType, true),
                StructField ("current_in_A.imag", DoubleType, true),
                StructField ("current_in_B.real", DoubleType, true),
                StructField ("current_in_B.imag", DoubleType, true),
                StructField ("current_in_C.real", DoubleType, true),
                StructField ("current_in_C.imag", DoubleType, true)
            )
        )

        val df = session.read
            .format ("csv")
            .option ("header", "true")
            .option ("comment", "#")
            .schema (customSchema)
            .csv (filename)

        import session.implicits._
        df.map ( r => ThreePhaseComplexCurrentDataElement (toTimeStamp (r.getString (0)), Complex (r.getDouble (1), r.getDouble (2)), Complex (r.getDouble (3), r.getDouble (4)), Complex (r.getDouble (5), r.getDouble (6))) ).rdd
    }

    def check (input: String): Boolean =
    {
        if (input.contains ("WARNING") || input.contains ("ERROR") || input.contains ("FAIL"))
        {
            println ("gridlabd failed, message is: " + input)
            false
        }
        else
            true
    }

    def list_files (directory: String): TraversableOnce[String] =
    {
        val hdfs_configuration = new Configuration ()
        hdfs_configuration.set ("fs.hdfs.impl", "org.apache.hadoop.hdfs.DistributedFileSystem")
        hdfs_configuration.set ("fs.file.impl", "org.apache.hadoop.fs.LocalFileSystem")
        val hdfs = FileSystem.get (URI.create (directory), hdfs_configuration)
        val root = new Path (directory)
        var files = Vector[String] ()
        val iterator = hdfs.listFiles (root, false) // ToDo: recursive
        while (iterator.hasNext ())
        {
            // "LocatedFileStatus{path=hdfs://sandbox:8020/data/KS_Leistungen.csv; isDirectory=false; length=403242; replication=1; blocksize=134217728; modification_time=1478602451352; access_time=1478607251538; owner=root; group=supergroup; permission=rw-r--r--; isSymlink=false}"
            // "LocatedFileStatus{path=hdfs://sandbox:8020/data/NIS_CIM_Export_sias_current_20160816_V9_Kiental.rdf; isDirectory=false; length=14360795; replication=1; blocksize=134217728; modification_time=1478607196243; access_time=1478607196018; owner=root; group=supergroup; permission=rw-r--r--; isSymlink=false}"
            val fs = iterator.next ()
            val path = fs.getPath ().toString ()
            files = files :+ path
        }
        files
    }

    def read_result (session: SparkSession, filename: String): RDD[Solution] =
    {
        val customSchema = StructType (
            Array
            (
                StructField ("node_name", StringType, true),
                StructField ("voltA_mag", DoubleType, true),
                StructField ("voltA_angle", DoubleType, true),
                StructField ("voltB_mag", DoubleType, true),
                StructField ("voltB_angle", DoubleType, true),
                StructField ("voltC_mag", DoubleType, true),
                StructField ("voltC_angle", DoubleType, true)
            )
        )

        val df = session.read
            .format ("csv")
            .option ("header", "true")
            .option ("comment", "#")
            .schema (customSchema)
            .csv (filename)

        import session.implicits._
        df.map ( r => Solution (r.getString (0), r.getDouble (1), r.getDouble (2), r.getDouble (3), r.getDouble (4), r.getDouble (5), r.getDouble (6)) ).rdd
    }

    def solve (session: SparkSession, filename_root: String): RDD[Solution] =
    {
        val gridlabd =
            Array[String] (
                "bash",
                "-c",
                "while read line; do " +
                    "FILE=$line; " +
                    "gridlabd $FILE 2>" + _TempFilePrefix + "${FILE%.*}.out; " +
                    "cat " + _TempFilePrefix + "${FILE%.*}.out; " +
                "done < /dev/stdin")

        val files = session.sparkContext.parallelize (Array[String] (filename_root  + ".glm"))
        val out = files.pipe (gridlabd)
        val success = out.map (check).fold (true)(_ && _)
        val ret = if (success)
        {
            val outputs = list_files (_TempFilePrefix)
            for (x <- outputs)
            {
                if (x.endsWith ("_voltage.csv"))
                {
                    val data = read_voltage_records (session, x)
                    data.name = x.substring (x.lastIndexOf ("/") + 1)
                    data.cache ()
                }
                else if (x.endsWith ("_current.csv"))
                {
                    val data = read_current_records (session, x)
                    data.name = x.substring (x.lastIndexOf ("/") + 1)
                    data.cache ()
                }
            }
            read_result (session, _TempFilePrefix + filename_root + "_voltdump.csv")
        }
        else
            session.sparkContext.parallelize (Array[Solution] ())

        return (ret)
    }

    case class DataPoint (
        node: String,
        timestamp: Long,
        r: Complex,
        s: Complex,
        t: Complex)

    def read_datapoint (session: SparkSession, filename: String): RDD[DataPoint] =
    {
        def toTimeStamp (string: String): Long =
        {
            // ToDo: unkludge this assumption of CET
            javax.xml.bind.DatatypeConverter.parseDateTime (string.replace (" CET", "").replace (" ", "T")).getTimeInMillis ()
        }

        val node = filename.substring (0, filename.indexOf ('.'))
        val customSchema = StructType (
            Array
            (
                // "Timestamp","R_Mod","R_Ang","S_Mod","S_Ang","T_Mod","T_Ang"
                // "2015-11-18 12:00:00",0.198788486176047,83.4268614094015,0.194118852729444,70.3877147789223,0.420925355009587,5.30872600027156
                // "2015-11-18 12:05:00",0.210957266522948,82.5091613689104,0.199117955836656,73.3358437157651,0.425782866971673,3.81119591963858
                StructField ("Timestamp", StringType, true),
                StructField ("R_Mod", DoubleType, true),
                StructField ("R_Ang", DoubleType, true),
                StructField ("S_Mod", DoubleType, true),
                StructField ("S_Ang", DoubleType, true),
                StructField ("T_Mod", DoubleType, true),
                StructField ("T_Ang", DoubleType, true)
            )
        )

        val df = session.read
            .format ("csv")
            .option ("header", "true")
            .option ("comment", "#")
            .schema (customSchema)
            .csv (filename)

        import session.implicits._
        df.map ( r => DataPoint (
            node,
            toTimeStamp (r.getString (0)),
            Complex.fromPolar (r.getDouble (1), r.getDouble (2), true),
            Complex.fromPolar (r.getDouble (3), r.getDouble (4), true),
            Complex.fromPolar (r.getDouble (5), r.getDouble (6), true)
            ) ).rdd
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
        val filename = if (args.length > 0)
            args (0)
        else
            "hdfs://sandbox:8020/data/" + "NIS_CIM_Export_sias_current_20160816_Kiental_V9" + ".rdf"

        val house = if (args.length > 1)
            args (1)
        else
            "HAS174735" // Bubenei: "HAS97010", Brügg: "HAS76580" or "HAS6830" or "HAS78459", Gümligen: "HAS10002", Kiental: "HAS174735"

        val start = System.nanoTime ()

        // create the configuration
        val configuration = new SparkConf (false)
        configuration.setAppName ("GridLAB-D")
        configuration.setMaster ("spark://sandbox:7077")
//        configuration.setSparkHome ("/home/derrick/spark/spark-2.0.2-bin-hadoop2.7/")
        configuration.set ("spark.driver.memory", "2g")
        configuration.set ("spark.executor.memory", "4g")
//        configuration.set ("spark.executor.extraJavaOptions", "-XX:+UseCompressedOops -XX:+PrintGCDetails -XX:+PrintGCTimeStamps")
        // get the necessary jar files to send to the cluster
        val s1 = jarForObject (new DefaultSource ())
        val s2 = jarForObject (new Line ())
        configuration.setJars (Array (s1, s2))

        // register low level classes
        configuration.registerKryoClasses (Array (classOf[Element], classOf[BasicElement], classOf[Unknown]))
        // register CIM case classes
        CHIM.apply_to_all_classes { x => configuration.registerKryoClasses (Array (x.runtime_class)) }
        // register edge related classes
        configuration.registerKryoClasses (Array (classOf[PreEdge], classOf[Extremum], classOf[Edge]))
        // register topological classes
        configuration.registerKryoClasses (Array (classOf[CuttingEdge], classOf[TopologicalData]))
        // register GridLAB-D classes
        configuration.registerKryoClasses (Array (classOf[ch.ninecode.gl.PreNode], classOf[ch.ninecode.gl.PreEdge]))

        // make a Spark session
        val session = SparkSession.builder ().config (configuration).getOrCreate () // create the fixture
        session.sparkContext.setLogLevel ("OFF") // Valid log levels include: ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN

        val setup = System.nanoTime ()
        val gridlab = new GridLABD (session)

        val files = filename.split (",")
        val options = new HashMap[String, String] ()
        options.put ("path", filename)
        options.put ("StorageLevel", "MEMORY_AND_DISK_SER")
        options.put ("ch.ninecode.cim.make_edges", "false")
        options.put ("ch.ninecode.cim.do_join", "false")
        options.put ("ch.ninecode.cim.do_topo", "true")
        options.put ("ch.ninecode.cim.do_topo_islands", "true")

        val elements = session.read.format ("ch.ninecode.cim").options (options).load (files:_*)
        // this fails with ClassCastException:
        //     val count = elements.count
        // cannot assign instance of scala.collection.immutable.List$SerializationProxy
        // to field org.apache.spark.sql.execution.RDDConversions$$anonfun$rowToRowRdd$1.outputTypes$2
        // of type scala.collection.Seq in instance of org.apache.spark.sql.execution.RDDConversions$$anonfun$rowToRowRdd$1
        elements.printSchema
        elements.explain
        val read = System.nanoTime ()

        gridlab._StorageLevel = StorageLevel.MEMORY_AND_DISK_SER

        val hdfs_configuration = new Configuration ()
        hdfs_configuration.set ("fs.hdfs.impl", "org.apache.hadoop.hdfs.DistributedFileSystem") // .class.getName ()
        hdfs_configuration.set ("fs.file.impl", "org.apache.hadoop.fs.LocalFileSystem")
        val hdfs = FileSystem.get (URI.create (gridlab._TempFilePrefix), hdfs_configuration)

        val confPath = new Path (gridlab._TempFilePrefix, gridlab._ConfFileName)
        val nodePath = new Path (gridlab._TempFilePrefix, gridlab._NodeFileName)
        val edgePath = new Path (gridlab._TempFilePrefix, gridlab._EdgeFileName)

        hdfs.delete (confPath, true)
        hdfs.delete (nodePath, true)
        hdfs.delete (edgePath, true)

        val result = gridlab.export (session, "equipment=" + house + ",topologicalnodes=true")

        val graph = System.nanoTime ()
        Files.write (Paths.get (house + ".glm"), result.getBytes (StandardCharsets.UTF_8))

        // clean up this run
        hdfs.delete (new Path (gridlab._TempFilePrefix), true)

        //println ("" + count + " elements")
        println ("setup : " + (setup - start) / 1e9 + " seconds")
        println ("read : " + (read - setup) / 1e9 + " seconds")
        println ("graph: " + (graph - read) / 1e9 + " seconds")
        println ("write: " + (System.nanoTime () - graph) / 1e9 + " seconds")
        println ()
    }
}
