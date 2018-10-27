package ch.ninecode.mfi

/**
 * Final result record.
 *
 * @param trafo MRID of transformer feeding the house.
 * @param house MRID of the house.
 * @param max Maximum feed-in power (kW) or None if no limit was found.
 * @param reason Explanatory reason for the limit (voltage, current or power exceeded).
 * @param details The test which caused the limit including the network element.
 */
case class MaxEinspeiseleistung(
    trafo: String,
    house: String,
    max: Option[Double],
    reason: String,
    details: String)
