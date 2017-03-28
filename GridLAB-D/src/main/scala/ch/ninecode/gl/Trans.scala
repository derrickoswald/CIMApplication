package ch.ninecode.gl

import org.apache.spark.rdd.RDD
import org.slf4j.LoggerFactory

import ch.ninecode.model._

/**
 * @param transformers - the RDD of transformers
 * @param one_phase - flag to indicate if single phase output is desired
 */
class Trans (one_phase: Boolean, USE_TOPOLOGICAL_NODES: Boolean) extends Serializable
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
    def configurationName (iter: Iterable[TData]): String =
    {
        iter.map (_.transformer.id).map (x => valid_config_name (x)).toArray.sortWith (_ < _).mkString ("||") + "_configuration"
    }

    /**
     * Make one or more transformer configurations.
     * Most transformers have only two ends, so this should normally make one configurations
     */
    def make_transformer_configuration (trafokreis: Array[TData]): String =
    {
        // see http://gridlab-d.sourceforge.net/wiki/index.php/Power_Flow_User_Guide#Transformer_Configuration_Parameters
        val config = configurationName (trafokreis)
        // primary and secondary voltage should be the same on all edges - use the first
        // TODO check for voltages on terminal
        val v0 = 1000.0 * trafokreis.head.voltage0
        val v1 = 1000.0 * trafokreis.head.voltage1
        if (!trafokreis.forall ((edge) => (1000.0 * edge.voltage0 == v0)))
            log.error ("transformer group " + config + " has different voltages on terminal 0 " + trafokreis.map ((x) => x.voltage0).mkString (" "))
        if (!trafokreis.forall ((edge) => (1000.0 * edge.voltage1 == v1)))
            log.error ("transformer group " + config + " has different voltages on terminal 1 " + trafokreis.map ((x) => x.voltage1).mkString (" "))
        // rated power is the sum of the powers - use low voltage side, but high voltage side is the same for simple transformer
        val power_rating = trafokreis.foldLeft (0.0)((sum, edge) => sum + edge.end1.ratedS)
        // calculate the impedance as 1 / sum (1/Zi)
        val impedances = trafokreis.map (
            (edge) =>
            {
                val sqrt3 = Math.sqrt (3)
                val base_va = edge.end1.ratedS
                // equivalent per unit values
                val base_amps = base_va / v1 / sqrt3
                val base_ohms = v1 / base_amps / sqrt3
                // this end's impedance
                val r = edge.end1.r / base_ohms
                val x = edge.end1.x / base_ohms
                Complex (r, x)
            }
        )
        val total_impedance = impedances.map (_.reciprocal).foldLeft (Complex (0.0, 0.0))(_.+(_)).reciprocal

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
            "            resistance " + total_impedance.re + ";\n" +
            "            reactance " + total_impedance.im + ";\n" +
            "        };\n"

        return (ret)
    }

    // get one of each type of PowerTransformer and emit a configuration for each of them
    def getTransformerConfigurations (trafokreis: Array[TData]): String =
    {
        make_transformer_configuration(trafokreis)
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

    def getTransformers (edges: Iterable[Iterable[Tuple2[PreEdge,TData]]]): Iterable[Transformer] =
    {
        edges.map (make_transformer)
    }

  def emit(trafo: Array[TData]): String =
    {
        val config = configurationName (trafo)
        val name = trafo.map (_.transformer.id).map (x => valid_config_name (x)).mkString ("_")
        // get an edge, they all have the same connectivity nodes
        val (cn1, cn2) = if (USE_TOPOLOGICAL_NODES)
          (trafo.head.terminal0.TopologicalNode, trafo.head.terminal1.TopologicalNode)
        else
          (trafo.head.terminal0.ConnectivityNode, trafo.head.terminal1.ConnectivityNode)
        val obj = if (one_phase)
            "\n" +
            "        object transformer\n" +
            "        {\n" +
            "            name \"" + name + "\";\n" +
            "            phases AN;\n" +
            "            from \"" + cn1 + "\";\n" +
            "            to \"" + cn2 + "\";\n" +
            "            configuration \"" + config + "\";\n" +
            "        };\n"
        else
            "\n" +
            "        object transformer\n" +
            "        {\n" +
            "            name \"" + name + "\";\n" +
            "            phases ABCN;\n" +
            "            from \"" + cn1 + "\";\n" +
            "            to \"" + cn2 + "\";\n" +
            "            configuration \"" + config + "\";\n" +
            "        };\n"
        val rec = if (one_phase)
            "\n" +
            "        object recorder\n" +
            "        {\n" +
            "            name \"" + name + "_current_recorder\";\n" +
            "            parent \"" + name + "\";\n" +
            "            property current_out_A.real,current_out_A.imag;\n" +
            "            interval 5;\n" +
            "            file \"output_data/" + name + "_current.csv\";\n" +
            "        };\n"
        else
            "\n" +
            "        object recorder\n" +
            "        {\n" +
            "            name \"" + name + "_current_recorder\";\n" +
            "            parent \"" + name + "\";\n" +
            "            property current_out_A.real,current_out_A.imag,current_out_B.real,current_out_B.imag,current_out_C.real,current_out_C.imag;\n" +
            "            interval 5;\n" +
            "            file \"output_data/" + name + "_current.csv\";\n" +
            "        };\n"
        return (obj + rec)
    }
}
