package ch.ninecode.gl

import java.io.File
import java.io.PrintWriter
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission
import java.text.ParseException
import java.text.SimpleDateFormat

import scala.collection.JavaConverters.setAsJavaSetConverter
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
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.cim.CIMRDD
import ch.ninecode.model.ACLineSegment
import ch.ninecode.model.BaseVoltage
import ch.ninecode.model.Breaker
import ch.ninecode.model.ConductingEquipment
import ch.ninecode.model.Conductor
import ch.ninecode.model.Cut
import ch.ninecode.model.Disconnector
import ch.ninecode.model.Element
import ch.ninecode.model.Fuse
import ch.ninecode.model.GroundDisconnector
import ch.ninecode.model.Jumper
import ch.ninecode.model.LoadBreakSwitch
import ch.ninecode.model.MktSwitch
import ch.ninecode.model.PowerTransformer
import ch.ninecode.model.PowerTransformerEnd
import ch.ninecode.model.ProtectedSwitch
import ch.ninecode.model.Recloser
import ch.ninecode.model.Sectionaliser
import ch.ninecode.model.Switch
import ch.ninecode.model.Terminal
import ch.ninecode.model.TopologicalNode
import ch.ninecode.model.WireInfo
import ch.ninecode.net.TransformerSet
import ch.ninecode.util.Complex
import ch.ninecode.util.ThreePhaseComplexDataElement

case class GridlabFailure (trafoID: String, errorMessages: List[String])

/**
 * Interface to GridLAB-D load-flow analysis software.
 *
 * Given a network of CIM elements where a topology exists (TopologyNode elements are references by Terminal elements),
 * this class provides functionality to turn the network into a GridLAB-D .glm file, solve it and return the results.
 * That is, it assumes that the CIMReader has been instructed to run the NetworkTopologicalProcessor to update
 * the terminals, or the terminals already have a valid TopologicalNode when they are read in.
 *
 * @param session               The Spark session.
 * @param storage_level         Specifies the <a href="https://spark.apache.org/docs/latest/programming-guide.html#which-storage-level-to-choose">Storage Level
 *                              </a> used to persist and serialize the objects.
 * @param workdir               The working directory for .glm and input_file generation. If the scheme is file:// or none,
 *                              the gridlabd processes will assume that this is local and will try to execute in that directory.
 *                              If the scheme is hdfs:// or wasb:// (or something else) the processing will copy the files locally
 *                              (either to \$SPARK_HOME/work/app-<date-time>-<app#> when running standalone,
 *                              or to \$HADOOP_HOME/logs/userlogs/application_<timestamp>_<appId> when running under Yarn)
 *                              to execute and then copy the output files back to this location (workdir) when complete.
 * @param cable_impedance_limit cables with a R1 value higher than this are not calculated with GridLAB-D, the reason is bad performance in GridLAB-D with too high
 *                              impedance values
 */
class GridLABD
(
    session: SparkSession,
    storage_level: StorageLevel = StorageLevel.fromString ("MEMORY_AND_DISK_SER"),
    workdir: String = "hdfs://" + java.net.InetAddress.getLocalHost.getHostName + "/simulation/",
    cable_impedance_limit: Double = 5.0) extends CIMRDD with Serializable
{
    implicit val spark: SparkSession = session
    implicit val log: Logger = LoggerFactory.getLogger (getClass)

    /**
     * Get the working directory ensuring a slash terminator.
     */
    val workdir_slash: String = if (workdir.endsWith ("/")) workdir else s"$workdir/"

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
     * The name of the node associated with a terminal.
     *
     * @param t The terminal object to get the node for.
     * @return The name of the TopologicalNode.
     */
    def node_name (t: Terminal): String = t.TopologicalNode

    /**
     * Return <code>true</code> if there is connectivity through the edge (if the Pregel algorithm should continue tracing) or not.
     */
    def connected (element: Element, v1: Double, v2: Double): Boolean =
    {
        element match
        {
            case s: Switch             => !s.normalOpen
            case c: Cut                => !c.Switch.normalOpen
            case d: Disconnector       => !d.Switch.normalOpen
            case f: Fuse               => !f.Switch.normalOpen
            case g: GroundDisconnector => !g.Switch.normalOpen
            case j: Jumper             => !j.Switch.normalOpen
            case m: MktSwitch          => !m.Switch.normalOpen
            case p: ProtectedSwitch    => !p.Switch.normalOpen
            case b: Breaker            => !b.ProtectedSwitch.Switch.normalOpen
            case l: LoadBreakSwitch    => !l.ProtectedSwitch.Switch.normalOpen
            case r: Recloser           => !r.ProtectedSwitch.Switch.normalOpen
            case s: Sectionaliser      => !s.Switch.normalOpen
            case _: Conductor          => true
            case _: ACLineSegment      => true
            case _: PowerTransformer   => v1 <= 1000.0 && (v2 <= 1000.0 && v2 > 230.0) // ToDo: don't hard code these voltage values
            case _ =>
                log.error (s"trace setup encountered edge ${element.id} with unhandled class '${element.getClass.getName}', assumed conducting")
                true
        }
    }

    /**
     * Warn of special cases of transformers.
     *
     * @param element       Element to test
     * @param num_terminals total number of terminals on the ConductingEquipment
     * @param v1            primary voltage
     * @param v2            secondary voltage
     * @return an error string with additional information about validity, or <code>null</code> if none
     */
    def hasIssues (element: Element, num_terminals: Int, v1: Double, v2: Double): String =
    {
        element match
        {
            case cable: ACLineSegment =>
                if (cable.r >= cable_impedance_limit) // ToDo: use PSRType_Bogus
                    "invalid element (%s r=%s > limit=%s)".format (cable.id, cable.r, cable_impedance_limit)
                else
                    null
            case _: PowerTransformer =>
                // Three Winding Transformer - if there are more than 2 PowerTransformerEnd associated to the PowerTransformer
                if (num_terminals > 2)
                    "%s transformer windings for edge %s".format (num_terminals, element.id)
                // Voltage Regulator Transformer: if there are less than 3 PowerTransformerEnd associated to the PowerTransformer and the voltage of the two ends are both <= 400V
                else
                    if (v1 == v2)
                        "voltage (%sV) regulator edge %s".format (v1, element.id)
                    else
                        null
            case _ =>
                null
        }
    }

    def edge_operator (voltages: Map[String, Double])(arg: (((Element, Double), Option[Iterable[PowerTransformerEnd]]), Iterable[Terminal])): List[PreEdge] =
    {
        var ret = List[PreEdge]()

        val e = arg._1._1._1
        val ratedCurrent = arg._1._1._2
        val pte_op = arg._1._2
        val t_it = arg._2
        // get the ConductingEquipment
        var c = e
        while ((null != c) && !c.getClass.getName.endsWith (".ConductingEquipment"))
            c = c.sup
        if (null != c)
        {
            // get the equipment
            val equipment = c.asInstanceOf[ConductingEquipment]
            // sort terminals by sequence number (and hence the primary is index 0)
            val terminals = t_it.toArray.sortWith (_.ACDCTerminal.sequenceNumber < _.ACDCTerminal.sequenceNumber)
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
                        Array[Double](volt, volt)
                }
            // Note: we eliminate 230V edges because transformer information doesn't exist and
            // see also NE-51 NIS.CIM: Export / Missing 230V connectivity
            if (!volts.contains (230.0))
                // make a pre-edge for each pair of terminals
                ret = terminals.length match
                {
                    case 1 =>
                        ret :+
                            PreEdge (
                                terminals (0).id,
                                node_name (terminals (0)),
                                volts (0),
                                "",
                                "",
                                volts (0),
                                terminals (0).ConductingEquipment,
                                connected = true,
                                null,
                                ratedCurrent,
                                e)
                    case _ =>
                        for (i <- 1 until terminals.length) // for comprehension: iterate omitting the upper bound
                        {
                            ret = ret :+ PreEdge (
                                terminals (0).id,
                                node_name (terminals (0)),
                                volts (0),
                                terminals (i).id,
                                node_name (terminals (i)),
                                volts (i),
                                terminals (0).ConductingEquipment,
                                connected (e, volts (0), volts (i)),
                                hasIssues (e, terminals.length, volts (0), volts (i)),
                                ratedCurrent,
                                e)
                        }
                        ret
                }
        }
        //else // shouldn't happen, terminals always reference ConductingEquipment, right?

        ret
    }

    def topological_node_operator (arg: ((TopologicalNode, Terminal), PreEdge)): PreNode =
    {
        val node = arg._1._1
        val term = arg._1._2
        val edge = arg._2
        PreNode (node.id, if (term.ACDCTerminal.sequenceNumber == 1) edge.v1 else edge.v2, edge.problem)
    }

    def exists (filename: String): Boolean = new File (filename).exists

    def make_graph_vertices (v: PreNode): (VertexId, PreNode) =
    {
        (v.vertex_id (v.id), v)
    }

    def make_graph_edges (e: PreEdge): Edge[PreEdge] =
    {
        Edge (e.vertex_id (e.cn1), e.vertex_id (e.cn2), e)
    }

    /**
     * Get pairs of cable id and maximum current.
     */
    def getCableMaxCurrent: RDD[(String, Double)] =
    {
        val keyed = getOrElse[ACLineSegment].keyBy (_.Conductor.ConductingEquipment.Equipment.PowerSystemResource.AssetDatasheet)
        val cables = keyed.join (getOrElse[WireInfo].keyBy (_.id)).values.map (x => (x._1.id, x._2.ratedCurrent))

        cables.persist (storage_level)
        if (session.sparkContext.getCheckpointDir.isDefined) cables.checkpoint ()

        cables
    }

    def prepare (): (RDD[Edge[PreEdge]], RDD[(VertexId, PreNode)]) =
    {
        // get a map of voltages
        val voltages = getOrElse[BaseVoltage].map (v => (v.id, v.nominalVoltage)).collectAsMap ()

        // get the terminals
        val terminals = getOrElse[Terminal].filter (null != _.TopologicalNode)

        // get the terminals keyed by equipment
        val terms = terminals.groupBy (_.ConductingEquipment)

        // get all elements
        val elements = get[Element]("Elements")

        // join with WireInfo to get ratedCurrent (only for ACLineSegments)
        val cableMaxCurrent = getCableMaxCurrent
        val joined_elements: RDD[(String, (Element, Double))] = elements.keyBy (_.id).leftOuterJoin (cableMaxCurrent).values.map (
            arg =>
            {
                val (element, wire) = arg
                val wireinfo = wire match
                {
                    case Some (maxCurrent) => maxCurrent
                    case None => Double.PositiveInfinity
                }
                (element.id, (element, wireinfo))
            }
        )

        // get the transformer ends keyed by transformer
        val ends = getOrElse[PowerTransformerEnd].groupBy (_.PowerTransformer)

        // handle transformers specially, by attaching all PowerTransformerEnd objects to the elements
        val elementsplus = joined_elements.leftOuterJoin (ends)

        // map the terminal 'pairs' to edges
        val edges = elementsplus.join (terms).flatMapValues (edge_operator (voltages)).values

        // eliminate edges with only one topological node, or the same topological node
        val real_edges = edges.filter (x => null != x.cn1 && null != x.cn2 && "" != x.cn1 && "" != x.cn2 && x.cn1 != x.cn2)

        // get terminal to voltage mapping by referencing the equipment voltage for each of two terminals
        val tv = edges.keyBy (_.id_seq_1).union (edges.keyBy (_.id_seq_2)).distinct

        // get the nodes RDD (map the topological nodes to PreNode with voltages)
        val nodes = getOrElse[TopologicalNode]
            .keyBy (_.id)
            .join (terminals.keyBy (_.TopologicalNode))
            .values
            .keyBy (_._2.id).join (tv)
            .values
            .map (topological_node_operator)
            .distinct

        def worst (x: (VertexId, Iterable[PreNode])): (VertexId, PreNode) =
        {
            (x._1,
                {
                    val m = x._2.filter (null != _.problem)
                    if (m.nonEmpty)
                        m.head
                    else
                        x._2.head
                }
            )
        }

        // persist edges and nodes to avoid recompute
        val xedges = real_edges.map (make_graph_edges)
        val xnodes = nodes.map (make_graph_vertices).groupByKey.map (worst)
        xedges.name = "xedges"
        xedges.persist (storage_level)
        xnodes.name = "xnodes"
        xnodes.persist (storage_level)
        if (session.sparkContext.getCheckpointDir.isDefined)
        {
            xedges.checkpoint ()
            xnodes.checkpoint ()
        }

        (xedges, xnodes)
    }

    def trafokreis_key (transformers: TransformerSet): String =
    {
        transformers.transformer_name
    }

    def export (generator: GLMGenerator): Unit =
    {
        eraseInputFile (generator.directory)
        writeInputFile (generator.directory, generator.name + ".glm", generator.make_glm ().getBytes (StandardCharsets.UTF_8))
        writeInputFile (generator.directory, "input_data/dummy", null) // mkdir
        writeInputFile (generator.directory, "output_data/dummy", null) // mkdir
    }

    def check (input: String): Option[GridlabFailure] =
    {
        val criticalErrors = List ("FATAL", "ERROR", "FAIL", "command not found", "Cannot fork", "pthread_create")
        val allLines = input.split ('|').toList
        val trafoID = allLines.headOption.orNull (null)
        val criticalLines = allLines.filter (line => criticalErrors.exists (line.contains (_)))
        if (criticalLines.nonEmpty)
        {
            log.error (s"gridlabd failed for $trafoID, message is: ${criticalLines.mkString ("\n")}\n")
            Some (GridlabFailure (trafoID, criticalLines))
        }
        else
            None
    }

    def solve (files: RDD[String]): Array[GridlabFailure] =
    {
        // assumes gridlabd is installed on every node:
        // download gridlabd (e.g. latest stable release https://sourceforge.net/projects/gridlab-d/files/gridlab-d/Last%20stable%20release/gridlabd-4.1.0-1.el7.x86_64.rpm/download)
        // convert the rpm to a deb using alien:
        //   sudo alien gridlabd_4.1.0-1_amd64.rpm
        // install on every node:
        //   sudo dpkg -i gridlabd_4.1.0-1_amd64.deb

        val gridlabd =
            if ((workdir_scheme == "file") || (workdir_scheme == "")) // local[*]
            {
                val os = System.getProperty ("os.name")
                if (os.startsWith ("Windows"))
                {
                    log.info("Running GridLABD on Windows")
                    val pipeFileName = "./src/test/resources/pipe.sh"
                    val pipeContent = s"""#!/bin/bash
                                     |while read line; do
                                     |    export FILE=$${line/$$'\r'/};
                                     |    ulimit -Sn `ulimit -Hn`;
                                     |    pushd $$1/$$FILE > /dev/null;
                                     |    gridlabd.exe $$FILE.glm 2> $$FILE.out;
                                     |    cat output_data/* > output.txt;
                                     |    echo -n $$FILE'|';
                                     |    cat $$FILE.out | tr '\r\n' '|';
                                     |    popd > /dev/null;
                                     |done""".stripMargin
                    new PrintWriter(pipeFileName) {
                        write (pipeContent)
                        close ()
                    }
                    Array[String](
                        "bash",
                        pipeFileName,
                        workdir_path
                    )
                }
                else
                {
                    log.info("Running GridLABD on a non-cluster Linux")
                    Array[String](
                        "bash",
                        "-c",
                        "while read line; do " +
                            "export FILE=$line; " +
                            "ulimit -Sn `ulimit -Hn`; " +
                            "pushd " + workdir_path + "$FILE; " +
                            "gridlabd --quiet $FILE.glm 2> $FILE.out;" +
                            "cat output_data/* > output.txt; " +
                            "echo -n $FILE'|';" +
                            "cat $FILE.out | tr '\\r\\n' '|';" +
                            "popd; " +
                            "done < /dev/stdin")
                }
            }
            else // cluster, either hdfs://XX or wasb://YY
            {
                log.info("Running GridLABD on a Linux cluster")
                Array[String](
                    "bash",
                    "-c",
                    "while read line; do " +
                        "export FILE=$line; " +
                        s"HDFS_DIR=$${HADOOP_HDFS_HOME:-$$HADOOP_HOME}; " +
                        "HADOOP_USER_NAME=$SPARK_USER; " +
                        "ulimit -Sn `ulimit -Hn`; " +
                        "$HDFS_DIR/bin/hdfs dfs -copyToLocal " + workdir_path + "$FILE $FILE; " +
                        "pushd $FILE; " +
                        "gridlabd --quiet $FILE.glm 2> $FILE.out;" +
                        "cat output_data/* > output.txt; " +
                        "popd; " +
                        "$HDFS_DIR/bin/hdfs dfs -copyFromLocal -f $FILE/output.txt " + workdir_path + "$FILE; " +
                        "$HDFS_DIR/bin/hdfs dfs -copyFromLocal -f $FILE/$FILE.out " + workdir_path + "$FILE/$FILE.out; " +
                        "echo -n $FILE'|';" +
                        "cat $FILE/$FILE.out | tr '\\r\\n' '|';" +
                        "rm -rf $FILE; " +
                        "done < /dev/stdin")
            }

        val out = files.pipe (gridlabd).filter(_.trim() != "") // we somehow get some empty strings back, trim them
        out.flatMap (check).collect
    }

    def default_filenameparser (filename: String): (String, String) =
    {
        val element = filename.substring (0, filename.indexOf ("_"))
        val units = if (filename.endsWith ("_voltage.csv"))
            "Volts"
        else
            if (filename.endsWith ("_current.csv"))
                "Amps"
            else
                ""
        (element, units)
    }

    def read_output_files
    (
        one_phase: Boolean,
        filenameparser: String => (String, String) = default_filenameparser): RDD[(String, ThreePhaseComplexDataElement)] =
    {
        val date_format = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss z")

        def toTimeStamp (string: String): Long =
        {
            try
            {
                date_format.parse (string).getTime
            }
            catch
            {
                // so this is a thing
                //    2018-07-19 10:15:20 UTC,+400,-2.72146e-06
                //    2018-07-19 10:15:25 UTC,+395.948,-3.67446
                //    20107-19 10:15:30 UTC,+395.911,-3.70541
                //    2018-07-19 10:15:35 UTC,+398.857,-1.06614
                //    2018-07-19 10:15:40 UTC,+396.66,-3.06917
                // sometimes GridLAB-D emits a bogus date
                case pe: ParseException =>
                    log.warn (pe.getMessage)
                    0L
            }
        }

        val path = s"${workdir_slash}*/output.txt"
        val executors = session.sparkContext.getExecutorMemoryStatus.keys.size - 1
        val files = session.sparkContext.wholeTextFiles (path, executors)

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
            val content = f.split ("\n").filter (s => s.startsWith ("# file") || ((s.length > 0) && s.charAt (0).isDigit))

            def makeResult (c: String): ThreePhaseComplexDataElement =
            {
                if (c.startsWith ("# file"))
                {
                    val filename_pattern = "# file...... output_data/(.*)" //# file...... output_data/HAS138117_topo_voltage.csv
                    val filename = c.replaceAll (filename_pattern, "$1").replaceAll ("\\r", "")
                    val (e, u) = filenameparser (filename)
                    element = e
                    units = u
                    null
                }
                else
                {
                    val c_arr = c.split (",")
                    if (one_phase)
                        if (c_arr.length > 3)
                        {
                            val fd = FlowDirection (c_arr (3))
                            ThreePhaseComplexDataElement (element, toTimeStamp (c_arr (0)), fd.a * Complex (c_arr (1).toDouble, c_arr (2).toDouble), Complex (0.0), Complex (0.0), units)
                        }
                        else
                            if (c_arr.length == 3)
                                ThreePhaseComplexDataElement (element, toTimeStamp (c_arr (0)), Complex (c_arr (1).toDouble, c_arr (2).toDouble), Complex (0.0), Complex (0.0), units)
                            else
                            {
                                log.error ("""%s recorder text "%s" cannot be interpreted as one phase complex %s""".format (element, c, units))
                                null
                            }
                    else
                        if (c_arr.length > 7)
                        {
                            val fd = FlowDirection (c_arr (7))
                            ThreePhaseComplexDataElement (element, toTimeStamp (c_arr (0)), fd.a * Complex (c_arr (1).toDouble, c_arr (2).toDouble), fd.b * Complex (c_arr (3).toDouble, c_arr (4).toDouble), fd.c * Complex (c_arr (5).toDouble, c_arr (6).toDouble), units)
                        }
                        else
                            if (c_arr.length == 7)
                                ThreePhaseComplexDataElement (element, toTimeStamp (c_arr (0)), Complex (c_arr (1).toDouble, c_arr (2).toDouble), Complex (c_arr (3).toDouble, c_arr (4).toDouble), Complex (c_arr (5).toDouble, c_arr (6).toDouble), units)
                            else
                            {
                                log.error ("""%s recorder text "%s" cannot be interpreted as three phase complex %s""".format (element, c, units))
                                null
                            }
                }
            }

            content.map (makeResult).filter (_ != null)
        }

        files.map (extract_trafo).flatMapValues (read)
    }

    def parsePermissions (s: String): Set[PosixFilePermission] =
    {
        // ToDo: parse file permissions val pattern = Pattern.compile ("\\G\\s*([ugoa]*)([+=-]+)([rwx]*)([,\\s]*)\\s*")
       Set[PosixFilePermission] (
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE,
            PosixFilePermission.GROUP_READ,
            PosixFilePermission.GROUP_WRITE,
            PosixFilePermission.GROUP_EXECUTE,
            PosixFilePermission.OTHERS_READ,
            PosixFilePermission.OTHERS_WRITE,
            PosixFilePermission.OTHERS_EXECUTE
        )
    }

    def writeInputFile (directory: String, path: String, bytes: Array[Byte], permissions: String = null): Unit =
    {
        if ((workdir_scheme == "file") || (workdir_scheme == ""))
        {
            // ToDo: check for IOException
            val file = Paths.get (workdir_path + directory + "/" + path)
            Files.createDirectories (file.getParent)
            if (null != bytes)
            {
                Files.write (file, bytes)
                if (null != permissions)
                    Files.setPosixFilePermissions (file, parsePermissions (permissions).asJava)
            }
        }
        else
        {
            val hdfs_configuration = new Configuration ()
            hdfs_configuration.set ("fs.hdfs.impl", "org.apache.hadoop.hdfs.DistributedFileSystem")
            hdfs_configuration.set ("fs.file.impl", "org.apache.hadoop.fs.LocalFileSystem")
            val hdfs = FileSystem.get (URI.create (workdir_uri), hdfs_configuration)

            val file = new Path (workdir_slash + directory + "/" + path)
            // wrong: hdfs.mkdirs (file.getParent (), new FsPermission ("ugoa+rwx")) only permissions && umask
            // fail: FileSystem.mkdirs (hdfs, file.getParent (), new FsPermission ("ugoa+rwx")) if directory exists
            hdfs.mkdirs (file.getParent, new FsPermission ("ugo-rwx"))
            hdfs.setPermission (file.getParent, new FsPermission ("ugo-rwx")) // "-"  WTF?

            if (null != bytes)
            {
                val out = hdfs.create (file)
                out.write (bytes)
                out.close ()
                if (null != permissions)
                    hdfs.setPermission (file, new FsPermission (permissions))
            }
        }
    }

    def eraseInputFile (equipment: String)
    {
        if ((workdir_scheme == "file") || (workdir_scheme == ""))
            FileUtils.deleteQuietly (new File (workdir_path + equipment))
        else
        {
            val hdfs_configuration = new Configuration ()
            hdfs_configuration.set ("fs.hdfs.impl", "org.apache.hadoop.hdfs.DistributedFileSystem")
            hdfs_configuration.set ("fs.file.impl", "org.apache.hadoop.fs.LocalFileSystem")
            val hdfs = FileSystem.get (URI.create (workdir_uri), hdfs_configuration)

            val directory = new Path (workdir_slash + equipment)
            hdfs.delete (directory, true)
        }
    }

    def cleanup (equipment: String, includes_glm: Boolean, includes_input: Boolean, includes_output: Boolean)
    {
        if (includes_glm)
            eraseInputFile (equipment)
        else
        {
            if (includes_input)
                eraseInputFile (equipment + "/input_data/")
            if (includes_output)
            {
                eraseInputFile (equipment + "/output_data/")
                eraseInputFile (equipment + "/output.txt")
                eraseInputFile (equipment + "/" + equipment + ".out")
                writeInputFile (equipment, "/output_data/dummy", null) // mkdir
            }
        }
    }
}

object GridLABD
{
    /**
     * The list of classes that can be persisted.
     */
    lazy val classes: Array[Class[_]] =
    {
        Array (
            classOf[ch.ninecode.gl.FlowDirection],
            classOf[ch.ninecode.gl.GLMGenerator],
            classOf[ch.ninecode.gl.GLMNode],
            classOf[ch.ninecode.gl.GridLABD],
            classOf[ch.ninecode.gl.GLMLineEdge],
            classOf[ch.ninecode.gl.PreEdge],
            classOf[ch.ninecode.gl.PreNode],
            classOf[ch.ninecode.gl.PV],
            classOf[ch.ninecode.gl.Solar],
            classOf[ch.ninecode.gl.SwingNode],
            classOf[ch.ninecode.gl.GLMSwitchEdge],
            classOf[ch.ninecode.gl.GLMTransformerEdge]
        )
    }
}
