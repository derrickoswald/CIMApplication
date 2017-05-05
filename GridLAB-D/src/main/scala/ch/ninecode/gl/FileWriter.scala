package ch.ninecode.gl

import java.util.Calendar
import java.nio.charset.StandardCharsets

import java.io.File
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import org.apache.commons.io.FileUtils
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.permission._
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.FileUtil
import org.apache.hadoop.fs.Path

import ch.ninecode.cim._
import ch.ninecode.model._

class FileWriter (gridlabd: GridLABD, one_phase: Boolean) extends Serializable
{
    // emitting classes
    val line = new Line (one_phase)
    val trans = new Trans (one_phase, gridlabd.USE_TOPOLOGICAL_NODES)
    val switch = new SwitchDevice (one_phase)

    def nis_number (string: String): String =
    {
        val n = string.indexOf("_")
        if (0 < n)
            string.substring(0, n)
        else
            string
    }

    def gather(rdd: Iterable[String]): String =
    {
        rdd.fold("")((x: String, y: String) ⇒ if ("" == x) y else x + y)
    }

    def make_pv(node: MaxPowerFeedingNodeEEA): String =
    {
        val solargeneratingunits = node.eea.map((x) ⇒ { x.solar }).toList
        emit_pv(solargeneratingunits, node.id_seq, node.voltage)
    }

    // emit one GridLAB-D node
    def make_node(experiments: Array[Experiment])(arg: PowerFeedingNode): String =
    {
        emit_node(experiments, arg.id_seq, arg.voltage)
    }

    // emit one GridLAB-D edge
    def make_link (edges: Iterable[PreEdge]): String =
    {
        val edge = edges.head
        val cls = edge.element.getClass.getName
        val clazz = cls.substring(cls.lastIndexOf(".") + 1)
        val ret = clazz match {
            case "ACLineSegment" ⇒
                line.emit(edges)
            case "PowerTransformer" ⇒
                "" // handled specially
            case "Switch" ⇒
                switch.emit(edge, edge.element.asInstanceOf[Switch])
            case "Cut" |
                "Disconnector" |
                "GroundDisconnector" |
                "Jumper" |
                "ProtectedSwitch" |
                "Sectionaliser" ⇒
                switch.emit(edge, edge.element.sup.asInstanceOf[Switch])
            case "Breaker" |
                "LoadBreakSwitch" |
                "Recloser" ⇒
                switch.emit(edge, edge.element.sup.sup.asInstanceOf[Switch])
            case "Fuse" ⇒
                switch.emit(edge, edge.element.sup.asInstanceOf[Switch], true)
            case _ ⇒
                // by default, make a link
                "\n" +
                    "        object link\n" +
                    "        {\n" +
                    "            name \"" + edge.id_equ + "\";\n" +
                    "            phases " + (if (one_phase) "AN" else "ABCN") + ";\n" +
                    "            from \"" + edge.id_cn_1 + "\";\n" +
                    "            to \"" + edge.id_cn_2 + "\";\n" +
                    "        };\n"
        }
        return (ret)
    }

    def emit_pv(solargeneratingunits: List[SolarGeneratingUnit], parent: String, voltage: Double): String =
    {
        var load = ""
        var index = 1
        for (solargeneratingunit ← solargeneratingunits)
        {
            val power = solargeneratingunit.GeneratingUnit.ratedNetMaxP * 1000
            val power3 = power / 3 // per phase
            if (power > 0) {
                load +=
                    "\n" +
                    "        object load\n" +
                    "        {\n" +
                    "            name \"" + parent + "_pv_" + index + "\";\n" +
                    "            parent \"" + parent + "\";\n" +
                    "            phases " + (if (one_phase) "AN" else "ABCN") + ";\n" +
                    (if (one_phase)
                        "            constant_power_A -" + power + ";\n"
                    else
                        "            constant_power_A -" + power3 + ";\n" +
                        "            constant_power_B -" + power3 + ";\n" +
                        "            constant_power_C -" + power3 + ";\n") +
                    "            nominal_voltage " + voltage + "V;\n" +
                    "            load_class R;\n" +
                    "        }\n"
                index += 1
            }
        }
        load
    }


    def generate_load (experiments: Array[Experiment], name: String, voltage: Double): String =
    {
        val house = nis_number (name)
        val filtered = experiments.filter(p ⇒ p.house == house)
        val experiment = if (0 != filtered.length) filtered(0) else null

        if (null != experiment)
            "\n" +
            "        object load\n" +
            "        {\n" +
            "            name \"" + name + "_load\";\n" +
            "            parent \"" + name + "\";\n" +
            "            phases " + (if (one_phase) "AN" else "ABCN") + ";\n" +
            "            nominal_voltage " + voltage + "V;\n" +
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
            "            name \"" + nis_number (name) + "_voltage_recorder\";\n" +
            "            parent \"" + name + "\";\n" +
            "            property " + ( if (one_phase) "voltage_A.real,voltage_A.imag" else "voltage_A.real,voltage_A.imag,voltage_B.real,voltage_B.imag,voltage_C.real,voltage_C.imag") + ";\n" +
            "            interval 5;\n" +
            "            file \"output_data/" + name + "_voltage.csv\";\n" +
            "        };\n"
        else
            ""
    }

    def emit_node(experiments: Array[Experiment], name: String, voltage: Double): String =
    {
        "\n" +
        "        object meter\n" +
        "        {\n" +
        "            name \"" + name + "\";\n" +
        "            phases " + (if (one_phase) "AN" else "ABCN") + ";\n" +
        "            bustype PQ;\n" +
        "            nominal_voltage " + voltage + "V;\n" +
        "        };\n" +
        generate_load (experiments, name, voltage)  // or load_from_player_file (name, voltage)
    }

    def emit_slack(name: String, voltage: Double): String =
    {
        "\n" +
        "        object meter\n" +
        "        {\n" +
        "            name \"" + name + "\";\n" +
        "            phases " + (if (one_phase) "AN" else "ABCN") + ";\n" +
        "            bustype SWING;\n" +
        "            nominal_voltage " + voltage + "V;\n" +
        (if (one_phase)
            "            voltage_A " + voltage + ";\n"
        else
            // the DELTA-GWYE connection somehow introduces a 30° rotation in the phases, so we compensate here:
            "            voltage_A " + voltage + "+30.0d;\n" +
            "            voltage_B " + voltage + "-90.0d;\n" +
            "            voltage_C " + voltage + "+150.0d;\n") +
        "        };\n"
    }

    def make_glm (trafokreis: Trafokreis): String =
    {
        // GridLAB-D doesn't understand parallel admittance paths, so we have to do it
        val combined_edges = trafokreis.edges.groupBy(_.key).values

        // get one of each type of ACLineSegment and emit a configuration for each of them
        val l_strings = line.getACLineSegmentConfigurations(combined_edges)

        // get the transformer configuration
        val t_string = trans.getTransformerConfigurations(trafokreis.transformers)
        val o_string = emit_slack (trafokreis.swing_node (), trafokreis.swing_node_voltage ())

        // get the node strings
        val experiments = trafokreis.experiments
        val n_strings = trafokreis.nodes.map (make_node(experiments))
        val pv_strings = trafokreis.houses.filter (_.eea != null).map(make_pv)

        // get the edge strings
        val t_edges = trans.emit (trafokreis.transformers)
        val l_edges = combined_edges.map (make_link)

        /**
         * Create the output file.
         */
        val t0 = trafokreis.start_time ()
        val t1 = trafokreis.finish_time ()
        val prefix =
            "// $Id: " + trafokreis.name + ".glm\n" +
                "// Einspeiseleistung\n" +
                "//*********************************************\n" +
                "\n" +
                "        module tape;\n" +
                "\n" +
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
                "            timezone " + (if (gridlabd.USE_UTC) "UTC0UTC" else gridlabd.tzString) + ";\n" +
                "            starttime '" + gridlabd._DateFormat.format(t0.getTime()) + "';\n" +
                "            stoptime '" + gridlabd._DateFormat.format(t1.getTime()) + "';\n" +
                "        };\n" +
                "\n" +
                "        class player\n" +
                "        {\n" +
                "            complex value;\n" +
                "        };\n" +
                "\n" +
                "        object voltdump\n" +
                "        {\n" +
                "            filename \"output_data/" + trafokreis.name + "_voltdump.csv\";\n" +
                "            mode polar;\n" +
                "            runtime '" + gridlabd._DateFormat.format(t0.getTime()) + "';\n" +
                "        };\n"

        val result = new StringBuilder()
        result.append(prefix)
        result.append(t_string)
        result.append(gather(l_strings))
        result.append(o_string)
        result.append(gather(n_strings))
        result.append(t_edges)
        result.append(gather(l_edges))
        result.append(gather(pv_strings))

        result.toString()
    }

    def writeInputFile(equipment: String, path: String, bytes: Array[Byte]) =
    {
        if ("" == gridlabd.HDFS_URI)
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
            val hdfs = FileSystem.get(URI.create(gridlabd.HDFS_URI), hdfs_configuration)

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
        if ("" == gridlabd.HDFS_URI)
            FileUtils.deleteDirectory(new File("simulation/" + equipment + "/"))
        else
        {
            val hdfs_configuration = new Configuration()
            hdfs_configuration.set("fs.hdfs.impl", "org.apache.hadoop.hdfs.DistributedFileSystem")
            hdfs_configuration.set("fs.file.impl", "org.apache.hadoop.fs.LocalFileSystem")
            val hdfs = FileSystem.get(URI.create(gridlabd.HDFS_URI), hdfs_configuration)

            val directory = new Path("/simulation/" + equipment + "/")
            hdfs.delete(directory, true)
        }
    }

}