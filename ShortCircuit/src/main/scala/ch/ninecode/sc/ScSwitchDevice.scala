package ch.ninecode.sc

import ch.ninecode.gl.GLMEdge
import ch.ninecode.gl.SwitchDevice
import ch.ninecode.model.Switch

class ScSwitchDevice (override val one_phase: Boolean) extends SwitchDevice (one_phase)
{
    override def emit (edge: GLMEdge, switch: Switch, fuse: Boolean = false): String =
    {
        val status = if (switch.normalOpen) "OPEN" else "CLOSED"
        var current = 9999.0 // ensure it doesn't trip
        val details = if (fuse)
            "            mean_replacement_time 3600.0;\n" +
            "            current_limit " + current + "A;\n"
        else
            ""
        "\n" +
            "        object " + (if (fuse) "fuse" else "switch") + "\n" +
            "        {\n" +
            "            name \"" + edge.id + "\";\n" +
            "            phases " + (if (one_phase) "AN" else "ABCN") + ";\n" +
            "            from \"" + edge.cn1 + "\";\n" +
            "            to \"" + edge.cn2 + "\";\n" +
            details +
            "            status \"" + status + "\";\n" +
            "        };\n"
    }
}
