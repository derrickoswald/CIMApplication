package ch.ninecode.gl

import java.io.File
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.text.SimpleDateFormat

import scala.collection.Map

import org.apache.commons.io.FileUtils
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path
import org.apache.hadoop.fs.permission.FsPermission
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
    def vertex_id (string: String): VertexId =
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
    voltage: Double) extends GLMNode with Graphable with Serializable
{
    override def id () = id_seq
    override def nominal_voltage () = voltage
}

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
    connected: Boolean) extends GLMEdge with Graphable with Serializable {
    /**
     * Ordered key.
     * Provide a key on the two connections, independent of to-from from-to ordering.
     */
    def key(): String =
    {
        if (id_cn_1 < id_cn_2) id_cn_1 + id_cn_2 else id_cn_2 + id_cn_1
    }
    override def id (): String = id_equ
    override def cn1 (): String = id_cn_1
    override def cn2 (): String = id_cn_2
    override def eq (): ConductingEquipment = equipment
    override def el (): Element = element
}

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
 * @param session The Spark session.
 * @param topological_nodes If <code>true</code>, use the TopologyNode attribute of Terminal objects, otherwise use the ConnectivityNode attribute.
 * If true, it assumes that the CIMReader has been instructed to run the NetworkTopologicalProcessor to update the terminals,
 * or the terminals already have a valid TopologicalNode when they are read in.
 * @param one_phase If <code>true</code>, generate and analyze load flow with a single phase model.
 * @param storage_level Specifies the <a href="https://spark.apache.org/docs/latest/programming-guide.html#which-storage-level-to-choose">Storage Level</a> used to persist and serialize the objects.
 * @param workdir The working directory for .glm and input_file generation. If the scheme is file:// or none,
 * the gridlabd processes will assume that this is local and will try to execute in that directory.
 * If the scheme is hdfs:// or wasb:// (or something else) the gridlab processes will copy the files locally
 * (either to \$SPARK_HOME/work/app-<date-time>-<app#> when running standalone,
 * or to \$HADOOP_HOME/logs/userlogs/application_<timestamp>_<appId> when running under Yarn)
 * to execute and then copy the output files back to this location (workdir) when complete.
 */
class GridLABD (
    session: SparkSession,
    topological_nodes: Boolean = true,
    one_phase: Boolean = false,
    storage_level: StorageLevel = StorageLevel.fromString ("MEMORY_AND_DISK_SER"),
    workdir: String = "hdfs://" + java.net.InetAddress.getLocalHost().getHostName() + "/simulation/") extends Serializable
{
    val log = LoggerFactory.getLogger (getClass)

    /**
     * Get the working directory ensuring a slash terminator.
     */
    val workdir_slash: String = if (workdir.endsWith ("/")) workdir else workdir + "/"

    /**
     * Get the scheme for the working directory.
     */
    val workdir_scheme: String =
    {
        val uri = new URI (workdir_slash)
        if (null == uri.getScheme)
            ""
        else
            uri.getScheme
    }

    /**
     * Get the path component of the working directory.
     */
    val workdir_path: String =
    {
        val uri = new URI (workdir_slash)
        if (null == uri.getPath)
            "/"
        else
            uri.getPath
    }

    /**
     * Get just the URI for the working directory.
     */
    val workdir_uri: String =
    {
        val uri = new URI (workdir_slash)
        if (null == uri.getScheme)
            ""
        else
            uri.getScheme + "://" + (if (null == uri.getAuthority) "" else uri.getAuthority) + "/"
    }

    /**
     * Lookup CIM RDD by name.
     * @param name The unqualified name of the RDD (name of the class)
     * @return The RDD found or null if nothing was found.
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
        return (if (topological_nodes) t.TopologicalNode else t.ConnectivityNode)
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

    /**
     * Get pairs of cable id and maximum current.
     */
    def getCableMaxCurrent(): RDD[Tuple2[String, Double]] =
    {
        val wireinfos = session.sparkContext.getPersistentRDDs.filter(_._2.name == "WireInfo").head._2.asInstanceOf[RDD[WireInfo]]
        val lines = session.sparkContext.getPersistentRDDs.filter(_._2.name == "ACLineSegment").head._2.asInstanceOf[RDD[ACLineSegment]]
        val keyed = lines.keyBy(_.Conductor.ConductingEquipment.Equipment.PowerSystemResource.AssetDatasheet)
        val cables = keyed.join(wireinfos.keyBy(_.id)).values.map(x ⇒ (x._1.id, x._2.ratedCurrent))

        cables.persist(storage_level)
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
        val nodes = if (topological_nodes) {
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
        xedges.persist(storage_level)
        val n = xnodes.count
        xnodes.name = "xnodes"
        xnodes.persist(storage_level)
        session.sparkContext.getCheckpointDir match
        {
            case Some(dir) ⇒
                xedges.checkpoint()
                xnodes.checkpoint()
            case None ⇒
        }

        (xedges, xnodes)
    }

    def trafokreis_key (transformers: Array[TData]): String =
    {
        transformers.map(_.transformer.id).sortWith(_ < _).mkString("_")
    }

    def export (generator: GLMGenerator): Unit =
    {
        val name = generator.name
        eraseInputFile (name)
        val result = generator.make_glm ()
        writeInputFile (name, name + ".glm", result.getBytes (StandardCharsets.UTF_8))
        //writeInputFile (name, "input_data/dummy", null) // mkdir
        writeInputFile (name, "output_data/dummy", null) // mkdir
    }

    def check (input: String): Boolean =
    {
        if (input.contains ("FATAL") || input.contains ("ERROR") || input.contains ("FAIL") || input.contains ("command not found"))
        {
            log.error ("gridlabd failed, message is: " + input)
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
            if ((workdir_scheme == "file") || (workdir_scheme == "")) // local[*]
            {
                Array[String](
                    "bash",
                    "-c",
                    "while read line; do " +
                        "export FILE=$line; " +
                        "pushd " + workdir_path + "$FILE; " +
                        "gridlabd $FILE.glm 2>&1 | awk '{print ENVIRON[\"FILE\"] \" \" $0}' > $FILE.out; " +
                        "cat output_data/* > output.txt; " +
                        "cat $FILE.out; " +
                        "popd; " +
                        "done < /dev/stdin")
            }
            else // cluster, either hdfs://XX or wasb://YY
            {
                Array[String](
                    "bash",
                    "-c",
                    "while read line; do " +
                        "export FILE=$line; " +
                        "HDFS_DIR=${HADOOP_HDFS_HOME:-$HADOOP_HOME}; " +
                        "HADOOP_USER_NAME=$SPARK_USER; " +
                        "$HDFS_DIR/bin/hdfs dfs -copyToLocal " + workdir_path + "$FILE $FILE; " +
                        "pushd $FILE; " +
                        "gridlabd $FILE.glm 2>&1 | awk '{print ENVIRON[\"FILE\"] \" \" $0}' > $FILE.out; " +
                        "cat output_data/* > output.txt; " +
                        "popd; " +
                        "$HDFS_DIR/bin/hdfs dfs -copyFromLocal $FILE/output.txt " + workdir_path + "$FILE; " +
                        "$HDFS_DIR/bin/hdfs dfs -copyFromLocal $FILE/$FILE.out " + workdir_path + "$FILE/$FILE.out; " +
                        "cat $FILE/$FILE.out; " +
                            "rm -rf $FILE; " +
                            "done < /dev/stdin")
            }


        val out = files.pipe (gridlabd)
        out.map (check).fold (true)(_ && _)
    }

    def read_output_files (one_phase: Boolean, reduced_trafos: RDD[(String, (Double, Iterable[(String, Double)]))]): RDD[(String, ThreePhaseComplexDataElement)] =
    {
        val date_format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z")
        def toTimeStamp (string: String): Long =
        {
            date_format.parse (string).getTime ()
        }

        val path = workdir_slash + "*/output.txt"
        val executors = session.sparkContext.getExecutorMemoryStatus.keys.size - 1
        val files = session.sparkContext.wholeTextFiles(path, executors)

        // extract TRAxxx from the path name
        def extract_trafo (k: (String, String)): (String, String) =
        {
            val path = k._1
            val trafo_pattern = ".*/(.*)/output.txt"
            val trafo = path.replaceAll (trafo_pattern, "$1")
            (trafo, k._2)
        }
        def read (f: String): TraversableOnce[ThreePhaseComplexDataElement] =
        {
            var units = ""
            var element = ""
            val content = f.split ("\n").filter (s ⇒ s.startsWith ("# file") || s.charAt (0).isDigit)
            def makeResult (c: String): ThreePhaseComplexDataElement =
            {
                if (c.startsWith ("# file"))
                {
                    val filename_pattern = "# file...... output_data/(.*)" //# file...... output_data/HAS138117_topo_voltage.csv
                    val filename = c.replaceAll (filename_pattern, "$1")
                    element = filename.substring (0, filename.indexOf("_"))
                    if (filename.endsWith ("_voltage.csv"))
                        units = "Volts"
                    else if (filename.endsWith ("_current.csv"))
                        units = "Amps"
                    null.asInstanceOf[ThreePhaseComplexDataElement]
                }
                else
                {
                    val c_arr = c.split(",")
                    if (one_phase)
                        ThreePhaseComplexDataElement(element, toTimeStamp(c_arr(0)), Complex(c_arr(1).toDouble, c_arr(2).toDouble), Complex(0.0, 0.0), Complex(0.0, 0.0), units)
                    else
                        ThreePhaseComplexDataElement(element, toTimeStamp(c_arr(0)), Complex(c_arr(1).toDouble, c_arr(2).toDouble), Complex(c_arr(3).toDouble, c_arr(4).toDouble), Complex(c_arr(5).toDouble, c_arr(6).toDouble), units)
                }
            }
            content.map (makeResult).filter (_ != null)
        }
        files.map (extract_trafo).flatMapValues (read)
    }

    def writeInputFile (equipment: String, path: String, bytes: Array[Byte]) =
    {
        if ((workdir_scheme == "file") || (workdir_scheme == ""))
        {
            // ToDo: check for IOException
            val file = Paths.get (workdir_path + equipment + "/" + path)
            val parent = Files.createDirectories (file.getParent())
            if (null != bytes)
                Files.write (file, bytes)
        }
        else
        {
            val hdfs_configuration = new Configuration ()
            hdfs_configuration.set ("fs.hdfs.impl", "org.apache.hadoop.hdfs.DistributedFileSystem")
            hdfs_configuration.set ("fs.file.impl", "org.apache.hadoop.fs.LocalFileSystem")
            val hdfs = FileSystem.get (URI.create (workdir_uri), hdfs_configuration)

            val file = new Path (workdir_slash + equipment + "/" + path)
            // wrong: hdfs.mkdirs (file.getParent (), new FsPermission ("ugoa+rwx")) only permissions && umask
            // fail: FileSystem.mkdirs (hdfs, file.getParent (), new FsPermission ("ugoa+rwx")) if directory exists
            hdfs.mkdirs (file.getParent(), new FsPermission("ugoa-rwx"))
            hdfs.setPermission (file.getParent(), new FsPermission("ugoa-rwx")) // "-"  WTF?

            if (null != bytes)
            {
                val out = hdfs.create(file)
                out.write(bytes)
                out.close()
            }
        }
    }

    def eraseInputFile (equipment: String)
    {
        if ((workdir_scheme == "file") || (workdir_scheme == ""))
            FileUtils.deleteQuietly (new File (workdir_path + equipment))
        else
        {
            val hdfs_configuration = new Configuration()
            hdfs_configuration.set ("fs.hdfs.impl", "org.apache.hadoop.hdfs.DistributedFileSystem")
            hdfs_configuration.set ("fs.file.impl", "org.apache.hadoop.fs.LocalFileSystem")
            val hdfs = FileSystem.get (URI.create (workdir_uri), hdfs_configuration)

            val directory = new Path (workdir_slash + equipment)
            hdfs.delete (directory, true)
        }
    }

    def cleanup (equipment: String, includes_glm: Boolean, includes_input: Boolean): Unit =
    {
        if (includes_glm)
            eraseInputFile (equipment)
        else
        {
            if (includes_input)
                eraseInputFile (equipment + "/input_data/")
            eraseInputFile (equipment + "/output_data/")
            eraseInputFile (equipment + "/output.txt")
            eraseInputFile (equipment + "/" + equipment + ".out")
            writeInputFile (equipment, "/output_data/dummy", null) // mkdir
        }
    }
}

