package ch.ninecode.sc

import java.text.SimpleDateFormat
import java.util.Calendar

import ch.ninecode.gl._

case class ScGLMGenerator (
    one_phase: Boolean,
    date_format: SimpleDateFormat,
    area: SimulationTransformerServiceArea) extends GLMGenerator (one_phase, date_format)
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
}
