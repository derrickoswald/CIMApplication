package ch.ninecode.mfi

import ch.ninecode.gl.PV

/**
 * Results record.
 *
 * @param id_seq TopologicalNode mRID.
 * @param voltage Nominal voltage at the house connection.
 * @param mrid EnergyConsumer mRID.
 * @param psr_type Power system resource type.
 * @param source_obj Feeding transformer mRID.
 * @param feeder Feeding substation connector mRID.
 * @param max_power_feeding Calculated maximum feed-in power (Watts).
 * @param eea List of photo-voltaic installations associated with the house connection.
 * @param reason The reason for the feed-in maximum, either transformer, voltage or current limited.
 * @param details Further information about the feed-in limit, e.g. limiting node or equipment and the value.
 */
case class MaxPowerFeedingNodeEEA (
    id_seq: String,
    voltage: Double,
    mrid: String,
    psr_type: String,
    source_obj: String,
    feeder: String,
    max_power_feeding: Double,
    eea: Iterable[PV],
    reason: String,
    details: String)
{
    def problem: Boolean =
        reason.indexOf ("invalid element") != -1 ||
        reason.indexOf ("transformer windings for edge") != -1 ||
        reason.indexOf ("regulator edge") != -1 ||
        reason.indexOf ("subtransmission edge") != -1
}
