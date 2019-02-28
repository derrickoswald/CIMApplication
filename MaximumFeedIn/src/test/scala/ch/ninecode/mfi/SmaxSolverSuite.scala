package ch.ninecode.mfi

import ch.ninecode.gl.Complex

import org.scalatest.FunSuite

class SmaxSolverSuite extends FunSuite
{
    val root3: Double = math.sqrt (3)
    def toRadians (angle: Double): Double = angle * Math.PI / 180.0
    def near (number: Double, reference: Double, epsilon: Double = 1.0e-3): Boolean =
    {
        val diff = number - reference
        val ret = Math.abs (diff) < epsilon
        if (!ret) println ("""%s vs. reference %s differs by more than %s (%s)""".format (number, reference, epsilon, diff))
        ret
    }

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
        val p = solver.solve (v, z)
        assert (Math.abs (p.abs - smax) < 0.001 * smax, "unity power factor")
        assert (near (p.angle, Math.acos (cosphi)))
    }

    test ("unity pf @ 4°")
    {
        val cosphi = 1.0
        val angle = 4.0
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
        val p = solver.solve (v, z)
        assert (Math.abs (p.abs - smax) < 0.001 * smax, "unity power factor at 4°")
        assert (near (p.angle, Math.acos (cosphi)))
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
        val p = solver.solve (v, z)
        assert (Math.abs (p.abs - smax) < 0.001 * smax, "0.9 power factor @ 8.5°")
        assert (near (p.angle, Math.acos (cosphi)))
    }

    test ("0.9 pf @ -0.85°")
    {
        val cosphi = 0.9
        val angle = -0.85
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
        val p = solver.solve (v, z)
        assert (Math.abs (p.abs - smax) < 0.001 * smax, "0.9 power factor @ -0.85°")
        assert (near (p.angle, Math.acos (cosphi)))
    }

    test ("0.95 pf @ Π° for 1% at 230v")
    {
        val cosphi = 0.95
        val angle = Math.PI
        val v = 230.0
        val threshold = 0.01
        val smax = 22000.0

        val sinphi = Math.sin (Math.acos (cosphi))
        val smax1ph = smax / root3
        val s = Complex (smax1ph * cosphi, smax1ph * sinphi)
        val rad = toRadians (angle)
        val vc = Complex ((1 + threshold) * v * Math.cos (rad), (1 + threshold) * v * Math.sin (rad))
        val i = s / vc
        val z = (vc - v) / i

        val solver = SmaxSolver (threshold, cosphi)
        val p = solver.solve (v, z)
        assert (Math.abs (p.abs - smax) < 0.001 * smax, "0.95 power factor @ Π° for 1% at 230v")
        assert (near (p.angle, Math.acos (cosphi)))
    }

    test ("yamshid1.0")
    {
        val cosphi = 1.0
        val v = 400.0
        val threshold = 0.03
        // 250kVA 16kV:400V ukr(1): 4%, uRr(1): 0.86%
        val ztrans = Complex (0.005504, 0.02500131965)
        // GKN 3x150Al/95Cu, Imax: 290A, Z: 0.241+0.07jΩ/km, l: 210m
        val zcable = Complex (0.241, 0.07) * 210.0 / 1000.0
        val imax = 290.0

        val solver = SmaxSolver (threshold, cosphi)
        val z = Math.sqrt (3) * zcable + ztrans
        val p = solver.solve (v, z)

        assert (near (p.angle, Math.acos (cosphi)))
        val vc = Complex (412.0, 0.0)
        val i = p / vc
        assert (i.modulus < imax)
        assert (near (i.angle - vc.angle, Math.acos (cosphi)))
        assert (near (p.modulus, 91e3, 91e3 * 0.01))
    }

    test ("yamshid0.9")
    {
        val cosphi = 0.90
        val v = 400.0
        val threshold = 0.03
        // 250kVA 16kV:400V ukr(1): 4%, uRr(1): 0.86%
        val ztrans = Complex (0.005504, 0.02500131965)
        // GKN 3x150Al/95Cu, Imax: 290A, Z: 0.241+0.07jΩ/km, l: 210m
        val zcable = Complex (0.241, 0.07) * 210.0 / 1000.0
        val imax = 290.0

        val solver = SmaxSolver (threshold, cosphi)
        val z = Math.sqrt (3) * zcable + ztrans
        val p = solver.solve (v, z)

        assert (near (p.angle, Math.acos (cosphi)))
        val vc = Complex (412.0, 0.0)
        val i = p / vc
        // assert (i.modulus < imax)
        assert (near (i.angle - vc.angle, Math.acos (cosphi)))
        assert (near (p.modulus, 129e3, 129e3 * 0.01))
    }

    test ("small angle")
    {
        val cosphi = 0.8625088147918922
        val angle = -0.037239727380251886
        val v = 400.0
        val threshold = 0.03
        val smax = 25667.786854659604

        val sinphi = Math.sin (Math.acos (cosphi))
        val smax1ph = smax / root3
        val s = Complex (smax1ph * cosphi, smax1ph * sinphi)
        val rad = toRadians (angle)
        val vc = Complex ((1 + threshold) * v * Math.cos (rad), (1 + threshold) * v * Math.sin (rad))
        val i = s / vc
        val z = (vc - v) / i

        val solver = SmaxSolver (threshold, cosphi)
        val p = solver.solve (v, z)
        assert (Math.abs (p.abs - smax) < 0.001 * smax, "0.8625 power factor @ -0.037° for 3% at 400v")
        assert (near (p.angle, Math.acos (cosphi)))
    }

    test ("random")
    {
        for (i ← 1 to 10000)
        {
            val cosphi = 1.0 - Math.random () * 0.25
            val angle = 45.0 * (Math.random () - 0.5)
            val v = if (Math.random () > 0.75) 230.0 else 400.0
            val threshold = if (Math.random () > 0.5) 0.03 else 0.06
            val smax = Math.random () * 200000.0

            val sinphi = Math.sin (Math.acos (cosphi))
            val smax1ph = smax / root3
            val s = Complex (smax1ph * cosphi, smax1ph * sinphi)
            val rad = toRadians (angle)
            val vc = Complex ((1 + threshold) * v * Math.cos (rad), (1 + threshold) * v * Math.sin (rad))
            val i = s / vc
            val z = (vc - v) / i

            if (z.re > 0.0)
            {
                val solver = SmaxSolver (threshold, cosphi)
                val p = solver.solve (v, z)
                assert (Math.abs (p.abs - smax) < 0.01 * smax, "%s power factor @ %s° for %s%% at %sv %sW".format (cosphi, angle, threshold * 100, v, smax))
                assert (near (p.angle, Math.acos (cosphi)))
            }
        }
    }
}
