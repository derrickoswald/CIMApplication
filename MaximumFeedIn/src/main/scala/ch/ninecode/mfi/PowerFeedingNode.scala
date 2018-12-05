package ch.ninecode.mfi

import ch.ninecode.gl.Complex
import ch.ninecode.gl.GLMNode

/**
 * Vertex data for the precalculation Pregel algorithm.
 *
 * @param id              Node mRID.
 * @param nominal_voltage Nominal voltage.
 * @param source_obj      Feeding transformer.
 * @param feeder          Source connection from substation.
 * @param sum_z           Summation of impedance values in the path from the feeding transformer to this node.
 * @param min_ir          Minimum conductor current rating in the path from the feeding transformer to this node.
 * @param problem         Error message if the trace found multiple feeding transformers, or a problematic transformer type.
 */
case class PowerFeedingNode
(
    id: String,
    nominal_voltage: Double,
    source_obj: StartingTrafo,
    feeder: Feeder,
    sum_z: Complex,
    min_ir: Double,
    problem: String) extends GLMNode
{
    def asString: String = "[%s %gV %s@%s %sΩ %gA %s]".format (id, nominal_voltage, source_obj.asString, feeder.feeder_id, sum_z.toString, min_ir, problem)
}
