package ch.ninecode.gl

import ch.ninecode.net.LineData
import ch.ninecode.net.LineEdge
import ch.ninecode.util.Sequences

final case class GLMLineEdge
(
    override val data: LineData
)
extends LineEdge (data)
with GLMEdge
{
    /**
     * Emit a overhead or underground line.
     *
     * @param generator the driver program
     * @return A line string (.glm text) for this edge.
     */
    override def emit (generator: GLMGenerator): String =
    {
        // ToDo: with parallel cables of different length or type this is a problem:
        val conductor = lines.toIterator.next.Conductor
        // ToDo: use ProductAssetModel.usageKind (from AssetInfo.AssetModel)
        val typ = if (conductor.ConductingEquipment.Equipment.PowerSystemResource.PSRType == "PSRType_Underground")
            "underground_line"
        else
            "overhead_line"
        val length = conductor.len
        val config = configurationName
        val phases = if (generator.isSinglePhase) "AN" else "ABCN"
        s"""
          |        object $typ
          |        {
          |            name "$id";
          |            phases $phases;
          |            from "$cn1";
          |            to "$cn2";
          |            length ${length}m;
          |            configuration "$config";
          |        };
          |""".stripMargin
    }

    /**
     * Emit a GridLAB-D line_configuration.
     *
     * @param config    The name of the line configuration.
     * @param generator the driver program
     * @return A string suitable for inclusion in the .glm file.
     */
    def make_line_configuration (config: String, pli: Sequences, default: Boolean, generator: GLMGenerator): String =
    {
        val warning = if (default) s"#warning WARNING: using default line_configuration for $config\n" else ""
        val (diag, offd) =
            if (generator.isSinglePhase)
                (pli.z1, zero)
            else
                sequence2z (pli.z0, pli.z1)
        val dia = diag.asString (8) + " Ohm/m"
        val off = offd.asString (8) + " Ohm/m"
        val comment =  lines.map (line => s"            // ${line.id}").mkString ("\n", "\n", "")
        s"""
          |$warning        object line_configuration
          |        {$comment
          |            name "$config";
          |            z11 $dia;
          |            z12 $off;
          |            z13 $off;
          |            z21 $off;
          |            z22 $dia;
          |            z23 $off;
          |            z31 $off;
          |            z32 $off;
          |            z33 $dia;
          |        };
          |""".stripMargin
    }

    /**
     * Emit the configuration for the edge.
     *
     * @param generator the driver program
     * @return The configuration .glm element.
     */
    def configuration (generator: GLMGenerator): String =
    {
        val pli = data.perLengthImpedanceAt (generator.targetTemperature, data.lines.toIterator.next.CIMBaseTemperature)
        make_line_configuration (configurationName, pli, data.perLengthImpedanceIsDefault, generator)
    }
}
