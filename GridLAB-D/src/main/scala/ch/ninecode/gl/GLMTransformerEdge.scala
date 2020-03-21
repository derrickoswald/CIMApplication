package ch.ninecode.gl

import ch.ninecode.net.TransformerEdge
import ch.ninecode.net.TransformerSet

case class GLMTransformerEdge
(
    transformer: TransformerSet
)
extends TransformerEdge (transformer)
with GLMEdge
{
    /**
     * Emit a transformer.
     *
     * @param generator the driver program
     * @return A transformer string (.glm text) for this edge.
     */
    override def emit (generator: GLMGenerator): String =
    {
        val phase = if (generator.isSinglePhase) "AN" else "ABCN"
        if (multiwinding)
        {
            val intermediate = s"${transformer.node0}_intermediate"
            val int =
                s"""
                  |        object meter
                  |        {
                  |            name "$intermediate";
                  |            phases $phase;
                  |            bustype PQ;
                  |            nominal_voltage ${transformer.transformers(0).voltages(0)._2}V;
                  |        };
                  |""".stripMargin
            val prim =
            s"""
                |        object transformer
                |        {
                |            name "${transformer.transformer_name}";
                |            phases $phase;
                |            from "${transformer.node0}";
                |            to "$intermediate";
                |            configuration "$configurationName";
                |        };
                |""".stripMargin
            val sec = for (winding <- lv_windings)
                yield
                {
                    val config = configurationName
                    val number = winding.TransformerEnd.endNumber - 1
                    val conf = s"${config}_winding_$number"

                    s"""
                        |        object transformer
                        |        {
                        |            name "${transformer.transformer_name}_$number";
                        |            phases $phase;
                        |            from "$intermediate";
                        |            to "${transformer.transformers(0).terminals(number).TopologicalNode}";
                        |            configuration "$conf";
                        |        };
                        |""".stripMargin
                }
            int + prim + sec.mkString
        }
        else
        s"""
            |        object transformer
            |        {
            |            name "${transformer.transformer_name}";
            |            phases $phase;
            |            from "${transformer.node0}";
            |            to "${transformer.node1}";
            |            configuration "$configurationName";
            |        };
            |""".stripMargin
    }

    def configuration (generator: GLMGenerator, remark: String): String =
    {
        // see http://gridlab-d.sourceforge.net/wiki/index.php/Power_Flow_User_Guide#Transformer_Configuration_Parameters
        val config = configurationName
        val (total_impedance, default) = transformer.total_impedance_per_unit
        val warn = if (default) s"\n#warning WARNING: using default impedance for $config" else ""
        val comment = if (null == remark)
            transformer.transformers.map (trafo => s"            // ${trafo.transformer.id}").mkString ("\n")
        else
            s"            // $remark"
        // ToDo: should be DELTA_GWYE (Dyn5), pick up windingConnection values from CIM (see https://www.answers.com/Q/What_is_the_meaning_of_DYN_11_on_a_transformer_nameplate)
        val connect = if (generator.isSinglePhase) "WYE_WYE" else "DELTA_GWYE"

        val ret =
        s"""$warn
            |        object transformer_configuration
            |        {
            |$comment
            |            name "$config";
            |            connect_type $connect;
            |            install_type PADMOUNT;
            |            power_rating ${transformer.power_rating / 1000.0};
            |            primary_voltage ${transformer.v0};
            |            secondary_voltage ${if (multiwinding) transformer.v0 else transformer.v1};
            |            resistance ${total_impedance.re};
            |            reactance ${total_impedance.im};
            |        };
            |""".stripMargin

        val w = if (multiwinding)
        {
            // we need to emit pseudo configurations
            val c = for (winding <- lv_windings)
                yield
                {
                    val number = winding.TransformerEnd.endNumber - 1
                    val comment = s"\n            // multi-winding transformer low voltage winding $number"
                    val conf = s"${config}_winding_$number"
                    val v = transformer.transformers(0).voltages(number)._2

                    s"""
                        |        object transformer_configuration
                        |        {
                        |$comment
                        |            name "$conf";
                        |            connect_type $connect;
                        |            power_rating ${transformer.power_rating / 1000.0};
                        |            primary_voltage ${transformer.v0};
                        |            secondary_voltage $v;
                        |            resistance 1e-9;
                        |            reactance 1e-9;
                        |        };
                        |""".stripMargin
                }
            c.mkString
        }
        else
            ""

        ret + w
    }
}