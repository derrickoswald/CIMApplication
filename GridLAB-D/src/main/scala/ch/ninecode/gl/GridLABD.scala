package ch.ninecode.gl

import java.io.File
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

import scala.collection.Map

import org.apache.commons.io.FileUtils
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.permission._
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.FileUtil
import org.apache.hadoop.fs.Path
import org.apache.spark.graphx.Edge
import org.apache.spark.graphx.VertexId
import org.apache.spark.rdd.RDD
import org.apache.spark.rdd.RDD.rddToPairRDDFunctions
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.LoggerFactory

import ch.ninecode.model._

/**
 * Common GraphX functions.
 * Used just to get a single copy of the vertex_id function.
 */
trait Graphable
{
    /**
     * Compute the vertex id.
     * @param string The CIM MRID.
     */
    def vertex_id(string: String): VertexId =
    {
        var h = 2166136261l;
        for (c ← string)
            h = (h * 16777619) ^ c
        h.asInstanceOf[VertexId]
    }
}

// define the minimal node and edge classes

/**
 * Vertex data.
 * @param id_seq ConnectivityNode or TopologicalNode MRID.
 * @param voltage Node voltage.
 */
case class PreNode(
    id_seq: String,
    voltage: Double) extends Graphable with Serializable

/**
 * Edge data.
 * @param id_seq_1 Terminal 1 MRID.
 * @param id_cn_1 Terminal 1 ConnectivityNode or TopologicalNode MRID.
 * @param v1 Terminal 1 voltage
 * @param id_seq_2 Terminal 2 MRID.
 * @param id_cn_2 Terminal 2 ConnectivityNode or TopologicalNode MRID.
 * @param v2 Terminal 2 voltage
 * @param id_equ ConductingEquipment MRID.
 * @param ratedCurrent Cable rated current (A).
 * @param equipment ConductingEquipment object for the edge.
 * @param element Element object for the edge.
 * @param connected Flag indicating if there is connectivity through the edge (for tracing).
 */
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
    /**
     * Ordered key.
     * Provide a key on the two connections, independent of to-from from-to ordering.
     */
    def key(): String =
    {
        if (id_cn_1 < id_cn_2) id_cn_1 + id_cn_2 else id_cn_2 + id_cn_1
    }
}

/**
 * Photovoltaic attachment.
 * Generating equipment attached to a node.
 * @param node ConnectivityNode or TopologicalNode MRID.
 * @param solar SolarGeneratingUnit object attached to the node.
 */
case class PV(
    node: String,
    solar: SolarGeneratingUnit)

/**
 * Recorder time series element.
 * @param element Node or branch name (ConnectivityNode/TopologicalNode name or MRID of cable).
 * @param millis Number of milliseconds since the epoc.
 * @param value_a Phase A value.
 * @param value_b Phase B value.
 * @param value_c Phase C value.
 * @param units <code>Volts</code> for a node, <code>Amps</code> for an edge.
 */
case class ThreePhaseComplexDataElement(
    element: String,
    millis: Long,
    value_a: Complex,
    value_b: Complex,
    value_c: Complex,
    units: String)

/**
 * Compute the maximum feed-in power at house connections in a network.
 */
class GridLABD (session: SparkSession, one_phase: Boolean) extends Serializable
{
    val log = LoggerFactory.getLogger (getClass)

    var USE_TOPOLOGICAL_NODES = true
    var HDFS_URI = "hdfs://sandbox:8020/"
    var STORAGE_LEVEL = StorageLevel.MEMORY_ONLY

    val fileWriter = new FileWriter (this, one_phase)

    /**
     * Lookup CIM RDD by name.
     * @param name The unqualified name of the RDD (name of the class)
     */
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

    /**
     * The name of the node associated with a terminal.
     * @param terminal The terminal object to get the node for.
     * @return The name of the TopologicalNode or ConnectivityNode.
     */
    def node_name (t: Terminal): String =
    {
        return (if (USE_TOPOLOGICAL_NODES) t.TopologicalNode else t.ConnectivityNode)
    }

    /**
     * Function to see if the Pregel algorithm should continue tracing or not.
     * @param element The edge element (subclass of ConductingEquipment).
     */
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

    def edge_operator(voltages: Map[String, Double])(arg: Tuple2[Tuple2[(Element, Double), Option[Iterable[PowerTransformerEnd]]], Iterable[Terminal]]): List[PreEdge] =
    {
        var ret = List[PreEdge]()

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
        if (s.endsWith("_fuse_topo"))
            s.substring(0, s.length - "_fuse_topo".length)
        else if (s.endsWith("_topo"))
            s.substring(0, s.length - "_topo".length)
        else
            s
    }

    def exists(filename: String): Boolean =
    {
        val f = new File(filename)
        f.exists
    }

    def make_graph_vertices(v: PreNode): Tuple2[VertexId, PreNode] =
    {
        (v.vertex_id(v.id_seq), v)
    }

    def make_graph_edges(e: PreEdge): Edge[PreEdge] =
    {
        Edge(e.vertex_id(e.id_cn_1), e.vertex_id(e.id_cn_2), e)
    }

    def filterValidSolarUnits(pv: RDD[PV]): RDD[PV] =
    {
        val lifecycle = get("LifecycleDate").asInstanceOf[RDD[LifecycleDate]]
        val asset = get("Asset").asInstanceOf[RDD[Asset]]
        val lifecycle_per_eea = asset.keyBy(_.lifecycle).join(lifecycle.keyBy(_.id)).map(l ⇒ (l._2._1.IdentifiedObject.name, (l._2._2)))
        val pv_lifecycle = pv.keyBy(_.solar.id).leftOuterJoin(lifecycle_per_eea)

        def lifecycleValid(lifecycle: LifecycleDate): Boolean =
        {
            if (lifecycle.installationDate != null)
                true
            else if (lifecycle.receivedDate != null)
            {
                val _DateFormat = new SimpleDateFormat("dd.MM.yyyy")
                val receivedDate = _DateFormat.parse(lifecycle.receivedDate)
                val now = new Date()
                val diffTime = now.getTime() - receivedDate.getTime()
                val diffDays = diffTime / (1000 * 60 * 60 * 24);
                diffDays < 400
            }
            else
                false
        }

        val valid_pv = pv_lifecycle.filter(p ⇒ {
            val lifecycle_option = p._2._2
            if (lifecycle_option.isDefined)
                lifecycleValid(lifecycle_option.get)
            else
                false
        })

        valid_pv.map(_._2._1)
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

        val filteredPV = filterValidSolarUnits(t)
        val pv = filteredPV.groupBy(_.node)

        pv.persist(STORAGE_LEVEL)
        session.sparkContext.getCheckpointDir match {
            case Some(dir) ⇒ pv.checkpoint()
            case None ⇒
        }

        pv
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

    // Note: we return a bogus value just so there is a time sequential dependence on this by later code
    def prepare(): Tuple2[RDD[Edge[PreEdge]], RDD[(VertexId, PreNode)]] =
    {
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
        val edges = elementsplus.join(terms).flatMapValues (edge_operator (voltages)).values

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

    def export(trafokreis: Trafokreis): Unit =
    {
        val name = trafokreis.name
        eraseInputFile (name)
        val result = fileWriter.make_glm (trafokreis)
        writeInputFile (name, name + ".glm", result.getBytes (StandardCharsets.UTF_8))
        writeInputFile (name, "output_data/dummy", null) // mkdir
    }

    def check(input: String): Boolean =
    {
        if (input.contains ("FATAL") || input.contains("ERROR") || input.contains("FAIL")) {
            println("gridlabd failed, message is: " + input)
            false
        }
        else
            true
    }

    def solve (files: RDD[String]): Boolean =
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
                        "export FILE=$line; " +
                        "pushd simulation/$FILE; " +
                        "gridlabd $FILE.glm 2>&1 | awk '{print ENVIRON[\"FILE\"] \" \" $0}' > $FILE.out; " +
                        "cat output_data/* > output.txt; " +
                        "popd;" +
                        "cat simulation/$FILE/$FILE.out; " +
                        "done < /dev/stdin")
            else // cluster
            {
                Array[String](
                    "bash",
                    "-c",
                    "while read line; do " +
                        "export FILE=$line; " +
                        "HDFS_DIR=${HADOOP_HDFS_HOME:-$HADOOP_HOME}; " +
                        "HADOOP_USER_NAME=$SPARK_USER; " +
                        "$HDFS_DIR/bin/hdfs dfs -copyToLocal /simulation/$FILE $FILE; " +
                        "pushd $FILE; " +
                        "gridlabd $FILE.glm 2>&1 | awk '{print ENVIRON[\"FILE\"] \" \" $0}' > $FILE.out; " +
                        "cat output_data/* > output.txt; " +
                        "popd; " +
                        "$HDFS_DIR/bin/hdfs dfs -copyFromLocal $FILE/output.txt /simulation/$FILE; " +
                        "$HDFS_DIR/bin/hdfs dfs -copyFromLocal $FILE/$FILE.out /simulation/$FILE/$FILE.out; " +
                        "cat $FILE/$FILE.out; " +
                            "rm -rf $FILE; " +
                            "done < /dev/stdin")
            }

        val out = files.pipe(gridlabd)
        out.map(check).fold(true)(_ && _)
    }

    def read_output_files (one_phase: Boolean, reduced_trafos: RDD[(String, (Double, Iterable[(String, Double)]))]): RDD[(String, ThreePhaseComplexDataElement)] =
    {
        def toTimeStamp(string: String): Long =
        {
            fileWriter._DateFormat.parse(string).getTime()
        }

        val base_folder = "simulation"
        val output_folder = "output_data"
        val path =
            if (HDFS_URI == "")
                base_folder + "/*/output.txt"
            else
                "/" + base_folder + "/*/output.txt"

        val executors = session.sparkContext.getExecutorMemoryStatus.keys.size - 1
        val files = session.sparkContext.wholeTextFiles(path, executors)

        files.map(k ⇒ {
            val path = k._1
            val trafo_pattern = ".*" + base_folder + "/(.*)/output.txt"
            val trafo = path.replaceAll(trafo_pattern, "$1")
            (trafo, k._2)

        }).flatMapValues(f ⇒ {

            var units = ""
            var element = ""
            val content = f.split("\n").filter(s ⇒ s.startsWith("# file") || s.startsWith("2017"))

            content.map(c ⇒ {
                if (c.startsWith("# file")) {
                    val filename_pattern = "# file...... output_data/(.*)"
                    val filename = c.replaceAll(filename_pattern, "$1")
                    element = filename.substring(0, filename.indexOf("_"))

                    if (filename.endsWith("_voltage.csv"))
                        units = "Volts"
                    else if (filename.endsWith("_current.csv"))
                        units = "Amps"
                    null.asInstanceOf[ThreePhaseComplexDataElement]
                }
                else {
                    val c_arr = c.split(",")

                    if (one_phase)
                        ThreePhaseComplexDataElement(element, toTimeStamp(c_arr(0)), Complex(c_arr(1).toDouble, c_arr(2).toDouble), Complex(0.0, 0.0), Complex(0.0, 0.0), units)
                    else
                        ThreePhaseComplexDataElement(element, toTimeStamp(c_arr(0)), Complex(c_arr(1).toDouble, c_arr(2).toDouble), Complex(c_arr(3).toDouble, c_arr(4).toDouble), Complex(c_arr(5).toDouble, c_arr(6).toDouble), units)
                }
            }).filter(_ != null)
        })
    }

    def writeInputFile(equipment: String, path: String, bytes: Array[Byte]) =
    {
        if ("" == HDFS_URI)
        {
            // ToDo: check for IOException
            val file = Paths.get("simulation/" + equipment + "/" + path)
            Files.createDirectories(file.getParent())
            if (null != bytes)
                Files.write(file, bytes)
        }
        else
        {
            val hdfs_configuration = new Configuration()
            hdfs_configuration.set("fs.hdfs.impl", "org.apache.hadoop.hdfs.DistributedFileSystem")
            hdfs_configuration.set("fs.file.impl", "org.apache.hadoop.fs.LocalFileSystem")
            val hdfs = FileSystem.get(URI.create(HDFS_URI), hdfs_configuration)

            val file = new Path("/simulation/" + equipment + "/" + path)
            // wrong: hdfs.mkdirs (file.getParent (), new FsPermission ("ugoa+rwx")) only permissions && umask
            // fail: FileSystem.mkdirs (hdfs, file.getParent (), new FsPermission ("ugoa+rwx")) if directory exists
            hdfs.mkdirs(file.getParent(), new FsPermission("ugoa-rwx"))
            hdfs.setPermission(file.getParent(), new FsPermission("ugoa-rwx")) // "-"  WTF?

            if (null != bytes)
            {
                val out = hdfs.create(file)
                out.write(bytes)
                out.close()
            }
        }
    }

    def eraseInputFile(equipment: String)
    {
        if ("" == HDFS_URI)
            FileUtils.deleteDirectory(new File("simulation/" + equipment + "/"))
        else
        {
            val hdfs_configuration = new Configuration()
            hdfs_configuration.set("fs.hdfs.impl", "org.apache.hadoop.hdfs.DistributedFileSystem")
            hdfs_configuration.set("fs.file.impl", "org.apache.hadoop.fs.LocalFileSystem")
            val hdfs = FileSystem.get(URI.create(HDFS_URI), hdfs_configuration)

            val directory = new Path("/simulation/" + equipment + "/")
            hdfs.delete(directory, true)
        }
    }

    def cleanup(equipment: String, includes_glm: Boolean): Unit =
    {
        if (includes_glm)
            eraseInputFile(equipment)
        else
        {
            eraseInputFile(equipment + "/input_data")
            eraseInputFile(equipment + "/output_data")
            writeInputFile(equipment, "/output_data/dummy", null) // mkdir
            if (!(HDFS_URI == ""))
            {
                eraseInputFile(equipment + "/output.txt")
                eraseInputFile(equipment + "/" + equipment + ".out")
            }
        }
    }
}

