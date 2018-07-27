package ch.ninecode.esl

import java.text.SimpleDateFormat
import java.util.Calendar

import ch.ninecode.gl._
import ch.ninecode.model.SolarGeneratingUnit

class EinspeiseleistungGLMGenerator (one_phase: Boolean, date_format: SimpleDateFormat, trafokreis: Trafokreis)
extends
    GLMGenerator (one_phase, date_format)
{
    override def name: String = trafokreis.name

    override def header: String = "Einspeiseleistung"

    override def start_time: Calendar = trafokreis.start_time

    override def finish_time: Calendar = trafokreis.finish_time

    override def edge_groups: Iterable[Iterable[PreEdge]] = trafokreis.edges.groupBy(_.key).values

    override def transformers: Array[TransformerSet] = Array (trafokreis.transformers)

    override def swing_nodes: Iterable[GLMNode] = List (SwingNode (trafokreis.swing_node, trafokreis.swing_node_voltage, trafokreis.trafo))

    override def nodes: Iterable[GLMNode] = trafokreis.nodes

    override def extra: Iterable[String] =
    {
        def extra_nodes: Iterable[MaxPowerFeedingNodeEEA] = trafokreis.houses.filter (_.eea != null)

        def emit_extra_node (node: MaxPowerFeedingNodeEEA): String =
        {
            val solargeneratingunits = node.eea.map(x ⇒ { x.solar }).toList
            emit_pv (solargeneratingunits, node)
        }

        extra_nodes.map (emit_extra_node)
    }

    override def emit_node (node: GLMNode): String =
    {
        // or load_from_player_file (name, voltage)
        super.emit_node (node) + generate_load (node)
    }

    override def emit_edge (edges: Iterable[GLMEdge]): String =
    {
        val edge = edges.head
        val cls = edge.el.getClass.getName
        val clazz = cls.substring (cls.lastIndexOf(".") + 1)
        def current_recorder: String =
        {
            "\n" +
            "        object recorder\n" +
            "        {\n" +
            "            name \"" + edge.id + "_current_recorder\";\n" +
            "            parent \"" + edge.id + "\";\n" +
            "            property " + (if (one_phase) "current_in_A.real,current_in_A.imag" else "current_in_A.real,current_in_A.imag,current_in_B.real,current_in_B.imag,current_in_C.real,current_in_C.imag") + ";\n" +
            "            interval 5;\n" +
            "            file \"output_data/" + edge.id + "_current.csv\";\n" +
            "        };\n"
        }

        val t = super.emit_edge (edges)
        val r = clazz match
        {
            case "ACLineSegment" ⇒
                current_recorder
            case _ ⇒
                ""
        }
        t + r
    }

    override def emit_transformer (transformer: TransformerSet): String =
    {
        val name = transformer.transformer_name

        super.emit_transformer (transformer) +
        "\n" +
        "        object recorder\n" +
        "        {\n" +
        "            name \"" + name + "_current_recorder\";\n" +
        "            parent \"" + name + "\";\n" +
        "            property " + (if (one_phase) "current_out_A.real,current_out_A.imag" else "current_out_A.real,current_out_A.imag,current_out_B.real,current_out_B.imag,current_out_C.real,current_out_C.imag") + ";\n" +
        "            interval 5;\n" +
        "            file \"output_data/" + name + "_current.csv\";\n" +
        "        };\n"
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
                "                property \"constant_power_A\";\n" +
                "                file \"input_data/" + house + ".csv\";\n" +
                "            };\n"
            else
                "            object player\n" +
                "            {\n" +
                "                property \"constant_power_A\";\n" +
                "                file \"input_data/" + house + "_R.csv\";\n" +
                "            };\n" +
                "            object player\n" +
                "            {\n" +
                "                property \"constant_power_B\";\n" +
                "                file \"input_data/" + house + "_S.csv\";\n" +
                "            };\n" +
                "            object player\n" +
                "            {\n" +
                "                property \"constant_power_C\";\n" +
                "                file \"input_data/" + house + "_T.csv\";\n" +
                "            };\n") +
            "        };\n" +
            "\n" + // only need a recorder if there is a load
            "        object recorder\n" +
            "        {\n" +
            "            name \"" + nis_number (node.id) + "_voltage_recorder\";\n" +
            "            parent \"" + node.id + "\";\n" +
            "            property " + ( if (one_phase) "voltage_A.real,voltage_A.imag" else "voltage_A.real,voltage_A.imag,voltage_B.real,voltage_B.imag,voltage_C.real,voltage_C.imag") + ";\n" +
            "            interval 5;\n" +
            "            file \"output_data/" + node.id + "_voltage.csv\";\n" +
            "        };\n"
        else
            ""
    }

    def emit_pv (solargeneratingunits: List[SolarGeneratingUnit], node: MaxPowerFeedingNodeEEA): String =
    {
        val parent = node.id_seq
        val voltage = node.voltage
        var load = ""
        var index = 1
        for (solargeneratingunit ← solargeneratingunits)
        {
            val power = solargeneratingunit.GeneratingUnit.ratedNetMaxP * 1000
            val power3 = power / 3 // per phase
            if (power > 0) {
                load +=
                    "\n" +
                    "        object load\n" +
                    "        {\n" +
                    "            name \"" + parent + "_pv_" + index + "\";\n" +
                    "            parent \"" + parent + "\";\n" +
                    "            phases " + (if (one_phase) "AN" else "ABCN") + ";\n" +
                    (if (one_phase)
                        "            constant_power_A -" + power + ";\n"
                    else
                        "            constant_power_A -" + power3 + ";\n" +
                        "            constant_power_B -" + power3 + ";\n" +
                        "            constant_power_C -" + power3 + ";\n") +
                    "            nominal_voltage " + voltage + "V;\n" +
                    "            load_class R;\n" +
                    "        }\n"
                index += 1
            }
        }
        load
    }
}