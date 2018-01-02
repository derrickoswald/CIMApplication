package ch.ninecode.sc

/**
 * Short circuit results.
 *
 * @param mRID conducting equipment mRID
 * @param node TopologicalNode mRID
 * @param tx the feeding transformer
 * @param r aggregate positive sequence resistance from the source (primary of feeding transformer) to this node (Ω)
 * @param x aggregate positive sequence reactance from the source (primary of feeding transformer) to this node (Ω)
 * @param r0 aggregate zero sequence resistance from the source (primary of feeding transformer) to this node (Ω)
 * @param x0 aggregate zero sequence reactance from the source (primary of feeding transformer) to this node (Ω)
 * @param ik one phase short bolted circuit current (A)
 * @param ik3pol three phase bolted short circuit current (A)
 * @param ip maximum aperiodic short-circuit current according to IEC 60909-0 (A)
 * @param sk short-circuit power at the point of common coupling (VA)
 */
case class HouseConnection (
    mRID: String,
    node: String,
    tx: String,
    r: Double,
    x: Double,
    r0: Double,
    x0: Double,
    ik: Double = 0.0,
    ik3pol: Double = 0.0,
    ip: Double = 0.0,
    sk: Double = 0.0)

