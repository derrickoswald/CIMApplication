package ch.ninecode.sc

import ch.ninecode.util._

/**
 * Positive and zero sequence impedance.
 *
 * @param impedanz_low       positive sequence impedance at minimum temperature (Ω)
 * @param null_impedanz_low  zero sequence impedance at minimum temperature (Ω)
 * @param impedanz_high      positive sequence impedance at maximum temperature (Ω)
 * @param null_impedanz_high zero sequence impedance at maximum temperature (Ω)
 */
case class Impedanzen (impedanz_low: Complex, null_impedanz_low: Complex, impedanz_high: Complex, null_impedanz_high: Complex)
{
    def + (other: Impedanzen): Impedanzen = Impedanzen (
        this.impedanz_low + other.impedanz_low, this.null_impedanz_low + other.null_impedanz_low,
        this.impedanz_high + other.impedanz_high, this.null_impedanz_high + other.null_impedanz_high)

    def - (other: Impedanzen): Impedanzen = Impedanzen (
        this.impedanz_low + -other.impedanz_low, this.null_impedanz_low + -other.null_impedanz_low,
        this.impedanz_high + -other.impedanz_high, this.null_impedanz_high + -other.null_impedanz_high)

    def * (factor: Double): Impedanzen = Impedanzen (
        this.impedanz_low * factor, this.null_impedanz_low * factor,
        this.impedanz_high * factor, this.null_impedanz_high * factor)

    def parallel (other: Impedanzen): Impedanzen = Impedanzen (
        this.impedanz_low.parallel_impedanz (other.impedanz_low),
        this.null_impedanz_low.parallel_impedanz (other.null_impedanz_low),
        this.impedanz_high.parallel_impedanz (other.impedanz_high),
        this.null_impedanz_high.parallel_impedanz (other.null_impedanz_high))
}

object Impedanzen
{
    def apply (): Impedanzen = Impedanzen (Complex (0), Complex (0), Complex (0), Complex (0))
}