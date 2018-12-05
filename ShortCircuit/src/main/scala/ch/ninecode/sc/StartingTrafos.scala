package ch.ninecode.sc

import org.apache.spark.graphx.VertexId

import ch.ninecode.gl.Complex
import ch.ninecode.gl.TransformerSet

/**
 * Transformers for which an available power and angle is known, to be used as the starting points for calculation.
 *
 * @param osPin       the VertexId of the primary node
 * @param nsPin       the VertexId of the secondary node
 * @param transformer the transformer(s) connected to the (high or medium voltage) network
 */
case class StartingTrafos (osPin: VertexId, nsPin: VertexId, transformer: TransformerSet) extends Serializable
{
    val primary_impedance: Impedanzen =
    {
        val z1_max = transformer.network_short_circuit_impedance_max
        val z1_min = transformer.network_short_circuit_impedance_min
        val netz_r0 = 0.0
        val netz_x0 = 0.0
        val z0_max = Complex (netz_r0, netz_x0)
        val z0_min = Complex (netz_r0, netz_x0)
        Impedanzen (z1_max, z0_max, z1_min, z0_min)
    }

    val secondary_impedance: Impedanzen =
    {
        val zt = transformer.total_impedance._1
        val trafo_r1 = zt.re
        val trafo_r0 = zt.re // use r0=r1 & x0=x1 for trafos
        val trafo_x1 = zt.im
        val trafo_x0 = zt.im // use r0=r1 & x0=x1 for trafos
        val v1 = transformer.v0
        val v2 = transformer.v1
        val ratio = v2 / v1
        val ratio2 = ratio * ratio
        Impedanzen (
            Complex (trafo_r1, trafo_x1) + (primary_impedance.impedanz_low * ratio2), Complex (trafo_r0, trafo_x0) + (primary_impedance.null_impedanz_low * ratio2),
            Complex (trafo_r1, trafo_x1) + (primary_impedance.impedanz_high * ratio2), Complex (trafo_r0, trafo_x0) + (primary_impedance.null_impedanz_high * ratio2))
    }
}