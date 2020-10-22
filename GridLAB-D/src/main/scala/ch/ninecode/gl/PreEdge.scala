package ch.ninecode.gl

import ch.ninecode.model.Element
import ch.ninecode.net.LoadFlowEdge

/**
 * Edge data.
 *
 * @param id_seq_1     Terminal 1 mRID.
 * @param cn1          Terminal 1 ConnectivityNode or TopologicalNode mRID.
 * @param v1           Terminal 1 voltage (V).
 * @param id_seq_2     Terminal 2 mRID.
 * @param cn2          Terminal 2 ConnectivityNode or TopologicalNode mRID.
 * @param v2           Terminal 2 voltage (V).
 * @param id           ConductingEquipment mRID.
 * @param connected    Flag indicating if there is connectivity through the edge (if the Pregel algorithm should continue tracing) or not.
 * @param problem      Any problem with the ConductingEquipment.
 * @param ratedCurrent Cable rated current (A).
 * @param element      Element object for the edge.
 */
case class PreEdge
(
    id_seq_1: String,
    override val cn1: String,
    v1: Double,
    id_seq_2: String,
    override val cn2: String,
    v2: Double,
    override val id: String,
    connected: Boolean,
    problem: String,
    ratedCurrent: Double,
    element: Element)
    extends LoadFlowEdge(id, cn1, cn2)
        with GLMEdge
{
    override def emit (generator: GLMGenerator): String = "" // there isn't anything emitted by PreEdges
}
