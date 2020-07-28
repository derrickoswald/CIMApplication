package ch.ninecode.sim

/**
 * Queries for extra property data.
 *
 * @param title The name for the extra query.
 *              This is used as part of the Cassandra primary key (in addition to the simulation id and the key field).
 * @param query The query to get key value pairs.
 *              Must return key and value.
 *              The simulation will be added automatically
 */
case class SimulationExtraQuery
(
    title: String,
    query: String
)
object SimulationExtraQuery
{
    @SuppressWarnings (Array ("org.wartremover.warts.Null"))
    def apply (title: String, queries: Seq[String]): SimulationExtraQuery =
        SimulationExtraQuery (title, queries.lastOption.orNull)
}