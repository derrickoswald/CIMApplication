package ch.ninecode.sim

/**
 * Queries to determine which elements to add players to.
 *
 * @param title     The name for the player query.
 * @param query     The Spark query to determine what nodes or edges are played.
 *                  Must return mrid, name, parent, type, property, unit and island, and possibly synthesis if querying synthesized_value.
 * @param transform The transformation from meter data (from Cassandra) to the value to be applied during simulation.
 *                  Code that can be compiled to an instance of type MeasurementTransform.
 */
case class SimulationPlayerQuery
(
    title: String,
    query: String,
    transform: String
)

object SimulationPlayerQuery
{
    @SuppressWarnings(Array("org.wartremover.warts.Null"))
    def apply (title: String, queries: Seq[String], transform: Option[String]): SimulationPlayerQuery =
        SimulationPlayerQuery(title, queries.lastOption.orNull, transform.orNull)
}
