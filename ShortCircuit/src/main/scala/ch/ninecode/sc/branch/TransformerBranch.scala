package ch.ninecode.sc.branch

import ch.ninecode.sc.Impedanzen
import ch.ninecode.sc.ShortCircuitOptions
import ch.ninecode.util.Complex

/**
 * A transformer branch.
 *
 * @param from    the 'from' node
 * @param to      the 'to' node
 * @param current the current through this branch in the GridLAB-D experiment
 * @param mRID    the mRID of the CIM element
 * @param name    the name of the CIM element
 * @param pu      the per unit impedance
 */
case class TransformerBranch (
    override val from: String,
    override val to: String,
    override val current: Double,
    mRID: String, name: String,
    s: Double,
    vfrom: Double,
    vto: Double,
    pu: Complex,
    reference_impedanzen: Option[Impedanzen]) extends Branch(from, to, current)
{
    override def toString: String =
    {
        val n = name match
        {
            case s: String => s
            case _ => ""
        }
        s"""TransformerBranch ("$from" ⇒ "$to" ${current}A $mRID $n)"""
    }

    def asString: String = s"$mRID"

    def asFuse: String = ""

    def asId: String = ""

    def seq: Seq[TransformerBranch] = Seq(this)

    def iter: Iterable[TransformerBranch] = Iterable(this)

    def isFuse: Boolean = false

    def lastFuses: Iterable[Branch] = Seq()

    def justFuses: Option[Branch] = None

    def reverse: Branch = TransformerBranch(to, from, current, mRID, name, s, vto, vfrom, pu, reference_impedanzen)

    def ratios: Iterable[(Double, Branch)] = List((1.0, this))

    def voltageRatio: Double = vto / vfrom

    def contents: Iterable[TransformerBranch] = Iterable(this)

    def checkFuses (ik: Double, options: ShortCircuitOptions): (Boolean, Option[Branch]) =
    {
        (false, Some(this))
    }

    def z (in: Impedanzen): Impedanzen =
    {
        val base_ohms = vto * vto / s
        val secondary_z = pu * base_ohms
        val calculated_impedance = Impedanzen(secondary_z, secondary_z, secondary_z, secondary_z)
        val trafo_impedance = reference_impedanzen.getOrElse(calculated_impedance)

        convert_impedance(in) + trafo_impedance
    }

    def convert_impedance (in: Impedanzen): Impedanzen =
    {
        in * math.pow(voltageRatio, 2.0)
    }
}
