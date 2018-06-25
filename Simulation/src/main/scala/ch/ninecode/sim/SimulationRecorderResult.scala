package ch.ninecode.sim

/**
 * One row of results from a recorder query.
 *
 * @param title The user-friendly name of the query.
 * @param interval The recording interval (sec).
 * @param aggregations The list of aggregations to perform.
 * @param name The name of the target recorder file in the .glm file.
 * @param mrid The master resource ID of the node or edge being recorded.
 * @param parent The parent node in the .glm file.
 * @param `type` The type of value to be stored in the simulated_value table.
 * @param property The .glm property to record.
 * @param unit The units for the simulated value.
 */
case class SimulationRecorderResult (
    /**
     * The name for the recorder query.
     */
    title: String,

    /**
     * The recording interval in seconds.
     */
    interval: Int,

    /**
     * The list of aggregations.
     */
    aggregations: List[SimulationAggregate],

    /**
     * Name value from the Spark query.
     */
    name: String,

    /**
     * mRID value from the Spark query.
     */
    mrid: String,

    /**
     * Parent value from the Spark query.
     */
    parent: String,

    /**
     * Type value from the Spark query.
     */
    `type`: String,

    /**
     * Property value from the Spark query.
     */
    property: String,

    /**
     * Unit value from the Spark query.
     */
    unit: String
)
