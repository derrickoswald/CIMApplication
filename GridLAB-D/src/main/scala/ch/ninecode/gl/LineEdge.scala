package ch.ninecode.gl

import ch.ninecode.net.LineData
import ch.ninecode.model.ACLineSegment
import ch.ninecode.util.Complex
import ch.ninecode.util.Sequences

final case class LineEdge
(
    data: LineData
)
    extends GLMEdge
{
    /**
     * Return the name of the (possibly parallel) line.
     *
     * Uses the concatenation of the alphabetically sorted names of the ACLineSegments that make up this line.
     *
     * @return The ID of the edge (the mRID of the electrical element).
     */
    def id: String = lines.map (_.id).toArray.sortWith (_ < _).mkString ("_")

    def lines: Iterable[ACLineSegment] = data.lines.map (_.line)

    def cn1: String = data.node0

    def cn2: String = data.node1

    /**
     * Emit a overhead or underground line.
     *
     * @param generator the driver program
     * @return A line string (.glm text) for this edge.
     */
    override def emit (generator: GLMGenerator): String =
    {
        // ToDo: with parallel cables of different length or type this is a problem:
        val conductor = lines.head.Conductor
        // ToDo: use ProductAssetModel.usageKind (from AssetInfo.AssetModel)
        val typ = if (conductor.ConductingEquipment.Equipment.PowerSystemResource.PSRType == "PSRType_Underground")
            "underground_line"
        else
            "overhead_line"
        val length = conductor.len
        val config = configurationName
        """
          |        object %s
          |        {
          |            name "%s";
          |            phases %s;
          |            from "%s";
          |            to "%s";
          |            length %sm;
          |            configuration "%s";
          |        };
          |""".stripMargin.format (typ, id, if (generator.isSinglePhase) "AN" else "ABCN", cn1, cn2, length, config)
    }

    /**
     * Get the cable/wire type.
     *
     * @param line The ACLineSegment object to get the type from.
     * @return The name of the type.
     */
    def config_name (line: ACLineSegment): String =
    {
        val psr = line.Conductor.ConductingEquipment.Equipment.PowerSystemResource
        if (null != psr.AssetDatasheet)
            psr.AssetDatasheet // ToDo: this is not really what is needed, it should be ConcentricNeutralCableInfo.name e.g. GKN 3x16rm/16 1/0.6 kV
        else
            if (null != psr.IdentifiedObject.name)
                psr.IdentifiedObject.name // ToDo: this is a NIS specific characteristic where the ACLineSegment.name is the article.type
            else
                "_" + line.hashCode.toString
    }

    /**
     * Get the configuration name (of the parallel lines).
     *
     * @return An appropriate line configuration name.
     */
    def configurationName: String =
    {
        val n = lines.map (config_name).map (valid_config_name).toArray.sortWith (_ < _).mkString ("||") + "_configuration"
        // limit to 64 bytes (GridLAB-D OBJECTTREE.name is an array of 64 bytes) with null:
        if (n.getBytes.length > 63)
            "_" + Math.abs (n.hashCode ())
        else
            n
    }

    /**
     * Zero ohms.
     */
    lazy val zero: Complex = Complex (0.0, 0.0)

    /**
     * Convert zero and positive sequence impedance values into the Z matrix diagonal and off diagonal elements.
     *
     * @param z0 Zero sequence impedance value from the CIM ACLineSegment parameters.
     * @param z1 Positive sequence impedance value from the CIM ACLineSegment parameters.
     * @return The diagonal and off-diagonal values for the GridLAB-D Z matrix representation of line impedance.
     */
    def sequence2z (z0: Complex, z1: Complex): (Complex, Complex) =
    {
        val Z0 = z0
        val Z1, Z2 = z1
        val diag = (Z0 + Z1 + Z2) / 3
        val root3 = Math.sqrt (3.0)
        val x1 = Z1 * Complex (-0.5, root3 / 2.0)
        val x2 = Z2 * Complex (-0.5, root3 / -2.0)
        val off = (Z0 + x1 + x2) / 3.0
        (diag, off)
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
        val comment =  lines.map (line =>
            "            // %s".format (line.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.name)).mkString ("\n", "\n", "")
        """
          |%s        object line_configuration
          |        {%s
          |            name "%s";
          |            z11 %s;
          |            z12 %s;
          |            z13 %s;
          |            z21 %s;
          |            z22 %s;
          |            z23 %s;
          |            z31 %s;
          |            z32 %s;
          |            z33 %s;
          |        };
          |""".stripMargin.format (warning, comment, config, dia, off, off, off, dia, off, off, off, dia)
    }

    /**
     * Emit the configuration for the edge.
     *
     * @param generator the driver program
     * @return The configuration .glm element.
     */
    def configuration (generator: GLMGenerator): String =
    {
        val pli = data.perLengthImpedanceAt (generator.targetTemperature)
        make_line_configuration (configurationName, pli, data.perLengthImpedanceIsDefault, generator)
    }
}
