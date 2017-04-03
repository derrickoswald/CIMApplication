package ch.ninecode.gl

import java.io.File
import java.io.FileNotFoundException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.sql.Timestamp
import java.sql.Types
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

import scala.collection.Map
import scala.collection.mutable.HashMap
import scala.collection.mutable.HashSet

import org.apache.commons.io.FileUtils
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.permission._
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.FileUtil
import org.apache.hadoop.fs.Path
import org.apache.spark.graphx.Edge
import org.apache.spark.graphx.Graph
import org.apache.spark.graphx.VertexId
import org.apache.spark.graphx.VertexRDD
import org.apache.spark.graphx.EdgeRDD
import org.apache.spark.rdd.RDD
import org.apache.spark.rdd.RDD.rddToPairRDDFunctions
import org.apache.spark.sql.Dataset
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.types.DoubleType
import org.apache.spark.sql.types.StringType
import org.apache.spark.sql.types.StructField
import org.apache.spark.sql.types.StructType
import org.apache.spark.storage.StorageLevel
import org.slf4j.LoggerFactory

import ch.ninecode.cim._
import ch.ninecode.model._

import javax.xml.bind.DatatypeConverter

// just to get a single copy of the vertex_id function
trait Graphable {
    def vertex_id(string: String): VertexId =
        {
            var h = 2166136261l;
            for (c ← string)
                h = (h * 16777619) ^ c
            h.asInstanceOf[VertexId]
        }
}

// define the minimal node and edge classes
case class PreNode(id_seq: String, voltage: Double) extends Graphable with Serializable
case class PreEdge(
    id_seq_1: String,
    id_cn_1: String,
    v1: Double,
    id_seq_2: String,
    id_cn_2: String,
    v2: Double,
    id_equ: String,
    ratedCurrent: Double,
    equipment: ConductingEquipment,
    element: Element,
    connected: Boolean) extends Graphable with Serializable {
    // provide a key on the two connections, independent of to-from from-to ordering
    def key(): String =
        {
            if (id_cn_1 < id_cn_2) id_cn_1 + id_cn_2 else id_cn_2 + id_cn_1
        }
}

case class PV(node: String, solar: SolarGeneratingUnit)
case class Transformer(node: String, transformer: List[TData])

/**
 * trafo - string, NIS Number of transformer feeding house
 * house - string, NIS Number of house being experimented on
 * t0 - timestamp, origin for all experiments
 * t1 - timestamp, start time for this experiment
 * slot - number, unique experiment number (slot in windowed time)
 * window - seconds, duration of the experiment
 * interval - seconds, duration between steps in the experiment
 * from - KW, starting PV power
 * to - KW, ending PV power
 * step - KW, KW increment (resolution of the Einspeiseleistung value)
 */
case class Experiment(trafo: String, house: String, t0: Calendar, slot: Int, window: Int, interval: Int, from: Double, to: Double, step: Double) {
    def dup(c: Calendar): Calendar = c.clone().asInstanceOf[Calendar]
    def t1 = { val t = dup(t0); t.add(Calendar.SECOND, slot * window); t }
    def t2 = { val t = dup(t0); t.add(Calendar.SECOND, (slot + 1) * window); t }
}

/**
 * element - string, node or branch name (ConnectivityNode/TopologicalNode name or NIS Number of cable)
 * millis - long, number of milliseconds since the epoc
 * value_a - complex, phase A value
 * value_b - complex, phase B value
 * value_c - complex, phase C value
 * units - string, Volts for a node, Amps for a branch
 */
case class ThreePhaseComplexDataElement(element: String, millis: Long, value_a: Complex, value_b: Complex, value_c: Complex, units: String)

/**
 * trafo - string, NIS Number of transformer feeding house
 * house - string, NIS Number of house
 * max - Option[Double], maximum KW feed in power or None if no limit was found
 */
case class MaxEinspeiseleistung(trafo: String, house: String, max: Option[Double], reason: String, details: String)

class GridLABD(session: SparkSession) extends Serializable {
    @transient lazy val log = org.apache.log4j.LogManager.getLogger("myLogger")
    log.setLevel(org.apache.log4j.Level.DEBUG)

    //val log = LoggerFactory.getLogger (getClass)

    var USE_TOPOLOGICAL_NODES = true
    var HDFS_URI = "hdfs://sandbox:8020/"
    var DELETE_INTERMEDIATE_FILES = true
    var DELETE_SIMULATION_FILES = true
    var USE_ONE_PHASE = true
    var EXPORT_ONLY = false
    var STORAGE_LEVEL = StorageLevel.MEMORY_ONLY
    
    val fileWriter = new FileWriter(this)

    // for dates without time zones, the timezone of the machine is used:
    //    date +%Z
    // timezone can be set on each node of the cluster with:
    //    dpkg-reconfigure tzdata
    // then choose Europe and then choose Zürich
    //
    // all dates generated by this program include the time zone
    val USE_UTC = true
    val EN_US_LOCALE = new Locale("en", "US")
    val _DateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z")
    if (USE_UTC)
        _DateFormat.setTimeZone(TimeZone.getTimeZone("UTC"))

    def get(name: String, retry: Int): RDD[Element] =
        {
            val rdds = session.sparkContext.getPersistentRDDs.filter(_._2.name == name)
            if (0 < rdds.size)
                rdds.head._2.asInstanceOf[RDD[Element]]
            else if (0 < retry) {
                val continue = try {
                    log.error("get on RDD " + name + " failed, retry " + retry)
                    Thread.sleep(2000L)
                    true
                }
                catch {
                    case _: Throwable ⇒ false
                }
                if (continue)
                    get(name, retry - 1)
                else
                    null
            }
            else
                null
        }

    def get(name: String): RDD[Element] = get(name, 10)

    // make a valid configuration name
    // ERROR    [INIT] : object name '4x4' invalid, names must start with a letter or an underscore
    def valid_config_name(string: String): String =
        {
            if ((null == string) || ("" == string))
                "unknown"
            else if (string.charAt(0).isLetter || ('_' == string.charAt(0)))
                string
            else
                "_" + string
        }

    // function to see if the Pregel algorithm should continue tracing or not
    def shouldContinue(element: Element): Boolean =
        {
            val clazz = element.getClass.getName
            val cls = clazz.substring(clazz.lastIndexOf(".") + 1)
            cls match {
                case "Switch" ⇒
                    !element.asInstanceOf[Switch].normalOpen
                case "Cut" ⇒
                    !element.asInstanceOf[Cut].Switch.normalOpen
                case "Disconnector" ⇒
                    !element.asInstanceOf[Disconnector].Switch.normalOpen
                case "Fuse" ⇒
                    !element.asInstanceOf[Fuse].Switch.normalOpen
                case "GroundDisconnector" ⇒
                    !element.asInstanceOf[GroundDisconnector].Switch.normalOpen
                case "Jumper" ⇒
                    !element.asInstanceOf[Jumper].Switch.normalOpen
                case "ProtectedSwitch" ⇒
                    !element.asInstanceOf[ProtectedSwitch].Switch.normalOpen
                case "Sectionaliser" ⇒
                    !element.asInstanceOf[Sectionaliser].Switch.normalOpen
                case "Breaker" ⇒
                    !element.asInstanceOf[Breaker].ProtectedSwitch.Switch.normalOpen
                case "LoadBreakSwitch" ⇒
                    !element.asInstanceOf[LoadBreakSwitch].ProtectedSwitch.Switch.normalOpen
                case "Recloser" ⇒
                    !element.asInstanceOf[Recloser].ProtectedSwitch.Switch.normalOpen
                case "Conductor" ⇒
                    true
                case "ACLineSegment" ⇒
                    true
                case "PowerTransformer" ⇒
                    false
                case _ ⇒
                    log.error("trace setup encountered edge " + element.id + " with unhandled class '" + cls + "', assumed not conducting")
                    false
            }
        }

    def edge_operator(voltages: Map[String, Double], topologicalnodes: Boolean)(arg: Tuple2[Tuple2[(Element, Double), Option[Iterable[PowerTransformerEnd]]], Iterable[Terminal]]): List[PreEdge] =
        {
            var ret = List[PreEdge]()
                def node_name(t: Terminal): String =
                    {
                        return (if (topologicalnodes) t.TopologicalNode else t.ConnectivityNode)
                    }

            val e = arg._1._1._1
            val ratedCurrent = arg._1._1._2
            val pte_op = arg._1._2
            val t_it = arg._2
            // get the ConductingEquipment
            var c = e
            while ((null != c) && !c.getClass().getName().endsWith(".ConductingEquipment"))
                c = c.sup
            if (null != c) {
                // sort terminals by sequence number (and hence the primary is index 0)
                val terminals = t_it.toArray.sortWith(_.ACDCTerminal.sequenceNumber < _.ACDCTerminal.sequenceNumber)
                // get the equipment
                val equipment = c.asInstanceOf[ConductingEquipment]
                // make a list of voltages
                val volt = 1000.0 * voltages.getOrElse(equipment.BaseVoltage, 0.0)
                val volts =
                    pte_op match {
                        case Some(x: Iterable[PowerTransformerEnd]) ⇒
                            // sort ends by end number
                            // ToDo: handle the case where terminal sequence and end sequence aren't the same
                            val tends = x.toArray.sortWith(_.TransformerEnd.endNumber < _.TransformerEnd.endNumber)
                            tends.map(e ⇒ 1000.0 * voltages.getOrElse(e.TransformerEnd.BaseVoltage, 0.0))
                        case None ⇒
                            Array[Double](volt, volt)
                    }
                // Note: we eliminate 230V edges because transformer information doesn't exist and
                // see also NE-51 NIS.CIM: Export / Missing 230V connectivity
                if (!volts.contains(230.0))
                    // make a pre-edge for each pair of terminals
                    ret = terminals.length match {
                        case 1 ⇒
                            ret :+
                                new PreEdge(
                                    terminals(0).ACDCTerminal.id,
                                    node_name(terminals(0)),
                                    volts(0),
                                    "",
                                    "",
                                    volts(0),
                                    terminals(0).ConductingEquipment,
                                    ratedCurrent,
                                    equipment,
                                    e,
                                    false)
                        case _ ⇒
                            {
                                for (i ← 1 until terminals.length) // for comprehension: iterate omitting the upper bound
                                {
                                    ret = ret :+ new PreEdge(
                                        terminals(0).ACDCTerminal.id,
                                        node_name(terminals(0)),
                                        volts(0),
                                        terminals(i).ACDCTerminal.id,
                                        node_name(terminals(i)),
                                        volts(i),
                                        terminals(0).ConductingEquipment,
                                        ratedCurrent,
                                        equipment,
                                        e,
                                        shouldContinue(e))
                                }
                                ret
                            }
                    }
            }
            //else // shouldn't happen, terminals always reference ConductingEquipment, right?

            return (ret)
        }

    def topological_node_operator(arg: Tuple2[Tuple2[TopologicalNode, Terminal], PreEdge]): PreNode =
        {
            val node = arg._1._1
            val term = arg._1._2
            val edge = arg._2
            PreNode(node.id, if (term.ACDCTerminal.sequenceNumber == 1) edge.v1 else edge.v2)
        }

    def connectivity_node_operator(arg: Tuple2[Tuple2[ConnectivityNode, Terminal], PreEdge]): PreNode =
        {
            val node = arg._1._1
            val term = arg._1._2
            val edge = arg._2
            PreNode(node.id, if (term.ACDCTerminal.sequenceNumber == 1) edge.v1 else edge.v2)
        }

    def base_name(s: String): String =
        {
            if (s.endsWith("_topo_fuse"))
                s.substring(0, s.length - "_topo_fuse".length)
            else if (s.endsWith("_topo"))
                s.substring(0, s.length - "_topo".length)
            else
                s
        }

    def has(string: String): String =
        {
            val n = string.indexOf("_")
            if (0 < n)
                string.substring(0, n)
            else
                string
        }

    def exists(filename: String): Boolean =
        {
            val f = new File(filename)
            f.exists
        }

    //    def load_from_player_file (name: String, voltage: Double): String =
    //    {
    //        // assumes meter data files exist
    //        // from Yamshid,
    //        // then: for file in meter_data/*; do sed -i.bak '/Timestamp/d' $file; done
    //        // and then: for file in meter_data/*; do sed -i.bak 's/\"//g' $file; done
    //        val house = has (name)
    //        // by supplying player files for only EnergyConsumer objects
    //        // this existence test picks only HASXXXX nodes (i.e. not ABGXXXX or PINXXXX)
    //        val ret =
    //            if (exists ("meter_data/" + house + "_R.csv"))
    //                "\n" +
    //                "        object load\n" +
    //                "        {\n" +
    //                "            name \"" + name + "_load\";\n" +
    //                "            parent \"" + name + "\";\n" +
    //                "            phases ABCN;\n" +
    //                "            nominal_voltage " + voltage + "V;\n" +
    //                "            object player\n" +
    //                "            {\n" +
    //                "                property \"constant_current_A\";\n" +
    //                "                file \"meter_data/" + house + "_R.csv\";\n" +
    //                "            };\n" +
    //                "            object player\n" +
    //                "            {\n" +
    //                "                property \"constant_current_B\";\n" +
    //                "                file \"meter_data/" + house + "_S.csv\";\n" +
    //                "            };\n" +
    //                "            object player\n" +
    //                "            {\n" +
    //                "                property \"constant_current_C\";\n" +
    //                "                file \"meter_data/" + house + "_T.csv\";\n" +
    //                "            };\n" +
    //                "        };\n"
    //            else
    //                ""
    //        return (ret)
    //    }

    def make_graph_vertices(v: PreNode): Tuple2[VertexId, PreNode] =
        {
            (v.vertex_id(v.id_seq), v)
        }

    def make_graph_edges(e: PreEdge): Edge[PreEdge] =
        {
            Edge(e.vertex_id(e.id_cn_1), e.vertex_id(e.id_cn_2), e)
        }

    // get the existing photo-voltaic installations keyed by terminal
    def getSolarInstallations(topologicalnodes: Boolean): RDD[Tuple2[String, Iterable[PV]]] =
        {
            // note there are two independent linkages happening here through the UserAttribute class:
            // - SolarGeneratingUnit to ServiceLocation
            // - ServiceLocation to EnergyConsumer

            // link to service location ids via UserAttribute
            val attributes = get("UserAttribute").asInstanceOf[RDD[UserAttribute]]

            // user attributes link through string quantities
            val strings = get("StringQuantity").asInstanceOf[RDD[StringQuantity]]

            // get solar to service linkage, e.g. ("EEA5280", "MST115133")
            // and service to house linkage, e.g. ("MST115133", "HAS138130")
            val pairs = attributes.keyBy(_.value).join(strings.keyBy(_.id)).values.map(x ⇒ (x._1.name, x._2.value))

            // get a simple list of house to pv id pairs
            val links = pairs.join(pairs.map(x ⇒ (x._2, x._1))).values

            // get the pv stations
            val solars = get("SolarGeneratingUnit").asInstanceOf[RDD[SolarGeneratingUnit]]

            // get a simple list of house to pv pairs
            val house_solars = links.map(x ⇒ (x._2, x._1)).join(solars.keyBy(_.id)).values

            // get the terminals
            val terminals = get("Terminal").asInstanceOf[RDD[Terminal]]

            // link to the connectivity/topological node through the terminal
            val t = terminals.keyBy(_.ConductingEquipment).join(house_solars).values.map(
                (x) ⇒ PV(if (topologicalnodes) x._1.TopologicalNode else x._1.ConnectivityNode, x._2))

            val pv = t.groupBy(_.node)

            pv.persist(STORAGE_LEVEL)
            session.sparkContext.getCheckpointDir match {
                case Some(dir) ⇒ pv.checkpoint()
                case None ⇒
            }

            pv
        }

    // Note: we return a bogus value just so there is a time sequential dependence on this by later code
    def prepare(): Tuple2[RDD[Edge[PreEdge]], RDD[(VertexId, PreNode)]] =
        {
            log.info("prepare() begin")

            // get a map of voltages
            val voltages = get("BaseVoltage").asInstanceOf[RDD[BaseVoltage]].map((v) ⇒ (v.id, v.nominalVoltage)).collectAsMap()

            // get the terminals
            val terminals = get("Terminal").asInstanceOf[RDD[Terminal]].filter(null != _.ConnectivityNode)

            // get the terminals keyed by equipment
            val terms = terminals.groupBy(_.ConductingEquipment)

            // get all elements
            val elements = get("Elements").asInstanceOf[RDD[Element]]

            // join with WireInfo to get ratedCurrent (only for ACLineSegments)
            val cableMaxCurrent = getCableMaxCurrent()
            val joined_elements = elements.keyBy(_.id).leftOuterJoin(cableMaxCurrent).map(e ⇒
                {
                    val ele = e._2._1
                    val wire = e._2._2
                    val wireinfo = wire match {
                        case Some(maxCurrent) ⇒ maxCurrent
                        case None ⇒ Double.PositiveInfinity
                    }
                    (ele.id, (ele, wireinfo))
                })

            // get the transformer ends keyed by transformer
            val ends = get("PowerTransformerEnd").asInstanceOf[RDD[PowerTransformerEnd]].groupBy(_.PowerTransformer)

            // handle transformers specially, by attaching all PowerTransformerEnd objects to the elements
            val elementsplus = joined_elements.leftOuterJoin(ends)

            // map the terminal 'pairs' to edges
            val edges = elementsplus.join(terms).flatMapValues(edge_operator(voltages, USE_TOPOLOGICAL_NODES)).values

            // eliminate edges with only one connectivity node, or the same connectivity node
            val real_edges = edges.filter(x ⇒ null != x.id_cn_1 && null != x.id_cn_2 && "" != x.id_cn_1 && "" != x.id_cn_2 && x.id_cn_1 != x.id_cn_2)

            // get terminal to voltage mapping by referencing the equipment voltage for each of two terminals
            val tv = edges.keyBy(_.id_seq_1).union(edges.keyBy(_.id_seq_2)).distinct

            // get the nodes RDD
            val nodes = if (USE_TOPOLOGICAL_NODES) {
                // get the topological nodes RDD
                val tnodes = get("TopologicalNode").asInstanceOf[RDD[TopologicalNode]]

                // map the topological nodes to prenodes with voltages
                tnodes.keyBy(_.id).join(terminals.keyBy(_.TopologicalNode)).values.keyBy(_._2.id).join(tv).values.map(topological_node_operator).distinct
            }
            else {
                // get the connectivity nodes RDD
                val connectivitynodes = get("ConnectivityNode").asInstanceOf[RDD[ConnectivityNode]]

                // map the connectivity nodes to prenodes with voltages
                connectivitynodes.keyBy(_.id).join(terminals.keyBy(_.ConnectivityNode)).values.keyBy(_._2.id).join(tv).values.map(connectivity_node_operator).distinct
            }

            // persist edges and nodes to avoid recompute
            val xedges = real_edges.map(make_graph_edges)
            val xnodes = nodes.map(make_graph_vertices)
            val e = xedges.count
            xedges.name = "xedges"
            xedges.persist(STORAGE_LEVEL)
            val n = xnodes.count
            xnodes.name = "xnodes"
            xnodes.persist(STORAGE_LEVEL)
            session.sparkContext.getCheckpointDir match {
                case Some(dir) ⇒
                    xedges.checkpoint(); xnodes.checkpoint()
                case None ⇒
            }

            log.info("prepare() end")
            val _xedges = session.sparkContext.getPersistentRDDs.filter(_._2.name == "xedges").head._2.asInstanceOf[RDD[Edge[PreEdge]]]
            val _xnodes = session.sparkContext.getPersistentRDDs.filter(_._2.name == "xnodes").head._2.asInstanceOf[RDD[(VertexId, PreNode)]]

            (_xedges, _xnodes)
        }

    def tzString: String =
        {
            // "CET-1CEST"
            val t = Calendar.getInstance()
            val tz = t.getTimeZone
            // ToDo: fractional hour time zones
            tz.getDisplayName(false, TimeZone.SHORT) + (-tz.getOffset(t.getTimeInMillis) / 60 / 60 / 1000) + tz.getDisplayName(true, TimeZone.SHORT)
        }

    def trafokreis_key(transformers: Array[TData]): String =
        {
            transformers.map(_.transformer.id).sortWith(_ < _).mkString("_")
        }

    def export(trafokreis: (String, (Array[TData], Option[(Iterable[PowerFeedingNode], Iterable[PreEdge], Iterable[MaxPowerFeedingNodeEEA])]))): Array[Experiment] =
        {
            val start = javax.xml.bind.DatatypeConverter.parseDateTime("2017-01-24 12:00:00".replace(" ", "T"))
            val finish = javax.xml.bind.DatatypeConverter.parseDateTime("2017-01-24 14:00:00".replace(" ", "T"))
            val simulation = trafokreis._1
            val transformers = trafokreis._2._1

                // find the starting and swing node
                def node(t: Terminal) = if (USE_TOPOLOGICAL_NODES) t.TopologicalNode else t.ConnectivityNode
            val starting =
                transformers.size match {
                    case 0 ⇒
                        throw new IllegalStateException("no transformers in TData array")
                    case 1 ⇒
                        (node(transformers(0).terminal0), node(transformers(0).terminal1))
                    case _ ⇒
                        val s = (node(transformers(0).terminal0), node(transformers(0).terminal1))
                        if (!transformers.forall((x) ⇒ (node(x.terminal0) == s._1)))
                            log.error("transformer group " + simulation + " has different nodes on terminal 0 " + transformers.map((x) ⇒ node(x.terminal0)).mkString(" "))
                        if (!transformers.forall((x) ⇒ (node(x.terminal1) == s._2)))
                            log.error("transformer group " + simulation + " has different nodes on terminal 1 " + transformers.map((x) ⇒ node(x.terminal1)).mkString(" "))
                        s
                }

            fileWriter.eraseInputFile(simulation)

            val result = fileWriter.make_glm(trafokreis, starting._2, simulation, start, finish, starting._1)
            fileWriter.writeInputFile(simulation, simulation + ".glm", result._1.getBytes(StandardCharsets.UTF_8))
            fileWriter.writeInputFile(simulation, "output_data/dummy", null) // mkdir

            result._2
        }

    def read_records(filename_root: String)(filename: String, element: String, units: String): Dataset[(String, ThreePhaseComplexDataElement)] =
        {
                def toTimeStamp(string: String): Long =
                    {
                        _DateFormat.parse(string).getTime()
                    }

            val customSchema = if (USE_ONE_PHASE)
                StructType(
                    Array(
                        StructField("timestamp", StringType, true),
                        StructField("A.real", DoubleType, true),
                        StructField("A.imag", DoubleType, true)))
            else
                StructType(
                    Array(
                        StructField("timestamp", StringType, true),
                        StructField("A.real", DoubleType, true),
                        StructField("A.imag", DoubleType, true),
                        StructField("B.real", DoubleType, true),
                        StructField("B.imag", DoubleType, true),
                        StructField("C.real", DoubleType, true),
                        StructField("C.imag", DoubleType, true)))

            val df = session.read
                .format("csv")
                .option("header", "true")
                .option("comment", "#")
                .schema(customSchema)
                .load(filename)

            import session.implicits._

            df.map((r) ⇒
                {
                    if (USE_ONE_PHASE)
                        (filename_root, ThreePhaseComplexDataElement(element, toTimeStamp(r.getString(0)), Complex(r.getDouble(1), r.getDouble(2)), Complex(0.0, 0.0), Complex(0.0, 0.0), units))
                    else
                        (filename_root, ThreePhaseComplexDataElement(element, toTimeStamp(r.getString(0)), Complex(r.getDouble(1), r.getDouble(2)), Complex(r.getDouble(3), r.getDouble(4)), Complex(r.getDouble(5), r.getDouble(6)), units))
                })

        }

    def check(input: String): Boolean =
        {
            if ( input.contains ("FATAL") || input.contains("ERROR") || input.contains("FAIL")) {
                println("gridlabd failed, message is: " + input)
                false
            }
            else
                true
        }

    def list_files(equipment: String, path: String): TraversableOnce[(String, String)] =
        {
            val hdfs_configuration = new Configuration()
            hdfs_configuration.set("fs.hdfs.impl", "org.apache.hadoop.hdfs.DistributedFileSystem")
            hdfs_configuration.set("fs.file.impl", "org.apache.hadoop.fs.LocalFileSystem")
            val hdfs = FileSystem.get(URI.create(HDFS_URI), hdfs_configuration)

            val root = if ("" == HDFS_URI)
                new Path("simulation/" + equipment + "/" + path)
            else
                new Path("/simulation/" + equipment + "/" + path)

            var files = Vector[(String, String)]()
            try {
                val iterator = hdfs.listFiles(root, false) // ToDo: recursive
                while (iterator.hasNext()) {
                    val fs = iterator.next()
                    val path = fs.getPath().toString()
                    files = files :+ (equipment, path)
                }
            }
            catch {
                case fnf: FileNotFoundException ⇒
                    {
                        // just return 0 files
                    }
            }

            return ((files))
        }

    def read_csv(filename_root: String)(x: String): RDD[(String, ThreePhaseComplexDataElement)] =
        {
            if (x.endsWith("_voltage.csv")) {
                val filebase = x.substring(x.lastIndexOf("/") + 1)
                val element = filebase.substring(0, filebase.indexOf("_voltage.csv"))
                val units = "Volts"
                read_records(filename_root)(x, element, units).rdd
            }
            else if (x.endsWith("_current.csv")) {
                val filebase = x.substring(x.lastIndexOf("/") + 1)
                val element = filebase.substring(0, filebase.indexOf("_current.csv"))
                val units = "Amps"
                read_records(filename_root)(x, element, units).rdd
            }
            else
                null
        }

    def solve(files: RDD[String]): Boolean =
        {
            // assumes gridlabd is installed on every node:
            // download gridlabd (e.g. latest stable release https://sourceforge.net/projects/gridlab-d/files/gridlab-d/Last%20stable%20release/gridlabd-3.2.0-1.x86_64.rpm/download)
            // convert the rpm to a deb usig alien:
            //   sudo alien gridlabd_3.2.0-2_amd64.rpm
            // install on every node:
            //   sudo dpkg -i gridlabd_3.2.0-2_amd64.deb

            val gridlabd =
                if ("" == HDFS_URI) // local
                    Array[String](
                        "bash",
                        "-c",
                        "while read line; do " +
                            "FILE=$line; " +
                            "pushd simulation/$FILE; " +
                            "gridlabd $FILE.glm 2>$FILE.out; " +
                            "popd;" +
                            "cat simulation/$FILE/$FILE.out; " +
                            "done < /dev/stdin")
                else // cluster
                {
                    Array[String](
                        "bash",
                        "-c",
                        "while read line; do " +
                            "FILE=$line; " +
                            //                        "pwd > /tmp/$FILE.environment.log; " +
                            //                        "env >> /tmp/$FILE.environment.log; " +
                            "HDFS_DIR=${HADOOP_HDFS_HOME:-$HADOOP_HOME}; " +
                            "HADOOP_USER_NAME=$SPARK_USER; " +
                            "$HDFS_DIR/bin/hdfs dfs -copyToLocal /simulation/$FILE $FILE; " +
                            "pushd $FILE; " +
                            "gridlabd $FILE.glm 2>$FILE.out; " +
                            "popd; " +
                            "$HDFS_DIR/bin/hdfs dfs -copyFromLocal $FILE/output_data/ /simulation/$FILE; " +
                            "$HDFS_DIR/bin/hdfs dfs -copyFromLocal $FILE/$FILE.out /simulation/$FILE/$FILE.out; " +
                            "cat $FILE/$FILE.out; " +
                            "rm -rf $FILE; " +
                            "done < /dev/stdin")
                }

            val out = files.pipe(gridlabd).cache
            out.first
            out.map(check).fold(true)(_ && _)
        }

    def read_output_files(filename_root: String): RDD[(String, ThreePhaseComplexDataElement)] =
        {
            val file_list = list_files(filename_root, "output_data")
            val data = file_list.map(f => read_csv(f._1) (f._2))
            val some = data.filter ((x) => if (null != x) true else false) // eliminate TRAXXXX_voltdump.csv
            session.sparkContext.union (some.toSeq).cache
        }

    /**
     * Get pairs of cable id and maximum current.
     */
    def getCableMaxCurrent(): RDD[Tuple2[String, Double]] =
        {
            val wireinfos = session.sparkContext.getPersistentRDDs.filter(_._2.name == "WireInfo").head._2.asInstanceOf[RDD[WireInfo]]
            val lines = session.sparkContext.getPersistentRDDs.filter(_._2.name == "ACLineSegment").head._2.asInstanceOf[RDD[ACLineSegment]]
            val keyed = lines.keyBy(_.Conductor.ConductingEquipment.Equipment.PowerSystemResource.AssetDatasheet)
            val cables = keyed.join(wireinfos.keyBy(_.id)).values.map(x ⇒ (x._1.id, x._2.ratedCurrent))

            cables.persist(STORAGE_LEVEL)
            session.sparkContext.getCheckpointDir match {
                case Some(dir) ⇒ cables.checkpoint()
                case None ⇒
            }

            cables
        }

    /**
     * Find the minimum value solution from a collection
     * NOTE: we don't have to sort by time, since the power is monotonically increasing,
     * just by selecting the minimum power solution we've chosen the first measurement over the limit
     */
    def finder(values: Iterable[(Experiment, ThreePhaseComplexDataElement, String, String)]): MaxEinspeiseleistung =
        {
                def seqop(current: MaxEinspeiseleistung, arg: (Experiment, ThreePhaseComplexDataElement, String, String)): MaxEinspeiseleistung =
                    {
                        val experiment = arg._1
                        val data = arg._2
                        val steps = Math.round((data.millis - experiment.t1.getTimeInMillis()) / (experiment.interval * 1000))
                        val kw = experiment.from + (experiment.step * steps)
                        current.max match {
                            case None ⇒
                                MaxEinspeiseleistung(experiment.trafo, experiment.house, Some(kw), arg._3, arg._4)
                            case Some(kw1) ⇒
                                if (kw1 < kw) current else MaxEinspeiseleistung(experiment.trafo, experiment.house, Some(kw), arg._3, arg._4)
                        }
                    }
                def combop(a: MaxEinspeiseleistung, b: MaxEinspeiseleistung): MaxEinspeiseleistung =
                    {
                        a.max match {
                            case None ⇒
                                b
                            case Some(kw1) ⇒
                                b.max match {
                                    case None ⇒
                                        a
                                    case Some(kw2) ⇒
                                        if (kw1 < kw2) a else b
                                }
                        }
                    }
            val trafo = values.head._1.trafo
            val house = values.head._1.house
            values.aggregate(MaxEinspeiseleistung(trafo, house, None, "unknown", ""))(seqop, combop)
        }

    def voltcheck(experiments: Array[Experiment], element: ThreePhaseComplexDataElement, max: Double): (String, (Experiment, ThreePhaseComplexDataElement, String, String)) =
        {
            val limit = "voltage limit"

            // eliminate current measurements and measurements within tolerance
            def interesting1ph(r: ThreePhaseComplexDataElement): Boolean =
                {
                    return (
                        (r.units == "Volts") &&
                        (r.value_a.abs < 1000.0) && // ToDo: remove hard-coded constraint for niederspannung
                        (r.value_a.abs > max))
                }
            def interesting3ph(r: ThreePhaseComplexDataElement): Boolean =
                {
                    return (
                        (r.units == "Volts") &&
                        (r.value_a.abs < 1000.0) && // ToDo: remove hard-coded constraint for niederspannung
                        ((r.value_a.abs > max) || (r.value_b.abs > max) || (r.value_c.abs > max)))
                }          
            
            // assign an experiment to each measurement
            def assign(experiments: Array[Experiment])(r: ThreePhaseComplexDataElement): (String, (Experiment, ThreePhaseComplexDataElement, String, String)) =
                {
                    for (e ← experiments) {
                        if ((e.t1.getTimeInMillis() <= r.millis) && (e.t2.getTimeInMillis() >= r.millis))
                            return (e.house, (e, r, limit, has(r.element) + " > " + max + " Volts"))
                    }
                    null.asInstanceOf[(String, (Experiment, ThreePhaseComplexDataElement, String, String))]
                }

            val interesting = (if (USE_ONE_PHASE) interesting1ph(element) else interesting3ph(element))
            if (interesting)
                assign(experiments)(element)
            else 
                null.asInstanceOf[(String, (Experiment, ThreePhaseComplexDataElement, String, String))]    
        }

    def ampcheck(experiments: Array[Experiment], element: ThreePhaseComplexDataElement, cdata: Iterable[Tuple2[String, Double]]): (String, (Experiment, ThreePhaseComplexDataElement, String, String)) =
        {
            val limit = "current limit"

            // eliminate measurements below capacity
            def interesting1ph(arg: Tuple2[ThreePhaseComplexDataElement, Double]): Boolean =
                {
                    val r = arg._1
                    val max = arg._2
                    r.value_a.abs / Math.sqrt(3) > max
                }
            def interesting3ph(arg: Tuple2[ThreePhaseComplexDataElement, Double]): Boolean =
                {
                    val r = arg._1
                    val max = arg._2
                    ((r.value_a.abs > max) || (r.value_b.abs > max) || (r.value_c.abs > max))
                }

            val cable_max = cdata.find(c ⇒ element.element == c._1) match
            {
                case Some (cable) => cable._2
                case None =>
                    log.error ("cable type " + element.element + " not found")
                    100.0
            }

            // assign an experiment to each measurement
            def assign(experiments: Array[Experiment])(arg: Tuple2[ThreePhaseComplexDataElement, Double]): (String, (Experiment, ThreePhaseComplexDataElement, String, String)) =
                {
                    val r = arg._1
                    val max = arg._2
                    for (e ← experiments) {
                        if ((e.t1.getTimeInMillis() <= r.millis) && (e.t2.getTimeInMillis() >= r.millis))
                            return (e.house, (e, r, limit, r.element + " > " + max + " Amps"))
                    }
                    null.asInstanceOf[(String, (Experiment, ThreePhaseComplexDataElement, String, String))]
                }

            val interesting = (if (USE_ONE_PHASE) interesting1ph((element, cable_max)) else interesting3ph((element, cable_max)))
            if (interesting)
                assign(experiments)((element, cable_max))
            else
                null.asInstanceOf[(String, (Experiment, ThreePhaseComplexDataElement, String, String))]  
        }

    def powercheck(experiments: Array[Experiment], element: ThreePhaseComplexDataElement, power: Double, trafo_name: String): (String, (Experiment, ThreePhaseComplexDataElement, String, String)) =
        {
            val limit = "transformer limit"

            // eliminate voltage measurements and measurements below capacity
            def interesting1ph(i: Double)(r: ThreePhaseComplexDataElement): Boolean =
                {
                    return (
                        if ((r.element == trafo_name) &&
                            (r.units == "Amps") && // redundant
                            (r.value_a.abs > i))
                            true
                        else
                            false)
                }
            def interesting3ph(i: Double)(r: ThreePhaseComplexDataElement): Boolean =
                {
                    return (
                        if ((r.element == trafo_name) &&
                            (r.units == "Amps") && // redundant
                            ((r.value_a.abs > i) || (r.value_b.abs > i) || (r.value_c.abs > i)))
                            true
                        else
                            false)
                }

            // assign an experiment to each measurement
            def assign(experiments: Array[Experiment])(r: ThreePhaseComplexDataElement): (String, (Experiment, ThreePhaseComplexDataElement, String, String)) =
                {
                    for (e ← experiments) {
                        if ((e.t1.getTimeInMillis() <= r.millis) && (e.t2.getTimeInMillis() >= r.millis))
                            return (e.house, (e, r, limit, r.element + " > " + power + " Watts"))
                    }
                    null.asInstanceOf[(String, (Experiment, ThreePhaseComplexDataElement, String, String))]
                }
            
            val i = if (USE_ONE_PHASE) math.sqrt(3) * power / 400.0 else power / (400.0 * math.sqrt(3))
            val interesting = (if (USE_ONE_PHASE) interesting1ph(i)(element) else interesting3ph(i)(element))
            if (interesting)
                assign(experiments)(element)
            else
                null.asInstanceOf[(String, (Experiment, ThreePhaseComplexDataElement, String, String))]
        }

    def analyse(experiments: Array[Experiment])(element: (String, (ThreePhaseComplexDataElement, Option[(Double, Iterable[(String, Double)])]))): List[(String, (Experiment, ThreePhaseComplexDataElement, String, String))] =
        {
            var ret = List().asInstanceOf[List[(String, (Experiment, ThreePhaseComplexDataElement, String, String))]]
            val option = element._2._2
            if (option.isDefined)
            {
            
              val cdata = option.get._2
              val complexDataElement = element._2._1
  
              val nominal = 400.0 // ToDo: get voltage from CIM
              val tolerance = 3.0
              val max = nominal + (nominal * tolerance / 100.0)
              // could also check for under the minimum; r.value_a.abs < min
  
              // get the maximum transformer power as sum(Trafo_Power)*1.44 (from YF)
              val trafo_power = option.get._1
              // get the name of the transformer recorder (matches Trans.emit)
              val trafo_name = element._1
  
              
              if (complexDataElement.units == "Volts")
                  ret = List (voltcheck(experiments, complexDataElement, max))
              else if (complexDataElement.units == "Amps")
                  if (complexDataElement.element == trafo_name)
                      ret = List (powercheck(experiments, complexDataElement, trafo_power, trafo_name))
                  else
                      ret = List (ampcheck(experiments, complexDataElement, cdata))
            }
            ret
        }

    def einspeiseleistung(
        trafokreis: RDD[(String, (Array[TData], Option[(Iterable[PowerFeedingNode], Iterable[PreEdge], Iterable[MaxPowerFeedingNodeEEA])]))]): RDD[MaxEinspeiseleistung] =
        {
            val start = System.nanoTime()
            val filtered_trafos = trafokreis.filter(_._2._2.isDefined)
            val experiments = filtered_trafos.flatMap(export(_)).cache
            experiments.count
            val write = System.nanoTime()
            println("export: " + (write - start) / 1e9 + " seconds")
            println("number of processed trafos: " + filtered_trafos.count)

            var ret = null.asInstanceOf[RDD[MaxEinspeiseleistung]]
            if (!EXPORT_ONLY) {
                val success = solve(filtered_trafos.map(_._1))
                val gridlabd = System.nanoTime()
                println("solve: " + (gridlabd - write) / 1e9 + " seconds")
                println("solve success: " + success)
                
                if (success) {
                    val fileroot_list = filtered_trafos.map(_._1).collect
                    
                    val reduced_trafos = filtered_trafos.mapValues(t ⇒ {
                        val transformers = t._1.map(_.end1.ratedS).sum
                        val cdata_iter = t._2.get._2.filter(_.ratedCurrent < Double.PositiveInfinity).map(e ⇒ (e.element.id, e.ratedCurrent))
                        (transformers, cdata_iter)
                    })
                            
                    fileroot_list.map(fileroot => {
                        val output = read_output_files(fileroot)                      
                        if (output.count != 0) {

                            val exp_per_trafo = experiments.filter(_.trafo == fileroot)
                            val exp_array = exp_per_trafo.collect
                            val r = output.leftOuterJoin(reduced_trafos.filter(_._1 == fileroot)).flatMap(analyse(exp_array))
                            
                            // establish a "no limit found" default
                            val s = exp_per_trafo.map(
                                (x) ⇒
                                    {
                                        (
                                            x.house, (
                                            x,
                                            ThreePhaseComplexDataElement(x.house, x.t2.getTimeInMillis, Double.PositiveInfinity, Double.PositiveInfinity, Double.PositiveInfinity, ""),
                                            "no limit",
                                            ""))
                                    })
      
                            val ret = r.filter(_ != null).union(s).groupByKey().values.map(finder)
                            
                            val max_einpeiseleistung = ret.collect
                            Database.store("Einspeiseleistung", Calendar.getInstance())(max_einpeiseleistung)
    
                            //trafokreis.map(t => cleanup(t._1))
                        }
                    })
                    
                    val finish = System.nanoTime()
                    println("outputread and analyse: " + (finish - gridlabd) / 1e9 + " seconds")
                }
            }

            ret
        }

    def cleanup(equipment: String): Unit =
        {
            if (DELETE_INTERMEDIATE_FILES) {
                val rm =
                    if ("" == HDFS_URI) // local
                        Array[String](
                            "bash",
                            "-c",
                            "while read line; do " +
                                "FILE=$line; " +
                                "rm -rf simulation/$FILE; " +
                                "done < /dev/stdin")
                    else // cluster
                        Array[String](
                            "bash",
                            "-c",
                            "while read line; do " +
                                "FILE=$line; " +
                                "rm -rf $FILE; " +
                                "done < /dev/stdin")
                val files = session.sparkContext.parallelize(Array[String](equipment))
                val del = files.pipe(rm)
                val result = del.collect.mkString("\n")
                if ("" != result)
                    println(result)
            }
            if (DELETE_SIMULATION_FILES)
                fileWriter.eraseInputFile(equipment)
            else {
                fileWriter.eraseInputFile(equipment + "/output_data")
                fileWriter.writeInputFile(equipment, "output_data/dummy", null) // mkdir
            }
        }

}

