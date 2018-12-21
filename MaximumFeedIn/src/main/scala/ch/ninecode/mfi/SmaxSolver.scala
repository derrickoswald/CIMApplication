package ch.ninecode.mfi

import ch.ninecode.gl.Complex

/**
 *
 * Maximize Smax for a specific power factor.
 *
 * @param threshold maximum over-voltage factor, typically 0.03 or 3%
 * @param cosphi power factor = cos(Φ), typically 1.0 to 0.9
 */
case class SmaxSolver (threshold: Double, cosphi: Double)
{
    val root3: Double = math.sqrt (3)
    val phi: Double = Math.acos (cosphi)
    val sinphi: Double = Math.sin (phi)
    val k: Double = 1.0 + threshold

    def toRadians (angle: Double): Double = angle * Math.PI / 180.0

    def bruteForceSolve (vn: Double, z: Complex): Complex =
    {
        var angle: Int = 0
        var diff: Double = Double.MaxValue
        for (index ← -90000 to 90000) // thousandths of a degree
        {
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
        root3 * max
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
            val degrees = theta * 180.0 / Math.PI
            val vector = u (theta)
            val s = S (theta)
            val f = fn (theta)
            val d = derivative (theta)
            val gamma = f * 0.01
            val dx = - gamma * d
            if (Math.abs (f) > 1e-5)
                theta = theta + dx
            else
                done = true
        }
        while (!done)
        val degrees = theta * 180.0 / Math.PI
        val vector = u (theta)
        val s = S (theta)
        val f = fn (theta)
        val d = derivative (theta)
        val gamma = f * 0.01
        val dx = - gamma * d

        root3 * S (theta)
    }

    /**
     * Use non-linear equation solver to get the maximum power delivered from a PV.
     *
     * @param vn nominal voltage (V)
     * @param z impedence of feed-in point (Ω)
     * @return the maximum power (W)
     */
    def getMax (vn: Double, z: Complex): Complex =
    {
        if (cosphi == 1.0)
            root3 * (1 + threshold) * threshold * vn * vn / z.modulus
        else
            // bruteForceSolve (vn, z)
            naiveSolve (vn, z)
    }
}
