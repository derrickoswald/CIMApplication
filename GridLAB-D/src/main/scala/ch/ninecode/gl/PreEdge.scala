package ch.ninecode.gl

import ch.ninecode.model.Breaker
import ch.ninecode.model.Cut
import ch.ninecode.model.Disconnector
import ch.ninecode.model.Element
import ch.ninecode.model.Fuse
import ch.ninecode.model.GroundDisconnector
import ch.ninecode.model.Jumper
import ch.ninecode.model.LoadBreakSwitch
import ch.ninecode.model.MktSwitch
import ch.ninecode.model.ProtectedSwitch
import ch.ninecode.model.Recloser
import ch.ninecode.model.Sectionaliser
import ch.ninecode.model.Switch

/**
 * Edge data.
 *
 * @param id_seq_1 Terminal 1 MRID.
 * @param cn1 Terminal 1 ConnectivityNode or TopologicalNode MRID.
 * @param v1 Terminal 1 voltage
 * @param id_seq_2 Terminal 2 MRID.
 * @param cn2 Terminal 2 ConnectivityNode or TopologicalNode MRID.
 * @param v2 Terminal 2 voltage
 * @param id ConductingEquipment MRID.
 * @param ratedCurrent Cable rated current (A).
 * @param element Element object for the edge.
 */
case class PreEdge(
    id_seq_1: String,
    cn1: String,
    v1: Double,
    id_seq_2: String,
    cn2: String,
    v2: Double,
    id: String,
    ratedCurrent: Double,
    element: Element)
extends GLMEdge
{
    override def emit (generator: GLMGenerator): String = "" // there isn't anything emitted by PreEdges

    /**
     * Flag indicating if there is connectivity through the edge (if the Pregel algorithm should continue tracing) or not.
     */
    val connected: Boolean =
    {
        val clazz = element.getClass.getName
        val cls = clazz.substring(clazz.lastIndexOf(".") + 1)
        cls match {
            case "Switch" ⇒
                !element.asInstanceOf[Switch].normalOpen
            case "Cut" ⇒
                !element.asInstanceOf[Cut].Switch.normalOpen
            case "Disconnector" ⇒
                !element.asInstanceOf[Disconnector].Switch.normalOpen
            case "Fuse" ⇒
                !element.asInstanceOf[Fuse].Switch.normalOpen
            case "GroundDisconnector" ⇒
                !element.asInstanceOf[GroundDisconnector].Switch.normalOpen
            case "Jumper" ⇒
                !element.asInstanceOf[Jumper].Switch.normalOpen
            case "MktSwitch" ⇒
                !element.asInstanceOf[MktSwitch].Switch.normalOpen
            case "ProtectedSwitch" ⇒
                !element.asInstanceOf[ProtectedSwitch].Switch.normalOpen
            case "Breaker" ⇒
                !element.asInstanceOf[Breaker].ProtectedSwitch.Switch.normalOpen
            case "LoadBreakSwitch" ⇒
                !element.asInstanceOf[LoadBreakSwitch].ProtectedSwitch.Switch.normalOpen
            case "Recloser" ⇒
                !element.asInstanceOf[Recloser].ProtectedSwitch.Switch.normalOpen
            case "Sectionaliser" ⇒
                !element.asInstanceOf[Sectionaliser].Switch.normalOpen
            case "Conductor" ⇒
                true
            case "ACLineSegment" ⇒
                true
            case "PowerTransformer" ⇒
                false
            case _ ⇒
                //log.error("trace setup encountered edge " + element.id + " with unhandled class '" + cls + "', assumed conducting")
                true
        }
    }
}
