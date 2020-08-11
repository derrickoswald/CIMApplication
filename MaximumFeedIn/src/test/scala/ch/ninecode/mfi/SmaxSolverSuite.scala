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

    /**
     * Execute one test.
     *
     * @param cosphi    the target power factor
     * @param angle     the voltage angle (°)
     * @param v         the target voltage (V)
     * @param threshold the limit; fraction of voltage over nominal
     * @param smax      the expected maximum power
     * @param message   a message to print if the test fails
     */
    def run (cosphi: Double, angle: Double, v: Double, threshold: Double, smax: Double, message: String): Unit =
    {
        val limit = (1 + threshold) * v
        // https://en.wikipedia.org/wiki/Power_factor
        // Power factors are usually stated as "leading" or "lagging" to show the sign of the phase angle.
        // Capacitive loads are leading (current leads voltage), and inductive loads are lagging (current lags voltage).
        // So, without it being stated we assume PF is leading and that a negative power factor is actually an indicator of a lagging power factor.
        val phi = -Math.acos (cosphi) * Math.signum (cosphi)
        val s = Complex (smax * Math.cos (phi), smax * Math.sin (phi))
        val rad = toRadians (angle)
        val vc = Complex (limit * Math.cos (rad), limit * Math.sin (rad))
        val i = s / vc
        val z = (vc - v) / ~i
        if (z.re >= 0.0 && z.im >= 0.0)
        {
            val solver = SmaxSolver (threshold, cosphi)
            val p = solver.solve (v, z)
            assert ((p - s).modulus < 0.001 * smax, s"$message Z = $z")
            assert (near (p.angle, phi), s"power angle (${p.angle}) differs from expected ${phi}")
        }
        else
            assert (false, s"components of impedance $z must be positive: $message")
    }

    test ("unity pf @ 0°")
    {
        run (1.0, 0.0, 400.0, 0.03, 6000.0, "unity power factor")
    }

    test ("unity pf @ 4°")
    {
        run (1.0, 4.0, 400.0, 0.03, 6000.0, "unity power factor at 4°")
    }

    test ("0.9 pf @ 2.5°")
    {
        run (0.9, 2.5, 400.0, 0.03, 6000.0, "0.9 power factor @ 2.5°")
    }

    test ("0.9 pf @ 9.25°")
    {
        run (0.9, 9.25, 400.0, 0.03, 6000.0, "0.9 power factor @ 9.25°")
    }

    test ("0.95 pf @ 5.2° for 1% at 230v")
    {
        run (0.95, 5.2, 230.0, 0.01, 22000.0, "0.95 power factor @ 5.2° for 1% at 230v")
    }

    test ("yamshid1.0")
    {
        val cosphi = 1.0
        val v = 400.0
        val threshold = 0.03
        // 250kVA 16kV:400V ukr(1): 4%, uRr(1): 0.86%
        val z_trans = Complex (0.005504, 0.02500131965)
        // GKN 3x150Al/95Cu, Imax: 290A, Z: 0.241+0.07jΩ/km, l: 210m
        val z_cable = Complex (0.241, 0.07) * 210.0 / 1000.0
        // val imax = 290.0

        // https://en.wikipedia.org/wiki/Power_factor
        // Power factors are usually stated as "leading" or "lagging" to show the sign of the phase angle.
        // Capacitive loads are leading (current leads voltage), and inductive loads are lagging (current lags voltage).
        // So, without it being stated we assume PF is leading and that a negative power factor is actually an indicator of a lagging power factor.
        val phi = -Math.acos (cosphi) * Math.signum (cosphi)
        val solver = SmaxSolver (threshold, cosphi)
        val z = z_cable + z_trans
        val p = solver.solve (v, z)

        assert (near (p.angle, phi))
        assert (near (p.modulus, 89e3, 89e3 * 0.01))
    }

    test ("yamshid0.9")
    {
        val cosphi = 0.90
        val v = 400.0
        val threshold = 0.03
        // 250kVA 16kV:400V ukr(1): 4%, uRr(1): 0.86%
        val z_trans = Complex (0.005504, 0.02500131965)
        // GKN 3x150Al/95Cu, Imax: 290A, Z: 0.241+0.07jΩ/km, l: 210m
        val z_cable = Complex (0.241, 0.07) * 210.0 / 1000.0
        // val imax = 290.0

        // https://en.wikipedia.org/wiki/Power_factor
        // Power factors are usually stated as "leading" or "lagging" to show the sign of the phase angle.
        // Capacitive loads are leading (current leads voltage), and inductive loads are lagging (current lags voltage).
        // So, without it being stated we assume PF is leading and that a negative power factor is actually an indicator of a lagging power factor.
        val phi = -Math.acos (cosphi) * Math.signum (cosphi)
        val solver = SmaxSolver (threshold, cosphi)
        val z = z_cable + z_trans
        val p = solver.solve (v, z)

        assert (near (p.angle, phi))
        // assert (i.modulus < imax)
        assert (near (p.modulus, 157e3, 157e3 * 0.01))
    }

    test ("small angle")
    {
        run (0.9998088147918922, 0.037239727380251886, 400.0, 0.03, 25667.786854659604, "near unity power factor @ 0.037° for 3% at 400v")
    }

    test ("random")
    {
        for (_ ← 1 to 10000)
        {
            val cosphi = (if (Math.random () > 0.75) -1.0 else 1.0) * 1.0 - Math.random () * 0.25
            val angle = 15.0 * (Math.random () - 0.5)
            val v = if (Math.random () > 0.75) 230.0 else 400.0
            val threshold = if (Math.random () > 0.5) 0.03 else 0.06
            val smax = Math.random () * 200000.0

            // https://en.wikipedia.org/wiki/Power_factor
            // Power factors are usually stated as "leading" or "lagging" to show the sign of the phase angle.
            // Capacitive loads are leading (current leads voltage), and inductive loads are lagging (current lags voltage).
            // So, without it being stated we assume PF is leading and that a negative power factor is actually an indicator of a lagging power factor.
            val phi = -Math.acos (cosphi) * Math.signum (cosphi)
            val smax1ph = smax / root3
            val s = Complex (smax1ph * Math.cos (phi), smax1ph * Math.sin (phi))
            val rad = toRadians (angle)
            val vc = Complex ((1 + threshold) * v * Math.cos (rad), (1 + threshold) * v * Math.sin (rad))
            val i = ~(s / vc)
            val z = (vc - v) / i

            if (z.re >= 0.0 && z.im >= 0.0)
            {
                run (cosphi, angle, v, threshold, smax, s"$cosphi power factor @ ${angle}° for ${threshold * 100}%% at ${v}V ${smax}W")
                //                val solver = SmaxSolver (threshold, cosphi)
                //                val p = solver.solve (v, z)
                //                assert ((p - s).modulus < 0.001 * smax, s"$cosphi power factor @ $angle° for ${threshold * 100}% at ${v}V ${smax}VA Z = $z")
                //                assert (near (p.angle, phi))
            }
        }
    }
    /*
        test ("HAS138124 using Precalculation")
        {
            val cosphi = 0.9
            val v = 400.0
            val threshold = 0.03
            // the following two values are the current PreCalculation values for impedance and power
            val z = Complex (0.22915629,0.09288086)
            val power = Complex (18035.79988585,8734.91400283)

            // https://en.wikipedia.org/wiki/Power_factor
            // Power factors are usually stated as "leading" or "lagging" to show the sign of the phase angle.
            // Capacitive loads are leading (current leads voltage), and inductive loads are lagging (current lags voltage).
            // So, without it being stated we assume PF is leading and that a negative power factor is actually an indicator of a lagging power factor.
            val phi = - Math.acos (cosphi) * Math.signum (cosphi)
            val solver = SmaxSolver (threshold, cosphi)
            val p = solver.solve (v, z)
            assert ((p - power).modulus < 0.001 * power.modulus, "HAS138124 power")
            assert (near (p.angle, phi), s"power angle (${p.angle}) differs from expected ${phi}")
        }

        test ("HAS138124 using Load-Flow")
        {
            val cosphi = 0.9
            val v = 400.0
            // on the step before the 3% limit is exceeded, this is the percentage
            val threshold = 0.02956
            // the following two values are from the GridLAB-D recorder files for voltage and current at the house
            val vh = Complex (411.812, 3.14138)
            val ih = - Complex (-39.4825, 18.7518) // negate because the cable is ordered from the junction to the house, but current flows from the house to the junction

            val z = (vh - v) / ih // Complex (0.21327465, 0.18085642j
            val power = vh * ~ih // Complex (16200.46076052, 7846.24579745)

            // https://en.wikipedia.org/wiki/Power_factor
            // Power factors are usually stated as "leading" or "lagging" to show the sign of the phase angle.
            // Capacitive loads are leading (current leads voltage), and inductive loads are lagging (current lags voltage).
            // So, without it being stated we assume PF is leading and that a negative power factor is actually an indicator of a lagging power factor.
            val phi = - Math.acos (cosphi) * Math.signum (cosphi)
            val solver = SmaxSolver (threshold, cosphi)
            val p = solver.solve (v, z)
            assert ((p - power).modulus < 0.001 * power.modulus, "HAS138124 power")
            assert (near (p.angle, phi), s"power angle (${p.angle}) differs from expected ${phi}")
        }
     */
}
