package ch.ninecode.sim

/**
 * Queries to determine which elements to add recorders to.
 *
 * @param title The name for the recorder query.
 * @param query The query to determine what nodes or edges are recorded.
 *              Must return name, mrid, parent, type, property, unit and island.
 * @param interval The recording interval in seconds.
 * @param aggregations The list of aggregations.
 */
case class SimulationRecorderQuery
(
    title: String,
    query: String,
    interval: Int,
    aggregations: List[SimulationAggregate]
)
object SimulationRecorderQuery
{
    @SuppressWarnings (Array ("org.wartremover.warts.Null"))
    def apply (title: String, queries: Seq[String], interval: Int, aggregations: List[SimulationAggregate]): SimulationRecorderQuery =
        SimulationRecorderQuery (title, queries.lastOption.orNull, interval, aggregations)
}