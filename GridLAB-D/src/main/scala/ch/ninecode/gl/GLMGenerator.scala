package ch.ninecode.gl

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

import ch.ninecode.model.Switch

class GLMGenerator (one_phase: Boolean, date_format: SimpleDateFormat) extends Serializable
{

    val use_utc = date_format.getTimeZone == TimeZone.getTimeZone("UTC")
    /**
     * Get the name of the generated GLM file
     * @return The unique name for the generated file and directory structure.
     */
    def name: String = "gridlabd"

    def start_time (): Calendar = ???

    def finish_time (): Calendar = ???

    def edge_groups: Iterable[Iterable[PreEdge]] = List(List[PreEdge]())

    def transformers: Array[TData] = Array()

    def swing_node: String = ""

    def swing_node_voltage: Double = 0.0

    def nodes: Iterable[PowerFeedingNode] = List()

    def extra_nodes: Iterable[MaxPowerFeedingNodeEEA] = List ()

    def emit_extra_node (node: MaxPowerFeedingNodeEEA): String = ???

    def tzString: String =
    {
        // "CET-1CEST"
        val t = Calendar.getInstance()
        val tz = t.getTimeZone
        // ToDo: fractional hour time zones
        tz.getDisplayName(false, TimeZone.SHORT) + (-tz.getOffset(t.getTimeInMillis) / 60 / 60 / 1000) + tz.getDisplayName(true, TimeZone.SHORT)
    }

    // emitting classes
    val line = new Line (one_phase)
    val trans = new Trans (one_phase)
    val switch = new SwitchDevice (one_phase)

    def nis_number (string: String): String =
    {
        val n = string.indexOf("_")
        if (0 < n)
            string.substring(0, n)
        else
            string
    }

    def gather (rdd: Iterable[String]): String =
    {
        rdd.fold("")((x: String, y: String) ⇒ if ("" == x) y else x + y)
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

    def emit_node (node: PowerFeedingNode): String =
    {
        "\n" +
        "        object meter\n" +
        "        {\n" +
        "            name \"" + node.id_seq + "\";\n" +
        "            phases " + (if (one_phase) "AN" else "ABCN") + ";\n" +
        "            bustype PQ;\n" +
        "            nominal_voltage " + node.voltage + "V;\n" +
        "        };\n"
    }

    def emit_slack (name: String, voltage: Double): String =
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

    def make_glm (): String =
    {
        // GridLAB-D doesn't understand parallel admittance paths, so we have to do it
        val combined_edges = edge_groups

        // get a configuration for each type of ACLineSegment
        val l_strings = line.getACLineSegmentConfigurations (combined_edges)

        // get the transformer configurations
        val t_string = trans.getTransformerConfigurations (transformers)

        // emit the swing node
        val o_string = emit_slack (swing_node, swing_node_voltage)

        // get the node strings
        val n_strings = nodes.map (emit_node)

        val pv_strings = extra_nodes.map (emit_extra_node)

        // get the edge strings
        val t_edges = trans.emit (transformers)
        val l_edges = combined_edges.map (make_link)

        /**
         * Create the output file.
         */
        val t0 = start_time
        val t1 = finish_time
        val prefix =
            "// $Id: " + name + ".glm\n" +
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
                "            timezone " + (if (use_utc) "UTC0UTC" else tzString) + ";\n" +
                "            starttime '" + date_format.format (t0.getTime ()) + "';\n" +
                "            stoptime '" + date_format.format (t1.getTime ()) + "';\n" +
                "        };\n" +
                "\n" +
                "        class player\n" +
                "        {\n" +
                "            complex value;\n" +
                "        };\n" +
                "\n" +
                "        object voltdump\n" +
                "        {\n" +
                "            filename \"output_data/" + name + "_voltdump.csv\";\n" +
                "            mode polar;\n" +
                "            runtime '" + date_format.format (t0.getTime ()) + "';\n" +
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
}