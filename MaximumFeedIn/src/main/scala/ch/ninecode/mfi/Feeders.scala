package ch.ninecode.mfi

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.LoggerFactory
import org.slf4j.Logger

import ch.ninecode.cim.CIMRDD
import ch.ninecode.model.BaseVoltage
import ch.ninecode.model.Element
import ch.ninecode.model.Connector
import ch.ninecode.model.Terminal
import ch.ninecode.model.Switch

/**
 * Creates an RDD of feeders.
 *
 * The default operation selects Connector elements with a voltage less than 1000V (N7) contained in stations.
 *
 * @param session       the Spark session
 * @param storage_level the storage level for persistence
 */
class Feeders (
    session: SparkSession,
    storage_level: StorageLevel = StorageLevel.MEMORY_AND_DISK_SER
)
    extends CIMRDD with Serializable
{
    implicit val spark: SparkSession = session
    implicit val storage: StorageLevel = storage_level
    implicit val log: Logger = LoggerFactory.getLogger(getClass)

    // get the list of N7 voltages
    // ToDo: fix this 1000V multiplier
    val low_voltages: Array[String] = getOrElse[BaseVoltage].filter(x => x.nominalVoltage <= 1.0).map(_.id).collect

    // get the list of allowed power system resource types
    val allowed_PSRTypes: Array[String] = Array("PSRType_Substation", "PSRType_TransformerStation")

    /**
     * Predicate to test if the given Element is a feeder.
     *
     * All elements with associated terminals will be called with this predicate.
     * If the feeder is a two (or more) terminal device this predicate should be careful to select the correct one.
     *
     * @param element the element (subclass of ConductingEquipment) to test
     * @return <code>true</code> if the equipment at this terminal is a feeder
     */
    def isFeeder (element: Element): Boolean =
    {
        element match
        {
            case c: Connector =>
                val equipment = c.ConductingEquipment
                low_voltages.contains(equipment.BaseVoltage) &&
                    allowed_PSRTypes.contains(equipment.Equipment.PowerSystemResource.PSRType)
            case _ => false
        }
    }

    /**
     * The RDD of feeders.
     *
     * @param test predicate to determine if this element:terminal pair is a feeder
     * @return the RDD of feeders
     */
    def getFeeders (test: Element => Boolean = isFeeder): RDD[Feeder] =
    {
        // get terminals keyed by their conducting equipment
        val terminals = getOrElse[Terminal].keyBy(_.ConductingEquipment)
        // get switches keyed by their unique topological node (i.e. no busbars)
        val switches = getOrElse[Switch]
            .keyBy(_.id)
            .join(terminals)
            .map(x => (x._2._2.TopologicalNode, x._2._1.id))
            .groupByKey
            .filter(x => x._2.size < 2)
            .map(x => (x._1, x._2.headOption.orNull))
        // join filtered feeders with swtches and create Feeder objects
        val feeders = getOrElse[Element]
            .keyBy(_.id)
            .join(terminals)
            .values
            .filter(x => test(x._1))
            .keyBy(_._2.TopologicalNode)
            .leftOuterJoin(switches)
            .values
            .map(x => Feeder(x._1._1.id, x._1._2.TopologicalNode, x._2.orNull))
        put(feeders, "Feeders", true)
        feeders
    }
}
