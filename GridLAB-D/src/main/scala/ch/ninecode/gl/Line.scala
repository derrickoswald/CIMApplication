package ch.ninecode.gl

import org.apache.spark.rdd.RDD

import ch.ninecode.cim._
import ch.ninecode.model._

class Line extends Serializable
{
    final val DEFAULT_R = 0.225
    final val DEFAULT_X = 0.068

    // make a valid configuration name
    // ERROR    [INIT] : object name '4x4' invalid, names must start with a letter or an underscore
    def valid_config_name (string: String): String =
    {
        if ((null == string) || ("" == string))
            "unknown"
        else
            if (string.charAt (0).isLetter || ('_' == string.charAt (0)))
                string
            else
                "_" + string
    }

    // emit a GridLAB-D line_configuration
    def make_line_configuration (config: String, line: ACLineSegment): String =
    {
        var ret = ""

        // ToDo: convert the 0/1 sequence values from the CIM format into a Z matrix

        //        <cim:ACLineSegment rdf:ID="KLE9595">
        //                <cim:IdentifiedObject.name>GKN 3x25/25</cim:IdentifiedObject.name>
        //                <cim:IdentifiedObject.aliasName>202519879:nis_el_cable</cim:IdentifiedObject.aliasName>
        //                <cim:PowerSystemResource.Location rdf:resource="#_location_696818_1201171875_202519890"/>
        //                <cim:Conductor.length>55.60291275</cim:Conductor.length>
        //                <cim:PowerSystemResource.PSRType rdf:resource="#PSRType_Underground"/>
        //                <cim:ConductingEquipment.BaseVoltage rdf:resource="BaseVoltage_400"/>
        //                <cim:ConductingEquipment.SvStatus rdf:resource="#in_use"/>
        //                <cim:Equipment.EquipmentContainer rdf:resource="#_line_ABG52414|..."/>
        //                <cim:ACLineSegment.b0ch>106.8141502</cim:ACLineSegment.b0ch>
        //                <cim:ACLineSegment.bch>179.0707813</cim:ACLineSegment.bch>
        //                <cim:ACLineSegment.g0ch>0</cim:ACLineSegment.g0ch>
        //                <cim:ACLineSegment.gch>0</cim:ACLineSegment.gch>
        //                <cim:ACLineSegment.r0>3.368</cim:ACLineSegment.r0>
        //                <cim:ACLineSegment.r>0.841</cim:ACLineSegment.r>
        //                <cim:ACLineSegment.shortCircuitEndTemperature>250</cim:ACLineSegment.shortCircuitEndTemperature>
        //                <cim:ACLineSegment.x0>0.32</cim:ACLineSegment.x0>
        //                <cim:ACLineSegment.x>0.075</cim:ACLineSegment.x>
        //        </cim:ACLineSegment>
//
//            "        object line_configuration\n" +
//            "        {\n" +
//            "            name \"line_3x25Cu/25\";\n" +
//            "            z11 0.727+0.08j Ohm/km;\n" +
//            "            z12 0.0+0.0j Ohm/km;\n" +
//            "            z13 0.0+0.0j Ohm/km;\n" +
//            "            z21 0.0+0.0j Ohm/km;\n" +
//            "            z22 0.727+0.08j Ohm/km;\n" +
//            "            z23 0.0+0.0j Ohm/km;\n" +
//            "            z31 0.0+0.0j Ohm/km;\n" +
//            "            z32 0.0+0.0j Ohm/km;\n" +
//            "            z33 0.727+0.08j Ohm/km;\n" +
//            "        };\n" +
//            "\n" +
//            "        object line_configuration\n" +
//            "        {\n" +
//            "            name \"line_3x95Cu/95\";\n" +
//            "            z11 0.193+0.07j Ohm/km;\n" +
//            "            z12 0.0+0.0j Ohm/km;\n" +
//            "            z13 0.0+0.0j Ohm/km;\n" +
//            "            z21 0.0+0.0j Ohm/km;\n" +
//            "            z22 0.193+0.07j Ohm/km;\n" +
//            "            z23 0.0+0.0j Ohm/km;\n" +
//            "            z31 0.0+0.0j Ohm/km;\n" +
//            "            z32 0.0+0.0j Ohm/km;\n" +
//            "            z33 0.193+0.07j Ohm/km;\n" +
//            "        };\n" +

        // ToDo: get real values, "TT 1x150 EL_3" is actually "TT 1x150"
        val r = if (0 == line.r) DEFAULT_R else line.r
        val x = if (0 == line.x) DEFAULT_X else line.x
        val diag = r + "+" + x + "j Ohm/km"
        val zero = "0.0+0.0j Ohm/km"
        ret =
            "        object line_configuration\n" +
            "        {\n" +
            "            name \"" + config + "\";\n" +
            "            z11 " + diag + ";\n" +
            "            z12 " + zero + ";\n" +
            "            z13 " + zero + ";\n" +
            "            z21 " + zero + ";\n" +
            "            z22 " + diag + ";\n" +
            "            z23 " + zero + ";\n" +
            "            z31 " + zero + ";\n" +
            "            z32 " + zero + ";\n" +
            "            z33 " + diag + ";\n" +
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
        else if (2 == item._2.size)
        {
            // compute parallel impedance -- http://hyperphysics.phy-astr.gsu.edu/hbase/electric/imped.html#c3
            val line1 = item._2.head.element.asInstanceOf[ACLineSegment]
            val line2 = item._2.tail.head.element.asInstanceOf[ACLineSegment]
            val r1 = if (0 == line1.r) DEFAULT_R else line1.r
            val x1 = if (0 == line1.x) DEFAULT_X else line1.x
            val r2 = if (0 == line2.r) DEFAULT_R else line2.r
            val x2 = if (0 == line2.x) DEFAULT_X else line2.x
            val (r, x) = parallel (r1, x1, r2, x2)
            make_line_configuration (item._1, ACLineSegment (null, 0.0, 0.0, 0.0, 0.0, 0.0, r, 0.0, 0.0, x, "", "", ""))
        }
        else
            throw new Exception ("more than two parallel elements")
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
