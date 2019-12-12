package ch.ninecode.sc

import java.text.SimpleDateFormat
import java.util.Calendar

import ch.ninecode.gl._

/**
 *
 * @param one_phase   If <code>true</code> generate a single phase .glm file.
 * @param temperature The temperature of the elements in the .glm file (°C).
 * @param date_format The date format to use within the .glm file.
 * @param area        The area over which the simulation is to extend.
 * @param isMax       If <code>true</code> generate a .glm file for maximum currents (low impedance) [for motor starting currents], otherwise minimum currents (high impedance) [for fuse sizing and specificity].
 */
case class ScGLMGenerator
(
    one_phase: Boolean,
    temperature: Double,
    date_format: SimpleDateFormat,
    area: SimulationTransformerServiceArea,
    isMax: Boolean)
    extends GLMGenerator (one_phase, temperature, date_format)
{
    override def name: String = area.name

    override def directory: String = area.directory

    override def start_time: Calendar = area.start_time

    override def edges: Iterable[GLMEdge] = area.edges

    override def getTransformerConfigurations (transformers: Iterable[TransformerEdge]): Iterable[String] =
    {
        val subtransmission_trafos = edges.filter (edge => edge match { case _: TransformerEdge => true case _ => false }).asInstanceOf[Iterable[TransformerEdge]]
        val trafos = transformers ++ subtransmission_trafos
        trafos.groupBy (_.configurationName).values.map (_.head.configuration (this))
    }

    override def transformers: Iterable[TransformerEdge] = List (TransformerEdge (area.transformer.node0, area.transformer.node1, area.transformer))

    override def swing_nodes: Iterable[GLMNode] = area.swing_nodes

    override def finish_time: Calendar = area.finish_time

    override def nodes: Iterable[GLMNode] = area.nodes

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
        val z = if (isMax)
            area.transformer.network_short_circuit_impedance_max
        else
            area.transformer.network_short_circuit_impedance_min
        val nodename = node.id

        // if the network short circuit impedance isn't 0Ω, we have to invent a cable
        if (z != Complex (0))
        {
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

            val line = LineEdge ("N5", node.id, List ())
            val config = line.make_line_configuration ("N5_configuration", z.re, z.im, 0.0, 0.0, this)
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
                  |""".stripMargin.format (phase, nodename)

            val meter =
                """
                  |        object meter
                  |        {
                  |            name "%s";
                  |            phases %s;
                  |            bustype PQ;
                  |            nominal_voltage %sV;
                  |        };
                  |""".stripMargin.format (nodename, phase, voltage)

            swing +
                config +
                cable +
                meter
        }
        else
            """
              |        object meter
              |        {
              |            name "%s";
              |            phases %s;
              |            bustype SWING;
              |            nominal_voltage %sV;
              |            voltage_A %s;
              |        };
              |""".stripMargin.format (nodename, phase, voltage, voltage)
    }

    /**
     * Emit one GridLAB-D node.
     *
     * Override to emit a meter object for the node and if it is a house then a player too.
     *
     * @param node The node element.
     * @return The .glm file text for the node.
     */
    override def emit_node (node: GLMNode): String =
    {
        val meter = super.emit_node (node)
        val id = node.id
        val player = experiments.find (_.mrid == id) match
        {
            case Some (_) ⇒
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
                load

            case None ⇒ ""
        }
        meter + player
    }

    override def extra: Iterable[String] =
    {
        experiments.map (
            experiment ⇒
                """
                  |        object voltdump
                  |        {
                  |            filename "output_data/%s_voltdump.csv";
                  |            runtime "%s";
                  |        };
                  |""".stripMargin.format (experiment.mrid, date_format.format (experiment.t1.getTime))
        )
    }
}
