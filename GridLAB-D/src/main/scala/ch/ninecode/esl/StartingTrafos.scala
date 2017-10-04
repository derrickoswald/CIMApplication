package ch.ninecode.esl

import ch.ninecode.gl.Complex
import org.apache.spark.graphx.VertexId

/**
 * The starting point for the Pregel trace.
 *
 * @param osPin The VertexId for the high voltage node.
 * @param nsPin The VertexId for the low voltage node.
 * @param trafo_id The transformer (or ganged transformers) name.
 * @param z The per unit transformer impedance.
 * @param ratedS The transformer rated power.
 */
case class StartingTrafos (osPin: VertexId, nsPin: VertexId, trafo_id: String, z: Complex, ratedS: Double) extends Serializable
{
    def asString: String = trafo_id
}
