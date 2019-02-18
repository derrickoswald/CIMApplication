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
    query: String
)
