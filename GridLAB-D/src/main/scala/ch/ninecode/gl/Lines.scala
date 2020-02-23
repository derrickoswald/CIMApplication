package ch.ninecode.gl

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.cim.CIMRDD
import ch.ninecode.model._

final case class Lines (
    session: SparkSession,
    storage_level: StorageLevel = StorageLevel.fromString ("MEMORY_AND_DISK_SER")
) extends CIMRDD with Serializable
{
    import Lines._

    implicit val spark: SparkSession = session
    implicit val log: Logger = LoggerFactory.getLogger (getClass)

    def unpack (pair: (ACLineSegment, Option[Iterable[Terminal]])): Option[(ACLineSegment, Terminal, Terminal)] =
    {
        val (line, terminals) = pair
        terminals match
        {
            case Some (terminals) =>
                if (2 == terminals.size)
                {
                    val t1 = terminals.head
                    val t2 = terminals.tail.head
                    if (t1.id != t2.id && t1.TopologicalNode != t2.TopologicalNode)
                        if (t1.ACDCTerminal.sequenceNumber < t2.ACDCTerminal.sequenceNumber)
                            Some ((line, t1, t2))
                        else
                            Some ((line, t2, t1))
                    else
                        None
                }
                else
                    None
            case None => None
        }
    }

    def per_length_impedance: RDD[Element] = session.sparkContext.union (
        getOrElse[PerLengthSequenceImpedance].asInstanceOf[RDD[Element]],
        getOrElse[PerLengthPhaseImpedance].asInstanceOf[RDD[Element]] // ToDo: pick up PhaseImpedanceData
    )

    def wire_info: RDD[Element] = session.sparkContext.union (
        getOrElse[OverheadWireInfo].asInstanceOf[RDD[Element]],
        getOrElse[ConcentricNeutralCableInfo].asInstanceOf[RDD[Element]],
        getOrElse[TapeShieldCableInfo].asInstanceOf[RDD[Element]]
    )

    def refPerLengthImpedance (line: ACLineSegment): String = line.PerLengthImpedance

    def refAssetDataSheet (line: ACLineSegment): String = line.Conductor.ConductingEquipment.Equipment.PowerSystemResource.AssetDatasheet

    def topological_order (t1: Terminal, t2: Terminal): String =
        if (t1.TopologicalNode < t2.TopologicalNode)
            s"${t1.TopologicalNode}_${t2.TopologicalNode}"
        else
            s"${t2.TopologicalNode}_${t1.TopologicalNode}"

    /**
     * Create an RDD of composite ACLineSegment objects.
     *
     * @param line_filter filter to remove bad lines, default is lines > default_cable_impedance_limit Ω
     * @return the RDD of cable data
     */
    def getLines (line_filter: LineData => Boolean = impedance_limit): RDD[LineData] =
    {
        // get ac lines with two terminals
        val lines_terminals: RDD[(ACLineSegment, Terminal, Terminal)] =
            getOrElse[ACLineSegment]
            .keyBy (_.id)
            .leftOuterJoin (getOrElse[Terminal].keyBy (_.ConductingEquipment).groupByKey)
            .values
            .flatMap (unpack)

        // append parameters if any
        val lines_terminals_parameters: RDD[(ACLineSegment, Terminal, Terminal, Option[Element])] =
            lines_terminals.keyBy (x => refPerLengthImpedance (x._1)).leftOuterJoin (per_length_impedance.keyBy (_.id))
            .values
            .map (x => (x._1._1, x._1._2, x._1._3, x._2))

        // append asset info if any
        val lines_terminals_parameters_info: RDD[(ACLineSegment, Terminal, Terminal, Option[Element], Option[Element])] =
            lines_terminals_parameters.keyBy (x => refAssetDataSheet (x._1)).leftOuterJoin (wire_info.keyBy (_.id))
            .values
            .map (x => (x._1._1, x._1._2, x._1._3, x._1._4, x._2))

        // find parallel lines by grouping by alphabetically concatenated node id strings
        lines_terminals_parameters_info
            .keyBy (x => topological_order (x._2, x._3))
            .groupByKey
            .values
            .map (x => LineData (x.map (y => LineDetails (y._1, y._2, y._3, y._4, y._5))))
            .filter (line_filter)
            .persist (storage_level)
    }
}

object Lines
{
    /**
     * Maximum per length impedance for a cable (Ω/km).
     */
    var DEFAULT_CABLE_IMPEDANCE_LIMIT: Double = 5.0

    def impedance_limit (data: LineData): Boolean =
    {
        // all cable impedances are less than the limit
        data.lines.forall (line => (line.perLengthImpedance * 1000.0).z1.modulus < DEFAULT_CABLE_IMPEDANCE_LIMIT)
    }
}
