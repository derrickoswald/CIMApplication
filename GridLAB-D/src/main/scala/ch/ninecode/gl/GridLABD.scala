package ch.ninecode.gl

import java.io.UnsupportedEncodingException
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

import scala.tools.nsc.io.Jar
import scala.util.Random

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path
import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.apache.spark.rdd.RDD.rddToPairRDDFunctions
import org.apache.spark.sql.SQLContext
import org.apache.spark.storage.StorageLevel

import ch.ninecode.cim._
import ch.ninecode.model._

case class PreEdge (id_seq_1: String, id_cn_1: String, id_seq_2: String, id_cn_2: String, id_equ: String, equipment: ConductingEquipment, element: Element) extends Serializable
case class PreNode (id_seq: String, voltage: Double) extends Serializable

class GridLABD extends Serializable
{
    var _StorageLevel = StorageLevel.MEMORY_ONLY

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

    def edge_operator (arg: Tuple2[Element, Iterable[Terminal]]): List[PreEdge] =
    {
        var ret = List[PreEdge] ()
        val e = arg._1
        val it = arg._2
        // get the ConductingEquipment
        var c = e
        while ((null != c) && !c.getClass ().getName ().endsWith (".ConductingEquipment"))
            c = c.sup
        if (null != c)
        {
            // sort terminals by sequence number
            var terminals = it.toArray.sortWith (_.ACDCTerminal.sequenceNumber < _.ACDCTerminal.sequenceNumber)
            // make a pre-edge for each pair of terminals
            ret = terminals.length match
            {
                case 1 =>
                    ret :+
                        new PreEdge (
                            terminals(0).ACDCTerminal.IdentifiedObject.mRID,
                            terminals(0).ConnectivityNode,
                            "",
                            "",
                            terminals(0).ConductingEquipment,
                            c.asInstanceOf[ConductingEquipment],
                            e)
                case 2 =>
                    ret :+
                        new PreEdge (
                            terminals(0).ACDCTerminal.IdentifiedObject.mRID,
                            terminals(0).ConnectivityNode,
                            terminals(1).ACDCTerminal.IdentifiedObject.mRID,
                            terminals(1).ConnectivityNode,
                            terminals(0).ConductingEquipment,
                            c.asInstanceOf[ConductingEquipment],
                            e)
                case _ =>
                    {
                        var i = 0
                        while (i < terminals.length - 1)
                        {
                            ret = ret :+ new PreEdge (
                                    terminals(0).ACDCTerminal.IdentifiedObject.mRID,
                                    terminals(0).ConnectivityNode,
                                    terminals(i + 1).ACDCTerminal.IdentifiedObject.mRID,
                                    terminals(i + 1).ConnectivityNode,
                                    terminals(0).ConductingEquipment,
                                    c.asInstanceOf[ConductingEquipment],
                                    e)
                            i += 1
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

    def node_operator (arg: Tuple2[Tuple2[ConnectivityNode,Terminal], Double]): PreNode =
    {
        val node = arg._1._1
        val voltage = arg._2
        PreNode (node.id, voltage)
    }

    // emit one GridLAB-D node
    def make_node (node: PreNode): String =
    {
        val ret =
            "        object node\n" +
            "        {\n" +
            "            name \"" + node.id_seq + "\";\n" +
            "            phases \"ABCN\";\n" +
            "            bustype PQ;\n" +
            "            nominal_voltage " + node.voltage + "V;\n" +
            "        }\n" +
            ""
        return (ret)
    }
    def make_link (edge: PreEdge): String =
    {
        val ret =
            "        object link\n" +
            "        {\n" +
            "            name \"" + edge.id_equ + "\";\n" +
            "            phases ABCN;\n" +
            "            from \"" + edge.id_cn_1 + "\";\n" +
            "            to \"" + edge.id_cn_2 + "\";\n" +
            "        }\n" +
            ""
        return (ret)
    }

//            "        object underground_line\n" +
//            "        {\n" +
//            "            name \"HAS42130_0_stub\";\n" +
//            "            phases \"ABCN\";\n" +
//            "            from \"HAS42130\";\n" +
//            "            to \"HAS42130_0\";\n" +
//            "            length 25m;\n" +
//            "            configuration \"line_3x25Cu/25\";\n" +
//            "        };" +

    def preparation (sc: SparkContext, sqlContext: SQLContext, args: String): String  =
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

        val USE_UTC = false

        val format =
            if (USE_UTC)
            {
                // for dates in UTC
                val f = new SimpleDateFormat ("yyyy-MM-dd'T'HH:mm:ss'Z'")
                f.setTimeZone (TimeZone.getTimeZone ("UTC"))
                f
            }
            else
                // for dates in the local time zone
                new SimpleDateFormat ("yyyy-MM-dd'T'HH:mm:ssZ")
        val start = Calendar.getInstance ()
        val finish = start.clone ().asInstanceOf[Calendar]
        finish.add (Calendar.HOUR, 3)

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
            "            timezone " + (if (USE_UTC) "GMT" else "GMT0+2") + ";\n" +
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
            "            filename voltdump.csv;\n" +
            "            mode polar;\n" +
            "            runtime '" + format.format (finish.getTime ()) + "';\n" +
            "        };\n" +
            "\n" +
            "        object line_configuration\n" +
            "        {\n" +
            "            name \"line_3x25Cu/25\";\n" +
            "            z11 0.727+0.08j Ohm/km;\n" +
            "            z12 0.0+0.0j Ohm/km;\n" +
            "            z13 0.0+0.0j Ohm/km;\n" +
            "            z21 0.0+0.0j Ohm/km;\n" +
            "            z22 0.727+0.08j Ohm/km;\n" +
            "            z23 0.0+0.0j Ohm/km;\n" +
            "            z31 0.0+0.0j Ohm/km;\n" +
            "            z32 0.0+0.0j Ohm/km;\n" +
            "            z33 0.727+0.08j Ohm/km;\n" +
            "        };\n" +
            "\n" +
            "        object line_configuration\n" +
            "        {\n" +
            "            name \"line_3x95Cu/95\";\n" +
            "            z11 0.193+0.07j Ohm/km;\n" +
            "            z12 0.0+0.0j Ohm/km;\n" +
            "            z13 0.0+0.0j Ohm/km;\n" +
            "            z21 0.0+0.0j Ohm/km;\n" +
            "            z22 0.193+0.07j Ohm/km;\n" +
            "            z23 0.0+0.0j Ohm/km;\n" +
            "            z31 0.0+0.0j Ohm/km;\n" +
            "            z32 0.0+0.0j Ohm/km;\n" +
            "            z33 0.193+0.07j Ohm/km;\n" +
            "        };\n" +
            "\n" +
            "        object transformer_configuration\n" +
            "        {\n" +
            "            name transformer;\n" +
            "            connect_type WYE_WYE;\n" +
            "            install_type PADMOUNT;\n" +
            "            power_rating 500;\n" +
            "            primary_voltage 4800;\n" +
            "            secondary_voltage 400;\n" +
            "            resistance 0.011;\n" +
            "            reactance 0.02;\n" +
            "        };\n" +
            "\n" +
//            "        object meter\n" +
//            "        {\n" +
//            "            name \"HAS42130_0\";\n" +
//            "            phases \"ABCN\";\n" +
//            "            bustype PQ;\n" +
//            "            nominal_voltage 400V;\n" +
//            "        };\n" +
//            "\n" +
            "";

        val result = new StringBuilder ()
        result.append (prefix)

        val configuration = new Configuration ()
        configuration.set ("fs.hdfs.impl", "org.apache.hadoop.hdfs.DistributedFileSystem") // .class.getName ()
        configuration.set ("fs.file.impl", "org.apache.hadoop.fs.LocalFileSystem")
        val hdfs = FileSystem.get (URI.create ("hdfs://sandbox:9000"), configuration)

        val nodeFileName = "/output/gridlabd_nodes"
        val nodePath = new Path (nodeFileName)
        val edgeFileName = "/output/gridlabd_edges"
        val edgePath = new Path (edgeFileName)

        hdfs.delete (nodePath, true)
        hdfs.delete (edgePath, true)

        // for now add all nodes and edges

        // get the terminals
        val terminals = get ("Terminal", sc).asInstanceOf[RDD[Terminal]]

        // get the terminals keyed by equipment
        val terms = terminals.groupBy (_.ConductingEquipment)

        // get all elements
        val equipment = get ("Elements", sc).asInstanceOf[RDD[Element]]

        // map the terminal 'pairs' to edges
        val edges = equipment.keyBy (_.id).join (terms).flatMapValues (edge_operator).values

        // get a map of voltages
        val voltages = get ("BaseVoltage", sc).asInstanceOf[RDD[BaseVoltage]].map ((v) => (v.id, v.nominalVoltage)).collectAsMap ()

        def voltage_operator (arg: Tuple2[String,PreEdge]): (String, Double) =
        {
            (arg._1, 1000.0 * voltages.getOrElse (arg._2.equipment.BaseVoltage, 0.0))
        }

        // get terminal to voltage mapping by referencing the equipment voltage for each of two terminals
        val tv = edges.keyBy (_.id_seq_1).map (voltage_operator).union (edges.keyBy (_.id_seq_2).map (voltage_operator)).distinct

        // get the connectivity nodes RDD
        val connectivitynodes = get ("ConnectivityNode", sc).asInstanceOf[RDD[ConnectivityNode]]

        // map the connectivity nodes to prenodes with voltages
        val nodes = connectivitynodes.keyBy (_.id).join (terminals.keyBy (_.ConnectivityNode)).values.keyBy (_._2.id).join (tv).values.map (node_operator).distinct

        val n_strings = nodes.map (make_node);
        val e_strings = edges.map (make_link);

        n_strings.saveAsTextFile ("hdfs://sandbox:9000" + nodeFileName)
        e_strings.saveAsTextFile ("hdfs://sandbox:9000" + edgeFileName)

        val nodefiles = sc.wholeTextFiles ("hdfs://sandbox:9000" + nodeFileName)
        val edgefiles = sc.wholeTextFiles ("hdfs://sandbox:9000" + edgeFileName)
//        (a-hdfs-path/part-00000, its content)
//        (a-hdfs-path/part-00001, its content)
//        ...
//        (a-hdfs-path/part-nnnnn, its content)
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
            "hdfs://sandbox:9000/data/" + "NIS_CIM_Export_sias_current_20160816_V8_Bruegg" + ".rdf"

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

        val elements = _SqlContext.read.format ("ch.ninecode.cim").option ("StorageLevel", "MEMORY_AND_DISK_SER").load (filename)
        val count = elements.count

        val read = System.nanoTime ()

        gridlab._StorageLevel = StorageLevel.MEMORY_AND_DISK_SER

        val prep = System.nanoTime ()

        val result = gridlab.preparation (_Context, _SqlContext, "transformer=all") // TRA5401

        val graph = System.nanoTime ()

        Files.write (Paths.get ("gridlabd.glm"), result.getBytes (StandardCharsets.UTF_8))

        println ("" + count + " elements")
        println ("read : " + (read - start) / 1e9 + " seconds")
        println ("prep : " + (prep - read) / 1e9 + " seconds")
        println ("graph: " + (graph - prep) / 1e9 + " seconds")
        println ("write: " + (System.nanoTime () - graph) / 1e9 + " seconds")
        println ();
    }
}
