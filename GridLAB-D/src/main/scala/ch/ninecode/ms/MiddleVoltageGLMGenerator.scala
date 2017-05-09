package ch.ninecode.ms

import java.text.SimpleDateFormat
import java.util.Calendar

import org.apache.spark.rdd.RDD

import ch.ninecode.gl._

case class USTKreis (
    transformers: Array[TData],
    nodes: Iterable[PreNode],
    edges: Iterable[Iterable[PreEdge]])
{
    val start_time = javax.xml.bind.DatatypeConverter.parseDateTime ("2017-05-04T12:00:00")
    val finish_time = start_time

    def trafokreis_key (): String =
    {
        transformers.map(_.transformer.id).sortWith(_ < _).mkString("_")
    }

    def swing_node () = transformers(0).node0
    def swing_node_voltage () = transformers(0).voltage0 * 1000.0
}

case class MiddleVoltageGLMGenerator (
        one_phase: Boolean,
        date_format: SimpleDateFormat,
        ust: USTKreis) extends GLMGenerator (one_phase, date_format)
{

    override def name: String = ust.trafokreis_key

    override def start_time = ust.start_time

    override def finish_time = ust.finish_time

    override def edge_groups: Iterable[Iterable[PreEdge]] = ust.edges

    override def transformers: Array[TData] = ust.transformers

    override def swing_node: String = ust.swing_node

    override def swing_node_voltage: Double = ust.swing_node_voltage

    override def nodes: Iterable[GLMNode] = ust.nodes

    override def extra (): Iterable[String] =
    {
        List ("")
    }

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