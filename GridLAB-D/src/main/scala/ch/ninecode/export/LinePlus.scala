package ch.ninecode.export

import ch.ninecode.gl.GLMEdge
import ch.ninecode.gl.Line

import ch.ninecode.model.ACLineSegment

class LinePlus (one_phase: Boolean) extends Line (one_phase)
{
    override def emit (edges: Iterable[GLMEdge]): String =
    {
        val edge = edges.head
        val line = edge.el.asInstanceOf[ACLineSegment]
        val typ = if (line.Conductor.ConductingEquipment.Equipment.PowerSystemResource.PSRType == "PSRType_Underground")
            "underground_line"
        else
            "overhead_line"
        val config = configurationName (edges)

        "\n" +
        "        object " + typ + "\n" +
        "        {\n" +
        "            name \"" + edge.id + "_line\";\n" +
        "            phases " + (if (one_phase) "AN" else "ABCN") + ";\n" +
        "            from \"" + edge.cn1 + "\";\n" +
        "            to \"" + edge.cn2 + "\";\n" +
        "            length " + line.Conductor.len + "m;\n" +
        "            configuration \"" + config + "\";\n" +
        "        };\n" +
        "\n" +
        "        object recorder\n" +
        "        {\n" +
        "            name \"" + edge.id + "_current_recorder\";\n" +
        "            parent \"" + edge.id + "_line\";\n" +
        "            property " + ( if (one_phase) "current_in_A.real,current_in_A.imag" else "current_in_A.real,current_in_A.imag,current_in_B.real,current_in_B.imag,current_in_C.real,current_in_C.imag") + ";\n" +
        "            interval 300;\n" +
        "            file \"output_data/" + edge.id + "_current.csv\";\n" +
        "        };\n" +
        "\n" +
        "        object recorder\n" +
        "        {\n" +
        "            name \"" + edge.id + "_power_recorder\";\n" +
        "            parent \"" + edge.id + "_line\";\n" +
        "            property " + ( if (one_phase) "power_in_A.real,power_in_A.imag" else "power_in_A.real,power_in_A.imag,power_in_B.real,power_in_B.imag,power_in_C.real,power_in_C.imag") + ";\n" +
        "            interval 300;\n" +
        "            file \"output_data/" + edge.id + "_power.csv\";\n" +
        "        };\n" +
        "\n" +
        "        object recorder\n" +
        "        {\n" +
        "            name \"" + edge.id + "_losses_recorder\";\n" +
        "            parent \"" + edge.id + "_line\";\n" +
        "            property " + ( if (one_phase) "power_losses_A.real,power_losses_A.imag" else "power_losses_A.real,power_losses_A.imag,power_losses_B.real,power_losses_B.imag,power_losses_C.real,power_losses_C.imag") + ";\n" +
        "            interval 300;\n" +
        "            file \"output_data/" + edge.id + "_losses.csv\";\n" +
        "        };\n"
    }
}
