package ch.ninecode.lv

import java.text.SimpleDateFormat
import java.util.Calendar

import ch.ninecode.gl.GLMEdge
import ch.ninecode.gl.GLMGenerator
import ch.ninecode.gl.GLMNode
import ch.ninecode.gl.GLMLineEdge
import ch.ninecode.gl.SwingNode
import ch.ninecode.gl.GLMTransformerEdge

class LowVoltageGLMGenerator
(
    one_phase: Boolean,
    date_format: SimpleDateFormat,
    trafokreis: LowVoltageTrafokreis)
    extends GLMGenerator(one_phase, 20.0, date_format) // ToDo: get library base temperature and target temperature as command line input
{
    /**
     * Calendar duplication utility function.
     *
     * @param c The Calendar value to be cloned.
     */
    @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
    def dup (c: Calendar): Calendar = c.clone().asInstanceOf[Calendar]

    override def name: String = trafokreis.name

    override def start_time: Calendar = trafokreis.start

    override lazy val finish_time: Calendar =
    {
        val t = dup(start_time)
        t.add(Calendar.HOUR, 24)
        t
    }

    override lazy val edges: Iterable[GLMEdge] = trafokreis.edges.filter(_ match
    { case _: GLMTransformerEdge => false
        case _ => true
    })

    override lazy val transformers: Iterable[GLMTransformerEdge] = trafokreis.edges.flatMap(_ match
    { case tx: GLMTransformerEdge => Some(tx)
        case _ => None
    })

    // the swing node is the high voltage pin
    override lazy val swing_nodes: Iterable[GLMNode] =
        transformers.map(tx => SwingNode(tx.transformer.node0, tx.transformer.v0, tx.transformer.transformer_name))

    override def nodes: Iterable[GLMNode] = trafokreis.nodes

    override def emit_node (node: GLMNode): String =
    {
        val extra = node match
        {
            case lv: LowVoltageNode =>
                if (lv.isEnergyConsumer)
                    s"${generate_recorder(lv)}${generate_load(lv)}"
                else
                    ""
            case _ =>
                ""
        }
        s"${super.emit_node(node)}$extra"
    }

    override def emit_edge (edge: GLMEdge): String =
    {
        def recorders: String =
        {
            val id = edge.id
            val current = if (one_phase)
                "current_in_A.real,current_in_A.imag"
            else
                "current_in_A.real,current_in_A.imag,current_in_B.real,current_in_B.imag,current_in_C.real,current_in_C.imag"
            val power = if (one_phase)
                "power_in_A.real,power_in_A.imag"
            else
                "power_in_A.real,power_in_A.imag,power_in_B.real,power_in_B.imag,power_in_C.real,power_in_C.imag"
            val losses = if (one_phase)
                "power_losses_A.real,power_losses_A.imag"
            else
                "power_losses_A.real,power_losses_A.imag,power_losses_B.real,power_losses_B.imag,power_losses_C.real,power_losses_C.imag"
            s"""
               |        object recorder
               |        {
               |            name "${id}_current_recorder";
               |            parent "$id";
               |            property $current;
               |            interval 300;
               |            file "output_data/${id}_current.csv";
               |        };
               |
               |        object recorder
               |        {
               |            name "${id}_power_recorder";
               |            parent "$id";
               |            property $power;
               |            interval 300;
               |            file "output_data/${id}_power.csv";
               |        };
               |
               |        object recorder
               |        {
               |            name "${id}_losses_recorder";
               |            parent "$id";
               |            property $losses;
               |            interval 300;
               |            file "output_data/${id}_losses.csv";
               |        };
               |""".stripMargin
        }

        val rec = edge match
        {
            case _: GLMLineEdge => recorders
            case _ => ""
        }

        s"${super.emit_edge(edge)}$rec"
    }

    override def emit_slack (node: GLMNode, suffix:String=""): String =
    {
        node match
        {
            case swing: SwingNode =>
                // generate low voltage pin (NSPIN) swing node
                val trafo = swing.name
                val player = if (one_phase)
                    s"""            object player
                       |            {
                       |                property "voltage_A";
                       |                file "input_data/${trafo}.csv";
                       |            };
                       |""".stripMargin
                else
                    s"""            object player
                       |            {
                       |                property "voltage_A";
                       |                file "input_data/${trafo}_R.csv";
                       |            };
                       |            object player
                       |            {
                       |                property "voltage_B";
                       |                file "input_data/${trafo}_S.csv";
                       |            };
                       |            object player
                       |            {
                       |                property "voltage_C";
                       |                file "input_data/${trafo}_T.csv";
                       |            };
                       |""".stripMargin
                val id = swing.id
                val current = if (one_phase)
                    "measured_current_A.real,measured_current_A.imag"
                else
                    "measured_current_A.real,measured_current_A.imag,measured_current_B.real,measured_current_B.imag,measured_current_C.real,measured_current_C.imag"
                val power = if (one_phase)
                    "measured_power_A.real,measured_power_A.imag"
                else
                    "measured_power_A.real,measured_power_A.imag,measured_power_B.real,measured_power_B.imag,measured_power_C.real,measured_power_C.imag"
                s"""
                   |        object meter
                   |        {
                   |            name "$id";
                   |            phases ${if (one_phase) "AN" else "ABCN"};
                   |            bustype SWING;
                   |            nominal_voltage ${swing.nominal_voltage}V;
                   |$player
                   |        };
                   |
                   |        object recorder
                   |        {
                   |            name "${trafo}_current_recorder";
                   |            parent "$id";
                   |            property $current;
                   |            interval 300;
                   |            file "output_data/${trafo}_current.csv";
                   |        };
                   |
                   |        object recorder
                   |        {
                   |            name "${trafo}_power_recorder";
                   |            parent "$id";
                   |            property $power;
                   |            interval 300;
                   |            file "output_data/${trafo}_power.csv";
                   |        };
                   |""".stripMargin
            case _ =>
                ""
        }
    }

    override def emit_transformer (transformer: GLMTransformerEdge): String =
    {
        val name = transformer.transformer.transformer_name

        val swings = swing_nodes.map(_.id).toArray
        super.emit_transformer(transformer) +
            (if (!swings.contains(transformer.cn1) && !swings.contains(transformer.cn2))
            {
                val current = if (one_phase)
                    "current_in_A.real,current_in_A.imag"
                else
                    "current_in_A.real,current_in_A.imag,current_in_B.real,current_in_B.imag,current_in_C.real,current_in_C.imag"
                val power = if (one_phase)
                    "power_in_A.real,power_in_A.imag"
                else
                    "power_in_A.real,power_in_A.imag,power_in_B.real,power_in_B.imag,power_in_C.real,power_in_C.imag"
                val losses = if (one_phase)
                    "power_losses_A.real,power_losses_A.imag"
                else
                    "power_losses_A.real,power_losses_A.imag,power_losses_B.real,power_losses_B.imag,power_losses_C.real,power_losses_C.imag"
                s"""
                   |        object recorder
                   |        {
                   |            name "${name}_current_recorder";
                   |            parent "$name";
                   |            property $current;
                   |            interval 300;
                   |            file "output_data/${name}_current.csv";
                   |        };
                   |
                   |        object recorder
                   |        {
                   |            name "${name}_power_recorder";
                   |            parent "$name";
                   |            property $power;
                   |            interval 300;
                   |            file "output_data/${name}_power.csv";
                   |        };
                   |
                   |        object recorder
                   |        {
                   |            name "${name}_losses_recorder";
                   |            parent "$name";
                   |            property $losses;
                   |            interval 300;
                   |            file "output_data/${name}_losses.csv";
                   |        };""".stripMargin
            }
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
        val n = string.indexOf("_")
        if (0 < n)
            string.substring(0, n)
        else
            string
    }

    def generate_recorder (node: LowVoltageNode): String =
    {
        if (node.isEnergyConsumer)
        {
            val id = node.id
            val house = nis_number(id)
            val voltage = if (one_phase)
                "voltage_A.real,voltage_A.imag"
            else
                "voltage_A.real,voltage_A.imag,voltage_B.real,voltage_B.imag,voltage_C.real,voltage_C.imag"
            val power = if (one_phase)
                "power_A.real,power_A.imag"
            else
                "power_A.real,power_A.imag,power_B.real,power_B.imag,power_C.real,power_C.imag"
            s"""
               |        object recorder
               |        {
               |            name "${house}_voltage_recorder";
               |            parent "$id";
               |            property $voltage;
               |            interval 300;
               |            file "output_data/${id}_voltage.csv\";
               |        };
               |
               |        object recorder
               |        {
               |            name "${house}_power_recorder\";
               |            parent "$id";
               |            property $power;
               |            interval 300;
               |            file "output_data/${id}_power.csv";
               |        };""".stripMargin
        }
        else
            ""
    }

    def generate_load (node: LowVoltageNode): String =
    {
        if (node.isEnergyConsumer)
        {
            val id = node.id
            val house = nis_number(node.equipment)
            val player = if (one_phase)
                s"""            object player
                   |            {
                   |                property "constant_current_A";
                   |                file "input_data/$house.csv\";
                   |            };""".stripMargin
            else
                s"""            object player
                   |            {
                   |                property "constant_current_A";
                   |                file "input_data/${house}_R.csv";
                   |            };
                   |            object player
                   |            {
                   |                property \"constant_current_B\";
                   |                file "input_data/${house}_S.csv";
                   |            };
                   |            object player
                   |            {
                   |                property "constant_current_C";
                   |                file "input_data/${house}_T.csv";
                   |            };
                   |""".stripMargin
            s"""
               |        object load
               |        {
               |            name "${id}_load";
               |            parent "$id";
               |            phases ${if (one_phase) "AN" else "ABCN"};
               |            nominal_voltage ${node.nominal_voltage}V;
               |$player
               |        };""".stripMargin
        }
        else
            ""
    }
}
