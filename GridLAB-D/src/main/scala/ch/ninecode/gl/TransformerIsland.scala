package ch.ninecode.gl

/**
 * Groups transformers supplying the same TopologicalIsland.
 *
 * @param transformers The transformers that have their secondary node connected to the same island.
 */
case class TransformerIsland (transformers: Array[TransformerSet])
{
    // make a valid configuration name
    // - names must start with a letter or an underscore
    // - period and colon characters are problematic
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

    // get the island name (of the combination of transformers)
    lazy val island_name: String =
    {
        val n = transformers.map (_.transformer_name).map (valid_config_name).sortWith (_ < _).mkString ("_")
        if (n.getBytes.length > 63)
            "_" + Math.abs (n.hashCode ()) + "_" + n.substring (0, n.indexOf ("_", 32)) + "_etc"
        else
            n
    }

    // ToDo, do we need to break this out? or is a sum good enough?
    lazy val power_rating: Double = transformers.map (_.power_rating).sum

    override def toString: String =
    {
        s"$island_name ${power_rating / 1000.0}kVA"
    }
}

object TransformerIsland
{
    def apply (transformers: Iterable[TransformerData]): TransformerIsland =
    {
        val sets = transformers
            .groupBy (t => s"${t.node1}")
            .map (set => TransformerSet (set._2.toArray))
            .toArray
        TransformerIsland (sets)
    }

    def apply (transformers: Iterable[TransformerData], default_power_rating: Double = 630000, default_impedance: Complex = Complex (0.005899999998374999, 0.039562482211875)): TransformerIsland =
    {
        val sets = transformers
            .groupBy (t => s"${t.node1}")
            .map (set => TransformerSet (set._2.toArray, default_power_rating, default_impedance))
            .toArray
        TransformerIsland (sets)
    }
}
