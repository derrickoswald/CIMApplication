package ch.ninecode.sim

case class SimulationPlayerQuery
(
    /**
     * The name for the player query.
     */
    title: String,

    /**
     * The query to determine what nodes or edges are played.
     * Must return mrid, name, parent, type, property, and unit.
     */
    rdfquery: String,

    cassandraquery: String,
    bind: Array[String],

    /**
     * Results of the rdfquery as a Json array.
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
