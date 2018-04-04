package ch.ninecode.sim

case class SimulationRecorderQuery (
    /**
     * The name for the recorder query.
     */
    title: String,

    /**
     * The query to determine what nodes or edges are recorded.
     * Must return mrid, name, parent, property, and unit.
     */
    query: String,

    /**
     * The recording interval in seconds.
     */
    interval: Int = 0,

    /**
     * The results of the query as a Json array.
     */
    jsons: String = null,

    /**
     * The file name for recording.
     */
    file: String = null)
{
}
