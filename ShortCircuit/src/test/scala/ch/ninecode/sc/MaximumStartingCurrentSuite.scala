package ch.ninecode.sc

import scala.math._

import org.scalatest.fixture.FunSuite

class MaximumStartingCurrentSuite extends FunSuite
{
    type FixtureParam = Int
    def withFixture (test: OneArgTest): org.scalatest.Outcome =
    {
        withFixture (test.toNoArgTest (0))
    }

    /**
     * See example 4.6.1 Motor Start-up in DACHCZ Technical Rules for the Assessment of Network Disturbances
     */
    test ("Motor Start-up")
    {
        number: Int ⇒
            val v = 400.0
            val Sk = 2.13e6
            val phi = 29.5 // °
            val cosphi = 0.5
            val iair = 8.0
            val Z = v * v / Sk
            val z = Complex.fromPolar (Z, phi, true /* ° */)
            val pmax = MaximumStartingCurrent.max_power_3_phase_motor (Sk, z, cosphi, iair)
            val imax = pmax._1 / v
            val ratio = 0.013 / MaximumStartingCurrent.dmax_low_rep // example has dmax=1.3%
            assert (Math.abs ((imax * ratio) - 5.8) < 5e-3, "expected 5.8 A")
    }
}
