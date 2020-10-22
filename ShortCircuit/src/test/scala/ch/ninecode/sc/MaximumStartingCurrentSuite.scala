package ch.ninecode.sc

import ch.ninecode.util.Complex

import org.scalatest.funsuite.AnyFunSuite

class MaximumStartingCurrentSuite extends AnyFunSuite
{
    /**
     * See example 4.6.1 Motor Start-up in DACHCZ Technical Rules for the Assessment of Network Disturbances
     */
    test("Motor Start-up")
    {
        _: Int =>
            val v = 400.0
            val Sk = 2.13e6
            val phi = 29.5 // °
            val cosphi = 0.5
            val iair = 8.0
            val Z = v * v / Sk
            val z = Complex.fromPolar(Z, phi, degrees = true /* ° */)
            val options = ShortCircuitOptions(worstcasepf = false, cosphi = cosphi)
            val imax = MaximumStartingCurrent.max_current_3_phase(Sk, z, v, options)
            val ratio = 0.013 / MaximumStartingCurrent.dmax_low_rep // example has dmax=1.3%
            assert(Math.abs((imax._1 * ratio) / iair - 5.8) < 5e-3, "expected 5.8 A")
    }
}
