package ch.ninecode.mv

import java.text.SimpleDateFormat

import ch.ninecode.gl.Complex
import ch.ninecode.gl.GLMEdge
import ch.ninecode.gl.GLMGenerator
import ch.ninecode.gl.GLMNode
import ch.ninecode.gl.SwitchEdge
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
    feeder: FeederArea)
extends GLMGenerator (one_phase, temperature, date_format) // ToDo: get library base temperature and target temperature as command line input
{
    override def name: String = feeder.feeder

    override def edges: Iterable[GLMEdge] = feeder.edges.filter (!_.isInstanceOf[TransformerEdge])

    override def nodes: Iterable[GLMNode] = feeder.nodes.filter (_.feeder == null)

    override def transformers: Array[TransformerSet] = feeder.edges.filter (_.isInstanceOf[TransformerEdge]).map (_.asInstanceOf[TransformerEdge]).map (_.transformer).toArray

    override def swing_nodes: Iterable[GLMNode] = feeder.nodes.filter (_.feeder != null)

    override def extra: Iterable[String] = List ("")

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
        |            name "%s";
        |            phases %s;
        |            bustype SWING;
        |            nominal_voltage %sV;
        |            %s %s;
        |        };
        |""".stripMargin.format (
            swing.id, if (one_phase) "AN" else "ABCN", swing.nominal_voltage, three_or_one ("voltage"), three_or_one (Complex (swing.nominal_voltage, 0.0)))
    }

    override def emit_transformer (transformer: TransformerSet): String =
    {
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
        """.stripMargin.format (transformer.node1, transformer.node1, if (one_phase) "AN" else "ABCN", three_or_one ("constant_power"), three_or_one (Complex (10, 0)), 400.0)
    }
}
