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
 * @param date_format The date format to use within the .glm file
 * @param emit_voltage_dump if <code>true</code> add a voltage dump element to the .glm prefix text
 * @param emit_impedance_dump if <code>true</code> add a impedance dump element to the .glm prefix text
 */
class GLMGenerator (
    one_phase: Boolean = true,
    date_format: SimpleDateFormat =
    {
        val format = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss z")
        format.setTimeZone (TimeZone.getTimeZone ("UTC"))
        format
    },
    emit_voltage_dump: Boolean = false,
    emit_impedance_dump: Boolean = false)
extends
    Serializable
{
    /**
     * The name of the generated GLM file.
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
     * It can include a node voltage dump at time t0 and an impedance dump if the constructor boolean values are set.
     *
     * @return The text for the .glm file header.
     */
    def prefix: String =
    {
        val t0 = date_format.format (start_time.getTime)
        val t1 = date_format.format (finish_time.getTime)
        val preamble =
          """// %s.glm
            |// %s
            |//*********************************************
            |
            |        module tape;
            |
            |        module powerflow
            |        {
            |            solver_method NR;
            |            default_maximum_voltage_error 10e-6;
            |            NR_iteration_limit 5000;
            |            NR_superLU_procs 16;
            |            nominal_frequency 50;
            |        };
            |
            |        clock
            |        {
            |            timezone "%s";
            |            starttime "%s";
            |            stoptime "%s";
            |        };
            |
            |        class player
            |        {
            |            complex value;
            |        };
            |""".stripMargin.format (name, header, tzString, t0, t1)
        val voltage_dump =
            if (emit_voltage_dump)
                """
                  |        object voltdump
                  |        {
                  |            filename "output_data/%s_voltdump.csv";
                  |            mode polar;
                  |            runtime "%s";
                  |        };
                  |""".stripMargin.format (name, t0)
            else
                ""
        val impedance_dump =
            if (emit_impedance_dump)
                """
                  |        object impedance_dump
                  |        {
                  |            filename "output_data/%s_impedancedump.xml";
                  |        };
                  |""".stripMargin.format (name)
            else
                ""

        preamble +
        voltage_dump +
        impedance_dump
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
        if ((date_format.getTimeZone == TimeZone.getTimeZone("UTC")) || (date_format.getTimeZone == TimeZone.getTimeZone("GMT")))
            "UTC0UTC"
        else
        {
            val t = date_format.getCalendar
            val tz = t.getTimeZone
            tz.getDisplayName (false, TimeZone.SHORT) + (-tz.getOffset (t.getTimeInMillis) / 60 / 60 / 1000) + tz.getDisplayName (true, TimeZone.SHORT)
        }
    }

    // emitting classes
    var line = new Line (one_phase)
    var trans = new Trans (one_phase)
    var switch = new SwitchDevice (one_phase)

    /**
     * Combine the iterable over strings into one string.
     *
     * Note: This is very inefficient for large numbers of text strings.
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
            case _ ⇒ // by default, make a link
              """
                |        object link
                |        {
                |            name "%s";
                |            phases %s;
                |            from "%s";
                |            to "%s";
                |        };
                |""".stripMargin.format (edge.id, if (one_phase) "AN" else "ABCN", edge.cn1, edge.cn2)
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
        """
        |        object meter
        |        {
        |            name "%s";
        |            phases %s;
        |            bustype PQ;
        |            nominal_voltage %sV;
        |        };
        |""".stripMargin.format (node.id, if (one_phase) "AN" else "ABCN", node.nominal_voltage)
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
        val swing =
            if (one_phase)
                """            voltage_A %s;
                  |""".stripMargin.format (voltage)
            else
                // the DELTA-GWYE connection somehow introduces a 30° rotation in the phases, so we compensate here:
                """            voltage_A %s+30.0d;
                  |            voltage_B %s-90.0d;
                  |            voltage_C %s+150.0d;
                  |""".stripMargin.format (voltage, voltage, voltage)

        """
        |        object meter
        |        {
        |            name "%s";
        |            phases %s;
        |            bustype SWING;
        |            nominal_voltage %sV;
        |%s
        |        };
        |""".stripMargin.format (name, if (one_phase) "AN" else "ABCN", voltage, swing)

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