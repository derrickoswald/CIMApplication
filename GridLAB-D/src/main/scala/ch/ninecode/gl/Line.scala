package ch.ninecode.gl

import ch.ninecode.model.ACLineSegment

/**
 * GridLAB-D emitting functions for ACLineSegments.
 *
 * Provides methods to emit <em>line_configuration</em> and <em>underground_line</em> or <em>overhead_line</em> elements.
 * Allows for
 *
 * @param one_phase If <code>true</code> generate single phase .glm elements.
 * @param base_temperature The assumed temperature for line impedance (the library temperature).
 * @param target_temperature The desired temperature for line impedance (the simulation temperature).
 * @param DEFAULT_R The default line resistance if none is provided.
 * @param DEFAULT_X The default line reactance if none is provided.
 */
class Line (
    one_phase: Boolean = true,
    base_temperature: Double = 20.0,
    target_temperature: Double = 20.0,
    DEFAULT_R: Double = 0.225,
    DEFAULT_X: Double = 0.068
)
extends
    Serializable
{
    /**
     * Zero ohms.
     */
    lazy val zero = Complex (0.0, 0.0)

    /**
     * Default impedance at the target temperature.
     */
    lazy val DEFAULT_Z = Complex (resistanceAt (base_temperature, target_temperature, DEFAULT_R), DEFAULT_X)

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
     * Temperature adjusted resistance.
     *
     * @param temperature target temperature (°C)
     * @param base current temperature for the given resistance (°C)
     * @param r the given resistance (Ω)
     * @return the temperature compensated resistance (Ω)
     */
    def resistanceAt (temperature: Double, base: Double, r: Double): Double = (1.0 + (alpha * (temperature - base))) * r

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
     * Convert the 0/1 sequence values from the CIM format into a Z matrix.
     *
     * @param r ACLineSegment.r value
     * @param x ACLineSegment.x value
     * @param r0 ACLineSegment.r0 value
     * @param x0 ACLineSegment.x0 value
     * @return The diagonal and off-diagonal values for the Z matrix representation of line impedance,
     * plus a boolean flag indicating whether the values are the default <code>true</code> or not <code>false</code>.
     */
    def zMatrixValues (r: Double, x: Double, r0: Double, x0: Double): (Complex, Complex, Boolean) =
    {
        if ((0.0 != r) && (0.0 != x))
        {
            val z1 = Complex (resistanceAt (base_temperature, target_temperature, r), x)
            if (one_phase)
                (z1, zero, false)
            else
            {
                val z0 = Complex (resistanceAt (base_temperature, target_temperature, r0), x0)
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
     * @return A string suiatable for inclusion in the .glm file.
     */
    def make_line_configuration (config: String, r: Double, x: Double, r0: Double, x0: Double): String =
    {
        val (diag, offd, default) = zMatrixValues (r, x, r0, x0)
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
     * Predicate to check if this is an ACLineSegment.
     *
     * @param iter The set of edges.
     * @return <code>true</code> when all edges are ACLineSegment, false otherwise.
     */
    def isACLineSegment (iter: Iterable[GLMEdge]): Boolean =
    {
        iter.forall (_.el.getClass.getName.endsWith ("ACLineSegment"))
    }

    /**
     * Get the configuration name (of the parallel lines).
     *
     * @param iter The set of edges.
     * @return An appropriate line configuration name.
     */
    def configurationName (iter: Iterable[GLMEdge]): String =
    {
        val n = iter.map (
            x ⇒
            {
                val psr = x.el.asInstanceOf[ACLineSegment].Conductor.ConductingEquipment.Equipment.PowerSystemResource
                if (null != psr.AssetDatasheet)
                    psr.AssetDatasheet
                else
                    psr.IdentifiedObject.name
            }
        )
        .map (valid_config_name).toArray.sortWith (_ < _).mkString ("||") + "_configuration"
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
     * Emit the configuration for the set of edges.
     *
     * @param name The name of the configuration that is to be created.
     * @param edges The edges for which the configuration is to be created.
     * @return The configuration .glm element.
     */
    def configuration (name: String, edges: Iterable[GLMEdge]): String =
    {
        if (1 == edges.size)
        {
            val line = edges.head.el.asInstanceOf[ACLineSegment]
            make_line_configuration (name, line.r, line.x, line.r0, line.x0)
        }
        else
        {
            // compute parallel impedance -- http://hyperphysics.phy-astr.gsu.edu/hbase/electric/imped.html#c3
            val lines = edges.toArray
            var line = lines(0).el.asInstanceOf[ACLineSegment]
            var rt = if (0 == line.r) DEFAULT_R else line.r
            var xt = if (0 == line.x) DEFAULT_X else line.x
            var r0t = if (0 == line.r0) DEFAULT_R else line.r0
            var x0t = if (0 == line.x0) DEFAULT_X else line.x0
            for (i <- 1 until lines.length)
            {
                line = lines(i).el.asInstanceOf[ACLineSegment]
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
            make_line_configuration (name, rt, xt, r0t, x0t)
        }
    }

    /**
     * Emit configurations for all groups of edges.
     *
     * Get one of each type of ACLineSegment group and emit a configuration for each of them.
     *
     * @param edges the groups of edges.
     * @return The configuration elements as a single string.
     */
    def getACLineSegmentConfigurations (edges: Iterable[Iterable[GLMEdge]]): Iterable[String] =
    {
        edges.filter (isACLineSegment).groupBy (configurationName).map (x => configuration (x._1, x._2.head))
    }

    /**
     * Emit a overhead or underhground line.
     *
     * @param edges The group of edges comprising this line.
     * @return A line (string) for this group of edges.
     */
    def emit (edges: Iterable[GLMEdge]): String =
    {
        val edge = edges.head
        val line = edge.el.asInstanceOf[ACLineSegment]
        // ToDo: use ProductAssetModel.usageKind (from AssetInfo.AssetModel)
        val typ = if (line.Conductor.ConductingEquipment.Equipment.PowerSystemResource.PSRType == "PSRType_Underground")
            "underground_line"
        else
            "overhead_line"
        val config = configurationName (edges)
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
          |""".stripMargin.format (typ, edge.id, if (one_phase) "AN" else "ABCN", edge.cn1, edge.cn2, line.Conductor.len, config)
    }
}
