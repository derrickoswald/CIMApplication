package ch.ninecode.sc

import ch.ninecode.model.PowerTransformer
import ch.ninecode.model.PowerTransformerEnd
import ch.ninecode.model.Substation
import ch.ninecode.model.Terminal

/**
 * Transformer data.
 *
 * @param transformer The PowerTransformer object.
 * @param station The Substation object where the transformer is located.
 * @param shortcircuit The ShortCircuit object with the available short circuit power and phase at the primary.
 * @param voltage0 The voltage for the transformer high voltage end (kV).
 * @param end0 The high voltage PowerTransformerEnd.
 * @param terminal0 The high voltage Terminal.
 * @param node0 The ConnectivityNode or TopologicalNode id of the high voltage terminal.
 * @param voltage1 The voltage for the transformer low voltage end (kV).
 * @param end1 The low voltage PowerTransformerEnd.
 * @param terminal1 The low voltage Terminal.
 * @param node1 The ConnectivityNode or TopologicalNode id of the low voltage terminal.
 */
case class TData (
    transformer: PowerTransformer,
    station: Substation,
    shortcircuit: ShortCircuitData,
    end0: PowerTransformerEnd,
    voltage0: Double,
    terminal0: Terminal,
    node0: String,
    end1: PowerTransformerEnd,
    voltage1: Double,
    terminal1: Terminal,
    node1: String)
{
    def asString: String = "%s:%s %gkVA %g:%g %s:%s".format (station.id, transformer.id, end0.ratedS / 1000.0, voltage0 * 1000.0, voltage1 * 1000.0, node0, node1)
}
