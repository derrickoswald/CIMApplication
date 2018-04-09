package ch.ninecode.sim

case class SimulationRecorder
(
    /**
     * The name for the recorder.
     * Must be unique in the generated GLM file.
     */
    name: String,

    /**
     * The parent object (node or edge).
     */
    parent: String,

    /**
     * The recorder type - 'energy' performs sum() for aggregate values, all others perform average().
     */
    typ: String,

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
    interval: Int = 0
)
{
}
