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
         // get the pv units
        val pv_cim = getOrElse[PhotoVoltaicUnit]
        // get electronics connections
        val pv_connection = getOrElse[PowerElectronicsConnection]

        // get the terminals
        val terminals = getOrElse [Terminal]

        val pv_connection_terminals = terminals.keyBy(_.ConductingEquipment).join (pv_cim.keyBy (_.id))
        val pv_cim_pv_connection = pv_cim.keyBy(_.PowerElectronicsUnit.PowerElectronicsConnection).join(pv_connection.keyBy(_.id)).values

        val pv_joined = pv_connection_terminals.join (pv_cim_pv_connection.keyBy (_._1.id))
        val t: RDD[PV] = pv_joined.map(
            x => PV(if (topologicalnodes) x._2._1._1.TopologicalNode else x._2._1._1.ConnectivityNode,x._2._2._1, x._2._2._2)
        )

        val pv = t.groupBy (_.node).persist (storage_level)

        if (session.sparkContext.getCheckpointDir.isDefined) pv.checkpoint ()

        pv
    }
}