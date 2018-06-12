package ch.ninecode.sim

case class SimulationPlayerQuery
(
    /**
     * The name for the player query.
     */
    title: String,

    /**
     * The Spark query to determine what nodes or edges are played.
     * Must return mrid, name, parent, type, property, unit and island.
     */
    query: String,

    /**
     * The Cassandra query to determine the player file contents.
     * Must return time, and the real and imag values for each time.
     */
    cassandraquery: String,

    /**
     * Bind variables for placeholders (%s) in the cassandraquery.
     * Can be any value returned from the Spark query.
     * Placeholders are bound 1:1 with variables in this array.
     */
    bind: Array[String],

    /**
     * Results of the Spark query as a Json array.
     */
    jsons: String = null,

    /**
     * Per binding and date file name, e.g. HAS1964_2017-07-18.csv.
     */
    file: String = null,

    /**
     * The number of measurements in the file.
     */
    count: Int = 0
)
extends SimulationQuery
