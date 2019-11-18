package ch.ninecode.gl

/**
 * Positive and zero sequence values.
 * @param c1 Positive sequence.
 * @param c0 Zero sequence.
 */
final case class Sequences (c1: Complex, c0: Complex)
{
    def + (s: Sequences): Sequences = Sequences (c1 + s.c1, c0 + s.c0)

    def * (d: Double): Sequences = Sequences (c1 * d, c0 * d)

    def reciprocal: Sequences = Sequences (c1.reciprocal, c0.reciprocal)

    override def toString: String = s"(z1=${c1.toString (6)},z0=${c0.toString (6)})"
}

object Sequences
{
    def apply (): Sequences = new Sequences (Complex (0.0), Complex (0.0))
}