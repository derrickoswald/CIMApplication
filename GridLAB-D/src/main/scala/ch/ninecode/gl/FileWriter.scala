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

class FileWriter(gridlabd: GridLABD) extends Serializable
{

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
    def make_link(line: Line, switch: SwitchDevice)(edges: Iterable[PreEdge]): String =
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
                if (switch.one_phase) // borrow the phasing from switch
                    "\n" +
                        "        object link\n" +
                        "        {\n" +
                        "            name \"" + edge.id_equ + "\";\n" +
                        "            phases AN;\n" +
                        "            from \"" + edge.id_cn_1 + "\";\n" +
                        "            to \"" + edge.id_cn_2 + "\";\n" +
                        "        };\n"
                else
                    "\n" +
                        "        object link\n" +
                        "        {\n" +
                        "            name \"" + edge.id_equ + "\";\n" +
                        "            phases ABCN;\n" +
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
        for (solargeneratingunit ← solargeneratingunits) {
            val power = solargeneratingunit.GeneratingUnit.ratedNetMaxP * 1000
            if (power > 0) {
                if (gridlabd.USE_ONE_PHASE)
                    load +=
                        "\n" +
                        "        object load\n" +
                        "        {\n" +
                        "            name \"" + parent + "_pv_" + index + "\";\n" +
                        "            parent \"" + parent + "\";\n" +
                        "            phases AN;\n" +
                        "            constant_power_A -" + power + ";\n" +
                        "            nominal_voltage " + voltage + "V;\n" +
                        "            load_class R;\n" +
                        "        }\n"
                else {
                    val power3 = power / 3 // per phase
                    load +=
                        "\n" +
                        "        object load\n" +
                        "        {\n" +
                        "            name \"" + parent + "_pv_" + index + "\";\n" +
                        "            parent \"" + parent + "\";\n" +
                        "            phases ABCN;\n" +
                        "            constant_power_A -" + power3 + ";\n" +
                        "            constant_power_B -" + power3 + ";\n" +
                        "            constant_power_C -" + power3 + ";\n" +
                        "            nominal_voltage " + voltage + "V;\n" +
                        "            load_class R;\n" +
                        "        }\n"
                }
                index += 1
            }
        }
        load
    }

    def emit_node(experiments: Array[Experiment], name: String, voltage: Double): String =
    {
        val meter =
            if (gridlabd.USE_ONE_PHASE)
                "\n" +
                    "        object meter\n" +
                    "        {\n" +
                    "            name \"" + name + "\";\n" +
                    "            phases AN;\n" +
                    "            bustype PQ;\n" +
                    "            nominal_voltage " + voltage + "V;\n" +
                    "        };\n"
            else
                "\n" +
                    "        object meter\n" +
                    "        {\n" +
                    "            name \"" + name + "\";\n" +
                    "            phases ABCN;\n" +
                    "            bustype PQ;\n" +
                    "            nominal_voltage " + voltage + "V;\n" +
                    "        };\n"

        //           load_from_player_file (name, voltage)
        val player = generate_player_file(experiments, name, voltage)

        val recorder =
            if (gridlabd.USE_ONE_PHASE)
                "\n" +
                    "        object recorder\n" +
                    "        {\n" +
                    "            name \"" + gridlabd.has(name) + "_voltage_recorder\";\n" +
                    "            parent \"" + name + "\";\n" +
                    "            property voltage_A.real,voltage_A.imag;\n" +
                    "            interval 5;\n" +
                    "            file \"output_data/" + name + "_voltage.csv\";\n" +
                    "        };\n"
            else
                "\n" +
                    "        object recorder\n" +
                    "        {\n" +
                    "            name \"" + gridlabd.has(name) + "_voltage_recorder\";\n" +
                    "            parent \"" + name + "\";\n" +
                    "            property voltage_A.real,voltage_A.imag,voltage_B.real,voltage_B.imag,voltage_C.real,voltage_C.imag;\n" +
                    "            interval 5;\n" +
                    "            file \"output_data/" + name + "_voltage.csv\";\n" +
                    "        };\n"

        return (meter + (if ("" == player) "" else (player + recorder)))
    }

    def emit_slack(name: String, voltage: Double): String =
    {
        if (gridlabd.USE_ONE_PHASE)
            "\n" +
                "        object meter\n" +
                "        {\n" +
                "            name \"" + name + "\";\n" +
                "            phases AN;\n" +
                "            bustype SWING;\n" +
                "            nominal_voltage " + voltage + "V;\n" +
                "            voltage_A " + voltage + ";\n" +
                "        };\n"
        else
            "\n" +
                "        object meter\n" +
                "        {\n" +
                "            name \"" + name + "\";\n" +
                "            phases ABCN;\n" +
                "            bustype SWING;\n" +
                "            nominal_voltage " + voltage + "V;\n" +
                // the DELTA-GWYE connection somehow introduces a 30° rotation in the phases, so we compensate here:
                "            voltage_A " + voltage + "+30.0d;\n" +
                "            voltage_B " + voltage + "-90.0d;\n" +
                "            voltage_C " + voltage + "+150.0d;\n" +
                "        };\n"
    }

    def make_glm(
        trafokreis: (String, (Array[TData], Option[(Iterable[PowerFeedingNode], Iterable[PreEdge], Iterable[MaxPowerFeedingNodeEEA])])),
        simulation: String,
        start: Calendar,
        finish: Calendar,
        swing_node: String): Tuple2[String, Array[Experiment]] =
    {
        val starting_transformers = trafokreis._2._1
        val precalc_results = trafokreis._2._2.get
        val traced_nodes = precalc_results._1
        val traced_edges = precalc_results._2
        def significant (h: MaxPowerFeedingNodeEEA): Boolean =
        {
            (h.max_power_feeding > 1000.0) // don't do houses where we already know it's less than a kilowatt
        }
        val houses = precalc_results._3.filter (significant)

        // generate experiments
        val window = 3 * 60 // window size in simulated seconds per experiment
        val margin = 1.25 // check up to 25% over the precalculated value
        val step = 10000.0
        val experiments = houses.zipWithIndex.map (
        h ⇒
        {
            val house = gridlabd.has (h._1.id_seq) // the house under test
            val index = h._2.toInt // experiment #
            def limit (d: Double) = math.ceil (d * margin / step) * step // limit as ceiling(d*margin%) in thousands
            val max = limit (h._1.max_power_feeding) // upper kilowatt limit to test
            val interval = 5 // seconds per step
            val steps = window / interval - 2 // total possible number of steps in the experiment (need 0 input on both ends, hence -2)
            val riser = if (steps * step >= max) step else math.ceil (max / steps / step) * step // limit as ceiling(minimum step size) in thousands
            Experiment (simulation, house, start, index, window, interval, 0, max, riser) // in 5 second intervals go from 0 to max in steps of <1000>
        }).toArray

        // GridLAB-D doesn't understand parallel admittance paths, so we have to do it
        val combined_edges = traced_edges.groupBy(_.key).values

        // get one of each type of ACLineSegment and emit a configuration for each of them
        val line = new Line(gridlabd.USE_ONE_PHASE)

        val l_strings = line.getACLineSegmentConfigurations(combined_edges)

        // get the transformer configuration
        val trans = new Trans(gridlabd.USE_ONE_PHASE, gridlabd.USE_TOPOLOGICAL_NODES)
        val t_string = trans.getTransformerConfigurations(starting_transformers)
        val o_string = emit_slack(swing_node, starting_transformers(0).voltage0 * 1000)

        // get the node strings
        val n_strings = traced_nodes.map(make_node(experiments))
        val pv_strings = houses.filter(_.eea != null).map(make_pv)

        // get the edge strings
        val t_edges = trans.emit(starting_transformers)
        val l_edges = combined_edges.map(make_link(line, new SwitchDevice(gridlabd.USE_ONE_PHASE)))

        /**
         * Create the output file.
         */
        val t1 = start.clone().asInstanceOf[Calendar]
        t1.add(Calendar.SECOND, experiments.length * window)
        val prefix =
            "// $Id: " + simulation + ".glm\n" +
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
                "            starttime '" + gridlabd._DateFormat.format(start.getTime()) + "';\n" +
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
                "            filename \"output_data/" + simulation + "_voltdump.csv\";\n" +
                "            mode polar;\n" +
                "            runtime '" + gridlabd._DateFormat.format(start.getTime()) + "';\n" +
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

        return ((result.toString(), experiments))
    }

    def generate_player_file(experiments: Array[Experiment], name: String, voltage: Double): String =
    {
        val house = gridlabd.has(name)
        val filtered = experiments.filter(p ⇒ p.house == house)
        val experiment = if (0 != filtered.length) filtered(0) else null

        if (null != experiment) {
            if (gridlabd.USE_ONE_PHASE) {
                writeInputFile(experiment.trafo, "input_data/" + house + ".csv", ramp_up(experiment, 0.0))
                "\n" +
                    "        object load\n" +
                    "        {\n" +
                    "            name \"" + name + "_load\";\n" +
                    "            parent \"" + name + "\";\n" +
                    "            phases AN;\n" +
                    "            nominal_voltage " + voltage + "V;\n" +
                    "            object player\n" +
                    "            {\n" +
                    "                property \"constant_power_A\";\n" +
                    "                file \"input_data/" + house + ".csv\";\n" +
                    "            };\n" +
                    "        };\n"
            }
            else {
                val r_phase = 0.0
                val s_phase = 240.0
                val t_phase = 120.0
                writeInputFile(experiment.trafo, "input_data/" + house + "_R.csv", ramp_up(experiment, r_phase))
                writeInputFile(experiment.trafo, "input_data/" + house + "_S.csv", ramp_up(experiment, s_phase))
                writeInputFile(experiment.trafo, "input_data/" + house + "_T.csv", ramp_up(experiment, t_phase))
                "\n" +
                    "        object load\n" +
                    "        {\n" +
                    "            name \"" + name + "_load\";\n" +
                    "            parent \"" + name + "\";\n" +
                    "            phases ABCN;\n" +
                    "            nominal_voltage " + voltage + "V;\n" +
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
                    "            };\n" +
                    "        };\n"
            }
        }
        else
            ""
    }

    def ramp_up(exp: Experiment, angle: Double): Array[Byte] =
    {
        val ret = new StringBuilder()
            def addrow(time: Calendar, power: Double, angle: Double) =
                {
                    ret.append(gridlabd._DateFormat.format(time.getTime()))
                    ret.append(",")
                    if (gridlabd.USE_ONE_PHASE) {
                        ret.append(-power)
                        ret.append("\n")
                    }
                    else {
                        ret.append(-power / 3) // negative load injects power, 1/3 per phase
                        ret.append("<")
                        ret.append(angle)
                        ret.append("d\n")
                    }
                    time.add(Calendar.SECOND, exp.interval)
                }
        val time = exp.t1
        addrow(time, 0.0, angle) // gridlab extends the first and last rows till infinity -> make them zero
        var power = exp.from
        while (power < exp.to) {
            addrow(time, power, angle)
            power = power + exp.step
        }
        addrow(time, 0.0, angle) // gridlab extends the first and last rows till infinity -> make them zero

        return (ret.toString().getBytes(StandardCharsets.UTF_8))
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