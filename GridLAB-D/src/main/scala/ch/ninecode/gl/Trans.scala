package ch.ninecode.gl

import org.apache.spark.rdd.RDD

import ch.ninecode.model._

/**
 * @param transformers the RDD of transformers
 * @param voltages a map of voltage mRID to floating point voltages
 */
class Trans (transformers: RDD[TData]) extends Serializable
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

    def make_transformer (s: Tuple2[Iterable[PreEdge],TData]): Transformer =
    {
        val tdata = s._2
        val ends = tdata.ends // .sortWith (_.TransformerEnd.endNumber < _.TransformerEnd.endNumber) // sort ends by sequence number
        val primary = ends (0)
        val an_edge = s._1.head
        val node = if (an_edge.id_seq_1 == primary.TransformerEnd.Terminal) an_edge.id_cn_1 else an_edge.id_cn_2
        Transformer (node, tdata.transformer)
    }

    /**
     * Make one or more transformer configurations.
     * Most transformers have only two ends, so this should normally make one configurations
     */
    def make_transformer_configuration (s: Tuple2[Iterable[PreEdge],TData]): String =
    {
        // see http://gridlab-d.sourceforge.net/wiki/index.php/Power_Flow_User_Guide#Transformer_Configuration_Parameters
        val edges = s._1
        val config: String = configurationName (edges)
        val transformer = edges.head.element.asInstanceOf[PowerTransformer]
        val power = transformer_power (transformer)
        val tdata = s._2
        val ends = tdata.ends // .sortWith (_.TransformerEnd.endNumber < _.TransformerEnd.endNumber) // sort ends by sequence number
        val voltages = tdata.voltages
        var ret = ""
        for (i <- 1 until ends.length)
        {
            val v0 = 1000.0 * voltages (0)
            val v = 1000.0 * voltages (i)
            // calculate per unit r and x
            var base_va = ends(i).ratedS
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

            ret +=
                "\n" +
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
                "        };\n"
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
        // ToDo: this assumes that all transformers are identical -- what if there are random transformers in parallel
        val pt = edges.keyBy (_.head.element.id).join (transformers.keyBy (_.transformer.id)).values
        pt.map (make_transformer_configuration)
    }

    def getTransformers (edges: RDD[Iterable[PreEdge]]): RDD[Transformer] =
    {
        // ToDo: this assumes that all transformers are identical -- what if there are random transformers in parallel
        val pt = edges.keyBy (_.head.element.id).join (transformers.keyBy (_.transformer.id)).values
        pt.map (make_transformer)
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
            var config = configurationName (edges)
            "        object transformer\n" +
            "        {\n" +
            "            name \"" + edge.id_equ + "\";\n" +
            "            phases ABCN;\n" +
            "            from \"" + edge.id_cn_1 + "\";\n" +
            "            to \"" + edge.id_cn_2 + "\";\n" +
            "            configuration \"" + config + "\";\n" +
            "        };\n"
        }
    }
}
