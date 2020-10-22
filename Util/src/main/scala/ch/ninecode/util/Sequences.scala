package ch.ninecode.util

/**
 * Positive and zero sequence values.
 *
 * @param z1 Positive sequence impedance (Ω).
 * @param z0 Zero sequence impedance (Ω).
 */
final case class Sequences (z1: Complex, z0: Complex)
{
    def + (s: Sequences): Sequences = Sequences(z1 + s.z1, z0 + s.z0)

    def * (d: Double): Sequences = Sequences(z1 * d, z0 * d)

    def reciprocal: Sequences = Sequences(z1.reciprocal, z0.reciprocal)

    override def toString: String = s"(z1=${z1.toString(6)},z0=${z0.toString(6)})"
}

object Sequences
{
    def apply (): Sequences = new Sequences(Complex(0.0), Complex(0.0))
}