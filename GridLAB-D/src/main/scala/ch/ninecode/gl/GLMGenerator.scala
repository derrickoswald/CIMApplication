package ch.ninecode.gl

import java.text.SimpleDateFormat
import java.util.{Calendar, TimeZone}

/**
 * The .glm file generator.
 *
 * Base class for .glm export. The derived classes must provide specific
 * details for the actual export to be valid, such as edges, nodes, swing node,
 * simulation start and stop times, etc.
 *
 * @param one_phase           If <code>true</code> generate a single phase .glm file.
 * @param temperature         The reference temperature of the elements in the CIM file (°C).
 * @param date_format         The date format to use within the .glm file.
 * @param emit_voltage_dump   if <code>true</code> add a voltage dump element to the .glm prefix text
 * @param emit_impedance_dump if <code>true</code> add a impedance dump element to the .glm prefix text
 * @param emit_fault_check    if <code>true</code> add a fault check element to the .glm prefix text
 * @param swing_voltage_factor Factor to apply to the nominal slack voltage, e.g. 1.03 = 103% of nominal.
 */
class GLMGenerator
(
    one_phase: Boolean = true,
    temperature: Double = 60.0,
    date_format: SimpleDateFormat =
    {
        val format = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss z")
        format.setTimeZone (TimeZone.getTimeZone ("UTC"))
        format
    },
    emit_voltage_dump: Boolean = false,
    emit_impedance_dump: Boolean = false,
    emit_fault_check: Boolean = false,
    swing_voltage_factor: Double = 1.0)
    extends Serializable
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
              |            NR_iteration_limit 500;
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
        val fault_check =
            if (emit_fault_check)
                """
                  |        object fault_check
                  |        {
                  |            name fault_check_obj;
                  |            output_filename "output_data/%s_faultcheck.txt";
                  |        };
                  |""".stripMargin.format (name)
            else
                ""

        preamble +
            voltage_dump +
            impedance_dump +
            fault_check
    }

    /**
     * The edges to include in the .glm file.
     *
     * @return The edges to be included in the export.
     */
    def edges: Iterable[GLMEdge] = List ()

    /**
     * The nodes to be included in the .glm file.
     *
     * @return The ConnectivityNode or TopologicalNode elements to include in the export.
     */
    def nodes: Iterable[GLMNode] = List ()

    /**
     * Details about transformers used in the edges list.
     *
     * @return The TransformerSet objects to include in the export.
     */
    def transformers: Iterable[GLMTransformerEdge] = List ()

    /**
     * The ID of the SWING or slack bus nodes and their voltages.
     *
     * The swing bus is used to provide for system losses by emitting or absorbing active and/or reactive power
     * to and from the system, acting as an infinite source or sink.
     *
     * @return The id and voltage of the node or nodes that are a swing bus (or slack bus).
     */
    def swing_nodes: Iterable[GLMNode] = List ()

    /**
     * Additional text to add to the .glm file.
     *
     * This is sort of an escape hatch to provide the means to inject arbitrary lines of text into the
     * exported .glm file.
     *
     * @return Text to add to the .glm file.
     */
    def extra: Iterable[String] = List ()

    /**
     * Time zone.
     *
     * Form the time zone into an appropriate string, e.g. "CET-1CEST".
     *
     * @todo fractional hour time zones
     * @return A formatted time zone string.
     */
    def tzString: String =
    {
        if ((date_format.getTimeZone == TimeZone.getTimeZone ("UTC")) || (date_format.getTimeZone == TimeZone.getTimeZone ("GMT")))
            "UTC0UTC"
        else
        {
            val t = date_format.getCalendar
            val tz = t.getTimeZone
            val hrs = -tz.getOffset (t.getTimeInMillis) / 60 / 60 / 1000
            s"${tz.getDisplayName (false, TimeZone.SHORT)}$hrs${tz.getDisplayName (true, TimeZone.SHORT)}"
        }
    }

    /**
     * Create a one phase .glm file.
     *
     * @return <code>true</code> if there should only be the A phase (and neutral) in the model, <code>false</code> otherwise.
     */
    def isSinglePhase: Boolean = one_phase

    /**
     * Create a .glm file at this target temperature.
     *
     * @return the temperature of the components in the model (°C).
     */
    def targetTemperature: Double = temperature

    /**
     * Emit configurations for all groups of edges that are ACLineSegments.
     *
     * Get one of each type of ACLineSegment and emit a configuration for each of them.
     *
     * @param edges The edges in the model.
     * @return The configuration elements as strings.
     */
    @SuppressWarnings (Array ("org.wartremover.warts.TraversableOps"))
    def getACLineSegmentConfigurations (edges: Iterable[GLMEdge]): Iterable[String] =
    {
        val lines = edges.flatMap (_ match { case line: GLMLineEdge => Some (line); case _ => None })
        lines.groupBy (_.configurationName).values.map (_.head.configuration (this))
    }

    /**
     * Emit configurations for all edges that are PowerTransformers.
     *
     * Get one of each type of TransformerSet and  emit a configuration for each of them.
     *
     * @param transformers The transformers in the model.
     * @return The configuration elements as strings.
     */
    @SuppressWarnings (Array ("org.wartremover.warts.TraversableOps"))
    def getTransformerConfigurations (transformers: Iterable[GLMTransformerEdge]): Iterable[String] =
    {
        val configurations = transformers.groupBy (_.configurationName).values
        configurations.map (config => config.head.configuration (this, config.map (_.transformer.transformer_name).mkString (", ")))
    }

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
        rdd.fold ("")((x: String, y: String) => s"$x$y")
    }

    /**
     * Emit one GridLAB-D edge.
     *
     * Generate the text for an edge.
     *
     * @param edge The edge element.
     * @return The .glm file text for the edge.
     */
    def emit_edge (edge: GLMEdge): String =
    {
        edge.emit (this)
    }

    /**
     * Emit one GridLAB-D node.
     *
     * Generate the text for a node.
     *
     * @param node The node element.
     * @return The .glm file text for the node.
     */
    def emit_node (node: GLMNode): String =
    {
        node.emit (this)
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
        val voltage = node.nominal_voltage * swing_voltage_factor
        val phase = if (one_phase) "AN" else "ABCN"
        val swing =
            if (one_phase)
                s"            voltage_A $voltage;"
            else
            {
                val phase_voltage = voltage / math.sqrt (3.0)
             s"""            voltage_A $phase_voltage+0.0d;
                |            voltage_B $phase_voltage-120.0d;
                |            voltage_C $phase_voltage+120.0d;""".stripMargin
            }

        s"""
        |        object meter
        |        {
        |            name "$name";
        |            phases $phase;
        |            bustype SWING;
        |            nominal_voltage ${node.nominal_voltage}V;
        |$swing
        |        };
        |""".stripMargin

    }

    /**
     * Emit one transformer edge.
     *
     * @param transformer the edge to emit
     * @return The .glm file text for the transformer.
     */
    def emit_transformer (transformer: GLMTransformerEdge): String =
    {
        transformer.emit (this)
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
        // get the transformer configurations
        val t_string = getTransformerConfigurations (transformers)

        // get a configuration for each type of ACLineSegment
        val l_strings = getACLineSegmentConfigurations (edges)

        // emit the swing node
        val o_strings = swing_nodes.map (emit_slack)

        // get the node strings
        val swing_ids = swing_nodes.map (_.id).toSet
        val n_strings = nodes.filter (node => !swing_ids.contains (node.id)).map (emit_node)

        // get the transformer strings
        val t_edges = transformers.map (emit_transformer)

        // get the edge strings
        val l_edges = edges.map (emit_edge)

        // get the extra strings
        val e_strings = extra

        // create the output file.
        val result = new StringBuilder ()
            .append (prefix)
            .append (gather (t_string))
            .append (gather (l_strings))
            .append (gather (o_strings))
            .append (gather (n_strings))
            .append (gather (t_edges))
            .append (gather (l_edges))
            .append (gather (e_strings))

        result.toString
    }
}