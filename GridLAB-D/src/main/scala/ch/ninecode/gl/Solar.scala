package ch.ninecode.gl

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.cim.CIMRDD
import ch.ninecode.model.PhotoVoltaicUnit
import ch.ninecode.model.PowerElectronicsConnection
import ch.ninecode.model.Terminal

/**
 * Photovoltaic attachment.
 * Generating equipment attached to a node.
 *
 * @param node  ConnectivityNode or TopologicalNode MRID.
 * @param solar SolarGeneratingUnit object attached to the node.
 * @param connection TODO
 */
case class PV
(
    node: String,
    solar: PhotoVoltaicUnit,
    connection: PowerElectronicsConnection
)

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

         // get the pv stations
        val pv_cim = getOrElse[PhotoVoltaicUnit]

        val pv_connection = getOrElse[PowerElectronicsConnection]
        val pv_join: RDD[(String, (PhotoVoltaicUnit, PowerElectronicsConnection))] = pv_cim.keyBy(_.id).join (pv_connection.keyBy (_.RegulatingCondEq.EnergyConnection.ConductingEquipment.id))

        // get the terminals
        val terminals = getOrElse [Terminal]

        // link to the connectivity/topological node through the terminal
        val t: RDD[PV] = terminals.keyBy (_.ConductingEquipment).join (pv_join.keyBy (_._2._1.id)).values.map (
            x => PV (if (topologicalnodes) x._1.TopologicalNode else x._1.ConnectivityNode, x._2._2._1, x._2._2._2))

        val pv = t.groupBy (_.node).persist (storage_level)

        if (session.sparkContext.getCheckpointDir.isDefined) pv.checkpoint ()

        pv
    }
}