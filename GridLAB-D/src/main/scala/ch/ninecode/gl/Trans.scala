package ch.ninecode.gl

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * @param one_phase - flag to indicate if single phase output is desired
 */
class Trans (one_phase: Boolean) extends Serializable
{
    val log: Logger = LoggerFactory.getLogger (getClass)

    /**
     * Make one or more transformer configurations.
     */
    def getTransformerConfigurations (transformers: Array[TransformerSet]): String =
    {
        val ret = new StringBuilder ()
        for (transformer ← transformers)
        {
            // see http://gridlab-d.sourceforge.net/wiki/index.php/Power_Flow_User_Guide#Transformer_Configuration_Parameters
            val config = transformer.configurationName
            val v0 = transformer.v0
            val v1 = transformer.v1
            val power_rating = transformer.power_rating
            val (total_impedance, default) = transformer.total_impedance
            ret.append (
                "\n" +
                (if (default) "#warning WARNING: using default impedance for " + config + "\n" else "\n") +
                "        object transformer_configuration\n" +
                "        {\n" +
                "            name \"" + config + "\";\n" +
                "            connect_type WYE_WYE;\n" + // ToDo: pick up Dyn5 values from CIM when they are exported correctly
                "            install_type PADMOUNT;\n" +
                "            power_rating " + (power_rating / 1000.0) + ";\n" +
                "            primary_voltage " + v0 + ";\n" +
                "            secondary_voltage " + v1 + ";\n" +
                "            resistance " + total_impedance.re + ";\n" +
                "            reactance " + total_impedance.im + ";\n" +
                "        };\n")
        }
        ret.toString
    }

    def emit (transformers: Array[TransformerSet]): String =
    {
        val ret = new StringBuilder ()
        for (transformer ← transformers)
        {
            val config = transformer.configurationName
            val name = transformer.transformer_name
            ret.append (
            "\n" +
            "        object transformer\n" +
            "        {\n" +
            "            name \"" + name + "\";\n" +
            "            phases " + (if (one_phase) "AN" else "ABCN") + ";\n" +
            "            from \"" + transformer.node0 + "\";\n" +
            "            to \"" + transformer.node1 + "\";\n" +
            "            configuration \"" + config + "\";\n" +
            "        };\n" +
            "\n" +
            "        object recorder\n" +
            "        {\n" +
            "            name \"" + name + "_current_recorder\";\n" +
            "            parent \"" + name + "\";\n" +
            "            property " + (if (one_phase) "current_out_A.real,current_out_A.imag" else "current_out_A.real,current_out_A.imag,current_out_B.real,current_out_B.imag,current_out_C.real,current_out_C.imag") + ";\n" +
            "            interval 5;\n" +
            "            file \"output_data/" + name + "_current.csv\";\n" +
            "        };\n")
        }
        ret.toString
    }
}
