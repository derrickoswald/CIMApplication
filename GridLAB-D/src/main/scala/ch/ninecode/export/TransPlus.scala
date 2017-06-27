package ch.ninecode.export

import ch.ninecode.gl.Trans
import ch.ninecode.gl.TData

class TransPlus (one_phase: Boolean) extends Trans (one_phase)
{
    override def emit (transformers: Array[TData]): String =
    {
        val config = configurationName (transformers)
        val name = transformer_name (transformers)

        // all transformers in the array have the same nodes
        "\n" +
        "        object transformer\n" +
        "        {\n" +
        "            name \"" + name + "_transformer\";\n" +
        "            phases " + (if (one_phase) "AN" else "ABCN") + ";\n" +
        "            from \"" + transformers.head.node0 + "\";\n" +
        "            to \"" + transformers.head.node1 + "\";\n" +
        "            configuration \"" + config + "\";\n" +
        "        };\n" +
        "\n" +
        "        object recorder\n" +
        "        {\n" +
        "            name \"" + name + "_current_recorder\";\n" +
        "            parent \"" + name + "_transformer\";\n" +
        "            property " + ( if (one_phase) "current_in_A.real,current_in_A.imag" else "current_in_A.real,current_in_A.imag,current_in_B.real,current_in_B.imag,current_in_C.real,current_in_C.imag") + ";\n" +
        "            interval 300;\n" +
        "            file \"output_data/" + name + "_current.csv\";\n" +
        "        };\n" +
        "\n" +
        "        object recorder\n" +
        "        {\n" +
        "            name \"" + name + "_power_recorder\";\n" +
        "            parent \"" + name + "_transformer\";\n" +
        "            property " + ( if (one_phase) "power_in_A.real,power_in_A.imag" else "power_in_A.real,power_in_A.imag,power_in_B.real,power_in_B.imag,power_in_C.real,power_in_C.imag") + ";\n" +
        "            interval 300;\n" +
        "            file \"output_data/" + name + "_power.csv\";\n" +
        "        };\n" +
        "\n" +
        "        object recorder\n" +
        "        {\n" +
        "            name \"" + name + "_losses_recorder\";\n" +
        "            parent \"" + name + "_transformer\";\n" +
        "            property " + ( if (one_phase) "power_losses_A.real,power_losses_A.imag" else "power_losses_A.real,power_losses_A.imag,power_losses_B.real,power_losses_B.imag,power_losses_C.real,power_losses_C.imag") + ";\n" +
        "            interval 300;\n" +
        "            file \"output_data/" + name + "_losses.csv\";\n" +
        "        };\n"
    }
}
