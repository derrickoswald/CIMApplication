package ch.ninecode.sc

import java.text.SimpleDateFormat
import java.util.Calendar

import ch.ninecode.gl._

case class ScGLMGenerator (
    one_phase: Boolean,
    date_format: SimpleDateFormat,
    area: SimulationTransformerServiceArea)
extends
    GLMGenerator (one_phase, date_format)
{
    override def name: String = area.name

    override def directory: String = area.directory

    override def start_time: Calendar = area.start_time

    override def edge_groups: Iterable[Iterable[GLMEdge]] = area.edges

    override def transformers: Array[TransformerSet] = Array(area.transformer)

    override def swing_nodes: Iterable[GLMNode] = area.swing_nodes

    override def finish_time: Calendar = area.finish_time

    override def nodes: Iterable[GLMNode] = area.nodes

    override def extra: Iterable[String] = List ("")

    val experiments: Array[ScExperiment] = area.experiments

    // override to set current_limit to 9999.0A
    switch = new ScSwitchDevice (one_phase)

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
        val config = line.make_line_configuration ("N5_configuration", z.re, z.im, 0.0, 0.0)

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
        val players_and_recorders = if (experiments.map (_.house).contains (id))
        {
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
                """.stripMargin.format (id, id, phase, node.nominal_voltage, id)
            val recorder1 =
                """
                  |
                  |        object recorder
                  |        {
                  |            name "%s_voltage_recorder";
                  |            parent "%s";
                  |            property voltage_A.real,voltage_A.imag;
                  |            interval 5;
                  |            file "output_data/%s_voltage.csv";
                  |        };
                """.stripMargin.format (id, id, id)
            // this will normally find the house fuse, but for other CIM files it could be an overhead or underground line
            val lines = area.edges.flatMap (x ⇒ x.filter (y ⇒ (y.cn1 == id) || (y.cn2 == id)))
            // if there are more than two edges (one in, one out) the current in the head element might not be the total current
            val warn = if (lines.size > 2)
                """#warning WARNING: node %s has more than 2 edges (%s), current recorder %s_current_recorder using %s may be incorrect""".format (id, lines.map (_.id).mkString (","), id, lines.head.id)
            else
                ""
            val line = lines.head.id
            val recorder2 =
                """
                |%s
                |        object recorder
                |        {
                |            name "%s_current_recorder";
                |            parent "%s";
                |            property current_out_A.real,current_out_A.imag;
                |            interval 5;
                |            file "output_data/%s_current.csv";
                |        };
                """.stripMargin.format (warn, id, line, id)
            load + recorder1 + recorder2
        }
        else
            ""
        meter + players_and_recorders
    }
}
