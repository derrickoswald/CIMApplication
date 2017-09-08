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

    override def transformers: Array[TData] = ust.transformers

    override def swing_node: String = ust.swing_node

    override def swing_node_voltage: Double = ust.swing_node_voltage

    override def nodes: Iterable[GLMNode] = ust.nodes

    override def extra: Iterable[String] = List ("")

    override def emit_node (node: GLMNode): String =
    {
        // or load_from_player_file (name, voltage)
        super.emit_node (node)// + generate_load (node)
    }

//    def generate_load (node: GLMNode): String =
//    {
//        val experiments = trafokreis.experiments
//        val house = nis_number (node.id)
//        val filtered = experiments.filter(p ⇒ p.house == house)
//        val experiment = if (0 != filtered.length) filtered(0) else null
//
//        if (null != experiment)
//            "\n" +
//            "        object load\n" +
//            "        {\n" +
//            "            name \"" + node.id + "_load\";\n" +
//            "            parent \"" + node.id + "\";\n" +
//            "            phases " + (if (one_phase) "AN" else "ABCN") + ";\n" +
//            "            nominal_voltage " + node.nominal_voltage + "V;\n" +
//            (if (one_phase)
//                "            object player\n" +
//                "            {\n" +
//                "                property \"constant_power_A\";\n" +
//                "                file \"input_data/" + house + ".csv\";\n" +
//                "            };\n"
//            else
//                "            object player\n" +
//                "            {\n" +
//                "                property \"constant_power_A\";\n" +
//                "                file \"input_data/" + house + "_R.csv\";\n" +
//                "            };\n" +
//                "            object player\n" +
//                "            {\n" +
//                "                property \"constant_power_B\";\n" +
//                "                file \"input_data/" + house + "_S.csv\";\n" +
//                "            };\n" +
//                "            object player\n" +
//                "            {\n" +
//                "                property \"constant_power_C\";\n" +
//                "                file \"input_data/" + house + "_T.csv\";\n" +
//                "            };\n") +
//            "        };\n" +
//            "\n" + // only need a recorder if there is a load
//            "        object recorder\n" +
//            "        {\n" +
//            "            name \"" + nis_number (node.id) + "_voltage_recorder\";\n" +
//            "            parent \"" + node.id + "\";\n" +
//            "            property " + ( if (one_phase) "voltage_A.real,voltage_A.imag" else "voltage_A.real,voltage_A.imag,voltage_B.real,voltage_B.imag,voltage_C.real,voltage_C.imag") + ";\n" +
//            "            interval 5;\n" +
//            "            file \"output_data/" + node.id + "_voltage.csv\";\n" +
//            "        };\n"
//        else
//            ""
//    }

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

}