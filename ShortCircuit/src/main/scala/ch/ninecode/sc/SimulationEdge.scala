package ch.ninecode.sc

import ch.ninecode.gl.GLMEdge
import ch.ninecode.gl.Graphable
import ch.ninecode.model.Element

/**
 * Edge data.
 *
 * @param id_equ ConductingEquipment MRID.
 * @param id_cn_1 Terminal 1 ConnectivityNode or TopologicalNode MRID.
 * @param id_cn_2 Terminal 2 ConnectivityNode or TopologicalNode MRID.
 * @param element Element object for the edge.
 */
case class SimulationEdge (
        id_equ: String,
        id_cn_1: String,
        id_cn_2: String,
        element: Element
    ) extends GLMEdge with Graphable
{
    /**
     * Ordered key.
     * Provide a key on the two connections, independent of to-from from-to ordering.
     */
    def key: String = if (id_cn_1 < id_cn_2) id_cn_1 + id_cn_2 else id_cn_2 + id_cn_1
    override def id: String = id_equ
    override def cn1: String = id_cn_1
    override def cn2: String = id_cn_2
    override def el: Element = element
}
