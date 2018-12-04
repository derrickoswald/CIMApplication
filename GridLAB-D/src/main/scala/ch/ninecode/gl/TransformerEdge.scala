package ch.ninecode.gl

case class TransformerEdge
(
    cn1: String,
    cn2: String,
    transformer: TransformerSet
)
    extends GLMEdge
{
    def id: String = transformer.transformer_name

    /**
     * Emit a transformer.
     *
     * @param generator the driver program
     * @return A transformer string (.glm text) for this edge.
     */
    override def emit (generator: GLMGenerator): String =
    {
        """
          |        object transformer
          |        {
          |            name "%s";
          |            phases %s;
          |            from "%s";
          |            to "%s";
          |            configuration "%s";
          |        };
          |""".stripMargin.format (transformer.transformer_name, if (generator.isSinglePhase) "AN" else "ABCN", cn1, cn2, configurationName)
        // ToDo: was transformer.node0, transformer.node1: can we get rid of those
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
        """%s
          |        object transformer_configuration
          |        {
          |            name "%s";
          |            connect_type %s;
          |            install_type PADMOUNT;
          |            power_rating %s;
          |            primary_voltage %s;
          |            secondary_voltage %s;
          |            resistance %s;
          |            reactance %s;
          |        };
          |""".stripMargin.format (
            if (default) "\n#warning WARNING: using default impedance for " + config else "",
            config,
            "WYE_WYE", // ToDo: should be DELTA_GWYE (Dyn5), pick up windingConnection values from CIM (see https://www.answers.com/Q/What_is_the_meaning_of_DYN_11_on_a_transformer_nameplate)
            transformer.power_rating / 1000.0,
            transformer.v0,
            transformer.v1,
            total_impedance.re,
            total_impedance.im)
    }
}