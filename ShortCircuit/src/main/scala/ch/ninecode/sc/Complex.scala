package ch.ninecode.sc

import java.util.regex.Pattern

import scala.math._
import scala.language.implicitConversions

// from http://www.stoyanr.com/2013/02/complex-numbers-in-scala.html
case class Complex (re: Double, im: Double = 0.0) extends Ordered[Complex]
{
    def this (complex: Complex) = this (complex.re, complex.im)

    val modulus: Double = sqrt (pow (re, 2) + pow (im, 2))
    val angle: Double = atan2 (im, re)

    // unary operators
    def unary_+ : Complex = this
    def unary_- : Complex = Complex (-re, -im)
    def unary_~ : Complex = Complex (re, -im) // conjugate
    def unary_! : Double = modulus

    // comparison
    def compare (that: Complex): Int = !this compare !that

    // arithmetic operations
    def + (c: Complex): Complex = Complex (re + c.re, im + c.im)
    def - (c: Complex): Complex = this + -c
    def * (c: Complex) = Complex (re * c.re - im * c.im, im * c.re + re * c.im)
    def / (c: Complex): Complex =
    {
        require (c.re != 0.0 || c.im != 0.0)
        val d = pow (c.re, 2) + pow (c.im, 2)
        Complex ((re * c.re + im * c.im) / d, (im * c.re - re * c.im) / d)
    }
    def / (that: Double): Complex =
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

    def parallel_impedanz (c: Complex): Complex =
    {
        if ((this == Complex (0)) || (c == Complex (0)))
            0.0
        else
            (this * c) / (this + c)
    }

    // string representation
    private def round (value: Double, digits: Int): Double =
    {
        require (digits >= 0)
        var bd = new java.math.BigDecimal (value)
        bd = bd.setScale (digits, java.math.RoundingMode.HALF_UP)
        bd.doubleValue ()
    }
    override def toString: String = toString (5)
    def numberformat (number: Double, digits: Int, leading_sign: Boolean = false): String =
    {
        number match
        {
            case Double.PositiveInfinity ⇒ (if (leading_sign) "+" else "") + "∞"
            case Double.NegativeInfinity ⇒ "-∞"
            case Double.NaN ⇒ (if (leading_sign) "?" else "") + "□"
            case _ ⇒
                if (leading_sign)
                    if (number < 0) "-" + round (-number, digits).toString else "+" + round (number, digits).toString
                else
                    round (re, digits).toString
        }
    }
    def toString (digits: Int): String =
        this match {
            case Complex.j ⇒ "j"
            case Complex (r, 0) ⇒ numberformat (r, digits)
            case Complex (0, i) ⇒ numberformat (i, digits) + "j"
            case _ ⇒ asString (digits)
        }
    private def asString (digits: Int) =
        numberformat (re, digits) + numberformat (im, digits, true) + "j"
    def asPair: (Double, Double) = (re, im)
}

object Complex {
    // constants
    val j = Complex (0, 1)

    // factory methods
    def apply (re: Double) = new Complex (re)

    // to/from polar coordinates
    def fromPolar (magnitude: Double, angle: Double, degrees: Boolean = false): Complex =
    {
        val a = if (degrees) angle * Pi / 180.0 else angle
        new Complex (magnitude * cos (a), magnitude * sin (a))
    }

    def toPolar (c: Complex, degrees: Boolean): (Double, Double) =
    {
        val a = c.angle
        (c.modulus, if (degrees) a * 180.0 / Pi else a)
    }

    // implicit conversions
    implicit def fromDouble (d: Double): Complex = new Complex (d)
    implicit def fromFloat (f: Float): Complex = new Complex (f)
    implicit def fromLong (l: Long): Complex = new Complex (l)
    implicit def fromInt (i: Int): Complex = new Complex (i)
    implicit def fromShort (s: Short): Complex = new Complex (s)
    def parseString (string: String): Complex =
    {
        var regex = Pattern.compile ("""((?:[+-]?(?:[0-9]*\.?[0-9]*)|(?:\.[0-9]+))(?:[Ee][+-]?[0-9]+)?)?[\s]*([+-])[\s]*[ij]?((?:[+-]?(?:[0-9]*\.?[0-9]*)|(?:\.[0-9]+))(?:[Ee][+-]?[0-9]+)?)[ij]?""")
        val matcher = regex.matcher (string)
        if (matcher.find)
        {
            val re = matcher.group (1)
            val sign = matcher.group (2)
            val im = matcher.group (3)
            Complex (re.toDouble, im.toDouble * (if (sign == "-") -1.0 else 1.0))
        }
        else
            Complex (0.0) // ToDo: warning
    }
    implicit def fromString (string: String): Complex = parseString (string)
}
