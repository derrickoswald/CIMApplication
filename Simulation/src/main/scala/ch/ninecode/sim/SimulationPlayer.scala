package ch.ninecode.sim

case class SimulationPlayer
(
    /**
     * The name for the player.
     * Must be unique in the generated GLM file.
     */
    name: String,

    /**
     * The parent object (node or edge).
     */
    parent: String,

    /**
     * The player type - 'energy' adds a load object to the glm.
     */
    `type`: String,

    /**
     * The property of the parent object to play.
     */
    property: String,

    /**
     * Per name and date file name, e.g. HAS1964_2017-07-18.csv.
     */
    file: String = null,

    /**
     * The mrid used to fetch the player data from the measured data, or mrid to attach to synthesized data.
     */
    mrid: String = null,

    /**
     * Starting time as unix epoch for client side filtering.
     */
    start: Long,

    /**
     * Ending time as unix epoch for client side filtering.
     */
    end: Long,

    /**
     * Transformation to apply to the measurements.
     */
    transform: String,

    /**
     * Primary key in synthesized_value table.
     */
    synthesis: String
)
