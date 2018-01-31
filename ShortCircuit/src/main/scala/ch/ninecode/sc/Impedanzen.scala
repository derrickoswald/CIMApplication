package ch.ninecode.sc

/**
 * Positive and zero sequence impedance.
 *
 * @param impedanz positive sequence impedance (Ω)
 * @param null_impedanz zero sequence impedance (Ω)
 */
case class Impedanzen (impedanz: Complex, null_impedanz: Complex)
{
    def - (other: Impedanzen): Impedanzen = Impedanzen (this.impedanz + -other.impedanz, this.null_impedanz + -other.null_impedanz)
}
