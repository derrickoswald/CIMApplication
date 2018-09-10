package ch.ninecode.mv

import java.text.SimpleDateFormat
import java.util.Calendar

import ch.ninecode.gl.GLMEdge
import ch.ninecode.gl.GLMGenerator
import ch.ninecode.gl.GLMNode
import ch.ninecode.gl.LineEdge
import ch.ninecode.gl.SwingNode
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

    override def edges: Iterable[GLMEdge] = feeder.edges

    override def nodes: Iterable[GLMNode] = feeder.nodes.filter (_.feeder == null)

//    override def transformers: Array[TransformerSet] = feeder.transformers

    override def swing_nodes: Iterable[GLMNode] = feeder.nodes.filter (_.feeder != null)

    override def extra: Iterable[String] = List ("")

    def three_or_one (property: String): String =
        if (one_phase)
            "%s_A.real,%s_A.imag".format (property, property)
        else
            "%s_A.real,%s_A.imag,%s_B.real,%s_B.imag,%s_C.real,%s_C.imag".format (property, property, property, property, property, property)

    def three_or_one_player (property: String, name: String): String =
        if (one_phase)
        """            object player
        |            {
        |                property "%s_A";
        |                file "input_data/%s.csv";
        |            };""".stripMargin.format (property, name)
        else
        """            object player
        |            {
        |                property "%s_A";
        |                file "input_data/%s_R.csv";
        |            };
        |            object player
        |            {
        |                property "%s_B";
        |                file "input_data/%s_S.csv";
        |            };
        |            object player
        |            {
        |                property "%s_C";
        |                file "input_data/%s_T.csv";
        |            };""".stripMargin.format (property, name, property, name, property, name)

    override def emit_slack (node: GLMNode): String =
    {
        val swing = node.asInstanceOf[FeederNode]
        // generate low voltage pin (NSPIN) swing node
        val trafo = swing.id
        """
        |        object meter
        |        {
        |            name "%s";
        |            phases %s;
        |            bustype SWING;
        |            nominal_voltage %sV;
        |%s
        |        };
        |""".stripMargin.format (
            swing.id, if (one_phase) "AN" else "ABCN", swing.nominal_voltage, three_or_one_player ("voltage", trafo))
    }

    override def emit_transformer (transformer: TransformerSet): String =
    {
        val name = transformer.transformer_name
        val swings = swing_nodes.map (_.id).toArray

        super.emit_transformer (transformer) +
            (if (!swings.contains (transformer.node0) && !swings.contains (transformer.node1))
                """
                |        object recorder
                |        {
                |            name "%s_current_recorder";
                |            parent "%s";
                |            property %s;
                |            interval 300;
                |            file "output_data/%s_current.csv";
                |        };
                |
                |        object recorder
                |        {
                |            name "%s_losses_recorder";
                |            parent "%s";
                |            property %s;
                |            interval 300;
                |            file "output_data/%s_losses.csv";
                |        };
                |""".stripMargin.format (
                    name, name, three_or_one ("current_out"), name,
                    name, name, three_or_one ("power_losses"), name)
            else
                ""
                )
    }

    def generate_load (node: USTNode): String =
    {
        if (null != node.load)
        {
            val trafo = node.load.transformer_name
            """
            |        object load
            |        {
            |            name "%s_load";
            |            parent "%s";
            |            phases %s;
            |            nominal_voltage %sV;
            |%s
            |        };
            |""".stripMargin.format (node.id, node.id, if (one_phase) "AN" else "ABCN", node.nominal_voltage, three_or_one_player ("constant_current", trafo))
        }
        else
            ""
    }
}
