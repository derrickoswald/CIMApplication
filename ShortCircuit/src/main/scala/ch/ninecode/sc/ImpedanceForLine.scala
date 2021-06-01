package ch.ninecode.sc

import ch.ninecode.model.ACLineSegment
import ch.ninecode.sc.ScEdge.resistanceAt
import ch.ninecode.util.Complex

trait ImpedanceForLine
{
    def getImpedanzenFor (line: ACLineSegment, dist_km: Double, options: ShortCircuitOptions): Impedanzen =
    {
        val x_per_km = line.x * dist_km
        val x0_per_km = line.x0 * dist_km
        Impedanzen(
            Complex(resistanceAt(options.low_temperature, options.base_temperature, line.r) * dist_km, x_per_km),
            Complex(resistanceAt(options.low_temperature, options.base_temperature, line.r0) * dist_km, x0_per_km),
            Complex(resistanceAt(options.high_temperature, options.base_temperature, line.r) * dist_km, x_per_km),
            Complex(resistanceAt(options.high_temperature, options.base_temperature, line.r0) * dist_km, x0_per_km))
    }
}
