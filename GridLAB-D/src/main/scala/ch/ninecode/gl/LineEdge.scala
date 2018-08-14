package ch.ninecode.gl

import ch.ninecode.model.ACLineSegment

case class LineEdge
(
    node1: String,
    node2: String,
    lines: Iterable[ACLineSegment],
    base_temperature: Double = 20.0,
    target_temperature: Double = 60.0,
    DEFAULT_R: Double = 0.225,
    DEFAULT_X: Double = 0.068
)
extends GLMEdge
{
    // ToDo: take the id of the first cable of a set of parallel cables
    def id: String = lines.head.Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.mRID

    /**
     * The node id connected to the first terminal.
     *
     * @return The ID of the ConnectivityNode or TopologicalNode reference by the Terminal with sequence number 1.
     */
    def cn1: String = node1

    /**
     * The node id connected to the second terminal.
     *
     * @return The ID of the ConnectivityNode or TopologicalNode reference by the Terminal with sequence number 2.
     */
    def cn2: String = node2

    /**
     * Emit a overhead or underground line.
     *
     * @param one_phase If <code>true</code>, emit a single phase node, otherwise emit a three phase node.
     * @return A line string (.glm text) for this edge.
     */
    override def emit (one_phase: Boolean = false): String =
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
          |""".stripMargin.format (typ,id, if (one_phase) "AN" else "ABCN", cn1, cn2, length, config)
    }

    /**
     * Temperature coefficient of resistance.
     *
     * A compromise between copper (0.00393) and aluminum (0.00403) /°C
     *
     * It's complicated (http://nvlpubs.nist.gov/nistpubs/bulletin/07/nbsbulletinv7n1p71_A2b.pdf) and depends on the
     * alloy and how the wire is drawn and worked, e.g.
     * "good commercial copper furnished for use as electrical conductors, the average deviation of C from the mean value
     * 0.00393<sub>8</sub> is only o.ooooo<sub>8</sub>, or 0.2%. Also, when the conductivity and temperature coefficient
     * are altered by annealing or hard-drawing, C has been found to remain constant within the experimental error."
     */
    val alpha: Double = 0.004

    /**
     * Zero ohms.
     */
    lazy val zero = Complex (0.0, 0.0)

    /**
     * Default impedance at the target temperature.
     */
    lazy val DEFAULT_Z = Complex (resistanceAt (base_temperature, target_temperature, DEFAULT_R), DEFAULT_X)

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
            psr.IdentifiedObject.name // ToDo: this is a NIS specific characteristic where the ACLineSegment.name is the article.type
    }

    /**
     * Generate a valid configuration name.
     *
     * Use the given string, usually a library type description (e.g. "GKN 3x95se/95 1/0.6 kV" or "4x95, Ceanderkabel",
     * to create a valid GridLAB-D configuration name.
     * The intent is to allow human-readable configuration names while adhering to GrdLAB-D rules such as:
     *
     * - no leading digits: ERROR    [INIT] : object name '4x4' invalid, names must start with a letter or an underscore
     * - no decimal points: KLE199604 (underground_line:227) reference to TT 3x2.5 is missing match value
     *
     */
    def valid_config_name (string: String): String =
    {
        val s = if ((null == string) || ("" == string))
            "unknown"
        else
            if (string.charAt (0).isLetter || ('_' == string.charAt (0)))
                string
            else
                "_" + string
        s.replace (".", "d")
    }

    /**
     * Get the configuration name (of the parallel lines).
     *
     * @return An appropriate line configuration name.
     */
    def configurationName: String =
    {
        val n = lines.map (config_name).map (valid_config_name).toArray.sortWith (_ < _).mkString ("||") + "_configuration"
        // limit to 64 bytes with null:
        // typedef struct s_objecttree {
        //     char name[64];
        //     OBJECT *obj;
        //     struct s_objecttree *before, *after;
        //     int balance; /* unused */
        // } OBJECTTREE;
        if (n.getBytes.length > 63)
            "_" + Math.abs (n.hashCode())
        else
            n
    }

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
     * Temperature adjusted resistance.
     *
     * @param temperature target temperature (°C)
     * @param base current temperature for the given resistance (°C)
     * @param r the given resistance (Ω)
     * @return the temperature compensated resistance (Ω)
     */
    def resistanceAt (temperature: Double, base: Double, r: Double): Double = (1.0 + (alpha * (temperature - base))) * r

    /**
     * Convert the 0/1 sequence values from the CIM format into a Z matrix.
     *
     * @param r ACLineSegment.r value
     * @param x ACLineSegment.x value
     * @param r0 ACLineSegment.r0 value
     * @param x0 ACLineSegment.x0 value
     * @param one_phase If <code>true</code>, calculate a single phase matrix, otherwise calculate a three phase matrix.
     * @return The diagonal and off-diagonal values for the Z matrix representation of line impedance,
     * plus a boolean flag indicating whether the values are the default <code>true</code> or not <code>false</code>.
     */
    def zMatrixValues (r: Double, x: Double, r0: Double, x0: Double, one_phase: Boolean = false): (Complex, Complex, Boolean) =
    {
        if ((0.0 != r) && (0.0 != x))
        {
            val z1 = Complex (resistanceAt (target_temperature, base_temperature, r), x)
            if (one_phase)
                (z1, zero, false)
            else
            {
                val z0 = Complex (resistanceAt (target_temperature, base_temperature, r0), x0)
                val (diag, off) = sequence2z (z0, z1)
                (diag, off, false)
            }
        }
        else
            (DEFAULT_Z, zero, true)
    }

    /**
     * Emit a GridLAB-D line_configuration.
     *
     * @param config The name of the line configuration.
     * @param r ACLineSegment.r value
     * @param x ACLineSegment.x value
     * @param r0 ACLineSegment.r0 value
     * @param x0 ACLineSegment.x0 value
     * @param one_phase If <code>true</code>, emit a single phase configuration, otherwise emit a three phase configuration.
     * @return A string suitable for inclusion in the .glm file.
     */
    def make_line_configuration (config: String, r: Double, x: Double, r0: Double, x0: Double, one_phase: Boolean = false): String =
    {
        val (diag, offd, default) = zMatrixValues (r, x, r0, x0, one_phase)
        // ToDo: fix this /km multiplier on the impedance
        val dia = diag.toString () + " Ohm/km"
        val off = offd.toString () + " Ohm/km"
        val warning = if (default) "#warning WARNING: using default line_configuration for " + config + "\n" else ""
        """
          |%s        object line_configuration
          |        {
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
          |""".stripMargin.format (warning, config, dia, off, off, off, dia, off, off, off, dia)
    }


    /**
     * Compute parallel impedance.
     *
     * See: http://hyperphysics.phy-astr.gsu.edu/hbase/electric/imped.html
     *
     * @param r1 Real part of impedance 1.
     * @param x1 Imaginary part of impedance 1.
     * @param r2 Real part of impedance 2.
     * @param x2 Imaginary part of impedance 2.
     * @return The parallel impedance (real, imaginary).
     */
    def parallel (r1: Double, x1: Double, r2: Double, x2: Double): (Double, Double) =
    {
        val rs = r1 + r2
        val xs = x1 + x2
        val rp = r1 * r2
        val xp = x1 * x2
        val r = (((rp - xp) * rs) + (((x1 * r2) + (x2 * r1)) * xs)) / ((rs * rs) + (xs * xs))
        val x = ((((x1 * r2) + (x2 * r1)) * rs) - ((rp - xp) * xs)) / ((rs * rs) + (xs * xs))
        (r, x)
    }

    /**
     * Emit the configuration for the edge.
     *
     * @param one_phase If <code>true</code>, emit a single phase configuration, otherwise emit a three phase configuration.
     * @return The configuration .glm element.
     */
    def configuration (one_phase: Boolean = false): String =
    {
        val line = lines.head
        var rt = if (0 == line.r) DEFAULT_R else line.r
        var xt = if (0 == line.x) DEFAULT_X else line.x
        var r0t = if (0 == line.r0) DEFAULT_R else line.r0
        var x0t = if (0 == line.x0) DEFAULT_X else line.x0
        for (line ← lines.tail)
        {
            val rl = if (0 == line.r) DEFAULT_R else line.r
            val xl = if (0 == line.x) DEFAULT_X else line.x
            val rl0 = if (0 == line.r0) DEFAULT_R else line.r0
            val xl0 = if (0 == line.x0) DEFAULT_X else line.x0
            val (r, x) = parallel (rt, xt, rl, xl)
            val (r0, x0) = parallel (r0t, x0t, rl0, xl0)
            rt = r
            xt = x
            r0t = r0
            x0t = x0
        }
        make_line_configuration (configurationName, rt, xt, r0t, x0t, one_phase)
    }
}