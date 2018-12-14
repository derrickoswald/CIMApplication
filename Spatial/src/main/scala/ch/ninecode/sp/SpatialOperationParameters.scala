package ch.ninecode.sp

case class SpatialOperationParameters
(
    /**
     * The name of the class of interest.
     */
    clazz: String = "EnergyConsumer",

    /**
     * Longitude of center point.
     */
    lon: Double = 7.281558,

    /**
     * Latitude of center point.
     */
    lat: Double = 47.124142,

    /**
     * Number of elements to get.
     */
    n: Int = 1
)

