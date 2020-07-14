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
) extends CIMRDD
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
                    val t1 :: t2 :: _ = terminals.toList
                    if ((null != t1.TopologicalNode) && (null != t2.TopologicalNode))
                        if (t1.ACDCTerminal.sequenceNumber < t2.ACDCTerminal.sequenceNumber)
                            Some (LineTerminals (line, t1, t2))
                        else
                            Some (LineTerminals (line, t2, t1))
                    else
                        None
                }
                else
                    None
            case None => None
        }
    }

    @SuppressWarnings (Array ("org.wartremover.warts.AsInstanceOf"))
    lazy val per_length_impedance: RDD[Element] = session.sparkContext.union (
        getOrElse[PerLengthSequenceImpedance].asInstanceOf[RDD[Element]],
        getOrElse[PerLengthPhaseImpedance].asInstanceOf[RDD[Element]] // ToDo: pick up PhaseImpedanceData
    )

    @SuppressWarnings (Array ("org.wartremover.warts.AsInstanceOf"))
    lazy val wire_info: RDD[Element] = session.sparkContext.union (
        getOrElse[OverheadWireInfo].asInstanceOf[RDD[Element]],
        getOrElse[ConcentricNeutralCableInfo].asInstanceOf[RDD[Element]],
        getOrElse[TapeShieldCableInfo].asInstanceOf[RDD[Element]]
    )

    def refPerLengthImpedance (lt: LineTerminals): String = lt.line.PerLengthImpedance

    def refAssetDataSheet (lt: LineTerminals): String = lt.line.Conductor.ConductingEquipment.Equipment.PowerSystemResource.AssetDatasheet

    def topological_order (lt: LineTerminals): String =
        if (null == lt.t1.TopologicalNode || null == lt.t2.TopologicalNode)
            if (null == lt.t1.ConnectivityNode || null == lt.t2.ConnectivityNode)
            {
                log.warn (s"${lt.line.id} has no topological or connectivity nodes on terminals ${lt.t1.id} or ${lt.t2.id}")
                if (lt.t1.id < lt.t2.id)
                    s"${lt.t1.id}_${lt.t2.id}"
                else
                    s"${lt.t2.id}_${lt.t1.id}"
            }
            else
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
     * @param line_filter filter to remove bad lines, default is lines > DEFAULT_CABLE_RESISTANCE_LIMIT Ω/km && in use
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
     * Maximum per length R1 resistance for a cable (Ω/km).
     */
    var DEFAULT_CABLE_RESISTANCE_LIMIT: Double = 5.0

    /**
     * Checks that the line segment resistance is not too large.
     *
     * @note The use of high impedance cables in GridLAB-D leads to long convergence times and
     *       often failures to converge. We use a rule of thumb that drops these cables from consideration.
     *
     * @param data the ACLineSegment data to check
     * @return <code>true</code> if all cable per length resistances are less than the limit
     */
    def impedance_limit (data: LineData): Boolean =
    {
        data.lines.forall (line => (line.perLengthImpedance * 1000.0).z1.re < DEFAULT_CABLE_RESISTANCE_LIMIT)
    }

    /**
     * Checks that the line segment is in use according to the related SvStatus.
     *
     * @note The CIM 16 reference from ConductingEquipment to SvStatus, was changed in CIM100 to be a
     *       reference from SvStatus to ConductingEquipment. This method relies on the incorrect
     *       (denormalized) CIM exports that have the original CIM16 reference *and* a also uses a
     *       hard-coded mRID for (the one) SvStatus that has SvStatus.inService == false.
     *       ToDo: This should be changed to get a list of SvStatus mRID where inService == "false"
     *       or add the SvStatus to the LineDetails via join when the CIM export is corrected.
     *
     * @param data the ACLineSegment data to check
     * @return <code>true</code> if this is an "in use" line segment
     */
    def in_use (data: LineData): Boolean =
    {
        data.lines.forall (
            line =>
            {
                val status = line.line.Conductor.ConductingEquipment.SvStatus
                status match
                {
                    case h :: _ => h != "not_in_use"
                    case _ => true
                }
            }
        )
    }

    /**
     * Checks that there are two different topological nodes on each end of the line segment.
     *
     * @note The CIMNetworkTopologyProcessor currently uses a simplistic check for a non-zero length
     *       and a non-zero impedance (ACLineSegment.r + ACLineSegment.x j) which collapses some
     *       erroneous ACLineSegments to start and end on the same TopologicalNode.
     *       When the CIMNetworkTopologyProcessor is changed to be smarter, this can be removed.
     *
     * @param data the ACLineSegment data to check
     * @return <code>true</code> if this is a valid edge in the topology
     */
    def topological_edge (data: LineData): Boolean = data.node0 != data.node1

    /**
     * Predicate to eliminate invalid ACLineSegments.
     *
     * @param data the ACLineSegment data to check
     * @return <code>true</code> if this is a valid line segment
     */
    def filter (data: LineData): Boolean = topological_edge (data) && in_use (data) && impedance_limit (data)
}
