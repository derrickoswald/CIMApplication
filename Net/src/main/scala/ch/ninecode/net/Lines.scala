package ch.ninecode.net;

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.cim.CIMRDD
import ch.ninecode.model.ACLineSegment
import ch.ninecode.model.ConcentricNeutralCableInfo
import ch.ninecode.model.Element
import ch.ninecode.model.OverheadWireInfo
import ch.ninecode.model.PerLengthPhaseImpedance
import ch.ninecode.model.PerLengthSequenceImpedance
import ch.ninecode.model.TapeShieldCableInfo
import ch.ninecode.model.Terminal

final case class LineTerminals (line: ACLineSegment, t1: Terminal, t2: Terminal)

final case class Lines (
    session: SparkSession,
    storage_level: StorageLevel = StorageLevel.fromString ("MEMORY_AND_DISK_SER")
) extends CIMRDD with Serializable
{
    import Lines._

    implicit val spark: SparkSession = session
    implicit val log: Logger = LoggerFactory.getLogger (getClass)
    implicit val static_line_details: LineDetails.StaticLineDetails = LineDetails.StaticLineDetails ()

    def unpack (pair: (ACLineSegment, Option[Iterable[Terminal]])): Option[LineTerminals] =
    {
        val (line, terminals) = pair
        terminals match
        {
            case Some (terminals) =>
                if (2 == terminals.size)
                {
                    val (t1 :: t2 :: _) = terminals.toList
                    if (t1.ACDCTerminal.sequenceNumber < t2.ACDCTerminal.sequenceNumber)
                        Some (LineTerminals (line, t1, t2))
                    else
                        Some (LineTerminals (line, t2, t1))
                }
                else
                    None
            case None => None
        }
    }

    lazy val per_length_impedance: RDD[Element] = session.sparkContext.union (
        getOrElse[PerLengthSequenceImpedance].asInstanceOf[RDD[Element]],
        getOrElse[PerLengthPhaseImpedance].asInstanceOf[RDD[Element]] // ToDo: pick up PhaseImpedanceData
    )

    lazy val wire_info: RDD[Element] = session.sparkContext.union (
        getOrElse[OverheadWireInfo].asInstanceOf[RDD[Element]],
        getOrElse[ConcentricNeutralCableInfo].asInstanceOf[RDD[Element]],
        getOrElse[TapeShieldCableInfo].asInstanceOf[RDD[Element]]
    )

    def refPerLengthImpedance (lt: LineTerminals): String = lt.line.PerLengthImpedance

    def refAssetDataSheet (lt: LineTerminals): String = lt.line.Conductor.ConductingEquipment.Equipment.PowerSystemResource.AssetDatasheet

    def topological_order (lt: LineTerminals): String =
        if (null == lt.t1.TopologicalNode || null == lt.t2.TopologicalNode)
            if (lt.t1.ConnectivityNode < lt.t2.ConnectivityNode)
                s"${lt.t1.ConnectivityNode}_${lt.t2.ConnectivityNode}"
            else
                s"${lt.t2.ConnectivityNode}_${lt.t1.ConnectivityNode}"
        else
            if (lt.t1.TopologicalNode < lt.t2.TopologicalNode)
                s"${lt.t1.TopologicalNode}_${lt.t2.TopologicalNode}"
            else
                s"${lt.t2.TopologicalNode}_${lt.t1.TopologicalNode}"

    /**
     * Create an RDD of composite ACLineSegment objects.
     *
     * @param line_filter filter to remove bad lines, default is lines > default_cable_impedance_limit Ω && in use
     * @return the RDD of cable data
     */
    def getLines (line_filter: LineData => Boolean = filter): RDD[LineData] =
    {
        // get ac lines with two terminals
        val tt = getOrElse[Terminal].keyBy (_.ConductingEquipment).groupByKey
        val lines_terminals =
            getOrElse[ACLineSegment]
            .keyBy (_.id)
            .leftOuterJoin (tt)
            .values
            .flatMap (unpack)

        // append parameters if any
        val lines_terminals_parameters: RDD[(LineTerminals, Option[Element])] =
            lines_terminals.keyBy (x => refPerLengthImpedance (x)).leftOuterJoin (per_length_impedance.keyBy (_.id))
            .values
            .map (x => (x._1, x._2))

        // append asset info if any
        val lines_terminals_parameters_info: RDD[(LineTerminals, Option[Element], Option[Element])] =
            lines_terminals_parameters.keyBy (x => refAssetDataSheet (x._1)).leftOuterJoin (wire_info.keyBy (_.id))
            .values
            .map (x => (x._1._1, x._1._2, x._2))

        // find parallel lines by grouping by alphabetically concatenated node id strings
        lines_terminals_parameters_info
            .keyBy (x => topological_order (x._1))
            .groupByKey
            .values
            .map (x => LineData (x.map (y => LineDetails (y._1.line, y._1.t1, y._1.t2, y._2, y._3))))
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

    def in_use (data: LineData): Boolean =
    {
        data.lines.forall (
            line =>
            {
                val status = line.line.Conductor.ConductingEquipment.SvStatus
                if (null != status)
                    // ToDo: get a list of SvStatus element mRID where inService == "false"
                    status.head != "not_in_use"
                else
                    true
            }
        )
    }

    def filter (data: LineData): Boolean = in_use (data) && impedance_limit (data)
}
