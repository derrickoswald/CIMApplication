package ch.ninecode.sm

import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.util.HashMap

import scala.collection.Map
import scala.tools.nsc.io.Jar
import scala.util.Random

import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import org.apache.spark.graphx.Edge
import org.apache.spark.graphx.Graph
import org.apache.spark.graphx.VertexId
import org.apache.spark.rdd.RDD
import org.apache.spark.rdd.RDD.rddToPairRDDFunctions
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.SQLContext
import org.apache.spark.storage.StorageLevel

import com.google.gson.Gson

import ch.ninecode.cim._
import ch.ninecode.model._

class SmartMeter extends Serializable
{
    var _StorageLevel = StorageLevel.MEMORY_ONLY

    // copied from GridLAB-D
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

  // function to get the edge distance
  def span(element: Element): Double =
    {
      val clazz = element.getClass.getName
      val cls = clazz.substring(clazz.lastIndexOf(".") + 1)
      val ret = cls match {
        case "ACLineSegment" =>
          element.asInstanceOf[ACLineSegment].Conductor.len
        case _ =>
          0.0
      }
      return (ret)
    }

  // copied from GridLAB-D
  def edge_operator(voltages: Map[String, Double], topologicalnodes: Boolean)(arg: Tuple2[Tuple2[Element, Option[Iterable[PowerTransformerEnd]]], Iterable[Terminal]]): List[PreEdge] =
    {
      var ret = List[PreEdge]()
      def node_name(t: Terminal): String =
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
                new PreEdge(
                  terminals(0).ACDCTerminal.IdentifiedObject.mRID,
                  node_name(terminals(0)),
                  volts(0),
                  "",
                  "",
                  volts(0),
                  terminals(0).ConductingEquipment,
                  equipment,
                  e,
                  span(e))
            case _ =>
              {
                for (i <- 1 until terminals.length) // for comprehension: iterate omitting the upper bound
                {
                  ret = ret :+ new PreEdge(
                    terminals(0).ACDCTerminal.IdentifiedObject.mRID,
                    node_name(terminals(0)),
                    volts(0),
                    terminals(i).ACDCTerminal.IdentifiedObject.mRID,
                    node_name(terminals(i)),
                    volts(i),
                    terminals(0).ConductingEquipment,
                    equipment,
                    e,
                    span(e))
                }
                ret
              }
          }
      }
      //else // shouldn't happen, terminals always reference ConductingEquipment, right?

      return (ret)
    }

  // copied from GridLAB-D
  def topological_node_operator(arg: Tuple2[Tuple2[TopologicalNode, Terminal], PreEdge]): PreNode =
    {
      val node = arg._1._1
      val term = arg._1._2
      val edge = arg._2
      PreNode(node.id, if (term.ACDCTerminal.sequenceNumber == 1) edge.v1 else edge.v2)
    }

  // copied from GridLAB-D
  def connectivity_node_operator(arg: Tuple2[Tuple2[ConnectivityNode, Terminal], PreEdge]): PreNode =
    {
      val node = arg._1._1
      val term = arg._1._2
      val edge = arg._2
      PreNode(node.id, if (term.ACDCTerminal.sequenceNumber == 1) edge.v1 else edge.v2)
    }

  // copied from GridLAB-D
  def make_graph_vertices(v: PreNode): Tuple2[VertexId, PreNode] =
    {
      (v.vertex_id(v.id_seq), v)
    }

  // copied from GridLAB-D
  def make_graph_edges(e: PreEdge): Edge[PreEdge] =
    {
      Edge(e.vertex_id(e.id_cn_1), e.vertex_id(e.id_cn_2), e)
    }

  // copied from GridLAB-D
  def prepare(sc: SparkContext, sqlContext: SQLContext, topologicalnodes: Boolean): Graph[PreNode, PreEdge] =
    {
      // get a map of voltages
      val voltages = get("BaseVoltage", sc).asInstanceOf[RDD[BaseVoltage]].map((v) => (v.id, v.nominalVoltage)).collectAsMap()

      // get the terminals
      val terminals = get("Terminal", sc).asInstanceOf[RDD[Terminal]].filter(null != _.ConnectivityNode)

      // get the terminals keyed by equipment
      val terms = terminals.groupBy(_.ConductingEquipment)

      // get all elements
      val elements = get("Elements", sc).asInstanceOf[RDD[Element]]

      // get the transformer ends keyed by transformer
      val ends = get("PowerTransformerEnd", sc).asInstanceOf[RDD[PowerTransformerEnd]].groupBy(_.PowerTransformer)

      // handle transformers specially, by attaching all PowerTransformerEnd objects to the elements
      val elementsplus = elements.keyBy(_.id).leftOuterJoin(ends)

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

      // persist edges and nodes to avoid recompute
      real_edges.persist(_StorageLevel)
      nodes.persist(_StorageLevel)

      // construct the initial graph from the real edges and nodes
      return (Graph.apply[PreNode, PreEdge](nodes.map(make_graph_vertices), real_edges.map(make_graph_edges), PreNode("", 0.0), _StorageLevel, _StorageLevel))
    }

  def node_JSON(node: FinalNodeData): String =
    {    
      val gson = new Gson
      gson.toJson(node)
    }

  def make_JSON(nodes: RDD[FinalNodeData]): String =
    {
      val nodestring = nodes.map(node_JSON).fold("")((x: String, y: String) => x + (if ("" == x) "" else ", ") + y)
      return ("[ " + nodestring + " ]")
    }
  
  def filterEmptyLeaves(vertex: RDD[NodeData]): RDD[NodeData] =
  {
      val parents = vertex.map(_.parent).collect
      vertex.filter(v => {parents.contains(v.id_seq) || v.id_seq.startsWith("HAS")})
  }

  def run(sc: SparkContext, sqlContext: SQLContext, starting_node: String, use_topological_nodes: Boolean): String =
    {
      val pn = PreNode("", 0.0) // just to access the vertex_id function

      val initial = prepare(sc, sqlContext, use_topological_nodes)

      // get the ConnectivityNode corresponding to the given starting node
      val terminal = get ("Terminal", sc).asInstanceOf[RDD[Terminal]].filter ((terminal) => terminal.ConductingEquipment == starting_node).first      
      val start_at = Array[VertexId] (pn.vertex_id (if (use_topological_nodes) terminal.TopologicalNode else terminal.ConnectivityNode))
      
      val trace = new Trace(initial)
      val tracedGraph = trace.run(start_at)
      
      val tracedVertices = tracedGraph.vertices.filter(_._2.total_distance < Double.PositiveInfinity)
      
      var tracedRenamedVertices = tracedVertices.map(vertex => {
        val node = vertex._2
        val name = node.id_seq
        val index1 = name.indexOf("_")
        val id = if (0 < index1) name.substring(0, index1) else name
        val neighbor = node.neighbor
        val index2 = neighbor.indexOf("_")
        val nid = if (0 < index2) neighbor.substring(0, index2) else neighbor
        val parent = node.parent
        val index3 = parent.indexOf("_")
        val parentid = if (0 < index3) parent.substring(0, index3) else parent
        node.copy(id_seq = id, neighbor = nid, parent = parentid)
      })
      
      var withoutEmptyLeafes = filterEmptyLeaves(tracedRenamedVertices)
      while (withoutEmptyLeafes.count < tracedRenamedVertices.count)
      {
        tracedRenamedVertices = withoutEmptyLeafes
        withoutEmptyLeafes = filterEmptyLeaves(tracedRenamedVertices)
      }
           
      val name = get ("Name", sc).asInstanceOf[RDD[Name]]     
      val userAttr = get ("UserAttribute", sc).asInstanceOf[RDD[UserAttribute]]
      val joinedMst = name.keyBy(_.IdentifiedObj).join(userAttr.keyBy(_.name))
      val preparedAoId = joinedMst.map(mst => (mst._2._2.value, mst._2._1.name)).groupByKey
      
      val sapNr = withoutEmptyLeafes.keyBy(_.id_seq)
                      .leftOuterJoin(preparedAoId)
                      .map(joinedNode => {
                        val node = joinedNode._2._1
                        val ao = joinedNode._2._2
                        val ao_ids = ao match {
                          case Some(i) => i.toArray
                          case _ => Array("")
                        }
                        FinalNodeData(node.id_seq, ao_ids, node.voltage, node.neighbor, node.parent, node.total_distance, node.nearest_distance)
                      })
                      
      make_JSON(sapNr)
    }
}

object SmartMeter
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
        val smart = new SmartMeter
        val filename = if (args.length > 0)
            args (0)
        else
      "hdfs://sandbox:8020/data/" + "NIS_CIM_Export_sias_current_20160816_Wildenrueti_V9" + ".rdf"

    val starting_node = if (args.length > 1)
      args(1)
    else
      "ABG91246"
      
    val use_topological_node = true

    val start = System.nanoTime()

    // create the configuration
    val configuration = new SparkConf(false)
    configuration.setAppName("SmartMeter")
    configuration.setMaster("spark://sandbox:7077")
    configuration.setSparkHome("/home/derrick/spark-1.6.0-bin-hadoop2.6/")
    configuration.set("spark.driver.memory", "2g")
    configuration.set("spark.executor.memory", "4g")
    configuration.set("spark.executor.extraJavaOptions", "-XX:+UseCompressedOops -XX:+PrintGCDetails -XX:+PrintGCTimeStamps")
    // get the necessary jar files to send to the cluster
    val s1 = jarForObject(new DefaultSource())
    val s2 = jarForObject(smart)
    configuration.setJars(Array(s1, s2))

    // register low level classes
    configuration.registerKryoClasses(Array(classOf[Element], classOf[BasicElement], classOf[Unknown]))

    // make a Spark session
    val session = SparkSession.builder().config(configuration).getOrCreate() // create the fixture
    session.sparkContext.setLogLevel("OFF") // Valid log levels include: ALL, DEBUG, ERROR, FATAL, INFO, OFF, TRACE, WARN

    val setup = System.nanoTime()

    val files = filename.split(",")
    val options = new HashMap[String, String]().asInstanceOf[java.util.Map[String, String]]
    options.put("path", filename)
    options.put("StorageLevel", "MEMORY_AND_DISK_SER")
    options.put("ch.ninecode.cim.do_topo_islands", "true")
    val elements = session.sqlContext.read.format("ch.ninecode.cim").options(options).load(files: _*)
    val count = elements.count

    val read = System.nanoTime()

    smart._StorageLevel = StorageLevel.MEMORY_AND_DISK_SER

    val result = smart.run(session.sparkContext, session.sqlContext, starting_node, use_topological_node)

    val graph = System.nanoTime()

    println(result)

    println("" + count + " elements")
    println("setup : " + (setup - start) / 1e9 + " seconds")
    println("read : " + (read - setup) / 1e9 + " seconds")
    println("graph: " + (graph - read) / 1e9 + " seconds")
    println()
  }
}

