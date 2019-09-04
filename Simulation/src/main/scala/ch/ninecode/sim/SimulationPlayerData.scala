package ch.ninecode.sim

/**
 * Measurement time series element.
 *
 * @param transformer The mRID of the transformer that needs this measurement.
 * @param mrid The mRID of the element that this measurement applies to.
 * @param `type` The measurement type - 'energy' is special (it isn't an average) so it is converted according to the period.
 * @param time Number of milliseconds since the epoc.
 * @param readings Array of real and imaginary meter reading values.
 */
case class SimulationPlayerData (
    transformer: String,
    mrid: String,
    `type`: String,
    time: Long,
    readings: Array[Double])
