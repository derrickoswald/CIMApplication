package ch.ninecode.gl

import scala.math.sqrt

import org.apache.spark.rdd.RDD

import ch.ninecode.model._

class Line extends Serializable
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

    def sequence2z (z0: Complex, z1: Complex): Tuple2[Complex, Complex] =
    {
        val Z1, Z2 = z1
        val r0rl: Double = z0.re / z1.re
        val x0xl: Double = z0.im / z1.im
        val Z0 = Complex (z1.re * r0rl, z1.im * x0xl)
        val diag = (Z0 + Z1 + Z2) / 3
        val x1 = Z1 * Complex (-0.5, sqrt (3.0) / 2.0)
        val x2 = Z2 * Complex (-0.5, sqrt (3.0) / -2.0)
        val off = (Z0 + x1 + x2) / 3.0
        (diag, off)
    }

    // convert the 0/1 sequence values from the CIM format into a Z matrix
    def zMatrixValues (line: ACLineSegment): Tuple2[Complex, Complex] =
    {
        val ret =
            if ((0 != line.r) && (0 != line.x))
                sequence2z (Complex (line.r0, line.x0), Complex (line.r, line.x))
            else
                (Complex (DEFAULT_R, DEFAULT_X), Complex (0.0, 0.0))

        return (ret)
    }

    // emit a GridLAB-D line_configuration
    def make_line_configuration (config: String, line: ACLineSegment): String =
    {
        var ret = ""

        val (diag, off) = zMatrixValues (line)
        val z11 = diag.toString () + " Ohm/km"
        val z12 = off.toString () + " Ohm/km"
        ret =
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

        return (ret)
    }

    // is this an ACLineSegment
    // ToDo: this currently only looks at the first element -- what about combinations?
    def isACLineSegment (iter: Iterable[PreEdge]): Boolean =
    {
        iter.head.element.getClass.getName.endsWith ("ACLineSegment")
    }

    // get the configuration name (of the parallel lines)
    def configurationName (iter: Iterable[PreEdge]): String =
    {
        iter.map (_.equipment.Equipment.PowerSystemResource.IdentifiedObject.name)
        .map (x => valid_config_name (x)).mkString ("||")
    }

    // see http://hyperphysics.phy-astr.gsu.edu/hbase/electric/imped.html
    def parallel (r1: Double, x1: Double, r2: Double, x2: Double): Tuple2[Double, Double] =
    {
        val rs = r1 + r2
        val xs = x1 + x2
        val rp = r1 * r2
        val xp = x1 * x2
        val r = (((rp - xp) * rs) + (((x1 * r2) + (x2 * r1)) * xs)) / ((rs * rs) + (xs * xs))
        val x = ((((x1 * r2) + (x2 * r1)) * rs) - ((rp - xp) * xs)) / ((rs * rs) + (xs * xs))
        return (new Tuple2 (r, x))
    }

    def configuration (item: Tuple2[String, Iterable[ch.ninecode.gl.PreEdge]]): String =
    {
        if (1 == item._2.size)
            make_line_configuration (item._1, item._2.head.element.asInstanceOf[ACLineSegment])
        else
        {
            // compute parallel impedance -- http://hyperphysics.phy-astr.gsu.edu/hbase/electric/imped.html#c3
            val lines = item._2.toArray
            var line = lines(0).element.asInstanceOf[ACLineSegment]
            var rt = if (0 == line.r) DEFAULT_R else line.r
            var xt = if (0 == line.x) DEFAULT_X else line.x
            var r0t = if (0 == line.r0) DEFAULT_R else line.r0
            var x0t = if (0 == line.x0) DEFAULT_X else line.x0
            for (i <- 1 until lines.size)
            {
                line = lines(i).element.asInstanceOf[ACLineSegment]
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
            make_line_configuration (item._1, ACLineSegment (null, 0.0, 0.0, 0.0, 0.0, r0t, rt, 0.0, x0t, xt, "", "", ""))
        }
    }

    // get one of each type of ACLineSegment and emit a configuration for each of them
    def getACLineSegmentConfigurations (edges: RDD[Iterable[PreEdge]]): RDD[String] =
    {
        edges.filter (x => isACLineSegment (x)).keyBy (x => configurationName (x))
            .reduceByKey ((a, b) => a) // all lines with the same name have the same configuration
            .map (configuration)
    }

    def emit (edges: Iterable[PreEdge]): String =
    {
        val edge = edges.head
        val line = edge.element.asInstanceOf[ACLineSegment]
        val typ = if (line.Conductor.ConductingEquipment.Equipment.PowerSystemResource.PSRType == "PSRType_Underground")
            "underground_line"
        else
            "overhead_line"
        val config = configurationName (edges)
        "        object " + typ + "\n" +
        "        {\n" +
        "            name \"" + edge.id_equ + "\";\n" +
        "            phases ABCN;\n" +
        "            from \"" + edge.id_cn_1 + "\";\n" +
        "            to \"" + edge.id_cn_2 + "\";\n" +
        "            length " + line.Conductor.len + "m;\n" +
        "            configuration \"" + config + "\";\n" +
        "        };\n"
    }
}
