package ch.ninecode.mfi

import ch.ninecode.gl.PV

/**
 * Results record.
 *
 * @param id_seq House connection mRID.
 * @param voltage Nominal voltage at the house connection.
 * @param psr_type Power system resource type.
 * @param source_obj Feeding transformer mRID.
 * @param max_power_feeding Calculated maximum feed-in power (Watts).
 * @param eea List of photo-voltaic installations associated with the house connection.
 * @param reason The reason for the feed-in maximum, either transformer, voltage or current limited.
 * @param details Further information about the feed-in limit, e.g. limiting node or equipment and the value.
 */
case class MaxPowerFeedingNodeEEA (
    id_seq: String,
    voltage: Double,
    psr_type: String,
    source_obj: String,
    max_power_feeding: Double,
    eea: Iterable[PV],
    reason: String,
    details: String)
{
    def nis_number: String =
    {
        val n = id_seq.indexOf("_")
        if (0 < n)
            id_seq.substring(0, n)
        else
            id_seq
    }

    def problem: Boolean =
        reason.indexOf ("invalid element") != -1 ||
        reason.indexOf ("transformer windings for edge") != -1 ||
        reason.indexOf ("regulator edge") != -1 ||
        reason.indexOf ("subtransmission edge") != -1
}
