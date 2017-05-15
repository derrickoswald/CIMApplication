package ch.ninecode.gl

import ch.ninecode.model.Switch

class SwitchDevice (val one_phase: Boolean) extends Serializable
{
    def emit (edge: GLMEdge, switch: Switch, fuse: Boolean = false): String =
    {
        val status = if (switch.normalOpen) "OPEN" else "CLOSED"
        var current = switch.ratedCurrent
        if (current <= 0) current = 9999.0 // ensure it doesn't trip
        val details = if (fuse)
            "            mean_replacement_time 3600.0;\n" + // sometimes: WARNING  [INIT] : Fuse:SIG8494 has a negative or 0 mean replacement time - defaulting to 1 hour
            "            current_limit " + current + "A;\n"
        else
            "            status \"" + status + "\";\n"

        "\n" +
        "        object " + (if (fuse) "fuse" else "switch") + "\n" +
        "        {\n" +
        "            name \"" + edge.id + "\";\n" +
        "            phases " + (if (one_phase) "AN" else "ABCN") + ";\n" +
        "            from \"" + edge.cn1 + "\";\n" +
        "            to \"" + edge.cn2 + "\";\n" +
        details +
        "        };\n"
    }
}