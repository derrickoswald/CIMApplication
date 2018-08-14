package ch.ninecode.sc

import java.text.SimpleDateFormat
import java.util.Calendar

import ch.ninecode.gl._

case class ScGLMGenerator (
    one_phase: Boolean,
    temperature: Double,
    date_format: SimpleDateFormat,
    area: SimulationTransformerServiceArea)
extends GLMGenerator (one_phase, temperature, date_format)
{
    override def name: String = area.name

    override def directory: String = area.directory

    override def start_time: Calendar = area.start_time

    override def edges: Iterable[GLMEdge] = area.edges

    override def transformers: Array[TransformerSet] = Array(area.transformer)

    override def swing_nodes: Iterable[GLMNode] = area.swing_nodes

    override def finish_time: Calendar = area.finish_time

    override def nodes: Iterable[GLMNode] = area.nodes

    override def extra: Iterable[String] = List ("")

    val experiments: Array[ScExperiment] = area.experiments

    /**
     * Emit the swing node(s).
     * Override to emit EuivalentInjection as a line with appropriate impedance.
     *
     * @param node The swing node to emit.
     * @return The .glm file text for the swing bus.
     */
    override def emit_slack (node: GLMNode): String =
    {
        val voltage = node.nominal_voltage
        val phase = if (one_phase) "AN" else "ABCN"

        val swing =
          """
            |        object meter
            |        {
            |            name "N5";
            |            phases %s;
            |            bustype SWING;
            |            nominal_voltage %sV;
            |            voltage_A %s;
            |        };
            |""".stripMargin.format (phase, voltage, voltage)

        val z = area.transformer.network_short_circuit_impedance
        val line = LineEdge ("N5", node.id, List())
        val config = line.make_line_configuration ("N5_configuration", z.re, z.im, 0.0, 0.0, this)

        val name = node.id
        val cable =
          """
            |        object overhead_line
            |        {
            |            name "HV";
            |            phases %s;
            |            from "N5";
            |            to "%s";
            |            length 1000m;
            |            configuration "N5_configuration";
            |        };
            |""".stripMargin.format (phase, name)

        val meter =
          """
            |        object meter
            |        {
            |            name "%s";
            |            phases %s;
            |            bustype PQ;
            |            nominal_voltage %sV;
            |        };
            |""".stripMargin.format (name, phase, voltage)

        swing +
        config +
        cable +
        meter
    }

    /**
     * Emit one GridLAB-D node.
     *
     * Override to emit a meter object for the node and if it's a house then players and recorders too.
     *
     * @param node The node element.
     * @return The .glm file text for the node.
     */
    override def emit_node (node: GLMNode): String =
    {
        val meter = super.emit_node (node)
        val id = node.id
        val players_and_recorders = experiments.find (_.mrid == id) match
        {
            case Some (experiment) ⇒
                val phase = if (one_phase) "AN" else "ABCN"
                val load =
                    """
                    |        object load
                    |        {
                    |            name "%s_load";
                    |            parent "%s";
                    |            phases %s;
                    |            nominal_voltage %sV;
                    |            object player
                    |            {
                    |                property "constant_impedance_A";
                    |                file "input_data/%s.csv";
                    |            };
                    |        };
                    |""".stripMargin.format (id, id, phase, node.nominal_voltage, id)
                val recorder1 =
                    """
                    |        object recorder
                    |        {
                    |            name "%s_voltage_recorder";
                    |            parent "%s";
                    |            property voltage_A.real,voltage_A.imag;
                    |            interval 5;
                    |            file "output_data/%s_voltage.csv";
                    |        };
                    |""".stripMargin.format (id, id, id)
                load + recorder1

            case None ⇒ ""
        }
        meter + players_and_recorders
    }

    /**
     * Emit one GridLAB-D edge.
     *
     * Generate the text for an edge.
     *
     * @param edge The edge to emit.
     * @return The .glm file text for the edge.
     */
    override def emit_edge (edge: GLMEdge): String =
    {
        val link = super.emit_edge (edge)
        val recorders =
            """
            |        object recorder
            |        {
            |            name "%s_%s_current_recorder";
            |            parent "%s";
            |            property current_in_A.real,current_in_A.imag,flow_direction;
            |            interval 5;
            |            file "output_data/%s%%%s_current.csv";
            |        };
            |
            |        object recorder
            |        {
            |            name "%s_%s_current_recorder";
            |            parent "%s";
            |            property current_out_A.real,current_out_A.imag,flow_direction;
            |            interval 5;
            |            file "output_data/%s%%%s_current.csv";
            |        };
            """.stripMargin.format (edge.cn1, edge.id, edge.id, edge.cn1, edge.id, edge.cn2, edge.id, edge.id, edge.cn2, edge.id)
        link + recorders
    }

    /**
     * Emit one transformer edge.
     *
     * @param transformer The transformer specifics.
     * @return The .glm file text for the transformer.
     */
    override def emit_transformer (transformer: TransformerSet): String =
    {
        val t = super.emit_transformer (transformer)
        val recorder =
            """
              |        object recorder
              |        {
              |            name "%s_%s_current_recorder";
              |            parent "%s";
              |            property current_out_A.real,current_out_A.imag,flow_direction;
              |            interval 5;
              |            file "output_data/%s%%%s_current.csv";
              |        };
            """.stripMargin.format (transformer.node1, transformer.transformer_name, transformer.transformer_name, transformer.node1, transformer.transformer_name)
        t + recorder
    }
}
