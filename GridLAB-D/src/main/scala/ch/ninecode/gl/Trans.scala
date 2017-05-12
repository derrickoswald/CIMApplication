package ch.ninecode.gl

import org.slf4j.LoggerFactory

/**
 * @param transformers - the RDD of transformers
 * @param one_phase - flag to indicate if single phase output is desired
 */
class Trans (one_phase: Boolean) extends Serializable
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
        val n = iter.map (_.transformer.id).map (valid_config_name).toArray.sortWith (_ < _).mkString ("||") + "_configuration"
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
     * Make one or more transformer configurations.
     * Most transformers have only two ends, so this should normally make one configurations
     */
    def getTransformerConfigurations (transformers: Array[TData]): String =
    {
        // see http://gridlab-d.sourceforge.net/wiki/index.php/Power_Flow_User_Guide#Transformer_Configuration_Parameters
        val config = configurationName (transformers)
        // primary and secondary voltage should be the same on all edges - use the first
        // TODO check for voltages on terminal
        val v0 = 1000.0 * transformers.head.voltage0
        val v1 = 1000.0 * transformers.head.voltage1
        if (!transformers.forall ((edge) => (1000.0 * edge.voltage0 == v0)))
            log.error ("transformer group " + config + " has different voltages on terminal 0 " + transformers.map ((x) => x.voltage0).mkString (" "))
        if (!transformers.forall ((edge) => (1000.0 * edge.voltage1 == v1)))
            log.error ("transformer group " + config + " has different voltages on terminal 1 " + transformers.map ((x) => x.voltage1).mkString (" "))
        // rated power is the sum of the powers - use low voltage side, but high voltage side is the same for simple transformer
        val power_rating = transformers.foldLeft (0.0)((sum, edge) => sum + edge.end1.ratedS)
        // calculate the impedance as 1 / sum (1/Zi)
        val impedances = transformers.map (
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
        val zero = Complex (0.0, 0.0)
        val sum = impedances.map (_.reciprocal).foldLeft (zero)(_.+(_))
        val (total_impedance, default) = if (sum == zero)
            (Complex (2.397460317, 16.07618325), true)
        else
            (sum.reciprocal, false)

        "\n" +
        (if (default) "#warning WARNING: using default impedance for " + config + "\n" else "\n") +
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
    }

    def emit (transformers: Array[TData]): String =
    {
        val config = configurationName (transformers)
        val name = transformers.map (_.transformer.id).map (x => valid_config_name (x)).mkString ("_")
        // all transformers in the array have the same nodes
        "\n" +
        "        object transformer\n" +
        "        {\n" +
        "            name \"" + name + "\";\n" +
        "            phases " + (if (one_phase) "AN" else "ABCN") + ";\n" +
        "            from \"" + transformers.head.node0 + "\";\n" +
        "            to \"" + transformers.head.node1 + "\";\n" +
        "            configuration \"" + config + "\";\n" +
        "        };\n" +
        "\n" +
        "        object recorder\n" +
        "        {\n" +
        "            name \"" + name + "_current_recorder\";\n" +
        "            parent \"" + name + "\";\n" +
        "            property " + (if (one_phase) "current_out_A.real,current_out_A.imag" else "current_out_A.real,current_out_A.imag,current_out_B.real,current_out_B.imag,current_out_C.real,current_out_C.imag") + ";\n" +
        "            interval 5;\n" +
        "            file \"output_data/" + name + "_current.csv\";\n" +
        "        };\n"
    }
}
