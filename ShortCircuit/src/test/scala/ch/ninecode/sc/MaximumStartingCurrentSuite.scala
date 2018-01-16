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
            val z = Complex.fromPolar (400.0 * 400.0 / 2.13e6, 29.5, true)
            val pmax = MaximumStartingCurrent.max_power_3_phase_motor (2.13e6, z, 0.5, 8.0, 0.013) // example has dmax=1.3%
            val imax = pmax / (400.0 * sqrt (3))
            assert (Math.abs (imax - 5.8) < 5e-3, "expected 5.8 A")
    }
}
