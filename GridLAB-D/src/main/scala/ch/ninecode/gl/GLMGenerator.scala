package ch.ninecode.gl

import java.text.SimpleDateFormat
import java.util.{Calendar, TimeZone}

import ch.ninecode.model.Switch

/**
 * The .glm file generator.
 *
 * Base class for .glm export. The derived classes must provide specific
 * details for the actual export to be valid, such as edges, nodes, swing node,
 * simulation start and stop times, etc.
 *
 * @param one_phase If <code>true</code> generate a single phase .glm file.
 * @param date_format The date format to use in
 */
class GLMGenerator (one_phase: Boolean, date_format: SimpleDateFormat) extends Serializable
{
    /**
     * The internal name of the generated GLM file.
     *
     * Currently, this name is used as metadata for a file name within the generated .glm file,
     * and as the hard-coded name of a voltage dump file at t0 (the start of simulation).
     *
     * This metadata file name is an erroneous holdover from a CVS keyword expansion of \$Id\$ observed
     * in supplied .glm samples, and therefore should be removed.
     *
     * Unfortunately, other code in uses this name as the name of the directory in which to
     * save the .glm file and as the name of the .glm file ganerated.
     *
     * @todo Remove this def and the \$Id: name comment from the header.
     *
     * @return The unique name for the generated file and directory structure.
     */
    def name: String = "gridlabd"

    /**
     * The directory to place the simulation files in.
     * Defaults to the name of the generated GLM file.
     *
     * @return The (relative) directory to use for files (glm, players, recorders).
     */
    def directory: String = name

    /**
     * The .glm header comment.
     *
     * @return A single line of text for the comment header.
     */
    def header: String = "GridLAB-D"

    /**
     * Simulation clock start time.
     *
     * Beginning time for the GridLAB-d simulation run.
     *
     * @return The date and time of the simulation start with time zone.
     */
    def start_time: Calendar = javax.xml.bind.DatatypeConverter.parseDateTime ("2017-05-08T12:00:00")

    /**
     * Simulation clock stop time.
     *
     * Ending time for the GridLAB-d simulation run.
     * Default is the same as the start time.
     *
     * @return The date and time of the simulation finish with time zone.
     */
    def finish_time: Calendar = start_time

    /**
     * Generate the .glm header.
     *
     * This includes the meta-data comments, tape and powerflow modules, clock, and player definitions.
     *
     * It also currently includes a node voltage dump at time t0.
     *
     * @todo Remove name_voltdump.csv.
     *
     * @return The text for the .glm file header.
     */
    def prefix: String =
    {
        val t0 = start_time
        val t1 = finish_time

        "// $Id: " + name + ".glm\n" +
        "// " + header + "\n" +
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
        "            timezone \"" + tzString + "\";\n" +
        "            starttime \"" + date_format.format (t0.getTime) + "\";\n" +
        "            stoptime \"" + date_format.format (t1.getTime) + "\";\n" +
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
        "            runtime \"" + date_format.format (t0.getTime) + "\";\n" +
        "        };\n"
    }

    /**
     * The edges to include in the .glm file.
     *
     * The edges may have parallel elements, and thus this is a "list of lists"
     * in the form of the generic Scala trait Iterable for iterable collections.
     *
     * Only edges defined by this method are exported, so it is important to include
     * exactly the edges required by the network, no more, no less.
     *
     * @return The edges to be included in the export.
     */
    def edge_groups: Iterable[Iterable[GLMEdge]] = List(List[GLMEdge]())

    /**
     * The nodes to be included in the .glm file.
     *
     * @return The ConnectivityNode or TopologicalNode elements to include in the export.
     */
    def nodes: Iterable[GLMNode] = List()

    /**
     * Details about transformers used in the edges list.
     *
     * Transformer data is very rich and hence cannot be contained in a simple edge.
     * This method provides a richer set of details about the transformers in the network.
     * The link is between the GLMEdge.edge.id and TData.transformer.id.
     *
     * @return The details about pertinent transformers.
     */
    def transformers: Array[TransformerSet] = Array()

    /**
     * The ID of the SWING or slack bus nodes and their voltages.
     *
     * The swing bus is used to provide for system losses by emitting or absorbing active and/or reactive power
     * to and from the system, acting as an infinite source or sink.
     *
     * @return The id and voltage of the node or nodes that are a swing bus (or slack bus).
     */
    def swing_nodes: Iterable[GLMNode] = List()

    /**
     * Additional text to add to the .glm file.
     *
     * This is sort of an escape hatch to provide the means to inject arbitrary lines of text into the
     * exported .glm file.
     *
     * @return Text to add to the .glm file.
     */
    def extra: Iterable[String] = List()

    /**
     * Time zone.
     *
     * Form the time zone into an appropriate string, e.g. "CET-1CEST".
     *
     * @todo fractional hour time zones
     *
     * @return A formatted time zone string.
     */
    def tzString: String =
    {
        if (date_format.getTimeZone == TimeZone.getTimeZone("UTC"))
            "UTC0UTC"
        else
        {
            val t = Calendar.getInstance
            val tz = t.getTimeZone
            tz.getDisplayName (false, TimeZone.SHORT) + (-tz.getOffset (t.getTimeInMillis) / 60 / 60 / 1000) + tz.getDisplayName (true, TimeZone.SHORT)
        }
    }

    /**
     * Get the NIS database id of a node.
     *
     * The NIS id is suffixed with "_node" or "_topo", and this just gets the prefix.
     * For example "ABG123401_node" is converted to "ABG123401".
     *
     * @param string The node id to split.
     * @return The NIS number from the node id.
     */
    def nis_number (string: String): String =
    {
        val n = string.indexOf ("_")
        if (0 < n)
            string.substring (0, n)
        else
            string
    }

    // emitting classes
    val line = new Line (one_phase)
    val trans = new Trans (one_phase)
    val switch = new SwitchDevice (one_phase)

    /**
     * Combine the iterable over strings into one string.
     *
     * Note: This is very inifficient for large numbers of text strings.
     *
     * @param rdd The elements to gather.
     * @return The combined string.
     */
    def gather (rdd: Iterable[String]): String =
    {
        rdd.fold("")((x: String, y: String) ⇒ if ("" == x) y else x + y)
    }

    /**
     * Emit one GridLAB-D edge.
     *
     * Generate the text for an edge.
     * Uses the Line and SwitchDevice handlers to create the text,
     * using possibly parallel edges for lines but only the head element for switch edges.
     * Transformers are handled separately.
     *
     * @param edges The edge element(s).
     * @return The .glm file text for the edge.
     */
    def emit_edge (edges: Iterable[GLMEdge]): String =
    {
        val edge = edges.head
        val cls = edge.el.getClass.getName
        val clazz = cls.substring (cls.lastIndexOf(".") + 1)
        clazz match {
            case "ACLineSegment" ⇒
                line.emit (edges)
            case "PowerTransformer" ⇒
                "" // handled specially
            case "Switch" ⇒
                switch.emit (edge, edge.el.asInstanceOf[Switch])
            case "Cut" |
                "Disconnector" |
                "GroundDisconnector" |
                "Jumper" |
                "ProtectedSwitch" |
                "Sectionaliser" ⇒
                switch.emit (edge, edge.el.sup.asInstanceOf[Switch])
            case "Breaker" |
                "LoadBreakSwitch" |
                "Recloser" ⇒
                switch.emit (edge, edge.el.sup.sup.asInstanceOf[Switch])
            case "Fuse" ⇒
                switch.emit (edge, edge.el.sup.asInstanceOf[Switch], fuse = true)
            case _ ⇒
                // by default, make a link
                "\n" +
                "        object link\n" +
                "        {\n" +
                "            name \"" + edge.id + "\";\n" +
                "            phases " + (if (one_phase) "AN" else "ABCN") + ";\n" +
                "            from \"" + edge.cn1 + "\";\n" +
                "            to \"" + edge.cn2 + "\";\n" +
                "        };\n"
        }
    }

    /**
     * Emit one GridLAB-D node.
     *
     * Emits a meter object for the node.
     *
     * @param node The node element.
     * @return The .glm file text for the node.
     */
    def emit_node (node: GLMNode): String =
    {
        "\n" +
        "        object meter\n" +
        "        {\n" +
        "            name \"" + node.id + "\";\n" +
        "            phases " + (if (one_phase) "AN" else "ABCN") + ";\n" +
        "            bustype PQ;\n" +
        "            nominal_voltage " + node.nominal_voltage + "V;\n" +
        "        };\n"
    }

    /**
     * Emit the swing node(s).
     *
     * @param node The swing node to emit.
     * @return The .glm file text for the swing bus.
     */
    def emit_slack (node: GLMNode): String =
    {
        val name = node.id
        val voltage = node.nominal_voltage
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

    def emit_transformer (transformer: TransformerSet): String =
    {
        trans.emit (transformer)
    }

    /**
     * Combine all .glm pieces into a complete network.
     *
     * Combines the header, transformer and line configurations, swing bus, nodes edges and any extra text
     * into the complete .glm text.
     *
     * @return The .glm file contents ready for saving.
     */
    def make_glm (): String =
    {
        // GridLAB-D doesn't understand parallel admittance paths, so we have to do it
        val combined_edges = edge_groups

        // get the transformer configurations
        val t_string = trans.getTransformerConfigurations (transformers)

        // get a configuration for each type of ACLineSegment
        val l_strings = line.getACLineSegmentConfigurations (combined_edges)

        // emit the swing node
        val o_strings = swing_nodes.map (emit_slack)

        // get the node strings
        val n_strings = nodes.map (emit_node)

        // get the transformer strings
        val t_edges = transformers.map (emit_transformer)

        // get the edge strings
        val l_edges = combined_edges.map (emit_edge)

        // get the extra strings
        val e_strings = extra

        // create the output file.
        val result = new StringBuilder()
        result.append (prefix)
        result.append (gather (t_string))
        result.append (gather (l_strings))
        result.append (gather (o_strings))
        result.append (gather (n_strings))
        result.append (gather (t_edges))
        result.append (gather (l_edges))
        result.append (gather (e_strings))

        result.toString
    }
}