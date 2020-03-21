package ch.ninecode.mfi

import ch.ninecode.gl.GLMNode
import ch.ninecode.gl.PreEdge
import ch.ninecode.net.LoadFlowNode
import ch.ninecode.util.Complex

/**
 * Vertex data for the precalculation Pregel algorithm.
 *
 * @param id              Node mRID.
 * @param prev_node       The previos node in the tracing
 * @param conn_edge       The connected edges
 * @param nominal_voltage Nominal voltage.
 * @param source_obj      Feeding transformer.
 * @param feeder          Source connection from substation.
 * @param sum_z           Summation of impedance values in the path from the feeding transformer to this node.
 * @param min_ir          Minimum conductor current rating in the path from the feeding transformer to this node.
 * @param problem         Error message if the trace found multiple feeding transformers, or a problematic transformer type.
 */
case class PowerFeedingNode
(
    override val id: String,
    prev_node: String,
    conn_edge: Array[PreEdge],
    override val nominal_voltage: Double,
    source_obj: StartingTrafo,
    feeder: Feeder,
    sum_z: Complex,
    min_ir: Double,
    problem: String
) extends LoadFlowNode (id, nominal_voltage)
with GLMNode
{
    def asString: String = "[%s %gV %s@%s %sΩ %gA %s]".format (id, nominal_voltage, if (null == source_obj) "" else source_obj.asString, if (null == feeder) "" else feeder.feeder_id, if (null == sum_z) "" else sum_z.toString, min_ir, problem)

    def hasNonRadial: Boolean =
        (null != problem) &&
        (
            problem.indexOf ("non-radial network") != -1
        )

    def hasIssues: Boolean =
        (null != problem) &&
        (
            problem.indexOf ("invalid element") != -1 ||
            problem.indexOf ("regulator edge") != -1 ||
            problem.indexOf ("subtransmission edge") != -1
        )
}
