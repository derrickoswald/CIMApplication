package ch.ninecode.sc

import ch.ninecode.model.ACLineSegment
import ch.ninecode.model.Breaker
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
import ch.ninecode.util.Complex
import ch.ninecode.util.Graphable

/**
 * Short circuit extended GraphX edge.
 *
 * @param id_cn_1       TopologicalNode 1 mRID
 * @param v1            voltage on Terminal 1 (V)
 * @param id_cn_2       TopologicalNode 2 mRID
 * @param v2            voltage on Terminal 2 (V)
 * @param num_terminals the number of terminals on the equipment (could be more than 2)
 * @param id_equ        ConductingEquipment mRID
 * @param element       conducting equipment subclass object
 * @param impedance     impedance of the edge (Ω)
 */
case class ScEdge
(
    id_cn_1: String,
    v1: Double,
    id_cn_2: String,
    v2: Double,
    num_terminals: Int,
    id_equ: String,
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
        if (0 != (switch.bitfields (openMask / 32) & (1 << (openMask % 32))))
            !switch.open // open valid
        else
            if (0 != (switch.bitfields (normalOpenMask / 32) & (1 << (normalOpenMask % 32))))
                !switch.normalOpen
            else
                true
    }

    /**
     * Predicate that determines if the trace should continue to the given node.
     *
     * @param node TopologicalNode to test
     * @return <code>false</code> for open Switch objects and higher voltage transformer nodes, <code>true</code> otherwise
     */
    def shouldContinueTo (node: ScNode, calculate_public_lighting: Boolean): Boolean =
    {
        if (node.id_prev == "network")
            false
        else
            element match
            {
                case switch: Switch => switchClosed (switch)
                case cut: Cut => switchClosed (cut.Switch)
                case disconnector: Disconnector => switchClosed (disconnector.Switch)
                case fuse: Fuse => switchClosed (fuse.Switch)
                case gd: GroundDisconnector => switchClosed (gd.Switch)
                case jumper: Jumper => switchClosed (jumper.Switch)
                case ps: ProtectedSwitch => switchClosed (ps.Switch)
                case sectionaliser: Sectionaliser => switchClosed (sectionaliser.Switch)
                case breaker: Breaker => switchClosed (breaker.ProtectedSwitch.Switch)
                case lbs: LoadBreakSwitch => switchClosed (lbs.ProtectedSwitch.Switch)
                case recloser: Recloser => switchClosed (recloser.ProtectedSwitch.Switch)
                case line: ACLineSegment => true
                case trafo: PowerTransformer =>
                    if (v1 < 230.0 || v2 < 230.0)
                        return false
                    else if ( !calculate_public_lighting && (v1 == 230.0 || v2 == 230.0) )
                        return false
                    val id_cn = node.id_seq // continue if voltage decreases or it stays below 1000.0
                    if (id_cn == id_cn_1)
                        v1 <= v2 || v1 <= 1000.0
                    else
                        if (id_cn == id_cn_2)
                            v2 <= v1 || v2 <= 1000.0
                        else
                            throw new Exception ("edge %s is not connected to %s (only %s and %s)".format (id_equ, id_cn, id_cn_1, id_cn_2))
                case _ =>
                    true
            }
    }

    /**
     * Warn of special cases of cables and transformers that preclude accurate short-circuit calculation.
     *
     * @param errors  current list of errors in the trace
     * @param options the options for this run
     * @return a new list of errors with additional information about validity.
     */
    def hasIssues (errors: List[ScError], options: ShortCircuitOptions): List[ScError] =
    {
        element match
        {
            case cable: ACLineSegment =>
                // ToDo: use PSRType_Bogus
                if (cable.r >= options.cable_impedance_limit)
                {
                    val error = ScError (fatal = false, invalid = true, "invalid element (%s)".format (cable.id))
                    ScError.combine_errors (errors, List (error), options.messagemax)
                }
                else
                    errors
            case _: PowerTransformer =>
                // Three Winding Transformer - if there are more than 2 PowerTransformerEnd associated to the PowerTransformer
                if (num_terminals > 2)
                {
                    val error = ScError (fatal = false, invalid = true, "%s transformer windings for edge %s".format (num_terminals, id_equ))
                    ScError.combine_errors (errors, List (error), options.messagemax)
                }
                // Voltage Regulator Transformer: if there are less than 3 PowerTransformerEnd associated to the PowerTransformer and the voltage of the two ends are both <= 400V
                else
                    if (v1 == v2)
                    {
                        val error = ScError (fatal = false, invalid = true, "voltage (%sV) regulator edge %s".format (v1, id_equ))
                        ScError.combine_errors (errors, List (error), options.messagemax)
                    }
                    // Low Voltage Transmission: if there are less than 3 PowerTransformerEnd associated to the PowerTransformer and the voltage of the two ends are both <= 1kV and one end is < 1kV
                    else
                        if (v1 <= 1000.0 && v2 <= 1000.0 && v2 != 230.0) // ignore public lighting
                        {
                            val error = ScError (fatal = false, invalid = true, "low voltage (%sV:%sV) subtransmission edge %s".format (v1, v2, id_equ))
                            ScError.combine_errors (errors, List (error), options.messagemax)
                        }
                    else
                        errors
            case _ =>
                errors
        }
    }

    /**
     * Compute reference impedance as seen at the given node.
     *
     * @param id_cn TopologicalNode to compute
     * @param ref   impedance of the other end of the edge (Ω)
     * @return impedance of the given node
     */
    def impedanceFrom (id_cn: String, ref: Impedanzen): Impedanzen =
    {
        element match
        {
            case transformer: PowerTransformer =>
                if (id_cn == id_cn_1)
                {
                    val ratio = v1 / v2
                    val ratio2 = ratio * ratio
                    Impedanzen (ref.impedanz_low * ratio2, ref.null_impedanz_low * ratio2, ref.impedanz_high * ratio2, ref.null_impedanz_high * ratio2)
                }
                else
                    if (id_cn == id_cn_2)
                    {
                        val ratio = v2 / v1
                        val ratio2 = ratio * ratio
                        Impedanzen (ref.impedanz_low * ratio2, ref.null_impedanz_low * ratio2, ref.impedanz_high * ratio2, ref.null_impedanz_high * ratio2)
                    }
                    else
                        ref
            case _ =>
                ref
        }
    }

    /**
     * Compute impedance through the edge.
     *
     * @param id_cn TopologicalNode to compute
     * @return impedance of the given node
     */
    def impedanceTo (id_cn: String): Impedanzen =
    {
        element match
        {
            case _: ACLineSegment =>
                this.impedance
            case _: PowerTransformer =>
                if (id_cn == id_cn_1)
                {
                    val tx_impedance_low = this.impedance.impedanz_low
                    val tx_impedance_high = this.impedance.impedanz_high
                    Impedanzen (tx_impedance_low, tx_impedance_low, tx_impedance_high, tx_impedance_high)
                }
                else
                    if (id_cn == id_cn_2)
                    {
                        val ratio = v2 / v1
                        val ratio2 = ratio * ratio
                        val tx_impedance_low = this.impedance.impedanz_low * ratio2
                        val tx_impedance_high = this.impedance.impedanz_high * ratio2
                        Impedanzen (tx_impedance_low, tx_impedance_low, tx_impedance_high, tx_impedance_high)
                    }
                    else
                        Impedanzen (Complex (0.0), Complex (0.0), Complex (0.0), Complex (0.0))
            case _ =>
                Impedanzen (Complex (0.0), Complex (0.0), Complex (0.0), Complex (0.0))
        }
    }

    /**
     * Compute the list of fuse values as accumulated from the reference
     *
     * @param ref fuse network of the node at one end of the edge
     * @return network of fuses at the other end of the edge
     */
    def fusesTo (ref: Branch): Branch =
    {
        element match
        {
            case fuse: Fuse =>
                val next = SimpleBranch (id_cn_1, id_cn_2, 0.0, fuse.id, fuse.Switch.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.name, Some (fuse.Switch.ratedCurrent))
                if (null == ref)
                    next
                else
                    ref match
                    {
                        case sim: SimpleBranch => SeriesBranch (sim.from, id_cn_2, 0.0, Seq (ref, next))
                        case ser: SeriesBranch => SeriesBranch (ser.from, id_cn_2, 0.0, ser.series ++ Seq (next))
                        case _ => throw new IllegalArgumentException ("unknown class for ref (%s)".format (ref.getClass.toString))
                    }
            case breaker: Breaker =>
                val next = SimpleBranch (id_cn_1, id_cn_2, 0.0, breaker.id, breaker.ProtectedSwitch.Switch.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.name, Some (breaker.ProtectedSwitch.Switch.ratedCurrent))
                if (null == ref)
                    next
                else
                    ref match
                    {
                        case sim: SimpleBranch => SeriesBranch (sim.from, id_cn_2, 0.0, Seq (ref, next))
                        case ser: SeriesBranch => SeriesBranch (ser.from, id_cn_2, 0.0, ser.series ++ Seq (next))
                        case _ => throw new IllegalArgumentException ("unknown class for ref (%s)".format (ref.getClass.toString))
                    }
            case _ =>
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

    /**
     * Temperature coefficient of resistance.
     *
     * A compromise between copper (0.00393) and aluminum (0.00403) /°C
     *
     * It's complicated (http://nvlpubs.nist.gov/nistpubs/bulletin/07/nbsbulletinv7n1p71_A2b.pdf) and depends on the
     * alloy and how the wire is drawn and worked, e.g.
     * "good commercial copper furnished for use as electrical conductors, the average deviation of C from the mean value
     * 0.00393<sub>8</sub> is only o.ooooo<sub>8</sub>, or 0.2%. Also, when the conductivity and temperature coefficient
     * are altered by annealing or hard-drawing, C has been found to remain constant within the experimental error."
     */
    val alpha: Double = 0.004

    /**
     * Temperature adjusted resistance.
     *
     * @param temperature target temperature (°C)
     * @param base        temperature for the given resistance (°C)
     * @param r           the given resistance (Ω)
     * @return the temperature compensated resistance (Ω)
     */
    def resistanceAt (temperature: Double, base: Double, r: Double): Double =
        (1.0 + (alpha * (temperature - base))) * r
}