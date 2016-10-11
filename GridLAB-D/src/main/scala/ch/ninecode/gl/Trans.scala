package ch.ninecode.gl

import org.apache.spark.rdd.RDD

import ch.ninecode.cim._
import ch.ninecode.model._

/**
 * @param shorts the RDD of short circuit data by substation, mapped to transformers
 * @param voltages a map of voltage mRID to floating point voltages
 */
class Trans (
    shorts: RDD[(PowerTransformer, Substation, ShortCircuitData)],
    ends: RDD[(String, Iterable[PowerTransformerEnd])],
    voltages: scala.collection.Map[String, Double]) extends Serializable
{
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
    def transformer_power (transformer: PowerTransformer): String =
    {
        val name = transformer.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.name // e.g. 250kVA
        """([0-9]+)""".r.findFirstIn (name) match
        {
            case Some (x) =>
                x
            case _ =>
                "unknown"
        }
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

    /**
     * Make one or more transformer configurations.
     * Most transformers have only two ends, so this should normally make one configurations
     */
    def make_transformer_configuration (s: Tuple2[Tuple2[Iterable[PreEdge],Tuple3[PowerTransformer,Substation,ShortCircuitData]],Option[Iterable[PowerTransformerEnd]]]): String =
    {
        // see http://gridlab-d.sourceforge.net/wiki/index.php/Power_Flow_User_Guide#Transformer_Configuration_Parameters
        val edges = s._1._1
        val config: String = configurationName (edges)
        val transformer = edges.head.element.asInstanceOf[PowerTransformer]
        val power = transformer_power (transformer)
        val sc_data = s._1._2._3
        val ret =
            s._2 match
            {
                case None =>
                    ""
                case Some (a: Any) =>
                    // sort ends by sequence number
                    val iter = a.asInstanceOf[Iterable[PowerTransformerEnd]]
                    val ends = iter.toArray.sortWith (_.TransformerEnd.endNumber < _.TransformerEnd.endNumber)
                    var temp = ""
                    for (i <- 1 until ends.length)
                    {
                        val v0 = 1000.0 * voltages.getOrElse (ends(0).TransformerEnd.BaseVoltage, 0.0)
                        val v = 1000.0 * voltages.getOrElse (ends(i).TransformerEnd.BaseVoltage, 0.0)
                        // calculate per unit r and x
                        var base_va = ends(i).ratedS * 1e6 // ToDo: remove this multiplier when NE-66 NIS.CIM: Scale of PowerTransformerEnd ratedS is wrong
                        if (0.0 == base_va && "unknown" != power)
                            base_va = power.toDouble * 1000
                        val base_amps = base_va / v / Math.sqrt (3)
                        val base_ohms = v / base_amps / Math.sqrt (3)
                        var r = ends(i).r / base_ohms
                        var x = ends(i).x / base_ohms
                        // ToDo: not sure how to parallel the transformers
                        if (edges.size > 1)
                        {
                            base_va *= 2.0
                            val par = parallel (r, x, r, x)
                            r = par._1
                            x = par._2
                        }

                        // compute the fake line impedance from the high voltage to the transformer
                        // Z = c * V^2 / (Sk x 1e6)     e.g. 0.90 * 16000 * 16000 / -82e6  = -2.8097
                        // r = Z * sin(Ikw)
                        // x = Z * cos(Ikw)
                        val c = 0.9
                        val z = c * v0 * v0 / (Math.abs (sc_data.Sk) * 1e6)
                        val diag = "" + z + (if (0 <= sc_data.Ikw) "+" else "") + sc_data.Ikw + "d Ohm/km"
                        temp +=
                            "        object transformer_configuration\n" +
                            "        {\n" +
                            "            name \"" + config + "\";\n" +
                            "            connect_type DELTA_GWYE;\n" + // ToDo: pick up Dyn5 values from CIM when they are exported correctly
                            "            install_type PADMOUNT;\n" +
                            "            power_rating " + (base_va / 1000.0) + ";\n" +
                            "            primary_voltage " + v0 + ";\n" +
                            "            secondary_voltage " + v + ";\n" +
                            "            resistance " + r + ";\n" +
                            "            reactance " + x + ";\n" +
                            "        };\n" +
                            // make a line configuration
                            "        object line_configuration\n" +
                            "        {\n" +
                            "            name \"" + config + "_fake_line_configuration\";\n" +
                            "            z11 " + diag + ";\n" +
                            "            z12 0.0+0.0d Ohm/km;\n" +
                            "            z13 0.0+0.0d Ohm/km;\n" +
                            "            z21 0.0+0.0d Ohm/km;\n" +
                            "            z22 " + diag + ";\n" +
                            "            z23 0.0+0.0d Ohm/km;\n" +
                            "            z31 0.0+0.0d Ohm/km;\n" +
                            "            z32 0.0+0.0d Ohm/km;\n" +
                            "            z33 " + diag + ";\n" +
                            "        };\n"
                    }
                    temp
            }

        return (ret)
    }

    // is this a Transformer
    // ToDo: this currently only looks at the first element -- what about combinations?
    def isPowerTransformer (iter: Iterable[PreEdge]): Boolean =
    {
        iter.head.element.getClass.getName.endsWith ("PowerTransformer")
    }

    // get the configuration name (of the parallel transformers)
    def configurationName (iter: Iterable[PreEdge]): String =
    {
        iter.map (_.element.id).map (x => valid_config_name (x)).mkString ("||") + "_configuration"
    }

    // get one of each type of PowerTransformer and emit a configuration for each of them
    def getTransformerConfigurations (edges: RDD[Iterable[PreEdge]]): RDD[String] =
    {
        // ToDo: this assumes that all transformers are identical -- what if there are randowm transformers in parallel
        val pt = edges.keyBy (_.head.element.id).join (shorts.keyBy (_._1.id)).values
        pt.keyBy (_._1.head.element.id).leftOuterJoin (ends).values.map (make_transformer_configuration)
    }

    def emit (edges: Iterable[PreEdge]): String =
    {
        val edge = edges.head
        val transformer = edge.element.asInstanceOf[PowerTransformer]
        val power = transformer_power (transformer)
        // for power transformers without a configuration, just emit a link
        if ("unknown" == power)
            "        object link\n" +
            "        {\n" +
            "            name \"" + edge.id_equ + "\";\n" +
            "            phases ABCN;\n" +
            "            from \"" + edge.id_cn_1 + "\";\n" +
            "            to \"" + edge.id_cn_2 + "\";\n" +
            "        };\n"
        else
        {
            val config = configurationName (edges)
            "        object transformer\n" +
            "        {\n" +
            "            name \"" + edge.id_equ + "\";\n" +
            "            phases ABCN;\n" +
            "            from \"" + edge.id_cn_1 + "\";\n" +
            "            to \"" + edge.id_cn_2 + "\";\n" +
            "            configuration \"" + config + "\";\n" +
            "        };\n" +
            // make a slack bus
            "        object node\n" +
            "        {\n" +
            "            name \"" + edge.id_equ + "_swing_bus\";\n" +
            "            phases ABCD;\n" + // ToDo: check if it's delta connected or not
            "            bustype SWING;\n" +
            "            nominal_voltage " + edge.v1 + " V;\n" +
            "            voltage_A " + edge.v1 + "+30.0d V;\n" +
            "            voltage_B " + edge.v1 + "-90.0d V;\n" +
            "            voltage_C " + edge.v1 + "+150.0d V;\n" +
            "        };\n" +
            // make a fake cable joining the slack bus to the transformer
            "        object underground_line\n" +
            "        {\n" +
            "            name \"" + edge.id_equ + "_feeder\";\n" +
            "            phases ABCD;\n" + // ToDo: check if it's delta connected or not
            "            from \"" + edge.id_equ + "_swing_bus\";\n" +
            "            to \"" + edge.id_cn_1 + "\";\n" +
            "            length 1000 m;\n" +
            "            configuration \"" + config + "_fake_line_configuration\";\n" +
            "        };\n"
        }
    }
}
