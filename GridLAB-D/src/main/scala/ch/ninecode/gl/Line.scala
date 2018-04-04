package ch.ninecode.gl

import ch.ninecode.model.ACLineSegment

class Line (one_phase: Boolean) extends Serializable
{
    final val DEFAULT_R = 0.225
    final val DEFAULT_X = 0.068

    // make a valid configuration name
    // no leading digits: ERROR    [INIT] : object name '4x4' invalid, names must start with a letter or an underscore
    // no decimal points: KLE199604 (underground_line:227) reference to TT 3x2.5 is missing match value
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

    def sequence2z (z0: Complex, z1: Complex): (Complex, Complex) =
    {
        val Z0 = z0
        val Z1, Z2 = z1
        val diag = (Z0 + Z1 + Z2) / 3
        val x1 = Z1 * Complex (-0.5, Math.sqrt (3.0) / 2.0)
        val x2 = Z2 * Complex (-0.5, Math.sqrt (3.0) / -2.0)
        val off = (Z0 + x1 + x2) / 3.0
        (diag, off)
    }

    // convert the 0/1 sequence values from the CIM format into a Z matrix
    def zMatrixValues (r: Double, x: Double, r0: Double, x0: Double): (Complex, Complex, Boolean) =
    {
        if ((0.0 != r) && (0.0 != x))
            if (one_phase)
                (Complex (r, x), Complex (0.0, 0.0), false)
            else
            {
                val z0 = Complex (r0, x0)
                val z1 = Complex (r, x)
                val (diag, off) = sequence2z (z0, z1)
                (diag, off, false)
            }
        else
            (Complex (DEFAULT_R, DEFAULT_X), Complex (0.0, 0.0), true)
    }

    // emit a GridLAB-D line_configuration
    def make_line_configuration (config: String, r: Double, x: Double, r0: Double, x0: Double): String =
    {
        val (diag, off, default) = zMatrixValues (r, x, r0, x0)
        val z11 = diag.toString () + " Ohm/km"
        val z12 = off.toString () + " Ohm/km"
        "\n" +
        (if (default) "#warning WARNING: using default line_configuration for " + config + "\n" else "") +
        "        object line_configuration\n" +
        "        {\n" +
        "            name \"" + config + "\";\n" +
        "            z11 " + z11 + ";\n" +
        "            z12 " + z12 + ";\n" +
        "            z13 " + z12 + ";\n" +
        "            z21 " + z12 + ";\n" +
        "            z22 " + z11 + ";\n" +
        "            z23 " + z12 + ";\n" +
        "            z31 " + z12 + ";\n" +
        "            z32 " + z12 + ";\n" +
        "            z33 " + z11 + ";\n" +
        "        };\n"
    }

    // is this an ACLineSegment
    def isACLineSegment (iter: Iterable[GLMEdge]): Boolean =
    {
        iter.forall (_.el.getClass.getName.endsWith ("ACLineSegment"))
    }

    // get the configuration name (of the parallel lines)
    def configurationName (iter: Iterable[GLMEdge]): String =
    {
        val n = iter.map (_.el.asInstanceOf[ACLineSegment].Conductor.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.name)
        .map (valid_config_name).toArray.sortWith (_ < _).mkString ("||")
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

    // see http://hyperphysics.phy-astr.gsu.edu/hbase/electric/imped.html
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

    def configuration (item: (String, Iterable[GLMEdge])): String =
    {
        if (1 == item._2.size)
        {
            val line = item._2.head.el.asInstanceOf[ACLineSegment]
            make_line_configuration (item._1, line.r, line.x, line.r0, line.x0)
        }
        else
        {
            // compute parallel impedance -- http://hyperphysics.phy-astr.gsu.edu/hbase/electric/imped.html#c3
            val lines = item._2.toArray
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
            make_line_configuration (item._1, rt, xt, r0t, x0t)
        }
    }

    // get one of each type of ACLineSegment and emit a configuration for each of them
    def getACLineSegmentConfigurations (edges: Iterable[Iterable[GLMEdge]]): Iterable[String] =
    {
        edges.filter (x => isACLineSegment (x)).groupBy(x => configurationName (x))
             .map(x => configuration((x._1, x._2.head)))
    }

    def emit (edges: Iterable[GLMEdge]): String =
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
        "            name \"" + edge.id + "\";\n" +
        "            phases " + (if (one_phase) "AN" else "ABCN") + ";\n" +
        "            from \"" + edge.cn1 + "\";\n" +
        "            to \"" + edge.cn2 + "\";\n" +
        "            length " + line.Conductor.len + "m;\n" +
        "            configuration \"" + config + "\";\n" +
        "        };\n"
    }
}
