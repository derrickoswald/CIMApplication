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
        for (winding <- transformer.transformers (0).ends
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
            val sec = for (index <- lv_windings.indices)
                yield
                    {
                        val config = configurationName
                        val number = lv_windings (index).TransformerEnd.endNumber - 1
                        val conf = s"${config}_winding_$number"

                        s"""
                           |        object transformer
                           |        {
                           |            name "${transformer.transformer_name}_$number";
                           |            phases $phase;
                           |            from "${transformer.node0}";
                           |            to "${transformer.transformers (0).nodes (index + 1).id}";
                           |            configuration "$conf";
                           |        };
                           |""".stripMargin
                    }
            sec.mkString
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

    def configuration (generator: GLMGenerator, remark: String = null): String =
    {
        // see http://gridlab-d.sourceforge.net/wiki/index.php/Power_Flow_User_Guide#Transformer_Configuration_Parameters
        val config = configurationName
        val (total_impedance, default) = transformer.total_impedance_per_unit
        val warn = if (default) s"\n#warning WARNING: using default impedance for $config" else ""
        val comment = if (null == remark)
            transformer.transformers.map (trafo => s"            // ${trafo.transformer.id}").mkString ("\n")
        else
            s"            // $remark"
        // ToDo: should be DELTA_GWYE (Dyn5), pick up windingConnection values from CIM (see https://www.answers.com/Q/What_is_the_meaning_of_DYN_11_on_a_transformer_nameplate)
        val connect = if (generator.isSinglePhase) "WYE_WYE" else "DELTA_GWYE"

        def nonNegative (d: Double): Double =
        {
            val n = math.abs (d)
            if (n < 1e-7) 1e-7 else n
        }

        if (!multiwinding)
        {
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
               |            resistance ${nonNegative (total_impedance.re)};
               |            reactance ${nonNegative (total_impedance.im)};
               |        };
               |""".stripMargin
        } else
        {

            val array = for (index <- lv_windings.indices)
                yield
                    {
                        val number = lv_windings (index).TransformerEnd.endNumber - 1
                        val power = lv_windings (index).ratedS
                        val voltsPrimary = transformer.v0
                        val voltsSecondary = transformer.transformers (0).voltages (index + 1)._2
                        //Zohms = Zpu * Zbase
                        //Zbase = Vll^2 / Sbase
                        val Zbase = voltsSecondary * voltsSecondary / power
                        val Rpu = lv_windings (index).r / Zbase
                        val Xpu = lv_windings (index).x / Zbase
                        s"""$warn
                           |        object transformer_configuration
                           |        {
                           |$comment
                           |            name ${config}_winding_$number;
                           |            connect_type $connect;
                           |            install_type PADMOUNT;
                           |            power_rating ${power / 1000.0};
                           |            primary_voltage ${voltsPrimary};
                           |            secondary_voltage ${voltsSecondary};
                           |            resistance ${nonNegative (Rpu)};
                           |            reactance ${nonNegative (Xpu)};
                           |        };
                           |""".stripMargin
                    }
            array.mkString
        }
    }
}