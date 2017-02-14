package ch.ninecode.gl

import org.apache.spark.rdd.RDD
import org.slf4j.LoggerFactory

import ch.ninecode.model._

/**
 * @param transformers - the RDD of transformers
 * @param one_phase - flag to indicate if single phase output is desired
 */
class Trans (transformers: RDD[TData], one_phase: Boolean) extends Serializable
{
    val log = LoggerFactory.getLogger (getClass)

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

    // get the configuration name (of the parallel transformers)
    def configurationName (iter: Iterable[Tuple2[PreEdge,TData]]): String =
    {
        iter.map (_._1.element.id).map (x => valid_config_name (x)).mkString ("||") + "_configuration"
    }

    // handle parallel impedance
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
    def make_transformer_configuration (edges: Iterable[Tuple2[PreEdge,TData]]): String =
    {
        // see http://gridlab-d.sourceforge.net/wiki/index.php/Power_Flow_User_Guide#Transformer_Configuration_Parameters
        val config = configurationName (edges)
        // primary and secondary voltage should be the same on all edges - use the first
        val v0 = 1000.0 * edges.head._2.voltage0
        val v1 = 1000.0 * edges.head._2.voltage1
        if (!edges.forall ((edge) => (1000.0 * edge._2.voltage0 == v0)))
            log.error ("transformer group " + config + " has different voltages on terminal 0 " + edges.map ((x) => x._2.voltage0).mkString (" "))
        if (!edges.forall ((edge) => (1000.0 * edge._2.voltage1 == v1)))
            log.error ("transformer group " + config + " has different voltages on terminal 1 " + edges.map ((x) => x._2.voltage1).mkString (" "))
        // rated power is the sum of the powers - use low voltage side, but high voltage side is the same for simple transformer
        val power_rating = edges.foldLeft (0.0)((sum, edge) => edge._2.end1.ratedS)
        // calculate the impedance
        val (r, x) = edges.foldLeft (null.asInstanceOf[Tuple2[Double, Double]]) (
            (impedance, edge) =>
            {
                val base_va = edge._2.end1.ratedS
                // equivalent per unit values
                val base_amps = base_va / v1 / Math.sqrt (3)
                val base_ohms = v1 / base_amps / Math.sqrt (3)
                // this end's impedance
                val r = edge._2.end1.r / base_ohms
                val x = edge._2.end1.x / base_ohms
                // parallel rule if more than one end
                if (null == impedance)
                    (r,x)
                else
                    parallel (impedance._1, impedance._2, r, x)
            }
        )

        val ret =
            "\n" +
            "        object transformer_configuration\n" +
            "        {\n" +
            "            name \"" + config + "\";\n" +
            "            connect_type WYE_WYE;\n" + // ToDo: pick up Dyn5 values from CIM when they are exported correctly
            "            install_type PADMOUNT;\n" +
            "            power_rating " + (power_rating / 1000.0) + ";\n" +
            "            primary_voltage " + v0 + ";\n" +
            "            secondary_voltage " + v1 + ";\n" +
            "            resistance " + r + ";\n" +
            "            reactance " + x + ";\n" +
            "        };\n"


        return (ret)
    }

    // get one of each type of PowerTransformer and emit a configuration for each of them
    def getTransformerConfigurations (edges: RDD[Iterable[Tuple2[PreEdge,TData]]]): RDD[String] =
    {
        edges.map (make_transformer_configuration)
    }

    def make_transformer (s: Iterable[Tuple2[PreEdge,TData]]): Transformer =
    {
        // get an edge, they all have the same connectivity nodes
        val an_edge = s.head._1
        // get a primary terminal (they are all the same)
        val a_primary = s.head._2.terminal0
        // get the connectivity node for the primary terminal
        val node = if (an_edge.id_seq_1 == a_primary.id) an_edge.id_cn_1 else an_edge.id_cn_2
        Transformer (node, s.map (_._2).toList)
    }

    def getTransformers (edges: RDD[Iterable[Tuple2[PreEdge,TData]]]): RDD[Transformer] =
    {
        edges.map (make_transformer)
    }

    def emit (edges: Iterable[Tuple2[PreEdge,TData]]): String =
    {
        val config = configurationName (edges)
        // get an edge, they all have the same connectivity nodes
        val edge = edges.head._1
        val obj = if (one_phase)
            "\n" +
            "        object transformer\n" +
            "        {\n" +
            "            name \"" + edge.id_equ + "\";\n" +
            "            phases AN;\n" +
            "            from \"" + edge.id_cn_1 + "\";\n" +
            "            to \"" + edge.id_cn_2 + "\";\n" +
            "            configuration \"" + config + "\";\n" +
            "        };\n"
        else
            "\n" +
            "        object transformer\n" +
            "        {\n" +
            "            name \"" + edge.id_equ + "\";\n" +
            "            phases ABCN;\n" +
            "            from \"" + edge.id_cn_1 + "\";\n" +
            "            to \"" + edge.id_cn_2 + "\";\n" +
            "            configuration \"" + config + "\";\n" +
            "        };\n"
        val rec = if (one_phase)
            "\n" +
            "        object recorder\n" +
            "        {\n" +
            "            name \"" + edge.id_equ + "_current_recorder\";\n" +
            "            parent \"" + edge.id_equ + "\";\n" +
            "            property current_out_A.real,current_out_A.imag;\n" +
            "            interval 5;\n" +
            "            file \"output_data/" + edge.id_equ + "_current.csv\";\n" +
            "        };\n"
        else
            "\n" +
            "        object recorder\n" +
            "        {\n" +
            "            name \"" + edge.id_equ + "_current_recorder\";\n" +
            "            parent \"" + edge.id_equ + "\";\n" +
            "            property current_out_A.real,current_out_A.imag,current_out_B.real,current_out_B.imag,current_out_C.real,current_out_C.imag;\n" +
            "            interval 5;\n" +
            "            file \"output_data/" + edge.id_equ + "_current.csv\";\n" +
            "        };\n"
        return (obj + rec)
    }
}
