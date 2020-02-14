package ch.ninecode.mfi

import ch.ninecode.gl.Complex

/**
 *
 * Maximize feed-in power for a specific power factor.
 *
 * @param threshold maximum over-voltage factor, typically 0.03 or 3%
 * @param cosphi power factor = cos(Φ), typically 1.0 to 0.9
 */
case class SmaxSolver (threshold: Double, cosphi: Double)
{
    // https://en.wikipedia.org/wiki/Power_factor
    // Power factors are usually stated as "leading" or "lagging" to show the sign of the phase angle.
    // Capacitive loads are leading (current leads voltage), and inductive loads are lagging (current lags voltage).
    // So, without it being stated we assume PF is leading and that a negative power factor is actually an indicator of a lagging power factor.
    val phi: Double = - Math.acos (cosphi) * Math.signum (cosphi)
    val k: Double = 1.0 + threshold

    def bruteForceSolve (vn: Double, z: Complex): Complex =
    {
        def toRadians (index: Double): Double = index * Math.PI / 180.0 / 1000.0

        val v = k * vn
        def S (theta: Double): Complex =
        {
            val c = math.cos (theta)
            val s = math.sin (theta)
            val vc = v * Complex (c, s)
            val i = (vc - vn) / z
            vc * ~i
        }
        var angle: Int = 0
        var diff: Double = Double.MaxValue
        for (index ← -90000 to 90000) // thousandths of a degree in the range where cos() is positive
        {
            val rad = toRadians (index)
            val vc = Complex (v * Math.cos (rad), v * Math.sin (rad))
            if (vc.re > 0.0)
            {
                val i = (vc - vn) / z
                val power = vc * ~i
                val d = Math.abs (Complex.toPolar (power, degrees = false)._2 - phi)
                if (d < diff)
                {
                    diff = d
                    angle = index
                }
            }
        }
        val power = S (toRadians (angle))
        power
    }

    def naiveSolve (vn: Double, z: Complex): Complex =
    {
        val GAMMA: Double = 0.001
        val MAXITERATIONS: Int = 5000
        val EPSILON: Double = 2e-7

        val v = k * vn
        def S (theta: Double): Complex =
        {
            val c = math.cos (theta)
            val s = math.sin (theta)
            val vc = v * Complex (c, s)
            val i = (vc - vn) / z
            vc * ~i
        }
        def fn (theta: Double): Double =
        {
            val s = S (theta)
            Math.abs (s.angle - phi)
        }
        def derivative (theta: Double): Double =
        {
            (fn (theta + 0.0001) - fn (theta - 0.0001)) / 0.0002
        }

        var theta = 0.0
        var done = false
        var iterations = 0
        do
        {
            iterations = iterations + 1
            val f = fn (theta)
            val d = derivative (theta)
            val dx = - GAMMA * f * d
            if (Math.abs (f) > EPSILON)
                theta = theta + dx
            else
                done = true
            if (iterations > MAXITERATIONS)
                done = true
        }
        while (!done)
        val power = S (theta)
        power
    }

    /**
     * Use non-linear equation solver to get the maximum power delivered from a PV.
     *
     * The minimization function is the square of the difference in angle between S and Φ.
     * Use gradient descent, which requires the derivative. Stop when the derivative times gamma is less than epsilon.
     *
     * @param vn nominal voltage (V)
     * @param z impedance of feed-in point (Ω)
     * @return the maximum power (W)
     */
    def solveNonLinear (vn: Double, z: Complex): Complex =
    {
        val GAMMA: Double = 0.1
        val MAXITERATIONS: Int = 5000
        val EPSILON: Double = 2e-7

        val v = k * vn
        def S (theta: Double): Complex =
        {
            val c = math.cos (theta)
            val s = math.sin (theta)
            val vc = v * Complex (c, s)
            val i = (vc - vn) / z
            vc * ~i
        }
//        def fn (theta: Double): Double =
//        {
//            val power = S (theta)
//            val angle = power.angle
//            val diff = angle - phi
//            diff * diff
//        }
        def derivative (theta: Double): Double =
        {
            val power = S (theta)
            val angle = power.angle
            val diff = angle - phi

            if (diff == 0.0)
                0.0
            else
            {
                val re =  power.re
                val im =  power.im
                val ratio = im / re
                val c = math.cos (theta)
                val s = math.sin (theta)
                val admittance = 1.0 / z
                val g = admittance.re
                val b = admittance.im
                val du = - vn * vn * k * (s * b + c * g)
                val dv = vn * vn * k * (s * g - c * b)
                val derivative = (1.0 / re * du) - (im / (re * re) * dv)
                // for sqrt (square) use:  1.0 / Math.sqrt (diff * diff) * diff  * (1.0 / (1.0 + ratio * ratio)) * derivative
                2 * (power.angle - phi) * (1.0 / (1.0 + ratio * ratio)) * derivative
            }
        }

        var theta = 0.0
        var done = false
        var iterations = 0
        do
        {
            iterations = iterations + 1
            val d = derivative (theta)

//            // check derivative
//            def difference (theta: Double): Double =
//            {
//                (fn (theta + 0.000001) - fn (theta - 0.000001)) / 0.000002
//            }
//            val d1 = difference (theta)

            // cheat here and approximate the double derivative
            val dd = (derivative (theta + 0.000001) - derivative (theta - 0.000001)) / 0.000002

            val dx = - GAMMA * d / dd
            // clip the step size to less than a ~6°
            val step = Math.min (Math.abs (dx), 0.1) * Math.signum (dx)
            if (Math.abs (step) > EPSILON)
                theta = theta + step
            else
                done = true
            if (iterations > MAXITERATIONS)
                done = true
        }
        while (!done)

        if (iterations > MAXITERATIONS)
            println (s"Smax minimization iterations exceeds $MAXITERATIONS")
//        else
//            println (s"iterations $iterations ${theta * 180.0 / Math.PI}°")
        val power = S (theta)
        power
    }

    val solve: (Double, Complex) => Complex = solveNonLinear
}
