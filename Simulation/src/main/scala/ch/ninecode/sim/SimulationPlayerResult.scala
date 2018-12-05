package ch.ninecode.sim

/**
 * One row of results from a player query.
 *
 * @param title          The user-friendly name of the query.
 * @param cassandraquery The Cassandra query to access the measured_value table (with placeholder %s for binding).
 * @param substitutions  The values of the variables to bind to the placeholders in the Cassandra query.
 * @param name           The name of the target player file in the .glm file.
 * @param parent         The parent node in the .glm file.
 * @param `type`         The type of value in the measured_value table.
 * @param property       The .glm property to play.
 */
case class SimulationPlayerResult
(
    /**
     * The name for the player query.
     */
    title: String,

    /**
     * The Cassandra query to determine the player file contents.
     * Must return time, and the real and imag values for each time.
     */
    cassandraquery: String,

    /**
     * Values to bind for placeholders (%s) in the cassandraquery.
     * The placeholders are bound 1:1 with variables in this array.
     */
    substitutions: Array[String],

    /**
     * Name value from the Spark query.
     */
    name: String,

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
    property: String
)
