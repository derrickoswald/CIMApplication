package ch.ninecode.sim

trait SimulationQuery
{
    /**
     * The name for the query.
     */
    val title: String

    /**
     * The query to execute.
     */
    val query: String

}
