package ch.ninecode.gl

import ch.ninecode.net.TransformerEdge
import ch.ninecode.net.TransformerSet

case class GLMTransformerEdge
(
    override val transformer: TransformerSet
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
            val sec = for (index <- lv_windings.indices)
                yield
                {
                    val config = configurationName
                    val number = lv_windings(index).TransformerEnd.endNumber - 1
                    val conf = s"${config}_winding_$number"

                    s"""
                        |        object transformer
                        |        {
                        |            name "${transformer.transformer_name}_$number";
                        |            phases $phase;
                        |            from "${transformer.node0}";
                        |            to "${transformer.transformers(0).nodes(index+1).id}";
                        |            configuration "$conf";
                        |        };
                        |""".stripMargin
                }
            sec.mkString
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

        def nonNegative (d: Double): Double =
        {
            val n = math.abs (d)
            if (n < 1e-7) 1e-7 else n
        }

        if (!multiwinding)
        {
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
            |            resistance ${nonNegative (total_impedance.re)};
            |            reactance ${nonNegative (total_impedance.im)};
            |        };
            |""".stripMargin
        }  else {

            val array = for ( index <- lv_windings.indices)
               yield {
                   val number = lv_windings(index).TransformerEnd.endNumber - 1
                   val power = lv_windings(index).ratedS
                   val voltsPrimary = transformer.v0
                   val voltsSecondary = transformer.transformers(0).voltages(index+1)._2
                   //Zohms = Zpu * Zbase
                   //Zbase = Vll^2 / Sbase
                   val Zbase = voltsSecondary*voltsSecondary / power
                   val Rpu = lv_windings(index).r / Zbase
                   val Xpu = lv_windings(index).x / Zbase
                       s"""$warn
                      |        object transformer_configuration
                      |        {
                      |$comment
                      |            name ${config}_winding_$number;
                      |            connect_type $connect;
                      |            install_type PADMOUNT;
                      |            power_rating ${power / 1000.0};
                      |            primary_voltage ${voltsPrimary};
                      |            secondary_voltage ${voltsSecondary};
                      |            resistance ${nonNegative (Rpu)};
                      |            reactance ${nonNegative (Xpu)};
                      |        };
                      |""".stripMargin
               }
            array.mkString
        }
    }
}