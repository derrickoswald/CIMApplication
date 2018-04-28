package ch.ninecode.sim

case class SimulationRecorderQuery (
    /**
     * The name for the recorder query.
     */
    title: String,

    /**
     * The query to determine what nodes or edges are recorded.
     * Must return mrid, name, parent, property, unit and island.
     */
    query: String,

    /**
     * The recording interval in seconds.
     */
    interval: Int,

    /**
     * The list of aggregations.
     */
    aggregations: List[SimulationAggregate],

    /**
     * The results of the query as a Json array.
     */
    jsons: String = null,

    /**
     * The file name for recording.
     */
    file: String = null
)
extends SimulationQuery
