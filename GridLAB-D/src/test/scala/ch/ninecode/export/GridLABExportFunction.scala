package ch.ninecode.export

import java.io.File
import java.io.UnsupportedEncodingException
import java.net.URI
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

import scala.tools.nsc.io.Jar
import scala.util.Random

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession

import ch.ninecode.gl.GLMEdge
import ch.ninecode.gl.GLMGenerator
import ch.ninecode.gl.GLMNode
import ch.ninecode.gl.GridLABD
import ch.ninecode.gl.Island
import ch.ninecode.gl.PreEdge
import ch.ninecode.gl.PreNode
import ch.ninecode.gl.SwingNode
import ch.ninecode.gl.TransformerSet
import ch.ninecode.gl.Transformers

// copy of same file in webapp
// remove superclass and override, add elements that are missing from the superclass, and change workdir and output path
case class GridLABExportFunction (island: String)
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

    // build a file system configuration, including core-site.xml
    def hdfs_configuration: Configuration =
    {
        val configuration = new Configuration ()
        if (null == configuration.getResource ("core-site.xml"))
        {
            val hadoop_conf: String = System.getenv ("HADOOP_CONF_DIR")
            if (null != hadoop_conf)
            {
                val site: Path = new Path (hadoop_conf, "core-site.xml")
                val f: File = new File (site.toString)
                if (f.exists && !f.isDirectory)
                    configuration.addResource (site)
            }
        }
        configuration
    }

    // get the file system
    def uri: URI = FileSystem.getDefaultUri (hdfs_configuration)
    // or: val uri: URI = URI.create (hdfs_configuration.get (FileSystem.FS_DEFAULT_NAME_KEY))

    def hdfs: FileSystem = FileSystem.get (uri, hdfs_configuration)

    val jars = Array (jarForObject (new GLMGenerator (one_phase = true, null)), jarForObject (this))

    class LocalGLMGenerator (one_phase: Boolean, date_format: SimpleDateFormat, title: String, tx: TransformerSet, xedges: RDD[PreEdge], xnodes: RDD[PreNode]) extends GLMGenerator (one_phase, date_format)
    {
        override def name: String = title

        override def header: String = "CIMApplication GridLABExport"

        override def start_time: Calendar = javax.xml.bind.DatatypeConverter.parseDateTime ("2017-07-18T00:00:00")

        override def finish_time: Calendar = javax.xml.bind.DatatypeConverter.parseDateTime ("2017-07-19T00:00:00")

        override def transformers: Array[TransformerSet] = Array (tx)

        override def edge_groups: Iterable[Iterable[GLMEdge]] = xedges.groupBy (_.key).values.collect.toIterable

        override def swing_nodes: Iterable[GLMNode] = List (SwingNode (tx.node0, tx.v0, tx.transformer_name))

        override def nodes: Iterable[GLMNode] = xnodes.collect.toIterable

        override def emit_node (node: GLMNode): String =
        {
            if (node.id != tx.transformers(0).node0)
                super.emit_node (node) + generate_load (node)
            else
                ""
        }

        def generate_load (node: GLMNode): String =
        {
            val house = nis_number (node.id)
            "\n" +
                "        object load\n" +
                "        {\n" +
                "            name \"" + node.id + "_load\";\n" +
                "            parent \"" + node.id + "\";\n" +
                "            phases " + (if (one_phase) "AN" else "ABCN") + ";\n" +
                "            nominal_voltage " + node.nominal_voltage + "V;\n" +
                (if (one_phase)
                    "            object player\n" +
                        "            {\n" +
                        "                property \"constant_power_A\";\n" +
                        "                file \"input_data/" + house + ".csv\";\n" +
                        "            };\n"
                else
                    "            object player\n" +
                        "            {\n" +
                        "                property \"constant_power_A\";\n" +
                        "                file \"input_data/" + house + "_R.csv\";\n" +
                        "            };\n" +
                        "            object player\n" +
                        "            {\n" +
                        "                property \"constant_power_B\";\n" +
                        "                file \"input_data/" + house + "_S.csv\";\n" +
                        "            };\n" +
                        "            object player\n" +
                        "            {\n" +
                        "                property \"constant_power_C\";\n" +
                        "                file \"input_data/" + house + "_T.csv\";\n" +
                        "            };\n") +
                "        };\n" +
                "\n" + // only need a recorder if there is a load
                "        object recorder\n" +
                "        {\n" +
                "            name \"" + nis_number (node.id) + "_voltage_recorder\";\n" +
                "            parent \"" + node.id + "\";\n" +
                "            property " + ( if (one_phase) "voltage_A.real,voltage_A.imag" else "voltage_A.real,voltage_A.imag,voltage_B.real,voltage_B.imag,voltage_C.real,voltage_C.imag") + ";\n" +
                "            interval 5;\n" +
                "            file \"output_data/" + node.id + "_voltage.csv\";\n" +
                "        };\n"
        }
    }

    def executeString (spark: SparkSession): String =
    {
        val format = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss z")
        format.setTimeZone (TimeZone.getTimeZone ("UTC"))
        val gridlabd = new GridLABD (
            session = spark,
            topological_nodes= true,
            one_phase = true,
            workdir = "target/simulation/")
        val i = new Island (spark)
        val (edges, nodes) = i.prepare (island)
        val transformers: Transformers = new Transformers (spark)
        val tdata = transformers.getTransformerData ().keyBy (_.transformer.id).join (edges.keyBy (_.id_equ)).map (_._2._1).collect
        val generator = new LocalGLMGenerator (one_phase = true, date_format = format, title = island, tx = TransformerSet (tdata), xedges = edges, xnodes = nodes)
        gridlabd.export (generator)
        val glm = spark.sparkContext.textFile ("target/simulation/" + island + "/" + island + ".glm")
        glm.collect.mkString ("\n")
    }

    override def toString: String =
    {
        val sb = new StringBuilder (super.toString)
        sb.append (" is GridLABExportFunction (island = %s)".format (island))
        sb.toString
    }
}
