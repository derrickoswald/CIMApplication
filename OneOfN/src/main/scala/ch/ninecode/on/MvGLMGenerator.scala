package ch.ninecode.on

import java.text.SimpleDateFormat

import ch.ninecode.gl.Complex
import ch.ninecode.gl.GLMEdge
import ch.ninecode.gl.GLMGenerator
import ch.ninecode.gl.GLMNode
import ch.ninecode.gl.LineEdge
import ch.ninecode.gl.TransformerEdge
import ch.ninecode.gl.TransformerSet

/**
 * Medium voltage GridLAB-D model file exporter.
 *
 * @param one_phase If <code>true</code> generate a single phase .glm file.
 * @param temperature The temperature of the elements in the .glm file (°C).
 * @param date_format The date format to use within the .glm file.
 * @param feeder The elements to be generated.
 */
case class MvGLMGenerator (
    one_phase: Boolean,
    temperature: Double,
    date_format: SimpleDateFormat,
    feeder: FeederArea,
    voltages: collection.Map[String, Double])
extends GLMGenerator (one_phase, temperature, date_format, emit_voltage_dump = true, emit_fault_check = true) // ToDo: get library base temperature and target temperature as command line input
{
    override def name: String = "%s_%s".format (feeder.station, feeder.number)

    override def header: String = feeder.description

    override def edges: Iterable[GLMEdge] = feeder.edges.filter (!_.isInstanceOf[TransformerEdge])

    override def nodes: Iterable[GLMNode] = feeder.nodes.filter (_.feeder == null)

    override def transformers: Iterable[TransformerEdge] = feeder.edges.filter (_.isInstanceOf[TransformerEdge]).map (_.asInstanceOf[TransformerEdge])

    override def swing_nodes: Iterable[GLMNode] = feeder.nodes.filter (_.feeder != null)
        .groupBy (_._id).values.map (_.head) // take only one feeder per node

    def nodelist: Map[String,GLMNode] = feeder.nodes.map (x ⇒ (x._id, x)).toMap

    /**
     * Add meter elements for nodes on the edge of the network.
     *
     * For switches and transformers with one terminal in the feeder network, and the other not,
     * GridLAB-D complains because the referenced node doesn't exist, so we add them
     * here... until a better method can be found.
     *
     * @return Node elements to add to the .glm file.
     */
    override def extra: Iterable[String] =
    {
        def ends_voltages (edge: GLMEdge): Iterable[(String, Double)] =
        {
            edge match
            {
                case line: LineEdge ⇒
                    val v = voltages.getOrElse (line.lines.head.Conductor.ConductingEquipment.BaseVoltage, 0.0)
                    List ((line.cn1, v), (line.cn2, v))
                case switch: PlayerSwitchEdge ⇒
                    val v = voltages.getOrElse (switch.switch.ConductingEquipment.BaseVoltage, 0.0)
                    List ((switch.cn1, v), (switch.cn2, v))
                case transformer: TransformerEdge ⇒
                    List ((transformer.cn1, transformer.transformer.v0), (transformer.cn2, transformer.transformer.v1))
                case edge: GLMEdge ⇒
                    List ((edge.cn1, 0.0), (edge.cn2, 0.0)) // unspecified transformers
            }
        }
        val missing: Iterable[(String, Double)] = feeder.edges.flatMap (ends_voltages) // get the nodes from each edge
            .filter (x ⇒ !nodelist.isDefinedAt (x._1)) // eliminate those that are emitted normally
            .groupBy (_._1).values.map (_.head) // eliminate duplicates from multiple edges
        missing.map (x ⇒ FeederNode (x._1, null, x._2).emit (this))
    }

    def three_or_one (property: String): String =
        if (one_phase)
            "%s_A".format (property)
        else
            "%s_A,%s_B,%s_C".format (property, property, property)

    def three_or_one (value: Complex): String =
        (for (i ← 1 to (if (one_phase) 1 else 3)) yield value.toString).mkString (",")

    override def emit_slack (node: GLMNode): String =
    {
        val swing = node.asInstanceOf[FeederNode]
        """
        |        object meter
        |        {
        |            name "%s_swing";
        |            phases %s;
        |            bustype SWING;
        |            nominal_voltage %sV;
        |            %s %s;
        |        };
        |
        |        object switch
        |        {
        |            name "%s_switch";
        |            phases %s;
        |            from "%s_swing";
        |            to "%s";
        |            status "CLOSED";
        |        };
        |
        |        object meter
        |        {
        |            name "%s";
        |            phases %s;
        |            bustype PQ;
        |            nominal_voltage %sV;
        |        };
        |""".stripMargin.format (
            swing.id, if (one_phase) "AN" else "ABCN", swing.nominal_voltage, three_or_one ("voltage"), three_or_one (Complex (swing.nominal_voltage, 0.0)),
            swing.id, if (one_phase) "AN" else "ABCN", swing.id, swing._id,
            swing._id, if (one_phase) "AN" else "ABCN", swing.nominal_voltage)
    }

    override def emit_transformer (transformer: TransformerEdge): String =
    {
        val name = transformer.transformer.transformer_name
        super.emit_transformer (transformer) +
        """
        |        object load
        |        {
        |            name "%s_load";
        |            parent "%s";
        |            phases %s;
        |            %s %s;
        |            nominal_voltage %sV;
        |            load_class R;
        |        };
        """.stripMargin.format (name, transformer.cn2, if (one_phase) "AN" else "ABCN", three_or_one ("constant_power"), three_or_one (Complex (10000, 0)), transformer.transformer.v1)
    }
}
