package ch.ninecode.gl

import scala.collection.Map

import org.apache.spark.rdd.RDD
import org.apache.spark.rdd.RDD.rddToPairRDDFunctions
import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.ninecode.cim.CIMRDD
import ch.ninecode.model.BaseVoltage
import ch.ninecode.model.BasicElement
import ch.ninecode.model.Bay
import ch.ninecode.model.ConductingEquipment
import ch.ninecode.model.ConnectivityNodeContainer
import ch.ninecode.model.Element
import ch.ninecode.model.Equipment
import ch.ninecode.model.EquipmentContainer
import ch.ninecode.model.EquivalentEquipment
import ch.ninecode.model.EquivalentInjection
import ch.ninecode.model.IdentifiedObject
import ch.ninecode.model.PowerSystemResource
import ch.ninecode.model.PowerTransformer
import ch.ninecode.model.PowerTransformerEnd
import ch.ninecode.model.Substation
import ch.ninecode.model.Terminal
import ch.ninecode.model.VoltageLevel

/**
 * Get information about transformers.
 * Joins PowerTransformer, PowerTransformerEnd and BaseVoltage objects to form complete details (TData) about transformers.
 *
 * @param session the Spark session
 * @param storage_level specifies the <a href="https://spark.apache.org/docs/latest/programming-guide.html#which-storage-level-to-choose">Storage Level</a> used to persist and serialize the objects
 */
class Transformers (session: SparkSession, storage_level: StorageLevel = StorageLevel.fromString ("MEMORY_AND_DISK_SER")) extends CIMRDD with Serializable
{
    implicit val spark: SparkSession = session
    implicit val log: Logger = LoggerFactory.getLogger (getClass)

    /**
     * Handle a join of power transformers and stations with the power transformer ends.
     * NOTE: Skips transformers with unusual numbers of ends.
     * @param voltages - a map of voltage name and voltage value (kV)
     * @param x - the list of transformers and stations keyed by transformer id, joined with transformer ends
     * @return a list of a pair of transformer name and a four-tuple of transformer, station, high side end with voltage and low side end with voltage
     */
    def addEnds (voltages: Map[String, Double]) (x: (String, ((PowerTransformer, Substation), Option[Iterable[PowerTransformerEnd]]))): List[(String, (PowerTransformer, Substation, (PowerTransformerEnd, Double), (PowerTransformerEnd, Double)))] =
    {
        val ends = x._2._2 match
        {
            case Some (y) => // Some (Iterable[PowerTransformerEnd])
                y.toArray.sortWith (_.TransformerEnd.endNumber < _.TransformerEnd.endNumber)
            case None =>
                Array[PowerTransformerEnd] ()
        }
        if (ends.length > 2)
            log.warn ("more than two transformer ends for %s".format (x._1))
        val ret = if (ends.length == 0)
        {
            log.error ("no transformer ends for %s".format (x._1))
            List[(String, (PowerTransformer, Substation, (PowerTransformerEnd, Double), (PowerTransformerEnd, Double)))] ()
        }
        else if (ends.length == 1)
        {
            log.error ("less than two transformer ends for %s".format (x._1))
            List[(String, (PowerTransformer, Substation, (PowerTransformerEnd, Double), (PowerTransformerEnd, Double)))] ()
        }
        else
        {
            val hv: String = ends(0).TransformerEnd.BaseVoltage
            val v0 = voltages.getOrElse (hv, 0.0)
            if (0.0 == v0)
                log.error ("no high voltage for %s %s".format (hv, x._1))
            val lv: String = ends(1).TransformerEnd.BaseVoltage
            val v1 = voltages.getOrElse (lv, 0.0)
            if (0.0 == v1)
                log.error ("no low voltage for %s %s".format (lv, x._1))
            List ((x._1, (x._2._1._1, x._2._1._2, (ends(0), v0), (ends(1), v1))))
        }
        ret
    }

    /**
     * Handle a join of power transformers, stations and transformer ends with terminals.
     * NOTE: Skips transformers with terminals not matching the transformer ends
     * @return a list of four-tuples of transformer, station, and two three-tuples of end, voltage and terminal
     */
    def addTerminals (x: (String, ((PowerTransformer, Substation, (PowerTransformerEnd, Double), (PowerTransformerEnd, Double)), Option[Iterable[Terminal]]))): List[(PowerTransformer, Substation, (PowerTransformerEnd, Double, Terminal), (PowerTransformerEnd, Double, Terminal))] =
    {
        val terminals = x._2._2 match
        {
            case Some (y) => // Some (Iterable[Terminal])
                y.toArray.sortWith (_.ACDCTerminal.sequenceNumber < _.ACDCTerminal.sequenceNumber)
            case None =>
                Array[Terminal] ()
        }
        // match terminals to ends
        val end1 = x._2._1._3
        val match1 = terminals.find (x => x.id == end1._1.TransformerEnd.Terminal)
        val end2 = x._2._1._4
        val match2 = terminals.find (x => x.id == end2._1.TransformerEnd.Terminal)
        val ret = match1 match
        {
            case Some (t1) =>
                match2 match
                {
                    case Some (t2) =>
                        List ((x._2._1._1, x._2._1._2, (end1._1, end1._2, t1), (end2._1, end2._2, t2)))
                    case None =>
                        log.error ("terminal not found for %s".format (end2._1.id))
                        List[(PowerTransformer, Substation, (PowerTransformerEnd, Double, Terminal), (PowerTransformerEnd, Double, Terminal))] ()
                }
            case None =>
                match1 match
                {
                    case Some (_) =>
                        log.error ("terminal not found for %s".format (end1._1.id))
                        List[(PowerTransformer, Substation, (PowerTransformerEnd, Double, Terminal), (PowerTransformerEnd, Double, Terminal))] ()
                    case None =>
                        log.error ("no terminals not found for %s".format (x._2._1._1.id))
                        List[(PowerTransformer, Substation, (PowerTransformerEnd, Double, Terminal), (PowerTransformerEnd, Double, Terminal))] ()
                }
        }
        ret
    }

    def addSC (voltages: Map[String, Double]) (
        default_supply_network_short_circuit_power: Double = 200.0e6,
        default_supply_network_short_circuit_impedance: Complex = Complex (0.437785783, -1.202806555))
        (arg: ((PowerTransformer, Substation, (PowerTransformerEnd, Double, Terminal), (PowerTransformerEnd, Double, Terminal)), Option[EquivalentInjection])): (PowerTransformer, Substation, (PowerTransformerEnd, Double, Terminal), (PowerTransformerEnd, Double, Terminal), EquivalentInjection) =
    {
        arg._2 match
        {
            case Some (sc) => (arg._1._1, arg._1._2, arg._1._3, arg._1._4, sc)
            case None =>
                // ToDo: fix this 1kV multiplier on the voltages
                val v1 = arg._1._3._2 * 1000.0
                val sk = default_supply_network_short_circuit_power
                val netz_r1 = default_supply_network_short_circuit_impedance.re
                val netz_x1 = default_supply_network_short_circuit_impedance.im
                //val ratioZ0Z1 = 4
                //val ratioX0R0 = 10
                //val zqt0 = zqt * ratioZ0Z1
                val netz_r0 = 0.0 // zqt0 * Math.cos (Math.abs (Math.atan (ratioX0R0)))
                val netz_x0 = 0.0 // zqt0 * Math.sin (Math.abs (Math.atan (ratioX0R0)))

                val voltage = voltages.find (_._2 == arg._1._3._2) match { case Some (v) ⇒ v._1 case None ⇒ "BaseVoltage_Unknown_%s".format (arg._1._3._2) }
                val mRID = "EquivalentInjection_" + arg._1._1.id
                val description = "default equivalent generation injection"
                val element = BasicElement (null, mRID)
                element.bitfields = Array (Integer.parseInt ("1", 2))
                val obj = IdentifiedObject (element, null, description, mRID, null, null, null)
                obj.bitfields = Array (Integer.parseInt ("110", 2))
                val psr = PowerSystemResource (obj, null, null, null, null, null, null, null, null, null, null, null)
                psr.bitfields = Array (0)
                val equipment = Equipment (psr, false, true, List(), List(), arg._1._2.id, List(), List(), List(), List(), List(), List(), List(), List(), List())
                equipment.bitfields = Array (Integer.parseInt ("10010", 2))
                val conducting = ConductingEquipment (equipment, voltage, null, null, List(), List(), null, List())
                conducting.bitfields = Array (Integer.parseInt ("1", 2))
                val equivalent = EquivalentEquipment (conducting, null)
                equivalent.bitfields = Array (0)
                val injection = EquivalentInjection (equivalent, sk, 0.0, 0.0, 0.0, 0.0, 0.0, netz_r1, netz_r0, netz_r1, false, false, 0.0, netz_x1, netz_x0, netz_x1, null)
                // note: exclude r0, x0, r2, x2 since we don't really know them and they aren't used
                // note: use RegulationStatus to indicate this is a default value, and not a real value
                injection.bitfields = Array (Integer.parseInt ("0001010001000001", 2))
                (arg._1._1, arg._1._2, arg._1._3, arg._1._4, injection)
        }
    }

    def getTransformerData (
        topological_nodes: Boolean = true,
        default_supply_network_short_circuit_power: Double = 200.0e6,
        default_supply_network_short_circuit_impedance: Complex = Complex (0.437785783, -1.202806555)): RDD[TData] =
    {
        /**
         * The name of the node associated with a terminal.
         * @param t The terminal object to get the node for.
         * @return The name of the TopologicalNode or ConnectivityNode.
         */
        def node_name (t: Terminal): String =
        {
            if (topological_nodes) t.TopologicalNode else t.ConnectivityNode
        }

        // get all transformers in substations
        val transformers = get[PowerTransformer]
        val substation_transformers = transformers.filter (_.ConductingEquipment.Equipment.PowerSystemResource.IdentifiedObject.name != "Messen_Steuern")

        // get an RDD of substations by filtering out distribution boxes
        val substations = get[Substation]
        val stations = if (null != substations)
            substations.filter (_.EquipmentContainer.ConnectivityNodeContainer.PowerSystemResource.PSRType != "PSRType_DistributionBox")
        else
        {
            val fake_element = BasicElement (null, "")
            val fake_obj = IdentifiedObject (fake_element, "fake_station", "dummy station", "", null, null, null)
            val fake_psr = PowerSystemResource (fake_obj, null, null, null, null, null, null, null, null, null, null, null)
            val fake_cncontainer = ConnectivityNodeContainer (fake_psr, null, null)
            val fake_container = EquipmentContainer (fake_cncontainer, null)
            val fake_substation = Substation (fake_container, null, null, null, null)
            session.sparkContext.parallelize (List[Substation] (fake_substation))
        }

        // the equipment container for a transformer could be a Bay, VoltageLevel or Station... the first two of which have a reference to their station
        def station_fn (x: (String, Any)) =
        {
            x match
            {
                case (_, (t: PowerTransformer, station: Substation)) => (station.id, t)
                case (_, (t: PowerTransformer, bay: Bay)) => (bay.Substation, t)
                case (_, (t: PowerTransformer, level: VoltageLevel)) => (level.Substation, t)
                case _ => throw new Exception ("unknown container type for %s".format (x._1))
            }
        }

        // create an RDD of container-transformer pairs, e.g. { (KAB8526,TRA13730), (STA4551,TRA4425), ... }
        val elements = get[Element]("Elements")
        val tpairs = if (null != get[EquipmentContainer])
            substation_transformers.keyBy (_.ConductingEquipment.Equipment.EquipmentContainer).join (elements.keyBy (_.id)).map (station_fn)
        else
            substation_transformers.map (("", _))

        // only keep the pairs where the transformer is in a substation we have
        val transformers_stations = tpairs.join (stations.keyBy (_.id)).values

        // get the transformer ends keyed by transformer
        val ends = get[PowerTransformerEnd].groupBy (_.PowerTransformer)

        // get a map of voltages
        val voltages = get[BaseVoltage].map (v => (v.id, v.nominalVoltage)).collectAsMap ()

        // attach PowerTransformerEnd elements
        val transformers_stations_plus_ends = transformers_stations.keyBy (_._1.id).leftOuterJoin (ends).flatMap (addEnds (voltages))

        // get the terminals keyed by transformer
        val terms = get[Terminal].groupBy (_.ConductingEquipment)

        // attach Terminal elements
        val transformers_stations_plus_ends_plus_terminals = transformers_stations_plus_ends.leftOuterJoin (terms).flatMap (addTerminals)

        // get equivalent injection values, if any
        val injections = if (null != get[EquivalentInjection])
            get[EquivalentInjection]
        else
            session.sparkContext.parallelize (List[EquivalentInjection] ())
        val injections_by_node = injections.keyBy (_.id).join (terms).values.map (x ⇒ (x._2.head.ConnectivityNode, x._1)) // ToDo: could be TopologicalNode?

        val transformers_stations_plus_ends_plus_terminals_plus_sc =
            transformers_stations_plus_ends_plus_terminals.keyBy (_._3._3.ConnectivityNode).leftOuterJoin (injections_by_node).values.map (addSC (voltages) (default_supply_network_short_circuit_power, default_supply_network_short_circuit_impedance))

        // convert to TData
        val transformer_data = transformers_stations_plus_ends_plus_terminals_plus_sc.map (
            x => TData (x._1, x._2, x._5, x._3._1, x._3._2, x._3._3, node_name (x._3._3), x._4._1, x._4._2, x._4._3, node_name (x._4._3))
            )
        transformer_data.persist (storage_level)
        session.sparkContext.getCheckpointDir match
        {
            case Some (_) => transformer_data.checkpoint ()
            case None =>
        }

        transformer_data
    }
}
