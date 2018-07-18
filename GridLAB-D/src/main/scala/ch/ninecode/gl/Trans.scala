package ch.ninecode.gl

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * @param one_phase - flag to indicate if single phase output is desired
 */
class Trans (one_phase: Boolean) extends Serializable
{
    val log: Logger = LoggerFactory.getLogger (getClass)

    def configuration (transformer: TransformerSet): String =
    {
        // see http://gridlab-d.sourceforge.net/wiki/index.php/Power_Flow_User_Guide#Transformer_Configuration_Parameters
        val config = transformer.configurationName
        val v0 = transformer.v0
        val v1 = transformer.v1
        val power_rating = transformer.power_rating
        val (total_impedance, default) = transformer.total_impedance_per_unit
        "\n" +
        (if (default) "#warning WARNING: using default impedance for " + config + "\n" else "") +
        "        object transformer_configuration\n" +
        "        {\n" +
        "            name \"" + config + "\";\n" +
        "            connect_type WYE_WYE;\n" + // ToDo: should be DELTA_GWYE (Dyn5), pick up windingConnection values from CIM (see https://www.answers.com/Q/What_is_the_meaning_of_DYN_11_on_a_transformer_nameplate)
        "            install_type PADMOUNT;\n" +
        "            power_rating " + (power_rating / 1000.0) + ";\n" +
        "            primary_voltage " + v0 + ";\n" +
        "            secondary_voltage " + v1 + ";\n" +
        "            resistance " + total_impedance.re + ";\n" +
        "            reactance " + total_impedance.im + ";\n" +
        "        };\n"
    }

    /**
     * Make one or more transformer configurations.
     */
    def getTransformerConfigurations (transformers: Iterable[TransformerSet]): Iterable[String] =
    {
        // if we assume assume all transformers with the same configuration name are interchangeable
        // transformers.groupBy (_.configurationName).values.map (x ⇒ configuration (x.head))
        transformers.map (configuration)
    }

    def emit (transformer: TransformerSet): String =
    {
        val config = transformer.configurationName
        val name = transformer.transformer_name

        "\n" +
        "        object transformer\n" +
        "        {\n" +
        "            name \"" + name + "\";\n" +
        "            phases " + (if (one_phase) "AN" else "ABCN") + ";\n" +
        "            from \"" + transformer.node0 + "\";\n" +
        "            to \"" + transformer.node1 + "\";\n" +
        "            configuration \"" + config + "\";\n" +
        "        };\n"
    }
}
