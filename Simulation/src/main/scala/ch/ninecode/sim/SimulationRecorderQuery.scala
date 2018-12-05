package ch.ninecode.sim

case class SimulationRecorderQuery
(
    /**
     * The name for the recorder query.
     */
    title: String,

    /**
     * The query to determine what nodes or edges are recorded.
     * Must return name, mrid, parent, type, property, unit and island.
     */
    query: String,

    /**
     * The recording interval in seconds.
     */
    interval: Int,

    /**
     * The list of aggregations.
     */
    aggregations: List[SimulationAggregate]
)
