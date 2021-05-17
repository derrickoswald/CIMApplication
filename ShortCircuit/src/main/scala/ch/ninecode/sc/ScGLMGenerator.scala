package ch.ninecode.sc

import java.text.SimpleDateFormat
import java.util.Calendar

import ch.ninecode.gl.GLMEdge
import ch.ninecode.gl.GLMGenerator
import ch.ninecode.gl.GLMLineEdge
import ch.ninecode.gl.GLMNode
import ch.ninecode.gl.GLMTransformerEdge
import ch.ninecode.gl.SwingNode
import ch.ninecode.model.ACLineSegment
import ch.ninecode.model.BasicElement
import ch.ninecode.model.ConductingEquipment
import ch.ninecode.model.Conductor
import ch.ninecode.model.Equipment
import ch.ninecode.model.IdentifiedObject
import ch.ninecode.model.PowerSystemResource
import ch.ninecode.model.Terminal
import ch.ninecode.net._
import ch.ninecode.util._

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
    extends GLMGenerator(one_phase, temperature, date_format)
{
    override def name: String = area.name

    override def directory: String = area.directory

    override def start_time: Calendar = area.start_time

    override def edges: Iterable[GLMEdge] = area.edges

    @SuppressWarnings(Array("org.wartremover.warts.TraversableOps"))
    override def getTransformerConfigurations (transformers: Iterable[GLMTransformerEdge]): Iterable[String] =
    {
        val subtransmission_trafos = edges.flatMap(
            {
                case trafo: GLMTransformerEdge => Some(trafo)
                case _ => None
            }
        )
        val trafos = transformers ++ subtransmission_trafos
        val configurations = trafos.groupBy(_.configurationName).values
        configurations.map(config => config.head.configuration(this, config.map(_.transformer.transformer_name).mkString(", ")))
    }

    override def transformers: Iterable[GLMTransformerEdge] = area.island.transformers.map(GLMTransformerEdge)

    class ShortCircuitSwingNode (val set: TransformerSet) extends SwingNode(set.node0, set.v0, set.transformer_name)

    override def swing_nodes: Iterable[GLMNode] = area.island.transformers.map(new ShortCircuitSwingNode(_))

    override def finish_time: Calendar = area.finish_time

    override def nodes: Iterable[GLMNode] = area.nodes

    val experiments: Array[ScExperiment] = area.experiments

    /**
     * Emit the swing node(s).
     * Override to emit EquivalentInjection as a line with appropriate impedance.
     *
     * @param node The swing node to emit.
     * @return The .glm file text for the swing bus.
     */
    override def emit_slack (node: GLMNode, suffix: String = ""): String =
    {
        val voltage = node.nominal_voltage
        val phase = if (one_phase) "AN" else "ABCN"
        val nodename = node.id
        val z: Complex = get_per_meter_Z_for(node)

        // if the network short circuit impedance isn't 0Ω, we have to invent a cable
        if (z != Complex(0))
        {
            val network_level = "N5_" + suffix
            val network_level_config_name = network_level + "_configuration"

            val swing = swing_meter_glm(network_level, voltage, phase)
            val config = generate_glm_configs(node, z, network_level, network_level_config_name)
            val cable = overhead_line_glm(phase, network_level, nodename, network_level_config_name, suffix)
            val meter = object_meter_glm(voltage, phase, nodename)

            swing +
                config +
                cable +
                meter
        }
        else
        {
            swing_meter_glm(nodename,voltage,phase)
        }
    }

    private def generate_glm_configs (node: GLMNode, z: Complex, N5_name: String, N5_config_name: String) =
    {
        val mrid = s"_generated_${N5_name}_${node.id}"
        val id = IdentifiedObject(Element = BasicElement(mRID = mrid), mRID = mrid)
        val equipment = Equipment(PowerSystemResource = PowerSystemResource(id))
        val conducting_equipment = ConductingEquipment(Equipment = equipment)
        val l = ACLineSegment(Conductor = Conductor(ConductingEquipment = conducting_equipment))
        val t1 = Terminal(TopologicalNode = N5_name)
        val t2 = Terminal(TopologicalNode = node.id)
        implicit val static_line_details: LineDetails.StaticLineDetails = LineDetails.StaticLineDetails()
        val line = GLMLineEdge(LineData(Iterable(LineDetails(l, t1, t2, None, None))))
        val config = line.make_line_configuration(N5_config_name, Sequences(Complex(z.re, z.im), Complex(0.0)), false, this)
        config
    }

    private def object_meter_glm (voltage: Double, phase: String, nodename: String) =
    {
        """
          |        object meter
          |        {
          |            name "%s";
          |            phases %s;
          |            bustype PQ;
          |            nominal_voltage %sV;
          |        };
          |""".stripMargin.format(nodename, phase, voltage)
    }

    private def overhead_line_glm (phase: String, from: String, to: String, configuration: String, suffix: String): String =
    {
        s"""
           |        object overhead_line
           |        {
           |            name "%s";
           |            phases %s;
           |            from "%s";
           |            to "%s";
           |            length 1000m;
           |            configuration "%s";
           |        };
           |""".stripMargin.format("HV"+"_"+suffix, phase, from, to, configuration)
    }

    private def swing_meter_glm (name:String, voltage: Double, phase: String) =
    {
        s"""
           |        object meter
           |        {
           |            name %s;
           |            phases %s;
           |            bustype SWING;
           |            nominal_voltage %sV;
           |            voltage_A %s;
           |        };
           |""".stripMargin.format(name, phase, voltage, voltage)
    }

    private def get_per_meter_Z_for (node: GLMNode) =
    {
        val _z = node match
        {
            case swing: ShortCircuitSwingNode =>
                val set = swing.set
                if (isMax)
                    set.network_short_circuit_impedance_max
                else
                    set.network_short_circuit_impedance_min
            case _ =>
                Complex(0.0)
        }
        // per length impedance is per meter now
        _z / 1000.0
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
        val meter = super.emit_node(node)
        val id = node.id
        val player = experiments.find(_.mrid == id) match
        {
            case Some(_) =>
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
                      |""".stripMargin.format(id, id, phase, node.nominal_voltage, id)
                load

            case None => ""
        }
        meter + player
    }

    override def extra: Iterable[String] =
    {
        experiments.map(
            experiment =>
                """
                  |        object voltdump
                  |        {
                  |            filename "output_data/%s_voltdump.csv";
                  |            runtime "%s";
                  |        };
                  |""".stripMargin.format(experiment.mrid, date_format.format(experiment.t1.getTime))
        )
    }
}
