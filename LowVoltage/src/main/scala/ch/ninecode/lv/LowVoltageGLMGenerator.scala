package ch.ninecode.lv

import java.text.SimpleDateFormat
import java.util.Calendar

import ch.ninecode.mfi.PowerFeedingNode
import ch.ninecode.mfi.Trafokreis
import ch.ninecode.gl.GLMEdge
import ch.ninecode.gl.GLMGenerator
import ch.ninecode.gl.GLMNode
import ch.ninecode.gl.LineEdge
import ch.ninecode.gl.SwingNode
import ch.ninecode.gl.TransformerEdge

class LowVoltageGLMGenerator
(
    one_phase: Boolean,
    date_format: SimpleDateFormat,
    trafokreis: Trafokreis)
    extends GLMGenerator (one_phase, 20.0, date_format) // ToDo: get library base temperature and target temperature as command line input
{
    /**
     * Calendar duplication utility function.
     *
     * @param c The Calendar value to be cloned.
     */
    def dup (c: Calendar): Calendar = c.clone ().asInstanceOf [Calendar]

    override def name: String = trafokreis.name

    override def start_time: Calendar = trafokreis.start

    override def finish_time: Calendar =
    {
        val t = dup (start_time)
        t.add (Calendar.HOUR, 24)
        t
    }

    override def edges: Iterable[GLMEdge] = trafokreis.edges.groupBy (_.key).values.map (edges ⇒ GLMEdge.toGLMEdge (edges.map (_.element), edges.head.cn1, edges.head.cn2))

    override def transformers: Iterable[TransformerEdge] = List (TransformerEdge (trafokreis.transformers.node0, trafokreis.transformers.node1, trafokreis.transformers))

    // the swing node is the low voltage pin
    override def swing_nodes: Iterable[GLMNode] = List (SwingNode (trafokreis.transformers.node1, trafokreis.transformers.v1, trafokreis.transformers.transformer_name))

    override def nodes: Iterable[GLMNode] =
    {
        val swings = swing_nodes.map (_.id).toArray
        trafokreis.nodes.filter (x => !swings.contains (x.id)).++: (
            Array (
                PowerFeedingNode (
                    trafokreis.transformers.node0,
                    null,
                    null,
                    trafokreis.transformers.v0,
                    null,
                    null,
                    0.0,
                    Double.PositiveInfinity,
                    null)))
    }

    override def emit_node (node: GLMNode): String =
    {
        super.emit_node (node) +
            (if (node.id.startsWith ("HAS"))
            {
                generate_recorder (node) +
                    generate_load (node)
            }
            else
                "")
    }

    override def emit_edge (edge: GLMEdge): String =
    {
        def recorders: String =
        {
            "\n" +
                "        object recorder\n" +
                "        {\n" +
                "            name \"" + edge.id + "_current_recorder\";\n" +
                "            parent \"" + edge.id + "\";\n" +
                "            property " + (if (one_phase) "current_in_A.real,current_in_A.imag" else "current_in_A.real,current_in_A.imag,current_in_B.real,current_in_B.imag,current_in_C.real,current_in_C.imag") + ";\n" +
                "            interval 300;\n" +
                "            file \"output_data/" + edge.id + "_current.csv\";\n" +
                "        };\n" +
                "\n" +
                "        object recorder\n" +
                "        {\n" +
                "            name \"" + edge.id + "_power_recorder\";\n" +
                "            parent \"" + edge.id + "\";\n" +
                "            property " + (if (one_phase) "power_in_A.real,power_in_A.imag" else "power_in_A.real,power_in_A.imag,power_in_B.real,power_in_B.imag,power_in_C.real,power_in_C.imag") + ";\n" +
                "            interval 300;\n" +
                "            file \"output_data/" + edge.id + "_power.csv\";\n" +
                "        };\n" +
                "\n" +
                "        object recorder\n" +
                "        {\n" +
                "            name \"" + edge.id + "_losses_recorder\";\n" +
                "            parent \"" + edge.id + "\";\n" +
                "            property " + (if (one_phase) "power_losses_A.real,power_losses_A.imag" else "power_losses_A.real,power_losses_A.imag,power_losses_B.real,power_losses_B.imag,power_losses_C.real,power_losses_C.imag") + ";\n" +
                "            interval 300;\n" +
                "            file \"output_data/" + edge.id + "_losses.csv\";\n" +
                "        };\n"
        }

        super.emit_edge (edge) + (if (edge.isInstanceOf [LineEdge]) recorders else "")
    }

    override def emit_slack (node: GLMNode): String =
    {
        val swing = node.asInstanceOf [SwingNode]
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
                    "            };\n"
                ) +
            "        };\n" +
            "\n" +
            "        object recorder\n" +
            "        {\n" +
            "            name \"" + trafo + "_current_recorder\";\n" +
            "            parent \"" + node.id + "\";\n" +
            "            property " + (if (one_phase) "measured_current_A.real,measured_current_A.imag" else "measured_current_A.real,measured_current_A.imag,measured_current_B.real,measured_current_B.imag,measured_current_C.real,measured_current_C.imag") + ";\n" +
            "            interval 300;\n" +
            "            file \"output_data/" + trafo + "_current.csv\";\n" +
            "        };\n" +
            "\n" +
            "        object recorder\n" +
            "        {\n" +
            "            name \"" + trafo + "_power_recorder\";\n" +
            "            parent \"" + node.id + "\";\n" +
            "            property " + (if (one_phase) "measured_power_A.real,measured_power_A.imag" else "measured_power_A.real,measured_power_A.imag,measured_power_B.real,measured_power_B.imag,measured_power_C.real,measured_power_C.imag") + ";\n" +
            "            interval 300;\n" +
            "            file \"output_data/" + trafo + "_power.csv\";\n" +
            "        };\n" +
            "\n"
    }

    override def emit_transformer (transformer: TransformerEdge): String =
    {
        val name = transformer.transformer.transformer_name

        val swings = swing_nodes.map (_.id).toArray
        super.emit_transformer (transformer) +
            (if (!swings.contains (transformer.cn1) && !swings.contains (transformer.cn2))
                "\n" +
                    "        object recorder\n" +
                    "        {\n" +
                    "            name \"" + name + "_current_recorder\";\n" +
                    "            parent \"" + name + "\";\n" +
                    "            property " + (if (one_phase) "current_in_A.real,current_in_A.imag" else "current_in_A.real,current_in_A.imag,current_in_B.real,current_in_B.imag,current_in_C.real,current_in_C.imag") + ";\n" +
                    "            interval 300;\n" +
                    "            file \"output_data/" + name + "_current.csv\";\n" +
                    "        };\n" +
                    "\n" +
                    "        object recorder\n" +
                    "        {\n" +
                    "            name \"" + name + "_power_recorder\";\n" +
                    "            parent \"" + name + "\";\n" +
                    "            property " + (if (one_phase) "power_in_A.real,power_in_A.imag" else "power_in_A.real,power_in_A.imag,power_in_B.real,power_in_B.imag,power_in_C.real,power_in_C.imag") + ";\n" +
                    "            interval 300;\n" +
                    "            file \"output_data/" + name + "_power.csv\";\n" +
                    "        };\n" +
                    "\n" +
                    "        object recorder\n" +
                    "        {\n" +
                    "            name \"" + name + "_losses_recorder\";\n" +
                    "            parent \"" + name + "\";\n" +
                    "            property " + (if (one_phase) "power_losses_A.real,power_losses_A.imag" else "power_losses_A.real,power_losses_A.imag,power_losses_B.real,power_losses_B.imag,power_losses_C.real,power_losses_C.imag") + ";\n" +
                    "            interval 300;\n" +
                    "            file \"output_data/" + name + "_losses.csv\";\n" +
                    "        };\n"
            else
                ""
                )
    }

    /**
     * Get the NIS database id of a node.
     *
     * The NIS id is suffixed with "_node" or "_topo", and this just gets the prefix.
     * For example "ABG123401_node" is converted to "ABG123401".
     *
     * @param string The node id to split.
     * @return The NIS number from the node id.
     */
    def nis_number (string: String): String =
    {
        val n = string.indexOf ("_")
        if (0 < n)
            string.substring (0, n)
        else
            string
    }

    def generate_recorder (node: GLMNode): String =
    {
        val experiments = trafokreis.experiments
        val house = nis_number (node.id)
        val filtered = experiments.filter (p ⇒ p.house == house)
        val experiment = if (0 != filtered.length) filtered (0) else null

        if (null != experiment)
            "\n" +
                "        object recorder\n" +
                "        {\n" +
                "            name \"" + nis_number (node.id) + "_voltage_recorder\";\n" +
                "            parent \"" + node.id + "\";\n" +
                "            property " + (if (one_phase) "voltage_A.real,voltage_A.imag" else "voltage_A.real,voltage_A.imag,voltage_B.real,voltage_B.imag,voltage_C.real,voltage_C.imag") + ";\n" +
                "            interval 300;\n" +
                "            file \"output_data/" + node.id + "_voltage.csv\";\n" +
                "        };\n" +
                "\n" +
                "        object recorder\n" +
                "        {\n" +
                "            name \"" + nis_number (node.id) + "_power_recorder\";\n" +
                "            parent \"" + node.id + "\";\n" +
                "            property " + (if (one_phase) "power_A.real,power_A.imag" else "power_A.real,power_A.imag,power_B.real,power_B.imag,power_C.real,power_C.imag") + ";\n" +
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
        val filtered = experiments.filter (p ⇒ p.house == house)
        val experiment = if (0 != filtered.length) filtered (0) else null

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
