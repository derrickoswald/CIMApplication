package ch.ninecode.sim

case class SimulationPlayerQuery
(
    /**
     * The name for the player query.
     */
    title: String,

    /**
     * The Spark query to determine what nodes or edges are played.
     * Must return mrid, name, parent, type, property, unit and island, and possibly synthesis if querying synthesized_value.
     */
    query: String,

    /**
     * The transformation from meter data (from Cassandra) to the value to be applied during simulation.
     * Code that can be compiled to an instance of type MeasurementTransform.
     */
    transform: String
)
