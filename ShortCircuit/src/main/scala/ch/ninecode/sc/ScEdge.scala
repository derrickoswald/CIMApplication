package ch.ninecode.sc

import ch.ninecode.model.ACLineSegment
import ch.ninecode.model.Breaker
import ch.ninecode.model.ConductingEquipment
import ch.ninecode.model.Conductor
import ch.ninecode.model.Cut
import ch.ninecode.model.Disconnector
import ch.ninecode.model.Element
import ch.ninecode.model.Fuse
import ch.ninecode.model.GroundDisconnector
import ch.ninecode.model.Jumper
import ch.ninecode.model.LoadBreakSwitch
import ch.ninecode.model.PowerTransformer
import ch.ninecode.model.ProtectedSwitch
import ch.ninecode.model.Recloser
import ch.ninecode.model.Sectionaliser
import ch.ninecode.model.Switch

/**
 * Short circuit extended GraphX edge.
 *
 * @param id_seq_1 Terminal 1 mRID
 * @param id_cn_1 TopologicalNode 1 mRID
 * @param v1 voltage on Terminal 1 (V)
 * @param id_seq_2 Terminal 2 mRID
 * @param id_cn_2 TopologicalNode 2 mRID
 * @param v2 voltage on Terminal 2 (V)
 * @param id_equ ConductingEquipment mRID
 * @param equipment ConductingEquipment object
 * @param element conducting equipment subclass object
 * @param impedance impedance of the edge (Ω)
 */
case class ScEdge (
    id_seq_1: String,
    id_cn_1: String,
    v1: Double,
    id_seq_2: String,
    id_cn_2: String,
    v2: Double,
    id_equ: String,
    equipment: ConductingEquipment,
    element: Element,
    impedance: Impedanzen) extends Graphable with Serializable
{
    import ScEdge._

    /**
     * Method to determine if a switch is closed (both terminals are the same topological node).
     *
     * If the switch has the <code>open</code> attribute set, use that.
     * Otherwise if it has the <code>normalOpen</code> attribute set, use that.
     * Otherwise assume it is closed.
     *
     * @param switch The switch object to test.
     * @return <code>true</code> if the switch is closed, <code>false</code> otherwise.
     */
    def switchClosed (switch: Switch): Boolean =
    {
        if (0 != (switch.bitfields(openMask / 32) | (1 << (openMask % 32))))
            !switch.open // open valid
        else if (0 != (switch.bitfields(normalOpenMask / 32) | (1 << (normalOpenMask % 32))))
            !switch.normalOpen
        else
            true
    }

    /**
     * Predicate that determines if the trace should continue to the given node.
     *
     * @param id_cn TopologicalNode to test
     * @return <code>false</code> for open Switch objects and higher voltage transformer nodes, <code>true</code> otherwise
     */
    def shouldContinueTo (id_cn: String): Boolean =
    {
        element match
        {
            case switch: Switch ⇒ switchClosed (switch)
            case cut: Cut ⇒ switchClosed (cut.Switch)
            case disconnector: Disconnector ⇒ switchClosed (disconnector.Switch)
            case fuse: Fuse ⇒ switchClosed (fuse.Switch)
            case gd: GroundDisconnector ⇒ switchClosed (gd.Switch)
            case jumper: Jumper ⇒ switchClosed (jumper.Switch)
            case ps: ProtectedSwitch ⇒ switchClosed (ps.Switch)
            case sectionaliser: Sectionaliser ⇒ switchClosed (sectionaliser.Switch)
            case breaker: Breaker ⇒ switchClosed (breaker.ProtectedSwitch.Switch)
            case lbs: LoadBreakSwitch ⇒ switchClosed (lbs.ProtectedSwitch.Switch)
            case recloser: Recloser ⇒ switchClosed (recloser.ProtectedSwitch.Switch)
            case _: PowerTransformer ⇒
                if (id_cn == id_cn_1)
                    v1 < v2
                else if (id_cn == id_cn_2)
                    v2 < v1
                else
                    throw new Exception ("edge %s is not connected to %s (only %s and %s)".format (id_equ, id_cn, id_cn_1, id_cn_2))
            case _ ⇒
                true
        }
    }

    /**
     * Compute impedance to the given node as accumulated from the reference impedance
     *
     * @param id_cn TopologicalNode to compute
     * @param ref impedance of the other end of the edge (Ω)
     * @return impedance of the given node
     */
    def impedanceTo (id_cn: String, ref: Impedanzen): Impedanzen =
    {
        element match
        {
            case line: ACLineSegment ⇒
                val dist_km = line.Conductor.len / 1000.0
                Impedanzen (Complex (line.r * dist_km, line.x * dist_km) + ref.impedanz, Complex (line.r0 * dist_km, line.x0 * dist_km) + ref.null_impedanz)
            case transformer: PowerTransformer ⇒
                if (id_cn == id_cn_1)
                {
                    val ratio = v1 / v2
                    val ratio2 = ratio * ratio
                    val tx_impedance = this.impedance.impedanz
                    Impedanzen (ref.impedanz * ratio2 + tx_impedance, ref.null_impedanz * ratio2 + tx_impedance)
                }
                else if (id_cn == id_cn_2)
                {
                    val ratio = v2 / v1
                    val ratio2 = ratio * ratio
                    val tx_impedance = this.impedance.impedanz
                    Impedanzen ((ref.impedanz + tx_impedance) * ratio2, (ref.null_impedanz + tx_impedance) * ratio2)
                }
                else
                    ref
            case _ ⇒
                ref
        }
    }

    /**
     * Compute the list of fuse values to the given node as accumulated from the reference
     *
     * @param ref fuse value list of the node at the end of the edge (List[Double] (A))
     * @return list of fuses to the given node
     */
    def fusesTo (ref: List[Double]): List[Double] =
    {
        element match
        {
            case fuse: Fuse ⇒
                ref :+ fuse.Switch.ratedCurrent
            case breaker: Breaker ⇒
                ref :+ breaker.ProtectedSwitch.Switch.ratedCurrent
            case _ ⇒
                ref
        }
    }
}

object ScEdge
{
    /**
     * Index of normalOpen field in Switch bitmask.
     */
    val normalOpenMask: Int = Switch.fields.indexOf ("normalOpen")

    /**
     * Index of open field in Switch bitmask.
     */
    val openMask: Int = Switch.fields.indexOf ("open")
}