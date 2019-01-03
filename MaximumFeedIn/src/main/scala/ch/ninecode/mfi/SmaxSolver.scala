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
    val phi: Double = Math.acos (cosphi)
    val sinphi: Double = Math.sin (phi)
    val k: Double = 1.0 + threshold

    val ROOT3: Double = math.sqrt (3)
    val GAMMA: Double = 0.001
    val MAXITERATIONS: Int = 5000
    val EPSILON: Double = 1e-6

    def bruteForceSolve (vn: Double, z: Complex): Complex =
    {
        def toRadians (angle: Double): Double = angle * Math.PI / 180.0

        var angle: Int = 0
        var diff: Double = Double.MaxValue
        var iterations = 0
        for (index ← -90000 to 90000) // thousandths of a degree
        {
            iterations = iterations + 1
            val rad = toRadians (index / 1000.0)
            val vc = Complex ((1 + threshold) * vn * Math.cos (rad), (1 + threshold) * vn * Math.sin (rad))
            val i = (vc - vn) / z
            val power = i * vc
            val d = Math.abs (Complex.toPolar (power, false)._2 - phi)
            if (d < diff)
            {
                diff = d
                angle = index
            }
        }
        val max =
        {
            val rad = toRadians (angle / 1000.0)
            val vc = Complex ((1 + threshold) * vn * Math.cos (rad), (1 + threshold) * vn * Math.sin (rad))
            val i = (vc - vn) / z
            i * vc
        }
        // println (iterations)
        ROOT3 * max
    }

    def naiveSolve (vn: Double, z: Complex): Complex =
    {
        def u (theta: Double): Complex = Complex (k * Math.cos (theta), k * Math.sin (theta))
        def S (theta: Double): Complex =
        {
            val f = u (theta)
            vn * vn / z * (f * f - f)
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
        }
        while (!done)
        // println (iterations)
        ROOT3 * S (theta)
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
    def solve (vn: Double, z: Complex): Complex =
    {
        def u (theta: Double): Complex = Complex (k * Math.cos (theta), k * Math.sin (theta))
        def S (theta: Double): Complex =
        {
            val f = u (theta)
            vn * vn / z * (f * f - f)
        }
        def fn (theta: Double): Double =
        {
            val power = S (theta)
            val angle = power.angle
            val diff = angle - phi
            Math.sqrt (diff * diff)
        }
        def derivative (theta: Double): Double =
        {
            val power = S (theta)
            val angle = power.angle
            val diff = angle - phi
            val c = Math.cos (theta)
            val s = Math.sin (theta)
            val c2 = Math.cos (2.0 * theta)
            val s2 = Math.sin (2.0 * theta)
            val re =  z.re * k * (       k * c2 - c) + z.im * k * (       k * s2 - s)
            val dre = z.re * k * (-2.0 * k * s2 + s) + z.im * k * ( 2.0 * k * c2 - c)
            val im =  z.re * k * (       k * s2 - s) - z.im * k * (       k * c2 - c)
            val dim = z.re * k * ( 2.0 * k * c2 - c) - z.im * k * (-2.0 * k * s2 + s)
            val ratio = im / re

            if (diff == 0.0)
                0.0
            else
                1.0 / Math.sqrt (diff * diff) * diff  * (1.0 / (1.0 + ratio * ratio)) * (1.0 / (re * re)) * (re * dim - im * dre)
        }

        var theta = 0.0
        var done = false
        var iterations = 0
        do
        {
            iterations = iterations + 1
            val d = derivative (theta)

            // check derivative
            //def difference (theta: Double): Double =
            //{
            //    (fn (theta + 0.000001) - fn (theta - 0.000001)) / 0.000002
            //}
            //val d1 = difference (theta)

            val dx = - GAMMA * d * Math.abs (fn (theta))
            // clip the step size to less than a ~3°
            val step = Math.min (Math.abs (dx), 0.05) * Math.signum (dx)
            if (Math.abs (step) > EPSILON)
                theta = theta + step
            else
                done = true
        }
        while (!done && iterations < MAXITERATIONS)
        if (iterations >= MAXITERATIONS)
            theta = 0.0

        // println (iterations + " " + theta * 180.0 / Math.PI)
        ROOT3 * S (theta)
    }
}
