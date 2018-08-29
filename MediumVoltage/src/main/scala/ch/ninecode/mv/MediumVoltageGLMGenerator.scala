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

case class MediumVoltageGLMGenerator (
    one_phase: Boolean,
    date_format: SimpleDateFormat,
    ust: USTKreis)
extends GLMGenerator (one_phase, 20.0, date_format) // ToDo: get library base temperature and target temperature as command line input
{

    override def name: String = ust.trafokreis_key

    override def start_time: Calendar = ust.start_time

    override def finish_time: Calendar = ust.finish_time

    override def edges: Iterable[GLMEdge] = ust.edges.groupBy (_.key).values.map (edges ⇒ ust.toGLMEdge (edges.map (_.element), edges.head.cn1, edges.head.cn2))

    override def transformers: Array[TransformerSet] = ust.transformers

    override def swing_nodes: Iterable[GLMNode] = ust.swing_nodes

    val lv: Array[String] = ust.hv_transformers.map (_.node1)
    override def nodes: Iterable[USTNode] = ust.nodes.filter (x ⇒ !lv.contains (x.id))

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

    override def emit_edge (edge: GLMEdge): String =
    {
        def recorders: String =
        {
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
            |            name "%s_power_recorder";
            |            parent "%s";
            |            property %s;
            |            interval 300;
            |            file "output_data/%s_power.csv";
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
                edge.id, edge.id, three_or_one ("current_in"), edge.id,
                edge.id, edge.id, three_or_one ("power_in"), edge.id,
                edge.id, edge.id, three_or_one ("power_losses"), edge.id)
        }
        if (edge.isInstanceOf[TransformerEdge])
            ""
        else
            super.emit_edge (edge) + (if (edge.isInstanceOf[LineEdge]) recorders else "")
    }

    override def emit_node (node: GLMNode): String =
    {
        val n = node.asInstanceOf[USTNode]
        def recorder (node: USTNode): String =
        {
            if (null != node.load)
            {
                val trafo = node.load.transformer_name
                """
                |        object recorder
                |        {
                |            name "%s_voltage_recorder";
                |            parent "%s";
                |            property %s;
                |            interval 300;
                |            file "output_data/%s_voltage.csv";
                |        };
                |
                |        object recorder
                |        {
                |            name "%s_power_recorder";
                |            parent "%s";
                |            property %s;
                |            interval 300;
                |            file "output_data/%s_power.csv";
                |        };
                |""".stripMargin.format (
                    trafo, node.id, three_or_one ("voltage"), trafo,
                    trafo, node.id, three_or_one ("measured_power"), trafo)
            }
            else
            {
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
                |            name "%s_voltage_recorder";
                |            parent "%s";
                |            property %s;
                |            interval 300;
                |            file "output_data/%s_voltage.csv";
                |        };
                |
                |        object recorder
                |        {
                |            name "%s_power_recorder";
                |            parent "%s";
                |            property %s;
                |            interval 300;
                |            file "output_data/%s_power.csv";
                |        };
                |""".stripMargin.format (
                    node.id, node.id, three_or_one ("measured_current"), node.id,
                    node.id, node.id, three_or_one ("voltage"), node.id,
                    node.id, node.id, three_or_one ("measured_power"), node.id)
            }
        }

        super.emit_node (node) + generate_load (n) + recorder (n)
    }

    override def emit_slack (node: GLMNode): String =
    {
        val swing = node.asInstanceOf[SwingNode]
        // generate low voltage pin (NSPIN) swing node
        val trafo = swing.name
        """
        |        object meter
        |        {
        |            name "%s";
        |            phases %s;
        |            bustype SWING;
        |            nominal_voltage %sV;
        |%s
        |        };
        |
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
        |            name "%s_power_recorder";
        |            parent "%s";
        |            property %s;
        |            interval 300;
        |            file "output_data/%s_power.csv";
        |        };
        |""".stripMargin.format (
            swing.id, if (one_phase) "AN" else "ABCN", swing.nominal_voltage, three_or_one_player ("voltage", trafo),
            trafo, node.id, three_or_one ("measured_current"), trafo,
            trafo, node.id, three_or_one ("measured_power"), trafo)
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
