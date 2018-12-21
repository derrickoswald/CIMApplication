package ch.ninecode.mfi

import ch.ninecode.gl.Complex

import org.scalatest.FunSuite

class SmaxSolverSuite extends FunSuite
{
    val root3: Double = math.sqrt (3)
    def toRadians (angle: Double): Double = angle * Math.PI / 180.0

    test ("unity pf @ 0°")
    {
        val cosphi = 1.0
        val angle = 0.0
        val v = 400.0
        val threshold = 0.03
        val smax = 6000.0

        val sinphi = Math.sin (Math.acos (cosphi))
        val smax1ph = smax / root3
        val s = Complex (smax1ph * cosphi, smax1ph * sinphi)
        val rad = toRadians (angle)
        val vc = Complex ((1 + threshold) * v * Math.cos (rad), (1 + threshold) * v * Math.sin (rad))
        val i = s / vc
        val z = (vc - v) / i

        val solver = SmaxSolver (threshold, cosphi)
        val p = solver.getMax (v, z)
        assert (Math.abs (p.abs - smax) < 1e-6, "unity power factor")
    }

    test ("0.9 pf @ 8.5°")
    {
        val cosphi = 0.9
        val angle = 8.5
        val v = 400.0
        val threshold = 0.03
        val smax = 6000.0

        val sinphi = Math.sin (Math.acos (cosphi))
        val smax1ph = smax / root3
        val s = Complex (smax1ph * cosphi, smax1ph * sinphi)
        val rad = toRadians (angle)
        val vc = Complex ((1 + threshold) * v * Math.cos (rad), (1 + threshold) * v * Math.sin (rad))
        val i = s / vc
        val z = (vc - v) / i

        val solver = SmaxSolver (threshold, cosphi)
        val p = solver.getMax (v, z)
        assert (Math.abs (p.abs - smax) < 1, "0.9 power factor @ 8.5°")
    }
}