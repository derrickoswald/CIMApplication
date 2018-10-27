package ch.ninecode.mfi

import ch.ninecode.gl.GLMNode

/**
 * Vertex data for the precalculation Pregel algorithm.
 *
 * @param id Node mRID.
 * @param nominal_voltage Nominal voltage.
 * @param source_obj Feeding transformer.
 * @param sum_r Summation of resistance values in the path from the feeding transformer to this node.
 * @param min_ir Minimum conductor current rating in the path from the feeding transformer to this node.
 * @param problem Error message if the trace found multiple feeding transformers, or a problematic transformer type.
 */
case class PowerFeedingNode (
    id: String,
    nominal_voltage: Double,
    source_obj: StartingTrafos,
    sum_r: Double,
    min_ir: Double,
    problem: String) extends GLMNode
{
    def asString: String = "[%s %gV %s %gΩ %gA %s]".format (id, nominal_voltage, source_obj.asString, sum_r, min_ir, problem)
}
