package ch.ninecode.sim

/**
 * Resolved player queries to be applied to the simulation as player files.
 *
 * @param name      The name for the player.
 *                  Must be unique in the generated GLM file.
 * @param parent    The parent object (node or edge).
 * @param `type`    The player type - 'energy' adds a load object to the glm.
 * @param property  The property of the parent object to play.
 * @param file      Player file name, e.g. HAS12345_load.csv
 * @param mrid      The mrid used to fetch the player data from the measured data, or mrid to attach to synthesized data.
 * @param start     Starting time as unix epoch for filtering.
 * @param end       Ending time as unix epoch for filtering.
 * @param transform Transformation to apply to the measurements.
 * @param synthesis Primary key in synthesized_value table, or "" if using mrid from measured_value table.
 */
case class SimulationPlayer
(
    name: String,
    parent: String,
    `type`: String,
    property: String,
    file: String,
    mrid: String,
    start: Long,
    end: Long,
    transform: String,
    synthesis: String
)
