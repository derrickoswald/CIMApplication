package ch.ninecode.gl

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Groups ganged transformers (usually identical tranformers in parallel).
 *
 * @param transformers The set of ttansformers in this gang.
 */
case class TransformerSet (transformers: Array[TData])
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
            "_" + Math.abs (n.hashCode()) + "_" + n.substring (0, n.indexOf ("_", 32)) + "_etc"
        else
            n
    }

    // primary and secondary voltage should be the same on all edges - use the first
    val v0: Double =
    {
        val v = transformers.head.voltage0
        if (!transformers.forall (_.voltage0 == v))
            log.error ("transformer set " + transformer_name + " has different voltages on terminal 0 " + transformers.map ((x) => x.voltage0).mkString (" "))
        // ToDo: fix this 1kV multiplier on the voltages
        1000.0 * v
    }
    val v1: Double =
    {
        val v = transformers.head.voltage1
        if (!transformers.forall (_.voltage1 == v))
            log.error ("transformer set " + transformer_name + " has different voltages on terminal 1 " + transformers.map ((x) => x.voltage1).mkString (" "))
        // ToDo: fix this 1kV multiplier on the voltages
        1000.0 * v
    }

    // all primaries and secondaries should be connected to the same nodes (respectively)
    val node0: String =
    {
        val n = transformers.head.node0
        if (!transformers.forall (_.node0 == n))
            log.error ("transformer set " + transformer_name + " has different nodes on terminal 0 " + transformers.map ((x) => x.node0).mkString (" "))
        n
    }
    val node1: String =
    {
        val n = transformers.head.node1
        if (!transformers.forall (_.node1 == n))
            log.error ("transformer set " + transformer_name + " has different nodes on terminal 1 " + transformers.map ((x) => x.node1).mkString (" "))
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
            "_" + Math.abs (n.hashCode()) + "_" + n.substring (0, n.indexOf ("||", 32)) + "_etc"
        else
            n
    }

    // rated power is the sum of the powers - use low voltage side, but high voltage side is the same for simple transformer
    val power_rating: Double = transformers.foldLeft (0.0) ((sum, edge) => sum + edge.end1.ratedS)

    // calculate the impedance as 1 / sum (1/Zi)
    val impedances: Array[Complex] = transformers.map (
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

    /**
     *  Return the total impedance and a flag indicating if it is the default value (no impedances were non-zero)
     *  i.e. (total_impedance, default)
     */
    val total_impedance: (Complex, Boolean) =
    {
        val zero = Complex (0.0)
        if (impedances.foldLeft (zero)(_.+(_)) == zero)
            (Complex (2.397460317, 16.07618325), true)
        else
            (impedances.map (_.reciprocal).foldLeft (zero)(_.+(_)).reciprocal, false)
    }
}
