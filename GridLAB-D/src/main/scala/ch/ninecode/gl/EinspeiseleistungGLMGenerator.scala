package ch.ninecode.gl

import java.text.SimpleDateFormat

import ch.ninecode.model.SolarGeneratingUnit

class EinspeiseleistungGLMGenerator (one_phase: Boolean, date_format: SimpleDateFormat, trafokreis: Trafokreis) extends GLMGenerator (one_phase, date_format)
{
    override def name: String = trafokreis.name

    override def start_time = trafokreis.start_time

    override def finish_time = trafokreis.finish_time

    override def edge_groups: Iterable[Iterable[PreEdge]] = trafokreis.edges.groupBy(_.key).values

    override def transformers: Array[TData] = trafokreis.transformers

    override def swing_node: String = trafokreis.swing_node

    override def swing_node_voltage: Double = trafokreis.swing_node_voltage

    override def nodes: Iterable[PowerFeedingNode] = trafokreis.nodes

    override def extra_nodes: Iterable[MaxPowerFeedingNodeEEA] = trafokreis.houses.filter (_.eea != null)

    override def emit_extra_node (node: MaxPowerFeedingNodeEEA): String =
    {
        val solargeneratingunits = node.eea.map((x) ⇒ { x.solar }).toList
        emit_pv (solargeneratingunits, node)
    }

    override def emit_node (node: PowerFeedingNode): String =
    {
        // or load_from_player_file (name, voltage)
        super.emit_node (node) + generate_load (node)
    }

    def generate_load (node: PowerFeedingNode): String =
    {
        val experiments = trafokreis.experiments
        val name = node.id_seq
        val voltage = node.voltage
        val house = nis_number (name)
        val filtered = experiments.filter(p ⇒ p.house == house)
        val experiment = if (0 != filtered.length) filtered(0) else null

        if (null != experiment)
            "\n" +
            "        object load\n" +
            "        {\n" +
            "            name \"" + name + "_load\";\n" +
            "            parent \"" + name + "\";\n" +
            "            phases " + (if (one_phase) "AN" else "ABCN") + ";\n" +
            "            nominal_voltage " + voltage + "V;\n" +
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
            "            name \"" + nis_number (name) + "_voltage_recorder\";\n" +
            "            parent \"" + name + "\";\n" +
            "            property " + ( if (one_phase) "voltage_A.real,voltage_A.imag" else "voltage_A.real,voltage_A.imag,voltage_B.real,voltage_B.imag,voltage_C.real,voltage_C.imag") + ";\n" +
            "            interval 5;\n" +
            "            file \"output_data/" + name + "_voltage.csv\";\n" +
            "        };\n"
        else
            ""
    }

//    def load_from_player_file (name: String, voltage: Double): String =
//    {
//        // assumes meter data files exist
//        // from Yamshid,
//        // then: for file in meter_data/*; do sed -i.bak '/Timestamp/d' $file; done
//        // and then: for file in meter_data/*; do sed -i.bak 's/\"//g' $file; done
//        val house = has (name)
//        // by supplying player files for only EnergyConsumer objects
//        // this existence test picks only HASXXXX nodes (i.e. not ABGXXXX or PINXXXX)
//        val ret =
//            if (exists ("meter_data/" + house + "_R.csv"))
//                "\n" +
//                "        object load\n" +
//                "        {\n" +
//                "            name \"" + name + "_load\";\n" +
//                "            parent \"" + name + "\";\n" +
//                "            phases ABCN;\n" +
//                "            nominal_voltage " + voltage + "V;\n" +
//                "            object player\n" +
//                "            {\n" +
//                "                property \"constant_current_A\";\n" +
//                "                file \"meter_data/" + house + "_R.csv\";\n" +
//                "            };\n" +
//                "            object player\n" +
//                "            {\n" +
//                "                property \"constant_current_B\";\n" +
//                "                file \"meter_data/" + house + "_S.csv\";\n" +
//                "            };\n" +
//                "            object player\n" +
//                "            {\n" +
//                "                property \"constant_current_C\";\n" +
//                "                file \"meter_data/" + house + "_T.csv\";\n" +
//                "            };\n" +
//                "        };\n"
//            else
//                ""
//        return (ret)
//    }

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