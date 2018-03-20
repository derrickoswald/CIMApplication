package ch.ninecode.sim

case class SimulationPlayer
(
    /**
     * The name for the player.
     */
    title: String,

    /**
     * The query to determine what nodes or edges are played.
     * Must return mrid, name, parent, property, and unit.
     */
    rdfquery: String,

    cassandraquery: String,
    bind: Array[String],

    /**
     * Results of the rdfquery, and generated file (includes name and (record) count) of the Cassandra query as a Json array.
     */
    jsons: String = null
)
{
}
