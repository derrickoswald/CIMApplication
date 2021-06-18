package ch.ninecode.net

;

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.cim.CIMRDD
import ch.ninecode.model.BreakerInfo
import ch.ninecode.model.Element
import ch.ninecode.model.OldSwitchInfo
import ch.ninecode.model.RecloserInfo
import ch.ninecode.model.Switch
import ch.ninecode.model.SwitchInfo
import ch.ninecode.model.Terminal
import ch.ninecode.model.UserAttribute

final case class Switches (
    session: SparkSession,
    storage_level: StorageLevel = StorageLevel.fromString("MEMORY_AND_DISK_SER")
) extends CIMRDD
{
    implicit val spark: SparkSession = session
    implicit val log: Logger = LoggerFactory.getLogger(getClass)

    def unpack (pair: (Switch, Option[Iterable[Terminal]])): Option[(Switch, Terminal, Terminal)] =
    {
        val (switch, terminals) = pair
        terminals match
        {
            case Some(terminals) =>
                terminals.toList match
                {
                    case t1 :: t2 :: Nil =>
                        if (t1.id != t2.id && t1.TopologicalNode != t2.TopologicalNode)
                            if (t1.ACDCTerminal.sequenceNumber < t2.ACDCTerminal.sequenceNumber)
                                Some((switch, t1, t2))
                            else
                                Some((switch, t2, t1))
                        else
                            None
                    case _ =>
                        None
                }
            case None => None
        }
    }

    @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
    def switch_info: RDD[Element] = session.sparkContext.union(
        getOrElse[SwitchInfo].asInstanceOf[RDD[Element]],
        getOrElse[OldSwitchInfo].asInstanceOf[RDD[Element]],
        getOrElse[BreakerInfo].asInstanceOf[RDD[Element]],
        getOrElse[RecloserInfo].asInstanceOf[RDD[Element]]
    )

    def refAssetDataSheet (switch: Switch): String = switch.ConductingEquipment.Equipment.PowerSystemResource.AssetDatasheet

    def topological_order (t1: Terminal, t2: Terminal): String =
        if (null == t1.TopologicalNode || null == t2.TopologicalNode)
            if (t1.id < t2.id)
                s"${t1.id}_${t2.id}"
            else
                s"${t2.id}_${t1.id}"
        else
            if (t1.TopologicalNode < t2.TopologicalNode)
                s"${t1.TopologicalNode}_${t2.TopologicalNode}"
            else
                s"${t2.TopologicalNode}_${t1.TopologicalNode}"

    def getFuseStandards (fuseTypes: Option[Array[String]] = None): RDD[(String, String)] =
    {
        val types = fuseTypes match
        {
            case None => Array ("DIN", "SEV")
            case Some (fuseType) => fuseType
        }
        val attributes = getOrElse[UserAttribute]
        attributes.filter(x => types.contains(x.name)).map(x => (x.value, x.name))
    }

    /**
     * Create an RDD of composite Switch objects.
     *
     * @return the RDD of cable data
     */
    def getSwitches(fuseTypes:Option[Array[String]] = None): RDD[SwitchData] =
    {
        // get switches with two terminals
        val switches_terminals: RDD[(Switch, Terminal, Terminal)] =
            getOrElse[Switch]
                .keyBy(_.id)
                .leftOuterJoin(getOrElse[Terminal].keyBy(_.ConductingEquipment).groupByKey)
                .values
                .flatMap(unpack)

        // append asset info if any
        val switches_terminals_parameters_info: RDD[(Switch, Terminal, Terminal, Option[Element])] =
            switches_terminals
                .keyBy(x => refAssetDataSheet(x._1))
                .leftOuterJoin(switch_info.keyBy(_.id))
                .values
                .map(x => (x._1._1, x._1._2, x._1._3, x._2))

        // append any standard info if any
        val switches_terminals_parameters_info_std: RDD[(Switch, Terminal, Terminal, Option[Element], Option[String])] =
            switches_terminals_parameters_info
                .keyBy(_._1.id)
                .leftOuterJoin(getFuseStandards(fuseTypes))
                .values
                .map(x => (x._1._1, x._1._2, x._1._3, x._1._4, x._2))

        // get the subclass Element
        val elements_terminals_parameters_info: RDD[(Element, Terminal, Terminal, Option[Element], Option[String])] =
            switches_terminals_parameters_info_std.keyBy(_._1.id)
                .join(getOrElse[Element].keyBy(_.id))
                .values
                .map(x => (x._2, x._1._2, x._1._3, x._1._4, x._1._5))

        // find parallel switches by grouping by alphabetically concatenated node id strings
        elements_terminals_parameters_info
            .keyBy(x => topological_order(x._2, x._3))
            .groupByKey
            .values
            .map(x => SwitchData(x.map(y => SwitchDetails(y._1, y._2, y._3, y._4, y._5))))
            .persist(storage_level)
    }
}
