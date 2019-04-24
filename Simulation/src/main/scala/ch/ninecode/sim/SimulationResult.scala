package ch.ninecode.sim

/**
 * Simulation time series result.
 *
 * @param simulation The simulation run this value was derived from.
 * @param mrid Node or branch name (equipment or cable mRID).
 * @param type The type of result, voltage, current, etc.
 * @param time  Number of microseconds since the epoc.
 * @param period The period over which this value was averaged or summed in  microseconds.
 * @param real_a Phase A real component.
 * @param imag_a Phase A quadrature component.
 * @param real_b Phase B real component.
 * @param imag_b Phase B quadrature component.
 * @param real_c Phase C real component.
 * @param imag_c Phase C quadrature component.
 * @param units The units for the value, e.g. <code>Volts</code> for a node, <code>Amperes</code> for an edge.
 * @param ttl The time-to-live in seconds (after which the row is deleted), use 0 (zero) for never delete.
 */
case class SimulationResult
(
    simulation: String,
    mrid: String,
    `type`: String,
    time: Long,
    period: Int,
    real_a: Double,
    imag_a: Double,
    real_b: Double,
    imag_b: Double,
    real_c: Double,
    imag_c: Double,
    units: String,
    ttl: Int
)
