package ch.ninecode.esl

import ch.ninecode.gl.GLMNode

/**
 * Vertex data for the precalculation Pregel algorithm.
 *
 * @param id Node mRID.
 * @param nominal_voltage Nominal voltage.
 * @param source_obj Feeding transformer.
 * @param sum_r Summation of resistance values in the path from the feeding transformer to this node.
 * @param min_ir Minimum conductor current rating in the path from the feeding transformer to this node.
 * @param multiple_paths Set <code>true</code> if the trace found multiple feeding transformers.
 */
case class PowerFeedingNode (
    id: String,
    nominal_voltage: Double,
    source_obj: StartingTrafos,
    sum_r: Double,
    min_ir: Double,
    multiple_paths: Boolean) extends GLMNode
{
    def asString: String = "[%s %gV %s %gΩ %gA%s]".format (id, nominal_voltage, source_obj.asString, sum_r, min_ir, if (multiple_paths) " m" else "")
}
