package ch.ninecode.net

import ch.ninecode.model.PowerTransformerEnd

class TransformerEdge
(
    _transformer: TransformerSet
)
extends LoadFlowEdge (
    _transformer.transformer_name,
    _transformer.node0,
    _transformer.node1
)
{
    val transformer: TransformerSet = _transformer

    lazy val name: String = transformer.transformer_name

    // check if this is a multi-winding transformer
    lazy val lv_windings: Array[PowerTransformerEnd] =
        for (winding <- transformer.transformers(0).ends
             if winding.TransformerEnd.endNumber > 1)
            yield winding
    lazy val multiwinding: Boolean = lv_windings.length > 1

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
}
