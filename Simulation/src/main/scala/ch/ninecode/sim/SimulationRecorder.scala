package ch.ninecode.sim

case class SimulationRecorder
(
    /**
     * The name for the recorder.
     * Must be unique in the generated GLM file.
     */
    name: String,

    /**
     * The mRID of the element to associate with the recorder.
     * For edges, this is the same as parent, but for nodes there are usually more than one choice (many mrid : one topological node).
     */
    mrid: String,

    /**
     * The parent object (node or edge).
     */
    parent: String,

    /**
     * The recorder type - 'energy' performs sum() for aggregate values, all others perform average().
     */
    `type`: String,

    /**
     * The property of the parent object to record.
     */
    property: String,

    /**
     * The units for the recorder.
     */
    unit: String,

    /**
     * The file name for recording.
     */
    file: String = null,

    /**
     * The recording interval in seconds.
     */
    interval: Int = 0,

    /**
     * The list of aggregations.
     */
    aggregations: List[SimulationAggregate]
)

