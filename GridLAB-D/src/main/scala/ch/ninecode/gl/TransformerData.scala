package ch.ninecode.gl

import ch.ninecode.model.EquivalentInjection
import ch.ninecode.model.PowerTransformer
import ch.ninecode.model.PowerTransformerEnd
import ch.ninecode.model.Substation
import ch.ninecode.model.Terminal

/**
 * Transformer data.
 *
 * Everything you need to know about a transformer.
 *
 * @param transformer  The PowerTransformer object.
 * @param ends         The associated PowerTransformerEnd objects ordered by endNumber (which by convention is descending by voltage).
 * @param terminals    The terminals ordered the same as the ends.
 * @param voltages     The voltages ordered the same as the ends (V).
 * @param station      The Substation object where the transformer is located.
 * @param shortcircuit The EquivalentInjection object with the available short circuit power and impedance at the primary.
 */
case class TransformerData
(
    transformer: PowerTransformer,
    ends: Array[PowerTransformerEnd],
    terminals: Array[Terminal],
    voltages: Array[(String, Double)],
    station: Substation,
    shortcircuit: EquivalentInjection
)
{
    /** The (assumed) index of the primary PowerTransformerEnd */
    val primary: Int = 0

    /** The (assumed) index of the secondary PowerTransformerEnd */
    val secondary: Int = 1

    /** @return the (assumed) primary (high voltage) PowerTransformerEnd */
    def end0: PowerTransformerEnd = ends (primary)

    /** @return the voltage for the transformer primary (high voltage) end (V) */
    def v0: Double = voltages (primary)._2

    /** @return the voltage for the transformer primary (high voltage) end (kV)
     * @deprecated use v0 / 1000.0 */
    def voltage0: Double = v0 / 1000.0

    /** @return the Terminal for the transformer primary (high voltage) end */
    def terminal0: Terminal = terminals (primary)

    /** @return the mRID of the TopologicalNode for the transformer primary (high voltage) end */
    def node0: String = terminal0.TopologicalNode

    /** @return the (assumed) secondary (low voltage) PowerTransformerEnd, for three or more winding transformers this may not be the one you want */
    def end1: PowerTransformerEnd = ends (secondary)

    /** @return the voltage for the transformer secondary (low voltage) end (V) */
    def v1: Double = voltages (secondary)._2

    /** @return the voltage for the transformer secondary (low voltage) end (kV)
     * @deprecated use v1 / 1000.0 */
    def voltage1: Double = v1 / 1000.0

    /** @return the Terminal for the transformer secondary (low voltage) end */
    def terminal1: Terminal = terminals (secondary)

    /** @return the mRID of the TopologicalNode for the transformer secondary (low voltage) end */
    def node1: String = terminal1.TopologicalNode

    /** @return The mRID of the TopologicalNode for the transformer ordered the same as the ends. */
    def nodes: Array[String] = terminals.map (_.TopologicalNode)

    /** @return a summary string for the transformer */
    def asString: String = "%s %s %gkVA %s %s".format (transformer.id, if (null != station) station.id else "", end0.ratedS / 1000.0, voltages.map (_._2 * 1000.0).mkString (":"), nodes.mkString (":"))
}