package ch.ninecode.gl

import scala.math._
import scala.language.implicitConversions

// from http://www.stoyanr.com/2013/02/complex-numbers-in-scala.html
case class Complex (re: Double, im: Double = 0.0) extends Ordered[Complex]
{
    private val modulus = sqrt (pow (re, 2) + pow (im, 2))

    // Constructors
    //def this (re: Double) = this (re, 0.0)

    // Unary operators
    def unary_+ : Complex = this
    def unary_- : Complex = Complex (-re, -im)
    def unary_~ : Complex = Complex (re, -im) // conjugate
    def unary_! : Double = modulus

    // Comparison
    def compare (that: Complex): Int = !this compare !that

    // Arithmetic operations
    def + (c: Complex): Complex = Complex (re + c.re, im + c.im)
    def - (c: Complex): Complex = this + -c
    def * (c: Complex): Complex = Complex (re * c.re - im * c.im, im * c.re + re * c.im)
    def / (c: Complex): Complex =
    {
        require (c.re != 0.0 || c.im != 0.0)
        val d = pow (c.re, 2) + pow (c.im, 2)
        Complex ((re * c.re + im * c.im) / d, (im * c.re - re * c.im) / d)
    }
    def / (that : Double): Complex =
    {
        require (that != 0)
        Complex (re / that, im / that)
    }
    def abs: Double = sqrt (re * re + im * im)
    def reciprocal: Complex = // https://en.wikipedia.org/wiki/Complex_number#Reciprocal
    {
        val d = re * re + im * im
        Complex (re / d, -im / d)
    }

    // string representation
    private def round (value: Double, digits: Int): Double =
    {
        require (digits >= 0)
        val bd = new java.math.BigDecimal (value)
        bd.setScale (digits, java.math.RoundingMode.HALF_UP).doubleValue ()
    }
    override def toString: String = toString (5)
    def toString (digits: Int): String =
        this match {
            case Complex.j      ⇒ "j"
            case Complex (real, 0) ⇒ round (real, digits).toString
            case Complex (0, imaginary) ⇒ round (imaginary, digits).toString + "j"
            case _              ⇒ asString (digits)
        }
    private def asString (digits: Int): String =
        round (re, digits).toString + (if (im < 0) "-" + round (-im, digits).toString else "+" + round (im, digits).toString) + "j"
}

object Complex
{
    // constants
    val j = Complex (0, 1)

    // factory methods
    def apply (re: Double): Complex = new Complex (re)

    // to/from polar coordinates
    def fromPolar (magnitude: Double, angle: Double, degrees: Boolean = false): Complex =
    {
        val a = if (degrees) angle * Pi / 180.0 else angle
        new Complex (magnitude * cos (a), magnitude * sin (a))
    }

    def toPolar (c: Complex, degrees: Boolean): (Double, Double) =
    {
        val a = atan2 (c.im, c.re)
        (c.modulus, if (degrees) a * 180.0 / Pi else a)
    }

    // implicit conversions
    implicit def fromDouble (d: Double): Complex = new Complex (d)
    implicit def fromFloat (f: Float): Complex = new Complex (f)
    implicit def fromLong (l: Long): Complex = new Complex (l)
    implicit def fromInt (i: Int): Complex = new Complex (i)
    implicit def fromShort (s: Short): Complex = new Complex (s)
}
