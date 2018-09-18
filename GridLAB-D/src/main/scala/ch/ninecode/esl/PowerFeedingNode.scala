package ch.ninecode.esl

import ch.ninecode.gl.GLMNode

/**
 * Vertex data for the precalculation Pregel algorithm.
 *
 * @param id_seq Node mRID.
 * @param voltage Nominal voltage.
 * @param source_obj Feeding transformer.
 * @param sum_r Summation of resistance values in the path from the feeding transformer to this node.
 * @param min_ir Minimum conductor current rating in the path from the feeding transformer to this node.
 * @param multiple_paths Set <code>true</code> if the trace found multiple feeding transformers.
 */
case class PowerFeedingNode (
    id_seq: String,
    voltage: Double,
    source_obj: StartingTrafos,
    sum_r: Double,
    min_ir: Double,
    multiple_paths: Boolean) extends GLMNode
{
    override def id: String = id_seq
    override def nominal_voltage: Double = voltage
    def asString: String = "[%s %gV %s %gΩ %gA%s]".format (id_seq, voltage, source_obj.asString, sum_r, min_ir, if (multiple_paths) " m" else "")
}
