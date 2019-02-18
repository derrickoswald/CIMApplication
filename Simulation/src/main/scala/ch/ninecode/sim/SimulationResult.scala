package ch.ninecode.sim

/**
 * Simulation time series result.
 *
 * @param mrid Node or branch name (equipment or cable mRID).
 * @param type The type of result, voltage, current, etc.
 * @param period The period over which this value was averaged or summed in  microseconds.
 * @param time  Number of microseconds since the epoc.
 * @param imag_a Phase A quadrature component.
 * @param imag_b Phase B quadrature component.
 * @param imag_c Phase C quadrature component.
 * @param real_a Phase A real component.
 * @param real_b Phase B real component.
 * @param real_c Phase C real component.
 * @param simulation The simulation run this value was derived from.
 * @param units The units for the value, e.g. <code>Volts</code> for a node, <code>Amperes</code> for an edge.
 * @param ttl The time-to-live in seconds (after which the row is deleted), use 0 (zero) for never delete.
 */
case class SimulationResult
(
    mrid: String,
    `type`: String,
    period: Int,
    time: Long,
    imag_a: Double,
    imag_b: Double,
    imag_c: Double,
    real_a: Double,
    real_b: Double,
    real_c: Double,
    simulation: String,
    units: String,
    ttl: Int
)
