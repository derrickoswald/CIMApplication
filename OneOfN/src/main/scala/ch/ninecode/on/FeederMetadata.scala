package ch.ninecode.on

import ch.ninecode.model.Element

/**
 * Information about the feeder.
 *
 * @param station     mRID of the station the feeder is supplied from
 * @param connector   number or identifier for the connection at the station (abgang#)
 * @param description textual note for the feeder, e.g. the neighborhood supplied, or major street
 * @param feeder      the CIM object (Connector) that "is" the feeder source
 */
case class FeederMetadata
(
    station: String,
    connector: String,
    description: String,
    feeder: Element)
{
    /**
     * Supplying feeder id.
     *
     * @return mRID of the feeder
     */
    def id: String = feeder.id
}
