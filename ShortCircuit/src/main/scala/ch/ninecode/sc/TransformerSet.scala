package ch.ninecode.sc

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Groups ganged transformers (usually identical transformers in parallel).
 *
 * Allows for an optional default power rating for any transformer without ratedS.
 * If none is supplied uses 630000.0 (630kVA).
 *
 * Allows for an optional default impedance for any transformer without values, r + jxΩ.
 * If none is supplied, uses 0.0059 + 0.04jΩ,
 * which corresponds to a typical primary impedance of a 630KVA transformer 16KV:400V of 2.4 + j16Ω
 * or a secondary impedance of 0.0015 +j0.01:
 * <ul>
 * <li>base_ohms = v<sup>2</sup> ÷ base_va = 16000<sup>2</sup> ÷ 630000 = 406.34</li>
 * <li>r ÷ base_ohms = 2.4 / 406.34 = 0.0059</li>
 * <li>x ÷ base_ohms = 16 / 406.34 = 0.04</li>
 * </ul>
 * or
 * <ul>
 * <li>base_ohms = v<sup>2</sup> ÷ base_va = 400<sup>2</sup> ÷ 630000 = 0.25397</li>
 * <li>r ÷ base_ohms = 0.0015 / 0.25397 = 0.0059</li>
 * <li>x ÷ base_ohms = 0.01 / 0.25397 = 0.04</li>
 * </ul>
 *
 * @param transformers the set of transformers in this gang
 * @param default_power_rating the default power rating, VA
 * @param default_impedance the default characteristic impedance, Ω
 */
case class TransformerSet (transformers: Array[TData], default_power_rating: Double = 630000, default_impedance: Complex = Complex (0.005899999998374999, 0.039562482211875))
{
    val log: Logger = LoggerFactory.getLogger (getClass)

    // there should be at least one transformer
    transformers.length match
    {
        case 0 ⇒
            throw new IllegalStateException ("no transformers in TData array")
        case _ ⇒
    }

    // get the transformer name (of the parallel transformers)
    val transformer_name: String =
    {
        val n = transformers.map (_.transformer.id).map (valid_config_name).sortWith (_ < _).mkString ("_")
        if (n.getBytes.length > 63)
            "_" + Math.abs (n.hashCode ()) + "_" + n.substring (0, n.indexOf ("_", 32)) + "_etc"
        else
            n
    }

    // primary and secondary voltage should be the same on all edges - use the first
    val v0: Double =
    {
        val v = transformers.head.voltage0
        if (!transformers.forall (_.voltage0 == v))
            log.error ("transformer set " + transformer_name + " has different voltages on terminal 0 " + transformers.map (_.voltage0).mkString (" "))
        // ToDo: fix this 1kV multiplier on the voltages
        1000.0 * v
    }

    val v1: Double =
    {
        val v = transformers.head.voltage1
        if (!transformers.forall (_.voltage1 == v))
            log.error ("transformer set " + transformer_name + " has different voltages on terminal 1 " + transformers.map (_.voltage1).mkString (" "))
        // ToDo: fix this 1kV multiplier on the voltages
        1000.0 * v
    }

    // all primaries and secondaries should be connected to the same nodes (respectively)
    val node0: String =
    {
        val n = transformers.head.node0
        if (!transformers.forall (_.node0 == n))
            log.error ("transformer set " + transformer_name + " has different nodes on terminal 0 " + transformers.map (_.node0).mkString (" "))
        n
    }
    val node1: String =
    {
        val n = transformers.head.node1
        if (!transformers.forall (_.node1 == n))
            log.error ("transformer set " + transformer_name + " has different nodes on terminal 1 " + transformers.map (_.node1).mkString (" "))
        n
    }

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
    val configurationName: String =
    {
        val n = transformers.map (_.transformer.id).map (valid_config_name).sortWith (_ < _).mkString ("||") + "_configuration"
        // limit to 64 bytes with null:
        // typedef struct s_objecttree {
        //     char name[64];
        //     OBJECT *obj;
        //     struct s_objecttree *before, *after;
        //     int balance; /* unused */
        // } OBJECTTREE;
        if (n.getBytes.length > 63)
            "_" + Math.abs (n.hashCode ()) + "_" + n.substring (0, n.indexOf ("||", 32)) + "_etc"
        else
            n
    }

    // rated power is the sum of the powers - use low voltage side, but high voltage side is the same for simple transformer
    val power_rating: Double = transformers.map (edge ⇒ if (0.0 == edge.end1.ratedS) default_power_rating else edge.end1.ratedS).sum

    // base power (for per unit calculations)
    val base_va: Double =
    {
        val va = transformers.map (_.end1.ratedS).max
        if (!transformers.forall (_.end1.ratedS == va))
            log.error ("transformer set " + transformer_name + " has units with different base VA " + transformers.map (_.end1.ratedS).mkString (" "))
        if (0.0 == va)
            default_power_rating
        else
            va
    }

    // the characteristic transformer impedances at the secondary
    // with a flag indicating if it is the default value (the impedance was zero)
    val impedances: Array[(Complex, Boolean)] = transformers.map (
        (edge) =>
        {
            if ((0.0 == edge.end1.r) && (0.0 == edge.end1.x))
            {
                val base_ohms = v1 * v1 / base_va
                (default_impedance / base_ohms, true)
            }
            else
                (Complex (edge.end1.r, edge.end1.x), false)
        }
    )

    /**
     *  Return the total impedance at the secondary and a flag indicating if it is the default value (some impedance was zero).
     *  i.e. (total_impedance, default)
     *
     *  Calculate the impedance as 1 / sum (1/Zi)
     */
    val total_impedance: (Complex, Boolean) =
    {
        val zinv = impedances.foldLeft ((Complex (0.0), false))((c1, c2) ⇒ (c1._1 + c2._1.reciprocal, c1._2 || c2._2))
        (zinv._1.reciprocal, zinv._2)
    }

    val network_short_circuit_power: Double =
    {
        val power = transformers.head.shortcircuit.maxP
        if (!transformers.forall (_.shortcircuit.maxP == power))
            log.error ("transformer set " + transformer_name + " has differing network short circuit powers " + transformers.map (_.shortcircuit.maxP).mkString (" "))
        power
    }

    val network_short_circuit_impedance: Complex =
    {
        val impedance = Complex (transformers.head.shortcircuit.r, transformers.head.shortcircuit.x)
        if (!transformers.forall (x ⇒ Complex (x.shortcircuit.r, x.shortcircuit.x) == impedance))
            log.error ("transformer set " + transformer_name + " has differing network short circuit impedance " + transformers.map (x ⇒ Complex (x.shortcircuit.r, x.shortcircuit.x)).mkString (" "))
        impedance
    }
}
