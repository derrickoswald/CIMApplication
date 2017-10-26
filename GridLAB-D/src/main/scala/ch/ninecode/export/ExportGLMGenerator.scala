package ch.ninecode.export

import java.text.SimpleDateFormat
import java.util.Calendar

import ch.ninecode.gl._
import ch.ninecode.esl.Trafokreis

class ExportGLMGenerator (
    one_phase: Boolean,
    date_format: SimpleDateFormat,
    trafokreis: Trafokreis)
extends
    GLMGenerator (one_phase, date_format)
{
    /**
     * Calendar duplication utility function.
     * @param c The Calendar value to be cloned.
     */
    def dup (c: Calendar): Calendar = c.clone().asInstanceOf[Calendar]

    override def name: String = trafokreis.name

    override def header: String = "Export"

    override def start_time: Calendar = trafokreis.start

    override def finish_time: Calendar = { val t = dup (start_time); t.add (Calendar.HOUR, 24); t }

    override def edge_groups: Iterable[Iterable[PreEdge]] = trafokreis.edges.groupBy (_.key).values

    override def transformers: Array[TransformerSet] = Array(trafokreis.transformers)

    // the swing node is the low voltage pin
    override def swing_nodes: Iterable[GLMNode] = List (SwingNode (trafokreis.transformers.node1, trafokreis.transformers.v1, trafokreis.transformers.transformer_name))

    override def nodes: Iterable[GLMNode] = trafokreis.nodes

    override def emit_node (node: GLMNode): String =
    {
        super.emit_node (node) +
        (if (name.startsWith ("HAS"))
        {
            generate_recorder (node) +
            generate_load (node)
        }
        else
            "")
    }

    override def emit_edge (edges: Iterable[GLMEdge]): String =
    {
        val edge = edges.head
        val cls = edge.el.getClass.getName
        val clazz = cls.substring (cls.lastIndexOf(".") + 1)
        def recorders: String =
        {
            "\n" +
            "        object recorder\n" +
            "        {\n" +
            "            name \"" + edge.id + "_current_recorder\";\n" +
            "            parent \"" + edge.id + "_line\";\n" +
            "            property " + ( if (one_phase) "current_in_A.real,current_in_A.imag" else "current_in_A.real,current_in_A.imag,current_in_B.real,current_in_B.imag,current_in_C.real,current_in_C.imag") + ";\n" +
            "            interval 300;\n" +
            "            file \"output_data/" + edge.id + "_current.csv\";\n" +
            "        };\n" +
            "\n" +
            "        object recorder\n" +
            "        {\n" +
            "            name \"" + edge.id + "_power_recorder\";\n" +
            "            parent \"" + edge.id + "_line\";\n" +
            "            property " + ( if (one_phase) "power_in_A.real,power_in_A.imag" else "power_in_A.real,power_in_A.imag,power_in_B.real,power_in_B.imag,power_in_C.real,power_in_C.imag") + ";\n" +
            "            interval 300;\n" +
            "            file \"output_data/" + edge.id + "_power.csv\";\n" +
            "        };\n" +
            "\n" +
            "        object recorder\n" +
            "        {\n" +
            "            name \"" + edge.id + "_losses_recorder\";\n" +
            "            parent \"" + edge.id + "_line\";\n" +
            "            property " + ( if (one_phase) "power_losses_A.real,power_losses_A.imag" else "power_losses_A.real,power_losses_A.imag,power_losses_B.real,power_losses_B.imag,power_losses_C.real,power_losses_C.imag") + ";\n" +
            "            interval 300;\n" +
            "            file \"output_data/" + edge.id + "_losses.csv\";\n" +
            "        };\n"
        }

        val t = super.emit_edge (edges)
        val r = clazz match
        {
            case "ACLineSegment" ⇒
                recorders
            case _ ⇒
                ""
        }
        t + r
    }

    override def emit_slack (node: GLMNode): String =
    {
        val swing = node.asInstanceOf[SwingNode]
        // generate low voltage pin (NSPIN) swing node
        val trafo = swing.name
        "\n" +
            "        object meter\n" +
            "        {\n" +
            "            name \"" + swing.id + "\";\n" +
            "            phases " + (if (one_phase) "AN" else "ABCN") + ";\n" +
            "            bustype SWING;\n" +
            "            nominal_voltage " + swing.nominal_voltage + "V;\n" +
            (if (one_phase)
                "            object player\n" +
                    "            {\n" +
                    "                property \"voltage_A\";\n" +
                    "                file \"input_data/" + trafo + ".csv\";\n" +
                    "            };\n"
            else
                "            object player\n" +
                    "            {\n" +
                    "                property \"voltage_A\";\n" +
                    "                file \"input_data/" + trafo + "_R.csv\";\n" +
                    "            };\n" +
                    "            object player\n" +
                    "            {\n" +
                    "                property \"voltage_B\";\n" +
                    "                file \"input_data/" + trafo + "_S.csv\";\n" +
                    "            };\n" +
                    "            object player\n" +
                    "            {\n" +
                    "                property \"voltage_C\";\n" +
                    "                file \"input_data/" + trafo + "_T.csv\";\n" +
                    "            };\n") +
            "        };\n"
    }

    override def emit_transformer (transformer: TransformerSet): String =
    {
        val name = transformer.transformer_name

        super.emit_transformer (transformer) +
        "\n" +
        "        object recorder\n" +
        "        {\n" +
        "            name \"" + name + "_current_recorder\";\n" +
        "            parent \"" + name + "_transformer\";\n" +
        "            property " + ( if (one_phase) "current_in_A.real,current_in_A.imag" else "current_in_A.real,current_in_A.imag,current_in_B.real,current_in_B.imag,current_in_C.real,current_in_C.imag") + ";\n" +
        "            interval 300;\n" +
        "            file \"output_data/" + name + "_current.csv\";\n" +
        "        };\n" +
        "\n" +
        "        object recorder\n" +
        "        {\n" +
        "            name \"" + name + "_power_recorder\";\n" +
        "            parent \"" + name + "_transformer\";\n" +
        "            property " + ( if (one_phase) "power_in_A.real,power_in_A.imag" else "power_in_A.real,power_in_A.imag,power_in_B.real,power_in_B.imag,power_in_C.real,power_in_C.imag") + ";\n" +
        "            interval 300;\n" +
        "            file \"output_data/" + name + "_power.csv\";\n" +
        "        };\n" +
        "\n" +
        "        object recorder\n" +
        "        {\n" +
        "            name \"" + name + "_losses_recorder\";\n" +
        "            parent \"" + name + "_transformer\";\n" +
        "            property " + ( if (one_phase) "power_losses_A.real,power_losses_A.imag" else "power_losses_A.real,power_losses_A.imag,power_losses_B.real,power_losses_B.imag,power_losses_C.real,power_losses_C.imag") + ";\n" +
        "            interval 300;\n" +
        "            file \"output_data/" + name + "_losses.csv\";\n" +
        "        };\n"
    }

    def generate_recorder (node: GLMNode): String =
    {
        val experiments = trafokreis.experiments
        val house = nis_number (node.id)
        val filtered = experiments.filter(p ⇒ p.house == house)
        val experiment = if (0 != filtered.length) filtered(0) else null

        if (null != experiment)
            "\n" +
            "        object recorder\n" +
            "        {\n" +
            "            name \"" + nis_number (node.id) + "_voltage_recorder\";\n" +
            "            parent \"" + node.id + "\";\n" +
            "            property " + ( if (one_phase) "voltage_A.real,voltage_A.imag" else "voltage_A.real,voltage_A.imag,voltage_B.real,voltage_B.imag,voltage_C.real,voltage_C.imag") + ";\n" +
            "            interval 300;\n" +
            "            file \"output_data/" + node.id + "_voltage.csv\";\n" +
            "        };\n" +
            "\n" +
            "        object recorder\n" +
            "        {\n" +
            "            name \"" + nis_number (node.id) + "_power_recorder\";\n" +
            "            parent \"" + node.id + "\";\n" +
            "            property " + ( if (one_phase) "power_A.real,power_A.imag" else "power_A.real,power_A.imag,power_B.real,power_B.imag,power_C.real,power_C.imag") + ";\n" +
            "            interval 300;\n" +
            "            file \"output_data/" + node.id + "_power.csv\";\n" +
            "        };\n"
        else
            ""
    }

    def generate_load (node: GLMNode): String =
    {
        val experiments = trafokreis.experiments
        val house = nis_number (node.id)
        val filtered = experiments.filter(p ⇒ p.house == house)
        val experiment = if (0 != filtered.length) filtered(0) else null

        if (null != experiment)
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
                "                file \"input_data/" + house + ".csv\";\n" +
                "            };\n"
            else
                "            object player\n" +
                "            {\n" +
                "                property \"constant_current_A\";\n" +
                "                file \"input_data/" + house + "_R.csv\";\n" +
                "            };\n" +
                "            object player\n" +
                "            {\n" +
                "                property \"constant_current_B\";\n" +
                "                file \"input_data/" + house + "_S.csv\";\n" +
                "            };\n" +
                "            object player\n" +
                "            {\n" +
                "                property \"constant_current_C\";\n" +
                "                file \"input_data/" + house + "_T.csv\";\n" +
                "            };\n") +
            "        };\n"
        else
            ""
    }
}
