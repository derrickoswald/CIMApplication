package ch.ninecode

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs._

import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import org.apache.spark.graphx._
import org.apache.spark.rdd._
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.SQLContext
import org.apache.spark.storage.StorageLevel

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

import ch.ninecode._
import ch.ninecode.cim._
import ch.ninecode.model._

case class PreEdge (id_seq_1: String, id_seq_2: String, id_equ: String, equipment: ConductingEquipment) extends Serializable
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

    def edge_operator (arg: Tuple2[ConductingEquipment, Iterable[Terminal]]): List[PreEdge] =
    {
        var ret = List[PreEdge] ()
        val e = arg._1
        val it = arg._2
        // sort terminals by sequence number
        var terminals = it.toArray.sortWith (_.ACDCTerminal.sequenceNumber < _.ACDCTerminal.sequenceNumber)
        // make a pre-edge for each pair of terminals
        ret = terminals.length match
        {
            case 1 =>
                ret :+
                    new PreEdge (
                        terminals(0).ACDCTerminal.IdentifiedObject.mRID,
                        "",
                        terminals(0).ConductingEquipment,
                        e)
            case 2 =>
                ret :+
                    new PreEdge (
                        terminals(0).ACDCTerminal.IdentifiedObject.mRID,
                        terminals(1).ACDCTerminal.IdentifiedObject.mRID,
                        terminals(0).ConductingEquipment,
                        e)
            case _ =>
                {
                    var i = 0
                    while (i < terminals.length - 1)
                    {
                        ret = ret :+ new PreEdge (
                                terminals(0).ACDCTerminal.IdentifiedObject.mRID,
                                terminals(i + 1).ACDCTerminal.IdentifiedObject.mRID,
                                terminals(0).ConductingEquipment,
                                e)
                        i += 1
                    }
                    ret
                }
        }

        return (ret)
    }

    def node_operator (arg: Tuple2[Tuple2[ConnectivityNode,Terminal], Double]): PreNode =
    {
        val node = arg._1._1
        val voltage = arg._2
        PreNode (node.id, voltage)
    }

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
//            "        object node\n" +
//            "        {\n" +
//            "            name \"HAS42130\";\n" +
//            "            phases \"ABCN\";\n" +
//            "            bustype PQ;\n" +
//            "            nominal_voltage 400V;\n" +
//            "        }\n" +
//            "\n" +
//            "        object meter\n" +
//            "        {\n" +
//            "            name \"HAS42130_0\";\n" +
//            "            phases \"ABCN\";\n" +
//            "            bustype PQ;\n" +
//            "            nominal_voltage 400V;\n" +
//            "        };\n" +
//            "\n" +
//            "        object underground_line\n" +
//            "        {\n" +
//            "            name \"HAS42130_0_stub\";\n" +
//            "            phases \"ABCN\";\n" +
//            "            from \"HAS42130\";\n" +
//            "            to \"HAS42130_0\";\n" +
//            "            length 25m;\n" +
//            "            configuration \"line_3x25Cu/25\";\n" +
//            "        };" +
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

        if (hdfs.delete (nodePath, true))
            result.append ("deleted " + nodeFileName + "\n")
        else
            result.append ("could not delete " + nodeFileName + "\n")
        if (hdfs.delete (edgePath, true))
            result.append ("deleted " + edgeFileName + "\n")
        else
            result.append ("could not delete " + edgeFileName + "\n")

        // for now add all nodes and edges

        // get the terminals
        val terminals = get ("Terminal", sc).asInstanceOf[RDD[Terminal]]

        // get the terminals keyed by equipment
        val terms = terminals.groupBy (_.ConductingEquipment)

        // get the conducting equipment
        val equipment = get ("ConductingEquipment", sc).asInstanceOf[RDD[ConductingEquipment]]

        // map the terminal 'pairs' to edges
        val edges = equipment.keyBy (_.id).join (terms).flatMapValues (edge_operator).values

        // get a map of voltages
        val voltages = get ("BaseVoltage", sc).asInstanceOf[RDD[BaseVoltage]].map ((v) => (v.id, v.nominalVoltage)).collectAsMap ()

        def voltage_operator (arg: Tuple2[String,PreEdge]): (String, Double) =
        {
            (arg._1, voltages.getOrElse (arg._2.equipment.BaseVoltage, 0.0))
        }

        // get terminal to voltage mapping by referencing the equipment voltage for each of two terminals
        val tv = edges.keyBy (_.id_seq_1).map (voltage_operator).union (edges.keyBy (_.id_seq_2).map (voltage_operator)).distinct
        println ("tv " + tv.count + " elements")

        // get the connectivity nodes RDD
        val connectivitynodes = get ("ConnectivityNode", sc).asInstanceOf[RDD[ConnectivityNode]]

        // map the connectivity nodes to nodes with voltages
        val nodes = connectivitynodes.keyBy (_.id).join (terminals.keyBy (_.ConnectivityNode)).values.keyBy (_._2.id).join (tv).values.map (node_operator).distinct

        println ("nodes " + nodes.count + " elements")
        val n_strings = nodes.map ( node => { "node: " + node.id_seq + " " + node.voltage + "\n" });

        println ("edges " + edges.count + " elements")
        val e_strings = edges.map ( edge => { "edge: " + edge.id_equ + " " + edge.equipment.getClass ().getName () + "\n" });

        n_strings.saveAsTextFile ("hdfs://sandbox:9000" + nodeFileName)
        e_strings.saveAsTextFile ("hdfs://sandbox:9000" + edgeFileName)

//        {
//            val status = hdfs.getFileStatus (nodePath)
//            val reader = new InputStreamReader (hdfs.open (nodePath), "UTF-8")
//            val length = status.getLen.asInstanceOf[Integer]
//            val buffer = new Array[Char](length)
//            val read = reader.read (buffer, 0, length)
//            reader.close
//            result.append (buffer)
//        }
//
//        {
//            val status = hdfs.getFileStatus (edgePath)
//            val reader = new InputStreamReader (hdfs.open (edgePath), "UTF-8")
//            val length = status.getLen.asInstanceOf[Integer]
//            val buffer = new Array[Char](length)
//            val read = reader.read (buffer, 0, length)
//            reader.close
//            result.append (buffer)
//        }

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
