package ch.ninecode.sm

import java.io.StringWriter
import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.util
import java.util.HashMap
import javax.json.Json
import javax.json.JsonWriterFactory
import javax.json.stream.JsonGenerator

import scala.collection.Map
import scala.tools.nsc.io.Jar
import scala.util.Random

import org.apache.spark.SparkConf
import org.apache.spark.graphx.Edge
import org.apache.spark.graphx.Graph
import org.apache.spark.graphx.VertexId
import org.apache.spark.rdd.RDD
import org.apache.spark.rdd.RDD.rddToPairRDDFunctions
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.cim.CIMClasses
import ch.ninecode.cim.CIMRDD
import ch.ninecode.cim.DefaultSource
import ch.ninecode.model.ACLineSegment
import ch.ninecode.model.BaseVoltage
import ch.ninecode.model.ConductingEquipment
import ch.ninecode.model.Conductor
import ch.ninecode.model.ConnectivityNode
import ch.ninecode.model.Element
import ch.ninecode.model.Name
import ch.ninecode.model.PowerTransformerEnd
import ch.ninecode.model.Terminal
import ch.ninecode.model.TopologicalNode
import ch.ninecode.model.UserAttribute
import ch.ninecode.model.WireInfo

class SmartMeter (session: SparkSession, storage_level: StorageLevel = StorageLevel.fromString ("MEMORY_AND_DISK_SER"), topological_nodes: Boolean) extends CIMRDD with Serializable
{
    implicit val spark: SparkSession = session
    implicit val log: Logger = LoggerFactory.getLogger (getClass)

    /**
     * Get the edge length
     *
     * @param element ACLineSegment object
     * @return
     */
    def span (element: Element): Double =
    {
        val clazz = element.getClass.getName
        val cls = clazz.substring (clazz.lastIndexOf (".") + 1)
        cls match
        {
            case "ACLineSegment" =>
                element.asInstanceOf[ACLineSegment].Conductor.len
            case "Conductor" =>
                element.asInstanceOf[Conductor].len
            case _ =>
                0.0
        }
    }

    // copied from an old version of GridLAB-D
    /**
     * The name of the node associated with a terminal.
     *
     * @param t The terminal object to get the node for.
     * @return The name of the TopologicalNode or ConnectivityNode.
     */
    def node_name (t: Terminal): String =
    {
        if (topological_nodes) t.TopologicalNode else t.ConnectivityNode
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
                                terminals (0).ACDCTerminal.id,
                                node_name (terminals (0)),
                                volts (0),
                                "",
                                "",
                                volts (0),
                                terminals (0).ConductingEquipment,
                                ratedCurrent,
                                equipment,
                                e,
                                span (e))
                    case _ =>
                        for (i <- 1 until terminals.length) // for comprehension: iterate omitting the upper bound
                        {
                            ret = ret :+ PreEdge (
                                terminals (0).ACDCTerminal.id,
                                node_name (terminals (0)),
                                volts (0),
                                terminals (i).ACDCTerminal.id,
                                node_name (terminals (i)),
                                volts (i),
                                terminals (0).ConductingEquipment,
                                ratedCurrent,
                                equipment,
                                e,
                                span (e))
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
        PreNode (node.id, if (term.ACDCTerminal.sequenceNumber == 1) edge.v1 else edge.v2)
    }

    def connectivity_node_operator (arg: ((ConnectivityNode, Terminal), PreEdge)): PreNode =
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
        else
            if (s.endsWith ("_fuse_topo"))
                s.substring (0, s.length - "_fuse_topo".length)
            else
                if (s.endsWith ("_topo"))
                    s.substring (0, s.length - "_topo".length)
                else
                    s
    }

    def make_graph_vertices (v: PreNode): (VertexId, PreNode) =
    {
        (v.vertex_id (v.id_seq), v)
    }

    def make_graph_edges (e: PreEdge): Edge[PreEdge] =
    {
        Edge (e.vertex_id (e.id_cn_1), e.vertex_id (e.id_cn_2), e)
    }

    /**
     * Get pairs of cable id and maximum current.
     */
    def getCableMaxCurrent: RDD[(String, Double)] =
    {
        val wireinfos = get[WireInfo]
        val lines = get[ACLineSegment]
        val keyed = lines.keyBy (_.Conductor.ConductingEquipment.Equipment.PowerSystemResource.AssetDatasheet)
        val cables = keyed.join (wireinfos.keyBy (_.id)).values.map (x => (x._1.id, x._2.ratedCurrent))

        cables.persist (storage_level)
        if (session.sparkContext.getCheckpointDir.isDefined) cables.checkpoint ()

        cables
    }

    // Note: we return a bogus value just so there is a time sequential dependence on this by later code
    def prepare: Graph[PreNode, PreEdge] =
    {
        // get a map of voltages
        val voltages = get ("BaseVoltage").asInstanceOf[RDD[BaseVoltage]].map ((v) => (v.id, v.nominalVoltage)).collectAsMap ()

        // get the terminals
        val terminals = get ("Terminal").asInstanceOf[RDD[Terminal]].filter (null != _.ConnectivityNode)

        // get the terminals keyed by equipment
        val terms = terminals.groupBy (_.ConductingEquipment)

        // get all elements
        val elements = get[Element]("Elements")

        // join with WireInfo to get ratedCurrent (only for ACLineSegments)
        val cableMaxCurrent = getCableMaxCurrent
        val joined_elements = elements.keyBy (_.id).leftOuterJoin (cableMaxCurrent).map (e =>
        {
            val ele = e._2._1
            val wire = e._2._2
            val wireinfo = wire match
            {
                case Some (maxCurrent) => maxCurrent
                case None => Double.PositiveInfinity
            }
            (ele.id, (ele, wireinfo))
        })

        // get the transformer ends keyed by transformer
        val ends = get ("PowerTransformerEnd").asInstanceOf[RDD[PowerTransformerEnd]].groupBy (_.PowerTransformer)

        // handle transformers specially, by attaching all PowerTransformerEnd objects to the elements
        val elementsplus = joined_elements.leftOuterJoin (ends)

        // map the terminal 'pairs' to edges
        val edges = elementsplus.join (terms).flatMapValues (edge_operator (voltages)).values

        // eliminate edges with only one connectivity node, or the same connectivity node
        val real_edges = edges.filter (x => null != x.id_cn_1 && null != x.id_cn_2 && "" != x.id_cn_1 && "" != x.id_cn_2 && x.id_cn_1 != x.id_cn_2)

        // get terminal to voltage mapping by referencing the equipment voltage for each of two terminals
        val tv = edges.keyBy (_.id_seq_1).union (edges.keyBy (_.id_seq_2)).distinct

        // get the nodes RDD
        val nodes = if (topological_nodes)
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
        val xedges = real_edges.map (make_graph_edges)
        val xnodes = nodes.map (make_graph_vertices)
        xedges.name = "xedges"
        xedges.persist (storage_level)
        xnodes.name = "xnodes"
        xnodes.persist (storage_level)
        if (session.sparkContext.getCheckpointDir.isDefined)
        {
            xedges.checkpoint (); xnodes.checkpoint ()
        }

        // construct the initial graph from the real edges and nodes
        Graph.apply[PreNode, PreEdge](xnodes, xedges, PreNode ("", 0.0), storage_level, storage_level)
    }


    lazy val FACTORY_INSTANCE: JsonWriterFactory =
    {
        val properties: util.Map[String, AnyRef] = new util.HashMap[String, AnyRef](1)
        properties.put (JsonGenerator.PRETTY_PRINTING, "true")
        Json.createWriterFactory (properties)
    }

    def node_JSON (node: FinalNodeData): String =
    {
        val string = new StringWriter
        val writer = FACTORY_INSTANCE.createWriter (string)
        val data = Json.createObjectBuilder
        data.add ("name", node.name)
        val ao = Json.createArrayBuilder ()
        node.ao_id.foreach (id => ao.add (id))
        data.add ("ao_id", ao)
        data.add ("voltage", node.voltage)
        data.add ("neighbor", node.neighbor)
        data.add ("parent", node.parent)
        data.add ("total_distance", node.total_distance)
        data.add ("nearest_distance", node.nearest_distance)
        writer.write (data.build)
        writer.close ()
        string.toString
    }

    def make_JSON (nodes: RDD[FinalNodeData]): String =
    {
        val nodestring = nodes.map (node_JSON).fold ("")((x: String, y: String) => x + (if ("" == x) "" else ", ") + y)
        "[ " + nodestring + " ]"
    }

    def filterEmptyLeaves (vertex: RDD[NodeData]): RDD[NodeData] =
    {
        val parents = vertex.map (_.parent).collect
        vertex.filter (v =>
        {
            parents.contains (v.id_seq) || v.id_seq.startsWith ("HAS")
        })
    }

    def run (starting_node: String): String =
    {
        val pn = PreNode ("", 0.0) // just to access the vertex_id function

        val initial = prepare

        // get the ConnectivityNode corresponding to the given starting node
        val terminal = get[Terminal].filter (terminal => terminal.ConductingEquipment == starting_node).first
        val start_at = Array[VertexId](pn.vertex_id (if (topological_nodes) terminal.TopologicalNode else terminal.ConnectivityNode))

        val trace = new Trace (initial)
        val tracedGraph = trace.run (start_at)

        val tracedVertices = tracedGraph.vertices.filter (_._2.total_distance < Double.PositiveInfinity)

        var tracedRenamedVertices = tracedVertices.map (vertex =>
        {
            val node = vertex._2
            val name = node.id_seq
            val index1 = name.indexOf ("_")
            val id = if (0 < index1) name.substring (0, index1) else name
            val neighbor = node.neighbor
            val index2 = neighbor.indexOf ("_")
            val nid = if (0 < index2) neighbor.substring (0, index2) else neighbor
            val parent = node.parent
            val index3 = parent.indexOf ("_")
            val parentid = if (0 < index3) parent.substring (0, index3) else parent
            node.copy (id_seq = id, neighbor = nid, parent = parentid)
        })

        var withoutEmptyLeafes = filterEmptyLeaves (tracedRenamedVertices)
        while (withoutEmptyLeafes.count < tracedRenamedVertices.count)
        {
            tracedRenamedVertices = withoutEmptyLeafes
            withoutEmptyLeafes = filterEmptyLeaves (tracedRenamedVertices)
        }

        val name = get[Name]
        val userAttr = get[UserAttribute]
        // legacy
        //      val joinedMst = name.keyBy(_.IdentifiedObject).join(userAttr.keyBy(_.name))
        val joinedMst = name.keyBy (_.IdentifiedObject).join (userAttr.keyBy (_.name))
        val preparedAoId = joinedMst.map (mst => (mst._2._2.value, mst._2._1.name)).groupByKey

        val sapNr = withoutEmptyLeafes.keyBy (_.id_seq)
            .leftOuterJoin (preparedAoId)
            .map (joinedNode =>
            {
                val node = joinedNode._2._1
                val ao = joinedNode._2._2
                val ao_ids = ao match
                {
                    case Some (i) => i.toArray
                    case _ => Array ("")
                }
                FinalNodeData (node.id_seq, ao_ids, node.voltage, node.neighbor, node.parent, node.total_distance, node.nearest_distance)
            })

        make_JSON (sapNr)
    }
}

object SmartMeter
{
    def jarForObject (obj: Object): String =
    {
        // see https://stackoverflow.com/questions/320542/how-to-get-the-path-of-a-running-jar-file
        var ret = obj.getClass.getProtectionDomain.getCodeSource.getLocation.getPath
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

        ret
    }

    def main (args: Array[String])
    {
        val filename = if (args.length > 0)
            args (0)
        else
            "hdfs://sandbox:8020/data/" + "NIS_CIM_Export_sias_current_20160816_Wildenrueti_V9" + ".rdf"

        val starting_node = if (args.length > 1)
            args (1)
        else
            "ABG91246"

        val use_topological_node = true

        val start = System.nanoTime ()

        // create the configuration
        val configuration = new SparkConf (false)
        configuration.setAppName ("SmartMeter")
        configuration.setMaster ("spark://sandbox:7077")
        configuration.setSparkHome ("/home/derrick/spark-1.6.0-bin-hadoop2.6/")
        configuration.set ("spark.driver.memory", "2g")
        configuration.set ("spark.executor.memory", "4g")
        configuration.set ("spark.executor.extraJavaOptions", "-XX:+UseCompressedOops -XX:+PrintGCDetails -XX:+PrintGCTimeStamps")
        // get the necessary jar files to send to the cluster
        val s1 = jarForObject (new DefaultSource ())
        val s2 = jarForObject (this)
        configuration.setJars (Array (s1, s2))

        // register CIMReader classes
        configuration.registerKryoClasses (CIMClasses.list)

        // make a Spark session
        val session = SparkSession.builder ().config (configuration).getOrCreate () // create the fixture
        session.sparkContext.setLogLevel ("OFF") // Valid log levels include: ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN

        val setup = System.nanoTime ()

        val files = filename.split (",")
        val options = new HashMap[String, String]().asInstanceOf[java.util.Map[String, String]]
        options.put ("path", filename)
        options.put ("StorageLevel", "MEMORY_AND_DISK_SER")
        options.put ("ch.ninecode.cim.do_topo_islands", "true")
        val elements = session.sqlContext.read.format ("ch.ninecode.cim").options (options).load (files: _*)
        val count = elements.count

        val read = System.nanoTime ()

        val smart = new SmartMeter (session, StorageLevel.MEMORY_AND_DISK_SER, use_topological_node)

        val result = smart.run (starting_node)

        val graph = System.nanoTime ()

        println (result)

        println ("" + count + " elements")
        println ("setup : " + (setup - start) / 1e9 + " seconds")
        println ("read : " + (read - setup) / 1e9 + " seconds")
        println ("graph: " + (graph - read) / 1e9 + " seconds")
        println ()
    }
}

