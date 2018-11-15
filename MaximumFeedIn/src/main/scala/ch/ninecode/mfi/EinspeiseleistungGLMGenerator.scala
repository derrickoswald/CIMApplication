package ch.ninecode.mfi

import java.text.SimpleDateFormat
import java.util.Calendar

import scala.math._

import ch.ninecode.gl._
import ch.ninecode.model.GeneratingUnit
import ch.ninecode.model.SolarGeneratingUnit
import ch.ninecode.model.Switch

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

    /**
     * Emit a switch or fuse with large current limit.
     *
     * @param edge the details about the switch or fuse
     * @param generator the driver program
     * @return A switch string (.glm text) for this edge.
     */
    def emit_switch (edge: SwitchEdge, generator: GLMGenerator): String =
    {
        val status = if (edge.switch.normalOpen) "OPEN" else "CLOSED"
        val current = 9999.0 // override so it never trips
        val fuse_details = if (edge.fuse)
            """
                mean_replacement_time 3600.0;
                current_limit %sA;""".format (current)
        else
            ""

            """
              |        object %s
              |        {
              |            name "%s";
              |            phases %s;
              |            from "%s";
              |            to "%s";
              |            status "%s";%s
              |        };
              |""".stripMargin.format (if (edge.fuse) "fuse" else "switch", edge.id, if (generator.isSinglePhase) "AN" else "ABCN", edge.cn1, edge.cn2, status, fuse_details)
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

        edge match
        {
            case cable: LineEdge ⇒ super.emit_edge (cable) + current_recorder
            case swtch: SwitchEdge ⇒ emit_switch (swtch, this)
            case _ ⇒ super.emit_edge (edge)
        }
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

        for (solargeneratingunit ← solargeneratingunits)
        {
            val ratedNetMaxP = solargeneratingunit.GeneratingUnit.ratedNetMaxP * 1000
            val normalPFMask = GeneratingUnit.fields.indexOf ("normalPF")
            val cosPhi = if (0 != (solargeneratingunit.GeneratingUnit.bitfields (normalPFMask / 32) & (1 << (normalPFMask % 32))))
                solargeneratingunit.GeneratingUnit.normalPF
            else
                1.0
            val ratedP = if (one_phase) ratedNetMaxP else ratedNetMaxP / 3
            // https://en.wikipedia.org/wiki/Power_factor
            // Power factors are usually stated as "leading" or "lagging" to show the sign of the phase angle.
            // Capacitive loads are leading (current leads voltage), and inductive loads are lagging (current lags voltage).
            // So, without it being stated we assume PF is lagging and that a negative power factor is actually an indicator of a leading power factor.
            val maxP = - new Complex (ratedP * math.abs (cosPhi), ratedP * math.signum (cosPhi) * sin (acos (math.abs (cosPhi))))
            if (ratedNetMaxP > 0) {
                load +=
                    "\n" +
                    "        object load\n" +
                    "        {\n" +
                    "            name \"" + parent + "_pv_" + index + "\";\n" +
                    "            parent \"" + parent + "\";\n" +
                    "            phases " + (if (one_phase) "AN" else "ABCN") + ";\n" +
                    (if (one_phase)
                        "            constant_power_A %s;\n".format (maxP.asString (6))
                    else
                    {
                        "            constant_power_A %s;\n".format (maxP.asString (6)) +
                        "            constant_power_A %s;\n".format (maxP.asString (6)) +
                        "            constant_power_A %s;\n".format (maxP.asString (6))
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