package ch.ninecode.ms

import java.text.SimpleDateFormat
import java.util.Calendar

import ch.ninecode.gl._

case class MediumVoltageGLMGenerator (
        one_phase: Boolean,
        date_format: SimpleDateFormat,
        ust: USTKreis) extends GLMGenerator (one_phase, date_format)
{

    override def name: String = ust.trafokreis_key

    override def start_time: Calendar = ust.start_time

    override def finish_time: Calendar = ust.finish_time

    override def edge_groups: Iterable[Iterable[PreEdge]] = ust.edges

    override def transformers: Array[TransformerSet] = ust.transformers

    override def swing_nodes: Iterable[GLMNode] = ust.swing_nodes
    lazy val swing_node_names: Array[String] = swing_nodes.map (_.id).toArray

    override def nodes: Iterable[USTNode] = ust.nodes

    override def extra: Iterable[String] = List ("")

    override def emit_node (node: GLMNode): String =
    {
        val n = node.asInstanceOf[USTNode]
        if (!swing_node_names.contains (node.id))
            super.emit_node (node) + generate_load (n)
        else
            ""
    }

    def generate_load (node: USTNode): String =
    {
        if (null != node.load)
        {
            val trafo = node.load.transformer_name
            "\n" +
            "        object load\n" +
            "        {\n" +
            "            name \"" + node.id + "_load\";\n" +
            "            parent \"" + node.id + "\";\n" +
            "            phases " + (if (one_phase) "AN" else "ABCN") + ";\n" +
            "            nominal_voltage " + node.nominal_voltage + "V;\n" +
            (if (one_phase)
                "            object player\n" +
                "            {\n" +
                "                property \"constant_current_A\";\n" +
                "                file \"input_data/" + trafo + ".csv\";\n" +
                "            };\n"
            else
                "            object player\n" +
                "            {\n" +
                "                property \"constant_current_A\";\n" +
                "                file \"input_data/" + trafo + "_R.csv\";\n" +
                "            };\n" +
                "            object player\n" +
                "            {\n" +
                "                property \"constant_current_B\";\n" +
                "                file \"input_data/" + trafo + "_S.csv\";\n" +
                "            };\n" +
                "            object player\n" +
                "            {\n" +
                "                property \"constant_current_C\";\n" +
                "                file \"input_data/" + trafo + "_T.csv\";\n" +
                "            };\n") +
            "        };\n" +
            "\n" + // only need a recorder if there is a load
            "        object recorder\n" +
            "        {\n" +
            "            name \"" + trafo + "_voltage_recorder\";\n" +
            "            parent \"" + node.id + "\";\n" +
            "            property " + ( if (one_phase) "voltage_A.real,voltage_A.imag" else "voltage_A.real,voltage_A.imag,voltage_B.real,voltage_B.imag,voltage_C.real,voltage_C.imag") + ";\n" +
            "            interval 5;\n" +
            "            file \"output_data/" + trafo + "_voltage.csv\";\n" +
            "        };\n"
        }
        else
            ""
    }
}