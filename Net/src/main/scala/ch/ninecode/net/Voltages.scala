package ch.ninecode.net

import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.cim.CIMRDD
import ch.ninecode.model.BaseVoltage

final case class Voltages (
    session: SparkSession,
    storage_level: StorageLevel = StorageLevel.fromString ("MEMORY_AND_DISK_SER")
) extends CIMRDD
{
    implicit val spark: SparkSession = session
    implicit val log: Logger = LoggerFactory.getLogger (getClass)

    /**
     * Create a collection of all BaseVoltage values
     *
     * @return
     */
    def getVoltages: Map[String, Double] =
        getOrElse [BaseVoltage]
            .map (voltage => (voltage.id, voltage.nominalVoltage * 1000.0)) // ToDo: remove this 1000.0V multiplier
            .collect
            .toMap
}