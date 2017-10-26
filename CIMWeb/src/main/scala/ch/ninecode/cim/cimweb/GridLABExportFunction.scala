package ch.ninecode.cim.cimweb

import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone
import javax.json.Json
import javax.json.JsonException
import javax.json.JsonObject

import scala.collection.JavaConverters._

import org.apache.hadoop.fs.Path
import org.apache.hadoop.io.Text
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession

import ch.ninecode.cim.cimweb.RESTfulJSONResult.FAIL
import ch.ninecode.cim.cimweb.RESTfulJSONResult.OK
import ch.ninecode.gl.GLMGenerator
import ch.ninecode.gl.GridLABD
import ch.ninecode.cim.connector.CIMFunction.Return
import ch.ninecode.gl.GLMEdge
import ch.ninecode.gl.GLMNode
import ch.ninecode.gl.Island
import ch.ninecode.gl.PreEdge
import ch.ninecode.gl.PreNode
import ch.ninecode.gl.SwingNode
import ch.ninecode.gl.Transformers
import ch.ninecode.gl.TransformerSet

/**
 * Export a glm file and the associated input_data.
 *
 * @param simulation the name of a JSON file on HDFS with the simulation to export
 */
case class GridLABExportFunction (simulation: String) extends CIMWebFunction
{
    jars = Array (jarForObject (new GLMGenerator (one_phase = true, null)), jarForObject (this))

    override def getReturnType: Return = Return.String

    case class Player (name: String, parent: String, file: String)

    case class Recorder (name: String, parent: String, property: String, interval: Double, file: String)

    class LocalGLMGenerator (one_phase: Boolean, date_format: SimpleDateFormat, title: String, tx: TransformerSet, players: Array[Player], recorders: Array[Recorder], xedges: RDD[PreEdge], xnodes: RDD[PreNode]) extends GLMGenerator (one_phase, date_format)
    {
        override def name: String = title

        override def header: String = "CIMApplication GridLABExport"

        override def start_time: Calendar = javax.xml.bind.DatatypeConverter.parseDateTime ("2017-07-18T00:00:00")

        override def finish_time: Calendar = javax.xml.bind.DatatypeConverter.parseDateTime ("2017-07-19T00:00:00")

        override def transformers: Array[TransformerSet] = Array (tx)

        override def edge_groups: Iterable[Iterable[GLMEdge]] = xedges.groupBy (_.key).values.collect.toIterable

        override def swing_nodes: Iterable[GLMNode] = List (SwingNode (tx.node0, tx.v0, tx.transformer_name))

        override def nodes: Iterable[GLMNode] = xnodes.collect.toIterable

        override def emit_edge (edges: Iterable[GLMEdge]): String =
        {
            super.emit_edge (edges) + generate_edge_recorders (edges)
        }

        override def emit_node (node: GLMNode): String =
        {
            if (node.id != tx.transformers(0).node0)
                super.emit_node (node) + generate_load (node) + generate_node_recorders (node)
            else
                ""
        }

        override def emit_transformer (transformer: TransformerSet): String =
        {
            val name = transformer.transformer_name

            super.emit_transformer (transformer) + generate_transformer_recorders (transformer)
        }

        def generate_load (node: GLMNode): String =
        {
            // ToDo: handle three phase files
            def one_to_three (file: String, phase: String) = file.substring (0, file.length - 4) + "_" + phase + file.substring (file.length - 4)
            players.find (player ⇒ player.parent == node.id) match
            {
                case Some (player: Player) ⇒
                    "\n" +
                    "        object load\n" +
                    "        {\n" +
                    "            name \"" + player.name + "\";\n" +
                    "            parent \"" + player.parent + "\";\n" +
                    "            phases " + (if (one_phase) "AN" else "ABCN") + ";\n" +
                    "            nominal_voltage " + node.nominal_voltage + "V;\n" +
                    (if (one_phase)
                        "            object player\n" +
                            "            {\n" +
                            "                property \"constant_power_A\";\n" +
                            "                file \"" + player.file + "\";\n" +
                            "            };\n"
                    else
                        "            object player\n" +
                            "            {\n" +
                            "                property \"constant_power_A\";\n" +
                            "                file \"" + one_to_three (player.file, "R") + "\";\n" +
                            "            };\n" +
                            "            object player\n" +
                            "            {\n" +
                            "                property \"constant_power_B\";\n" +
                            "                file \"" + one_to_three (player.file, "S") + "\";\n" +
                            "            };\n" +
                            "            object player\n" +
                            "            {\n" +
                            "                property \"constant_power_C\";\n" +
                            "                file \"" + one_to_three (player.file, "T") + "\";\n" +
                            "            };\n") +
                    "        };\n"
                case None ⇒ ""
            }
        }

        def add_phase (property: String, phase: String): String = property + "_" + phase + ".real," + property+ "_" + phase + ".imag"

        def property_phased (property: String): String =
        {
            if (one_phase)
                add_phase (property, "A")
            else
                add_phase (property, "A") + "," + add_phase (property, "B") + "," + add_phase (property, "C")
        }

        def generate_node_recorders (node:GLMNode): String =
        {
            val matching_recorders = recorders.filter (recorder ⇒ recorder.parent == node.id)
            val s = new StringBuilder
            matching_recorders.foreach (
                recorder ⇒
                    s.append (
                        "\n" +
                        "        object recorder\n" +
                        "        {\n" +
                        "            name \"" + recorder.name + "\";\n" +
                        "            parent \"" + recorder.parent + "\";\n" +
                        "            property " + property_phased (recorder.property) + ";\n" +
                        "            interval " + recorder.interval + ";\n" +
                        "            file \"" + recorder.file + "\";\n" +
                        "        };\n"
                    )
            )
            s.toString
        }

        def generate_edge_recorders (edges: Iterable[GLMEdge]): String =
        {
            // a little tricky here, the line configuration is maybe parallel cables, but the recorder may ask for any of the cables
            val edgeids = edges.map (_.id).toArray
            val matching_recorders = recorders.filter (recorder ⇒ edgeids.contains (recorder.parent))
            val s = new StringBuilder
            matching_recorders.foreach (
                recorder ⇒
                {
                    val edge = edges.head
                    val cls = edge.el.getClass.getName
                    val clazz = cls.substring (cls.lastIndexOf(".") + 1)
                    def current_recorder: String =
                    {
                        "\n" +
                        "        object recorder\n" +
                        "        {\n" +
                        "            name \"" + recorder.name + "\";\n" +
                        "            parent \"" + recorder.parent + "\";\n" +
                        "            property " + property_phased (recorder.property) + ";\n" +
                        "            interval " + recorder.interval + ";\n" +
                        "            file \"" + recorder.file + "\";\n" +
                        "        };\n"
                    }

                    clazz match
                    {
                        case "ACLineSegment" ⇒
                            s.append (current_recorder)
                        case _ ⇒

                    }
                }
            )
            s.toString
        }

        def generate_transformer_recorders (transformer: TransformerSet): String =
        {
            // a little tricky here, transformer.transformer_name may be the ganged name, e.g. TRA1234_TRA1235, but the recorder may ask for any of transformers
            val matching_recorders = recorders.filter (
                recorder ⇒
                    (recorder.parent == transformer.transformer_name) ||
                    (transformer.transformers.find (recorder.parent == _.transformer.id) match { case Some (_) ⇒ true case None ⇒ false }))
            val s = new StringBuilder
            matching_recorders.foreach (
                recorder ⇒
                    s.append (
                    "\n" +
                    "        object recorder\n" +
                    "        {\n" +
                    "            name \"" + recorder.name + "\";\n" +
                    "            parent \"" + recorder.parent + "\";\n" +
                    "            property " + property_phased (recorder.property) + ";\n" +
                    "            interval " + recorder.interval + ";\n" +
                    "            file \"" + recorder.file + "\";\n" +
                    "        };\n"))
            s.toString
        }
    }

    def fail (message: String) : JsonObject =
    {
        Json.createObjectBuilder.add ("status", FAIL).add ("message", message).add ("result", Json.createObjectBuilder).build
    }

    def readJSON (simulation: String): JsonObject =
    {
        val file: Path = new Path (hdfs.getUri.toString, simulation)
        // read the file
        try
        {
            val data = hdfs.open (file)
            val size = hdfs.getFileStatus (file).getLen.toInt
            val bytes = new Array[Byte] (size)
            data.readFully (0, bytes)
            val text = Text.decode (bytes, 0, size)
            try
                Json.createReader (new StringReader (text)).readObject match
                {
                    case obj: JsonObject ⇒ obj
                    case _ ⇒ fail ("%s does not contain a JsonObject".format (simulation))
                }
            catch
            {
                case je: JsonException ⇒
                    fail ("%s could not be parsed as JSON (%s)".format (simulation, je.getMessage))
            }
        }
        catch
        {
            case e: Exception =>
                fail (e.getMessage)
        }
    }

    def getPlayers (details: JsonObject): Array[Player] =
    {
        val array = details.getJsonArray ("players")
        if (null == array)
            Array()
        else
        {
            val buffer = for (element: JsonObject ← array.getValuesAs (classOf[JsonObject]).asScala) // ToDo: more robust checking
                yield Player (element.getString ("name", ""), element.getString ("parent", ""), element.getString ("player", ""))
            buffer.toArray
        }
    }

    def getRecorders (details: JsonObject): Array[Recorder] =
    {
        val array = details.getJsonArray ("recorders")
        if (null == array)
            Array()
        else
        {
            val buffer = for (element: JsonObject ← array.getValuesAs (classOf[JsonObject]).asScala) // ToDo: more robust checking
                yield Recorder (element.getString ("name", ""), element.getString ("parent", ""), element.getString ("property", ""), element.getJsonNumber ("interval").doubleValue, element.getString ("file", ""))
            buffer.toArray
        }
    }

    override def executeString (spark: SparkSession): String =
    {
        val details = readJSON (simulation)
        if (OK == details.getString ("status", OK))
        {
            val island = details.getString ("island", "")
            if ("" != island)
            {
                val players = getPlayers (details)
                val recorders = getRecorders (details)
                val format = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss z")
                format.setTimeZone (TimeZone.getTimeZone ("UTC"))
                val gridlabd = new GridLABD (
                    session = spark,
                    topological_nodes= true,
                    one_phase = true,
                    workdir = hdfs.getUri.toString + "/simulation/")
                val i = new Island (spark)
                val (edges, nodes) = i.prepare (island)
                val transformers: Transformers = new Transformers (spark)
                val tdata = transformers.getTransformerData ().keyBy (_.transformer.id).join (edges.keyBy (_.id_equ)).map (_._2._1).collect
                val generator = new LocalGLMGenerator (one_phase = true, date_format = format, title = island, tx = TransformerSet (tdata), players = players, recorders = recorders, xedges = edges, xnodes = nodes)
                gridlabd.export (generator)
                val glm = spark.sparkContext.textFile (hdfs.getUri.toString + "/simulation/" + island + "/" + island + ".glm")
                glm.collect.mkString ("\n")
            }
            else
                new RESTfulJSONResult (FAIL, "property 'island' not found in simulation object").toString
        }
        else
            new RESTfulJSONResult (details.getString ("status"), details.getString ("message")).toString

    }

    override def toString: String =
    {
        val sb = new StringBuilder (super.toString)
        sb.append (" is GridLABExportFunction (simulation = %s)".format (simulation))
        sb.toString
    }
}
