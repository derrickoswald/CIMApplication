package ch.ninecode.gl

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.model.EquivalentInjection
import ch.ninecode.util._

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
 * @param transformers         the set of transformers in this gang
 * @param default_power_rating the default power rating, VA
 * @param default_impedance    the default characteristic impedance, Ω
 */
case class TransformerSet (transformers: Array[TransformerData], default_power_rating: Double = 630000, default_impedance: Complex = Complex (0.005899999998374999, 0.039562482211875))
{
    val log: Logger = LoggerFactory.getLogger (getClass)

    // there should be at least one transformer
    transformers.length match
    {
        case 0 ⇒
            throw new IllegalStateException ("no transformers in TransformerData array")
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
        val v = transformers.head.v0
        if (!transformers.forall (_.v0 == v))
            log.error ("transformer set " + transformer_name + " has different voltages on terminal 0 " + transformers.map (_.v0).mkString (" "))
        v
    }

    val v1: Double =
    {
        val v = transformers.head.v1
        if (!transformers.forall (_.v1 == v))
            log.error ("transformer set " + transformer_name + " has different voltages on terminal 1 " + transformers.map (_.v1).mkString (" "))
        v
    }

    // all primaries and secondaries should be connected to the same nodes (respectively)
    val node0: String =
    {
        val n = transformers.head.node0.id
        if (!transformers.tail.forall (_.node0.id == n))
            log.error ("transformer set " + transformer_name + " has different nodes on terminal 0 " + transformers.map (_.node0.id).mkString (" "))
        n
    }

    val node1: String =
    {
        val n = transformers.head.node1.id
        if (!transformers.tail.forall (_.node1.id == n))
            log.error ("transformer set " + transformer_name + " has different nodes on terminal 1 " + transformers.map (_.node1.id).mkString (" "))
        n
    }

    // make a valid configuration name
    def valid_config_name (string: String): String =
    {
        val s = if ((null == string) || ("" == string))
            "unknown"
        else
            if (string.charAt (0).isLetter || ('_' == string.charAt (0)))
                string
            else
                "_" + string
        s.replace (".", "d").replace (":", "$")
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

    val base_amps: Double = power_rating / v0 / Math.sqrt (3)

    val base_ohms: Double = v0 / base_amps / Math.sqrt (3)

    // the characteristic transformer impedances at the secondary
    // with a flag indicating if it is the default value (the impedance was zero)
    val impedances: Array[(Complex, Boolean)] = transformers.map (
        edge =>
        {
            if ((0.0 == edge.end1.r) && (0.0 == edge.end1.x))
            {
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
                val base_ohms = v1 * v1 / base_va
                (default_impedance * base_ohms, true)
            }
            else
                (Complex (edge.end1.r, edge.end1.x), false)
        }
    )

    /**
     * Return the total impedance at the secondary and a flag indicating if it is the default value (some impedance was zero).
     *  i.e. (total_impedance, default)
     *
     * Calculate the impedance as 1 / sum (1/Zi)
     */
    val total_impedance: (Complex, Boolean) =
    {
        val zinv = impedances.foldLeft ((Complex (0.0), false))((c1, c2) ⇒ (c1._1 + c2._1.reciprocal, c1._2 || c2._2))
        (zinv._1.reciprocal, zinv._2)
    }

    /**
     * Return the maximum network short circuit power S<sub>k</sub> (VA).
     *
     * @return the network short circuit power (VA)
     */
    val network_short_circuit_power_max: Double =
    {
        def Sk (equiv: EquivalentInjection): Double = math.sqrt (equiv.maxP * equiv.maxP + equiv.maxQ * equiv.maxQ)

        val power = Sk (transformers.head.shortcircuit)
        if (!transformers.tail.forall (x ⇒ Sk (x.shortcircuit) == power))
            log.error ("transformer set " + transformer_name + " has differing maximum network short circuit powers " + transformers.map (x ⇒ Sk (x.shortcircuit)).mkString (" "))
        power
    }

    /**
     * Return the minimum network short circuit power S<sub>k</sub> (VA).
     *
     * @return the network short circuit power (VA)
     */
    val network_short_circuit_power_min: Double =
    {
        def Sk (equiv: EquivalentInjection): Double = math.sqrt (equiv.minP * equiv.minP + equiv.minQ * equiv.minQ)

        val power = Sk (transformers.head.shortcircuit)
        if (!transformers.tail.forall (x ⇒ Sk (x.shortcircuit) == power))
            log.error ("transformer set " + transformer_name + " has differing minimum network short circuit powers " + transformers.map (x ⇒ Sk (x.shortcircuit)).mkString (" "))
        power
    }

    /**
     * Return the network impedance at maximum short circuit power (Ω).
     *
     * @return the network impedance (Ω)
     */
    val network_short_circuit_impedance_max: Complex =
    {
        def Z (equiv: EquivalentInjection): Complex = Complex (equiv.r, equiv.x)

        val z = Z (transformers.head.shortcircuit)
        if (!transformers.tail.forall (x ⇒ Z (x.shortcircuit) == z))
            log.error ("transformer set " + transformer_name + " has differing maximum network short circuit impedance " + transformers.map (x ⇒ Z (x.shortcircuit)).mkString (" ") + " using the r and x")

        // check against Sk
        def Z2 (equiv: EquivalentInjection): Complex =
        {
            def Sk (equiv: EquivalentInjection): Double = math.sqrt (equiv.maxP * equiv.maxP + equiv.maxQ * equiv.maxQ)

            def Wik (equiv: EquivalentInjection): Double = math.atan2 (equiv.maxQ, equiv.maxP)

            val power = Sk (transformers.head.shortcircuit)
            val angle = Wik (transformers.head.shortcircuit)
            val c = 1.0
            val z = (c * v0 * v0) / power
            val r = z * Math.cos (angle)
            val x = z * Math.sin (angle)
            Complex (r, x)
        }

        val z2 = Z2 (transformers.head.shortcircuit)
        if (!transformers.tail.forall (x ⇒ Z2 (x.shortcircuit) == z2))
            log.error ("transformer set " + transformer_name + " has differing maximum network short circuit impedance " + transformers.map (x ⇒ Z2 (x.shortcircuit)).mkString (" ") + " using the maxP and maxQ")

        if (Complex (0.0) != z) z else z2
    }

    /**
     * Return the network impedance at minimum short circuit power (Ω).
     *
     * @return the network impedance (Ω)
     */
    val network_short_circuit_impedance_min: Complex =
    {
        def Z (equiv: EquivalentInjection): Complex =
        {
            def Sk (equiv: EquivalentInjection): Double =
            {
                var p = math.sqrt (equiv.minP * equiv.minP + equiv.minQ * equiv.minQ)
                if (0.0 == p)
                    p = math.sqrt (equiv.maxP * equiv.maxP + equiv.maxQ * equiv.maxQ)
                p
            }

            def Wik (equiv: EquivalentInjection): Double =
            {
                if (0.0 == equiv.minP * equiv.minP + equiv.minQ * equiv.minQ)
                    math.atan2 (equiv.maxQ, equiv.maxP)
                else
                    math.atan2 (equiv.minQ, equiv.minP)
            }

            val power = Sk (transformers.head.shortcircuit)
            val angle = Wik (transformers.head.shortcircuit)
            val c = 1.0
            val z = (c * v0 * v0) / power
            val r = z * Math.cos (angle)
            val x = z * Math.sin (angle)
            Complex (r, x)
        }

        val z = Z (transformers.head.shortcircuit)
        if (!transformers.tail.forall (x ⇒ Z (x.shortcircuit) == z))
            log.error ("transformer set " + transformer_name + " has differing minimum network short circuit impedance " + transformers.map (x ⇒ Z (x.shortcircuit)).mkString (" ") + " using the minP and minQ")

        z
    }

    /**
     * Return the maximum network short circuit zero sequence impedance Z (Ω).
     *
     * @return the network zero sequence impedance (Ω)
     */
    val network_short_circuit_zero_sequence_impedance_max: Complex =
    {
        def Z (equiv: EquivalentInjection): Complex = Complex (equiv.r0, equiv.x0)

        val impedance = Z (transformers.head.shortcircuit)
        if (!transformers.tail.forall (x ⇒ Z (x.shortcircuit) == impedance))
            log.error ("transformer set " + transformer_name + " has differing maximum network zero sequence short circuit impedance " + transformers.map (x ⇒ Z (x.shortcircuit)).mkString (" ") + " using the r0 and x0")
        impedance
    }

    /**
     * Return the total impedance and a flag indicating if it includes a default value (an impedance was zero)
     *  i.e. (total_impedance, default)
     * calculate the impedance as 1 / sum (1/Zi)
     */
    val total_impedance_per_unit: (Complex, Boolean) =
    {
        // get a list of primary impedances or default
        val impedances: Array[(Complex, Boolean)] = transformers.map (
            edge =>
            {
                // this end's impedance
                var default = false
                var r = edge.end0.r
                var x = edge.end0.x
                if ((0.0 == r) && (0.0 == x))
                {
                    val z = default_impedance * base_ohms
                    r = z.re // 2.397460317
                    x = z.im // 16.07618325
                    default = true
                }
                (Complex (r, x), default)
            }
        )

        val zero = Complex (0.0)
        val inv = impedances.map (z ⇒ (z._1.reciprocal, z._2)).foldLeft ((zero, false))((z1, z2) ⇒ (z1._1 + z2._1, z1._2 || z2._2))
        val parallel = (inv._1.reciprocal, inv._2)

        // per unit impedance
        if (0.0 == base_ohms)
            (Complex (2.397460317, 16.07618325), true)
        else
            (parallel._1 / base_ohms, parallel._2)
    }

    override def toString: String =
    {
        "%s %skVA %s:%s".format (transformer_name, power_rating / 1000.0, v0, v1)
    }
}
