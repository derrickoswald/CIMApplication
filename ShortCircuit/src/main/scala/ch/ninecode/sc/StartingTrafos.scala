package ch.ninecode.sc

import org.apache.spark.graphx.VertexId

/**
 * Transformers for which an available power and angle is known, to be used as the starting points for calculation.
 *
 * @param osPin the VertexId of the primary node
 * @param nsPin the VertexId of the secondary node
 * @param transformer the transformer(s) connected to the (high or medium voltage) network
 */
case class StartingTrafos (osPin: VertexId, nsPin: VertexId, transformer: TransformerSet)
extends
    Serializable
{
    lazy val primary_impedance: Impedanzen =
    {
        val c = 1.0

        //val ratioZ0Z1 = 4
        //val ratioX0R0 = 10

        val v1 = transformer.v0
        val v2 = transformer.v1
        val sk = transformer.network_short_circuit_power
        val zqt = transformer.network_short_circuit_impedance
        //val zqt0 = zqt * ratioZ0Z1
        val netz_r1 = zqt.re
        val netz_x1 = zqt.im
        val netz_r0 = 0.0 // zqt0 * Math.cos (Math.abs (Math.atan (ratioX0R0)))
        val netz_x0 = 0.0 // zqt0 * Math.sin (Math.abs (Math.atan (ratioX0R0)))
        Impedanzen (Complex (netz_r1, netz_x1), Complex (netz_r0, netz_x0))
    }

    lazy val secondary_impedance: Impedanzen =
    {
        val zt = transformer.total_impedance
//        if (zt._2)
//            log.warn ("transformer %s impedance not available, using approximation".format (transformer.transformer_name))
        // convert from per unit to secondary
        val zt2 = zt._1 * ((transformer.v1 * transformer.v1) / transformer.base_va)

        val trafo_r1 = zt2.re
        val trafo_r0 = zt2.re // use r0=r1 & x0=x1 for trafos
        val trafo_x1 = zt2.im
        val trafo_x0 = zt2.im // use r0=r1 & x0=x1 for trafos
        val v1 = transformer.v0
        val v2 = transformer.v1
        val ratio = v2 / v1
        val ratio2 = ratio * ratio
        Impedanzen (Complex (trafo_r1, trafo_x1) + (primary_impedance.impedanz * ratio2), Complex (trafo_r0, trafo_x0) + (primary_impedance.null_impedanz * ratio2))
    }
}