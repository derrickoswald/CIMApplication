package ch.ninecode.sim

case class SimulationExtraQuery (
   /**
    * The name for the extra query.
    * This is used as part of the Cassandra primary key (in addition to the simulation id and the key field).
    */
   title: String,

   /**
    * The query to get key value pairs.
    * Must return key and value.
    * The simulation will be added automatically
    */
   query: String

) extends SimulationQuery
