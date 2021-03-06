package ch.ninecode.on

import java.text.SimpleDateFormat

import ch.ninecode.gl.GLMEdge
import ch.ninecode.gl.GLMGenerator
import ch.ninecode.gl.GLMNode
import ch.ninecode.gl.GLMLineEdge
import ch.ninecode.gl.GLMTransformerEdge
import ch.ninecode.util.Complex

/**
 * Medium voltage GridLAB-D model file exporter.
 *
 * @param one_phase   If <code>true</code> generate a single phase .glm file.
 * @param temperature The temperature of the elements in the .glm file (°C).
 * @param date_format The date format to use within the .glm file.
 * @param feeder      The elements to be generated.
 * @param voltages    The voltage levels present in the CIM file.
 */
case class OneOfNGLMGenerator
(
    one_phase: Boolean,
    temperature: Double,
    date_format: SimpleDateFormat,
    feeder: FeederArea,
    voltages: collection.Map[String, Double])
    extends GLMGenerator(one_phase, temperature, date_format, emit_voltage_dump = true, emit_fault_check = false)
{
    override def name: String = s"${feeder.metadata.station}_${feeder.metadata.connector}"

    override def header: String = feeder.metadata.description

    override def edges: Iterable[GLMEdge] = feeder.edges.filter(!_.isInstanceOf[GLMTransformerEdge])

    override def nodes: Iterable[GLMNode] = feeder.nodes.filter(_.feeder.isEmpty)

    override def transformers: Iterable[GLMTransformerEdge] = feeder.edges.filter(_.isInstanceOf[GLMTransformerEdge]).map(_.asInstanceOf[GLMTransformerEdge])

    @SuppressWarnings(Array("org.wartremover.warts.TraversableOps"))
    override def swing_nodes: Iterable[GLMNode] = feeder.nodes.filter(_.feeder.isDefined)
        .groupBy(_._id).values.map(_.head) // take only one feeder per node

    def nodelist: Map[String, GLMNode] = feeder.nodes.map(x => (x._id, x)).toMap

    def ends_voltages (edge: GLMEdge): Iterable[(String, Double)] =
    {
        edge match
        {
            case line: GLMLineEdge =>
                val v = voltages.getOrElse(line.data.aLine.line.Conductor.ConductingEquipment.BaseVoltage, 0.0)
                List((line.cn1, v), (line.cn2, v))
            case switch: PlayerSwitchEdge =>
                val v = voltages.getOrElse(switch.switch.ConductingEquipment.BaseVoltage, 0.0)
                List((switch.cn1, v), (switch.cn2, v))
            case transformer: GLMTransformerEdge =>
                List((transformer.cn1, transformer.transformer.v0), (transformer.cn2, transformer.transformer.v1))
            case edge: GLMEdge =>
                List((edge.cn1, 0.0), (edge.cn2, 0.0)) // unspecified transformers
        }
    }

    /**
     * Add meter elements for nodes on the edge of the network.
     *
     * For switches and transformers with one terminal in the feeder network, and the other not,
     * GridLAB-D complains because the referenced node doesn't exist, so we add them
     * here... until a better method can be found.
     *
     * @return Node elements to add to the .glm file.
     */
    @SuppressWarnings(Array("org.wartremover.warts.TraversableOps"))
    override def extra: Iterable[String] =
    {
        val missing: Iterable[(String, Double)] = feeder.edges.flatMap(ends_voltages) // get the nodes from each edge
            .filter(x => !nodelist.isDefinedAt(x._1)) // eliminate those that are emitted normally
            .groupBy(_._1).values.map(_.head) // eliminate duplicates from multiple edges
        missing.map(x => FeederNode(x._1, x._2, None).emit(this))
    }

    def three_or_one (property: String): String =
        if (one_phase)
            s"${property}_A"
        else
            s"${property}_A,${property}_B,${property}_C"

    def three_or_one (value: Complex): String =
        (for (_ <- 1 to (if (one_phase) 1 else 3)) yield value.toString).mkString(",")

    /**
     * Add switch player to toggle the SWING bus.
     *
     * @param node The swing node to emit.
     * @return The .glm file text for the swing bus.
     */
    override def emit_slack (node: GLMNode, suffix:String=""): String =
    {
        val phases = if (one_phase) "AN" else "ABCN"
        node match
        {
            case swing: FeederNode =>
                s"""
                   |        object meter
                   |        {
                   |            name "${swing.id}_swing";
                   |            phases $phases;
                   |            bustype SWING;
                   |            nominal_voltage ${swing.nominal_voltage}V;
                   |            ${three_or_one("voltage")} ${three_or_one(Complex(swing.nominal_voltage, 0.0))};
                   |        };
                   |
                   |        object switch
                   |        {
                   |            name "${swing.id}_switch";
                   |            phases $phases;
                   |            from "${swing.id}_swing";
                   |            to "${swing._id}";
                   |            object player
                   |            {
                   |                property "status";
                   |                file "input_data/${swing._id}.csv";
                   |            };
                   |        };
                   |
                   |        object meter
                   |        {
                   |            name "${swing._id}";
                   |            phases $phases;
                   |            bustype PQ;
                   |            nominal_voltage ${swing.nominal_voltage}V;
                   |        };
                   |""".stripMargin
            case _ =>
                ""
        }
    }

    /**
     * Add load for each (low voltage) transformer.
     *
     * @param transformer the edge to emit
     * @return The .glm file text for the transformer.
     */
    override def emit_transformer (transformer: GLMTransformerEdge): String =
    {
        val name = transformer.transformer.transformer_name
        val phases = if (one_phase) "AN" else "ABCN"
        s"""${super.emit_transformer(transformer)}
           |        object load
           |        {
           |            name "${name}_load";
           |            parent "${transformer.cn2}";
           |            phases $phases;
           |            ${three_or_one("constant_power")} ${three_or_one(Complex(10000, 0))};
           |            nominal_voltage ${transformer.transformer.v1}V;
           |            load_class R;
           |        };
        """.stripMargin
    }
}
