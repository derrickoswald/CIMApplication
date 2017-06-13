package ch.ninecode.sc

import scala.math._
import scala.language.implicitConversions

// from http://www.stoyanr.com/2013/02/complex-numbers-in-scala.html
case class Complex(re: Double, im: Double = 0.0) extends Ordered[Complex] {
    private val modulus = sqrt (pow (re, 2) + pow (im, 2))

    // Constructors
    //def this (re: Double) = this (re, 0.0)

    // Unary operators
    def unary_+ = this
    def unary_- = Complex (-re, -im)
    def unary_~ = Complex (re, -im) // conjugate
    def unary_! = modulus

    // Comparison
    def compare(that: Complex) = !this compare !that

    // Arithmetic operations
    def +(c: Complex) = Complex (re + c.re, im + c.im)
    def -(c: Complex) = this + -c
    def *(c: Complex) = Complex (re * c.re - im * c.im, im * c.re + re * c.im)
    def /(c: Complex) =
        {
            require (c.re != 0.0 || c.im != 0.0)
            val d = pow (c.re, 2) + pow (c.im, 2)
            Complex ((re * c.re + im * c.im) / d, (im * c.re - re * c.im) / d)
        }
    def /(that: Double) =
        {
            require (that != 0)
            Complex (re / that, im / that)
        }

    def abs = sqrt (re * re + im * im)
    def reciprocal = // https://en.wikipedia.org/wiki/Complex_number#Reciprocal
        {
            val d = re * re + im * im
            Complex (re / d, -im / d)
        }

    def parallel_impedanz(c: Complex): Complex = {
        (this * c) / (this + c)
    }

    // string representation
    private def round(value: Double, digits: Int): Double =
        {
            require (digits >= 0)
            var bd = new java.math.BigDecimal(value)
            bd = bd.setScale (digits, java.math.RoundingMode.HALF_UP)
            return (bd.doubleValue ())
        }
    override def toString() = toString (5)
    def toString(digits: Int) =
        this match {
            case Complex.j ⇒ "j"
            case Complex(re, 0) ⇒ round (re, digits).toString
            case Complex(0, im) ⇒ round (im, digits).toString + "j"
            case _ ⇒ asString (digits)
        }
    private def asString(digits: Int) =
        round (re, digits).toString + (if (im < 0) "-" + round (-im, digits).toString else "+" + round (im, digits).toString) + "j"
}

object Complex {
    // constants
    val j = Complex (0, 1)

    // factory methods
    def apply(re: Double) = new Complex(re)

    // to/from polar coordinates
    def fromPolar(magnitude: Double, angle: Double, degrees: Boolean = false) =
        {
            val a = if (degrees) angle * Pi / 180.0 else angle
            new Complex(magnitude * cos (a), magnitude * sin (a))
        }

    def toPolar(c: Complex, degrees: Boolean) {
        val a = atan2 (c.im, c.re)
        (c.modulus, if (degrees) a * 180.0 / Pi else a)
    }

    // implicit conversions
    implicit def fromDouble(d: Double) = new Complex(d)
    implicit def fromFloat(f: Float) = new Complex(f)
    implicit def fromLong(l: Long) = new Complex(l)
    implicit def fromInt(i: Int) = new Complex(i)
    implicit def fromShort(s: Short) = new Complex(s)
}

