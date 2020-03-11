package ch.ninecode.gl

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import ch.ninecode.cim.CIMRDD
import ch.ninecode.model.SolarGeneratingUnit
import ch.ninecode.model.StringQuantity
import ch.ninecode.model.Terminal
import ch.ninecode.model.UserAttribute
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Photovoltaic attachment.
 * Generating equipment attached to a node.
 *
 * @param node  ConnectivityNode or TopologicalNode MRID.
 * @param solar SolarGeneratingUnit object attached to the node.
 */
case class PV
(
    node: String,
    solar: SolarGeneratingUnit)

case class Solar (session: SparkSession, topologicalnodes: Boolean, storage_level: StorageLevel) extends CIMRDD
{
    implicit val spark: SparkSession = session
    implicit val log: Logger = LoggerFactory.getLogger (getClass)

    // get the existing photo-voltaic installations keyed by terminal
    def getSolarInstallations: RDD[(String, Iterable[PV])] =
    {
        // note there are two independent linkages happening here through the UserAttribute class:
        // - SolarGeneratingUnit to ServiceLocation
        // - ServiceLocation to EnergyConsumer

        // link to service location ids via UserAttribute
        val attributes = getOrElse[UserAttribute]

        // user attributes link through string quantities
        val strings = getOrElse[StringQuantity]

        // get solar to service linkage, e.g. ("EEA5280", "MST115133")
        // and service to house linkage, e.g. ("MST115133", "HAS138130")
        val pairs = attributes.keyBy (_.value).join (strings.keyBy (_.id)).values.map (x => (x._1.name, x._2.value))

        // get a simple list of house to pv id pairs
        val links = pairs.join (pairs.map (_.swap)).values

        // get the pv stations
        val solars = getOrElse[SolarGeneratingUnit]

        // get a simple list of house to pv pairs
        val house_solars = links.map (x => (x._2, x._1)).join (solars.keyBy (_.id)).values

        // get the terminals
        val terminals = getOrElse[Terminal]

        // link to the connectivity/topological node through the terminal
        val t = terminals.keyBy (_.ConductingEquipment).join (house_solars).values.map (
            x => PV (if (topologicalnodes) x._1.TopologicalNode else x._1.ConnectivityNode, x._2))

        val pv = t.groupBy (_.node)

        pv.persist (storage_level)
        if (session.sparkContext.getCheckpointDir.isDefined) pv.checkpoint ()

        pv
    }
}