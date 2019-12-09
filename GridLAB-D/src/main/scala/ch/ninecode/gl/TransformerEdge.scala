package ch.ninecode.gl

import ch.ninecode.model.PowerTransformerEnd

case class TransformerEdge
(
    cn1: String,
    cn2: String,
    transformer: TransformerSet
)
    extends GLMEdge
{
    def id: String = transformer.transformer_name

    // check if this is a multi-winding transformer
    lazy val lv_windings: Array[PowerTransformerEnd] =
        for (winding <- transformer.transformers(0).ends
             if winding.TransformerEnd.endNumber > 1)
            yield winding
    lazy val multiwinding: Boolean = lv_windings.length > 1

    /**
     * Emit a transformer.
     *
     * @param generator the driver program
     * @return A transformer string (.glm text) for this edge.
     */
    override def emit (generator: GLMGenerator): String =
    {
        val phase = if (generator.isSinglePhase) "AN" else "ABCN"
        if (multiwinding)
        {
            val intermediate = s"${transformer.node0}_intermediate"
            val int =
                s"""
                  |        object meter
                  |        {
                  |            name "$intermediate";
                  |            phases $phase;
                  |            bustype PQ;
                  |            nominal_voltage ${transformer.transformers(0).voltages(0)._2}V;
                  |        };
                  |""".stripMargin
            val prim =
            s"""
                |        object transformer
                |        {
                |            name "${transformer.transformer_name}";
                |            phases $phase;
                |            from "${transformer.node0}";
                |            to "$intermediate";
                |            configuration "$configurationName";
                |        };
                |""".stripMargin
            val sec = for (winding <- lv_windings)
                yield
                {
                    val config = configurationName
                    val number = winding.TransformerEnd.endNumber - 1
                    val conf = s"${config}_winding_$number"

                    s"""
                        |        object transformer
                        |        {
                        |            name "${transformer.transformer_name}_$number";
                        |            phases $phase;
                        |            from "$intermediate";
                        |            to "${transformer.transformers(0).terminals(number).TopologicalNode}";
                        |            configuration "$conf";
                        |        };
                        |""".stripMargin
                }
            int + prim + sec.mkString
        }
        else
        s"""
            |        object transformer
            |        {
            |            name "${transformer.transformer_name}";
            |            phases $phase;
            |            from "${transformer.node0}";
            |            to "${transformer.node1}";
            |            configuration "$configurationName";
            |        };
            |""".stripMargin
    }

    /**
     * Rating in kVA.
     *
     * @return the transformer (set) rating in kVA.
     */
    def rating: String = Math.round (transformer.power_rating / 1000.0).toString

    /**
     * Primary voltage.
     *
     * @return the transformer primary voltage.
     */
    def primary: String = Math.round (transformer.v0).toString

    /**
     * Secondary voltage.
     *
     * @return the transformer secondary voltage.
     */
    def secondary: String = Math.round (transformer.v1).toString

    /**
     * Format a number in exponential notation without a decimal separator (decimal point or comma).
     *
     * @param d the number to format
     * @return a number of the form ddde±d
     */
    def nodecimals (d: Double): String =
    {
        val raw = "%1.2e".format (d)
        val parts = raw.split (Array ('.', ',', 'e'))
        val mantissa = parts (0) + parts (1)
        val exponent = parts (2).toInt - parts (1).length
        "%se%+d".format (mantissa, exponent)
    }

    /**
     * Per unit impedance.
     *
     * @return the transformer (set) per unit impedance in Ω
     */
    def per_unit_impedance: String =
    {
        val (re: Double, im: Double) = transformer.total_impedance_per_unit._1.asPair
        "%s%s%sj".format (nodecimals (re), if (im > 0.0) "+" else "", nodecimals (im))
    }

    /**
     * Get the configuration name (of the ganged transformer).
     *
     * @return An appropriate transformer configuration name.
     */
    def configurationName: String =
    {
        // "630kVA20000$400V123e-3+240e-2jΩ"
        val n = valid_config_name ("%skVA%s$%sV%sΩ".format (rating, primary, secondary, per_unit_impedance))
        // limit to 64 bytes with null:
        // typedef struct s_objecttree {
        //     char name[64];
        //     OBJECT *obj;
        //     struct s_objecttree *before, *after;
        //     int balance; /* unused */
        // } OBJECTTREE;
        if (n.getBytes.length > 63)
            "_" + Math.abs (n.hashCode ())
        else
            n
    }

    def configuration (generator: GLMGenerator): String =
    {
        // see http://gridlab-d.sourceforge.net/wiki/index.php/Power_Flow_User_Guide#Transformer_Configuration_Parameters
        val config = configurationName
        val (total_impedance, default) = transformer.total_impedance_per_unit
        val warn = if (default) s"\n#warning WARNING: using default impedance for $config" else ""
        val comment =  transformer.transformers.map (trafo => s"            // ${trafo.transformer.id}").mkString ("\n")
        // ToDo: should be DELTA_GWYE (Dyn5), pick up windingConnection values from CIM (see https://www.answers.com/Q/What_is_the_meaning_of_DYN_11_on_a_transformer_nameplate)
        val connect = if (generator.isSinglePhase) "WYE_WYE" else "DELTA_GWYE"

        val ret =
        s"""$warn
            |        object transformer_configuration
            |        {
            |$comment
            |            name "$config";
            |            connect_type $connect;
            |            install_type PADMOUNT;
            |            power_rating ${transformer.power_rating / 1000.0};
            |            primary_voltage ${transformer.v0};
            |            secondary_voltage ${if (multiwinding) transformer.v0 else transformer.v1};
            |            resistance ${total_impedance.re};
            |            reactance ${total_impedance.im};
            |        };
            |""".stripMargin

        val w = if (multiwinding)
        {
            // we need to emit pseudo configurations
            val c = for (winding <- lv_windings)
                yield
                {
                    val number = winding.TransformerEnd.endNumber - 1
                    val comment = s"\n            // multi-winding transformer low voltage winding $number"
                    val conf = s"${config}_winding_$number"
                    val v = transformer.transformers(0).voltages(number)._2

                    s"""
                        |        object transformer_configuration
                        |        {
                        |$comment
                        |            name "$conf";
                        |            connect_type $connect;
                        |            power_rating ${transformer.power_rating / 1000.0};
                        |            primary_voltage ${transformer.v0};
                        |            secondary_voltage $v;
                        |            resistance 1e-9;
                        |            reactance 1e-9;
                        |        };
                        |""".stripMargin
                }
            c.mkString
        }
        else
            ""

        ret + w
    }
}