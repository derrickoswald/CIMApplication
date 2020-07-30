package ch.ninecode.sp

/**
 * Options for spatial operations.
 *
 * @param clazz the name of the class of interest
 * @param lon longitude of center point
 * @param lat latitude of center point
 * @param n number of elements to get
 */
case class SpatialOperationParameters
(
    clazz: String = "EnergyConsumer",
    lon: Double = 7.281558,
    lat: Double = 47.124142,
    n: Int = 1
)

