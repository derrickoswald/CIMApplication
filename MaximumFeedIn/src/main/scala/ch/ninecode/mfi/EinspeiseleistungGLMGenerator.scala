package ch.ninecode.mfi

import java.text.SimpleDateFormat
import java.util.Calendar

import ch.ninecode.gl._
import ch.ninecode.model.SolarGeneratingUnit

class EinspeiseleistungGLMGenerator (one_phase: Boolean, date_format: SimpleDateFormat, trafokreis: Trafokreis)
extends GLMGenerator (one_phase, 20.0, date_format) // ToDo: get library base temperature and target temperature as command line input
{
    override def name: String = trafokreis.name

    override def header: String = "Einspeiseleistung"

    override def start_time: Calendar = trafokreis.start_time

    override def finish_time: Calendar = trafokreis.finish_time

    override def edges: Iterable[GLMEdge] = trafokreis.edges.groupBy (_.key).values.map (edges ⇒ GLMEdge.toGLMEdge (edges.map (_.element), edges.head.cn1, edges.head.cn2))

    override def transformers: Iterable[TransformerEdge] = List (TransformerEdge (trafokreis.transformers.node0, trafokreis.transformers.node1, trafokreis.transformers))

    override def swing_nodes: Iterable[GLMNode] = List (SwingNode (trafokreis.swing_node, trafokreis.swing_node_voltage, trafokreis.trafo))

    override def nodes: Iterable[GLMNode] = trafokreis.nodes

    override def extra: Iterable[String] =
    {
        def extra_nodes: Iterable[MaxPowerFeedingNodeEEA] = trafokreis.houses.filter (_.eea != null).groupBy (_.id_seq).values. map (_.head)

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

    override def emit_edge (edge: GLMEdge): String =
    {
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

        super.emit_edge (edge) + (if (edge.isInstanceOf[LineEdge]) current_recorder else "")
    }

    override def emit_transformer (transformer: TransformerEdge): String =
    {
        val name = transformer.transformer.transformer_name

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

    def generate_load (node: GLMNode): String =
    {
        val experiment = trafokreis.experiments.find (_.node == node.id).orNull
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
                "                file \"input_data/" + experiment.house + ".csv\";\n" +
                "            };\n"
            else
                "            object player\n" +
                "            {\n" +
                "                property \"constant_power_A\";\n" +
                "                file \"input_data/" + experiment.house + "_R.csv\";\n" +
                "            };\n" +
                "            object player\n" +
                "            {\n" +
                "                property \"constant_power_B\";\n" +
                "                file \"input_data/" + experiment.house + "_S.csv\";\n" +
                "            };\n" +
                "            object player\n" +
                "            {\n" +
                "                property \"constant_power_C\";\n" +
                "                file \"input_data/" + experiment.house + "_T.csv\";\n" +
                "            };\n") +
            "        };\n" +
            "\n" + // only need a recorder if there is a load
            "        object recorder\n" +
            "        {\n" +
            "            name \"" + experiment.house + "_voltage_recorder\";\n" +
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

        def active_part(s: Double, cosPhi: Double) = s * cosPhi
        def reactive_part(s: Double, cosPhi: Double) =
            {
                val p_val = active_part(s, cosPhi)
                Math.sqrt(s*s - p_val * p_val)
            }

        for (solargeneratingunit ← solargeneratingunits)
        {
            val ratedNetMaxP = solargeneratingunit.GeneratingUnit.ratedNetMaxP * 1000
            val normalPF = solargeneratingunit.GeneratingUnit.normalPF
            val cosPhi = if (normalPF > 0.0 ) normalPF else 1
            if (ratedNetMaxP > 0) {
                load +=
                    "\n" +
                    "        object load\n" +
                    "        {\n" +
                    "            name \"" + parent + "_pv_" + index + "\";\n" +
                    "            parent \"" + parent + "\";\n" +
                    "            phases " + (if (one_phase) "AN" else "ABCN") + ";\n" +
                    (if (one_phase) {
                        val active_power = active_part(ratedNetMaxP, cosPhi)
                        val reactive_power = reactive_part(ratedNetMaxP, cosPhi)
                            "            constant_power_A -" + active_power + "-" + reactive_power + "j;\n"
                    }
                    else
                    {
                        val active_power = active_part(ratedNetMaxP / 3, cosPhi)
                        val reactive_power = reactive_part(ratedNetMaxP / 3, cosPhi)
                        "            constant_power_A -" + active_power + "-" + reactive_power + "j;\n" +
                        "            constant_power_B -" + active_power + "-" + reactive_power + "j;\n" +
                        "            constant_power_C -" + active_power + "-" + reactive_power + "j;\n"
                    }) +
                    "            nominal_voltage " + voltage + "V;\n" +
                    "            load_class R;\n" +
                    "        }\n"
                index += 1
            }
        }
        load
    }
}