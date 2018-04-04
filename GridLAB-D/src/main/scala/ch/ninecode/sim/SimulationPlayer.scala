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
    typ: String,

    /**
     * The property of the parent object to play.
     */
    property: String,

    /**
     * Per name and date file name, e.g. HAS1964_2017-07-18.csv.
     */
    file: String = null,

    /**
     * The number of measurements in the file.
     */
    count: Int = 0
)
{
}