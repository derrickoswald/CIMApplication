package ch.ninecode.sim

/**
 * One row of results from a player query.
 *
 * @param title     The user-friendly name of the query.
 * @param name      The name of the target player file in the .glm file.
 * @param parent    The parent node in the .glm file.
 * @param mrid      The target node mrid from the CIM file.
 * @param `type`    The type of value in the measured_value or synthesized_value table.
 * @param property  The .glm property to play.
 * @param transform The transform code to apply to the queried values.
 * @param synthesis The synthesis primary key if the data is from the synthesized_value table.
 */
case class SimulationPlayerResult
(
    /**
     * The name for the player query.
     */
    title: String,

    /**
     * Name value from the Spark query.
     */
    name: String,

    /**
     * Parent value from the Spark query.
     */
    parent: String,

    /**
     * mRID value from the Spark query.
     */
    mrid: String,

    /**
     * Type value from the Spark query.
     */
    `type`: String,

    /**
     * Property value from the Spark query.
     */
    property: String,

    /**
     * Transformation to apply to the measurements
     */
    transform: String,

    /**
     * The synthesis name in synthesized_value, or null if the Cassandra query is against measured_value.
     */
    synthesis: String
)
